package jobs;

import play.jobs.Job;
import updaters.UpdaterFactory;
import play.modules.spring.Spring;

/**
 * Run all the updaters and call all their hooks.
 * @author mattwigway
 *
 */
//@Every('24h')
public class UpdateGtfs extends Job {
	public void doJob () {
		UpdaterFactory factory = Spring.getBeanOfType(UpdaterFactory.class);
		factory.update();
	}
}
