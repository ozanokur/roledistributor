package ozan.coolstuff.roledistributor;

import java.util.Set;

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
public class RoleInfo {
	Set<String> currentRoles;
	String operatedRole;
	RoleOperation operation;
}
