# Role Distributor

## Purpose

This is a spring boot library that enables instance based behavior by assigning roles to instances using Zookeeper/Curator. 
Roles are distributed among subscribing instances in a balanced fashion, meaning that there wont be a piling up of roles in a single instance.
Each time a role is assigned to or taken away from the instance, it will be notified.

## Example

Lets say there are 4 roles, and 3 instances in this fashion:
Roles: "A", "B", "C", "D"
Instances: 1, 2, 3

After assigning the roles, an example distribution might look like this:

| Instance 1 | Instance 2 | Instance 3 |
|------------|------------|------------|
| B, C       | A          | D          |

If instance 2 dies, the role will be taken away and given to a different instance. Because balance is a factor when assigning roles, eventual role distribution will be this way:

| Instance 1 | ~~Instance 2~~ | Instance 3 |
|------------|------------|------------|
| B, C       |            | D, A       |

## Usage

### Code
2 Beans must be created by the user for the system to work. A RoleConfiguration, and a RoleChangeHandler. Also if the user needs additional component scanning, they can import RoleDistributorEnabler.

#### RoleDistributorEnabler
```
@SpringBootApplication
@Import(RoleDistributorEnabler.class)
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
```

#### RoleConfiguration
The configuration that determines which roles will be distributed among instances, the instanceId and zookeeper path. Roles are mandatory, others are optional.
```
	@Bean
	RoleConfiguration roleConfiguration() {
		return RoleConfiguration.builder()
				.roles(List.of("wew", "lad")) 	// required
				.parentPath("/dc1-path/")		// optional
				.instanceId("instance1")		// optional
				.build();
	}
```

#### RoleChangeHandler
This bean will be notified when there is a role change.
```
@Component
public class TestRoleChangeHandler implements RoleChangeHandler {
	@Override
	public void onRoleChange(RoleInfo roleInfo) {
		System.out.println("Current roles: " + roleInfo.getCurrentRoles());
		switch (roleInfo.getOperation()) {
		case ADD: {
			System.out.println("Added role: " + roleInfo.getOperatedRole());
			break;
		}
		case REMOVE: {
			System.out.println("Removed role: " + roleInfo.getOperatedRole());
			break;
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + roleInfo.getOperation());
		}
	}
}
```

### Properties
System must be enabled from the properties. If there is no other CuratorFramework bean on your server, you can just give the zookeeper servers as a property and the system will create the required bean.
System tries to balance its roles using fixed intervals. balanceScheduleInMillis can be set from the properties to adjust the timing.
```
roleDistribution:
  enabled: true
  balanceScheduleInMillis: 30000
  zookeeper:
    servers: 127.0.0.1:2181
```

## Inner Workings

Each role is a LeaderLatch. If there are 10 roles, that means that there are 10 leader elections. When an instance is elected as the leader, it basically means that that instance has that role. When an instance goes down, it relinquishes its leadership and a new leader is elected, meaning the role will be redistributed.
Each instance continuously checks if there is an imbalance in the distribution of roles, by checking how many roles they have compared to how many instances there are. If they have too many roles, they will relinquish their leadership (of the role) and that role will be redistributed to the remaining instances.
