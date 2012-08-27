package updaters;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import models.MetroArea;

/**
 * Generates a deployment plan and dispatches to Deployer for each changed metro area.
 * @author mattwigway
 *
 */
public class DeploymentPlanGeneratorHook implements UpdaterHook {

	@Override
	public void update(Set<MetroArea> areas) {
		for (MetroArea area : areas) {
		    if (!area.disabled) {

		        // generate the plan
		        DeploymentPlan plan = new DeploymentPlan(area);

		        // and dispatch the JSON
		        /*
			try {
				FileWriter writer = new FileWriter("/tmp/" + area.name + ".json");
				writer.write(plan.toJson());
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		         */ 
		    }
		}
	}
}
