/* 
  This program is free software: you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public License
  as published by the Free Software Foundation, either version 3 of
  the License, or (props, at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program. If not, see <http://www.gnu.org/licenses/>. 
*/

package jobs;

import javax.inject.Inject;

import play.Logger;
import play.db.jpa.NoTransaction;
import play.jobs.Job;
import play.jobs.On;
import updaters.UpdaterFactory;

/**
 * Run all the updaters and call all their hooks.
 * @author mattwigway
 *
 */
// run at 7:37am GMT (2:37/3:37 am EST/EDT, 11:37 pm/12:37 am PST/PDT)
// the reason for the odd time is because it seems likely that a good number of people
// start cron jobs on the hour on AWS; this will fall between those.
@On("0 37 7 * * ?")
public class UpdateGtfs extends Job {
    /** Is this job currently running? This prevents trying to update GTFS twice at the same time */
    private static boolean running = false;
    
    /** 
     * The updater factory; injected because Spring.getBeanOfType doesn't work in a job controlled
     * by the @On annotation.
     */
    @Inject
    private static UpdaterFactory factory;
    
	public void doJob () {
	    if (UpdateGtfs.running) {
	        Logger.warn("Not running updater more than once concurrently!");
	        return;
	    }
	    
	    try {
	        UpdateGtfs.running = true;
	    
	        UpdateGtfs.factory.update();
	    }
	    finally {
	        UpdateGtfs.running = false;
	    }
	}
}
