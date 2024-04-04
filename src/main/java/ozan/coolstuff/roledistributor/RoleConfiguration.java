package ozan.coolstuff.roledistributor;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleConfiguration {
	private String parentPath;
	private String instanceId;
	private List<String> roles;

}