package ozan.coolstuff.roledistributor;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnBean({RoleChangeHandler.class, RoleConfiguration.class})
@Conditional(RoleDistributionCondition.class)
@EnableScheduling
@Slf4j
public class RoleDistribution {
	@Autowired
	CuratorFramework client;
	@Autowired
	RoleConfiguration roleConfiguration;
	@Autowired
	RoleChangeHandler roleChangeHandler;
	
	private String defaultParentPath = "/roles/";
	private String randomInstanceId = java.util.UUID.randomUUID().toString();
	
	private Set<String> selectedRolesForInstance = Collections.synchronizedSet(new HashSet<>());
	private Map<String, LeaderLatch> latchMap = Collections.synchronizedMap(new HashMap<>());
	
	@PostConstruct
	public void initializeRoles() throws Exception {
		log.debug("Initializing latches for roles: {}", getRoles());
		for (String role : getRoles()) {
			if (latchMap.containsKey(role)) operateOnInstanceRoles(role, RoleOperation.RELATCH);
			else createLatch(role);
		}

	}

	private void createLatch(String role) throws Exception {
		log.debug("Creating latch for role: {}", role);
		LeaderLatch latch = new LeaderLatch(client, getParentPath() + role, getInstanceId());
		LeaderLatchListener listener = new LeaderListener(role, this);
		addLatchToMap(role, latch);
		latch.addListener(listener);
		latch.start();
	}

	public void addLatchToMap(String role, LeaderLatch latch) {
		latchMap.put(role, latch);
	}
	
	@Scheduled(fixedDelayString = "${roleDistribution.balanceScheduleInMillis:20000}")
	public void handleRoleBalance() throws Exception {
		log.debug("Balancing latches for roles: {}", getRoles());
		roleConfiguration.getRoles().forEach(role -> operateOnInstanceRoles(role, RoleOperation.BALANCE));
		latchMap.keySet().stream()
			.filter(role -> !roleConfiguration.getRoles().contains(role))
			.forEach(role -> operateOnInstanceRoles(role, RoleOperation.RELEASE));

	}
	
	public synchronized void operateOnInstanceRoles(String role, RoleOperation operation) {
		switch (operation) {
		case ADD: {
			log.debug("Adding role to instance: {}", role);
			addRoleToInstance(role);
			break;
		}
		case REMOVE: {
			log.debug("Removing role from instance: {}", role);
			removeRoleFromInstance(role);
			break;
		}
		case RELATCH: {
			log.debug("Relatching role: {}", role);
			relatchRole(role);
			break;
		}
		case RELEASE: {
			log.debug("Releasing role: {}", role);
			releaseRole(role);
			break;
		}
		case BALANCE: {
			log.debug("Balancing role: {}", role);
			balanceRoleOfInstance(role);
			break;
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + operation);
		}
	}

	public List<String> getRoles() {
		return roleConfiguration.getRoles();
	}

	private void addRoleToInstance(String role) {
		if (isRoleAcceptable(role)) {
			addRole(role);
		}
		else {
			relatchRole(role);
		}
		
	}

	private boolean isRoleAcceptable(String role) {
		try {
			int participantCount = latchMap.get(role).getParticipants().size();
			int maximumAcceptableRolesForInstance = (int) Math.ceil((float) getRoles().size() / participantCount);
			return selectedRolesForInstance.size() > maximumAcceptableRolesForInstance ? false : true;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void addRole(String role) {
		log.debug("added role: {}", role);
		selectedRolesForInstance.add(role);
		RoleInfo roleInfo = RoleInfo.builder()
			.currentRoles(new HashSet<>(selectedRolesForInstance))
			.operation(RoleOperation.ADD)
			.operatedRole(role)
			.build();
		roleChangeHandler.onRoleChange(roleInfo);
	}

	private void relatchRole(String role) {
		try {
			releaseRole(role);
			createLatch(role);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void removeRoleFromInstance(String role) {
		log.debug("removed role: {}", role);
		selectedRolesForInstance.remove(role);
		RoleInfo roleInfo = RoleInfo.builder()
			.currentRoles(new HashSet<>(selectedRolesForInstance))
			.operation(RoleOperation.REMOVE)
			.operatedRole(role)
			.build();
		roleChangeHandler.onRoleChange(roleInfo);
		
	}


	private void releaseRole(String role) {
		try {
			removeRoleFromInstance(role);
			latchMap.get(role).close();
			latchMap.remove(role);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void balanceRoleOfInstance(String role) {
		if (selectedRolesForInstance.contains(role) && !isRoleAcceptable(role)) {
			relatchRole(role);
		}
	}

	public String getInstanceId() {
		return roleConfiguration.getInstanceId() == null ? randomInstanceId : roleConfiguration.getInstanceId();
	}

	public String getParentPath() {
		return roleConfiguration.getParentPath() == null ? defaultParentPath : roleConfiguration.getParentPath();
	}
}