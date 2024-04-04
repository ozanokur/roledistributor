package ozan.coolstuff.roledistributor;

import org.apache.curator.framework.recipes.leader.LeaderLatchListener;

public class LeaderListener implements LeaderLatchListener {

	String role;
	RoleDistribution roleDistribution;

	public LeaderListener(String role, RoleDistribution roleDistribution) {
		this.role = role;
		this.roleDistribution = roleDistribution;
	}

	@Override
	public void isLeader() {
		roleDistribution.operateOnInstanceRoles(role, RoleOperation.ADD);
	}

	@Override
	public void notLeader() {
		roleDistribution.operateOnInstanceRoles(role, RoleOperation.REMOVE);
	}

}
