package ozan.coolstuff.roledistributor;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(RoleDistributionCondition.class)
@ComponentScan("ozan.coolstuff.roledistributor")
public class RoleDistributorEnabler {

}
