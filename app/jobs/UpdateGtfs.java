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

import play.Logger;
import play.db.jpa.NoTransaction;
import play.jobs.Job;
import play.jobs.On;
import updaters.UpdaterFactory;
import play.modules.spring.Spring;

/**
 * Run all the updaters and call all their hooks.
 * @author mattwigway
 *
 */
// run at 8am GMT (3/4 am EST/EDT, 12/1 am PST/PDT)
@On("0 0 15 * * ?")
@NoTransaction
public class UpdateGtfs extends Job {
    /** Is this job currently running? This prevents trying to update GTFS twice at the same time */
    private static boolean running = false;
    
	public void doJob () {
	    if (UpdateGtfs.running) {
	        Logger.warn("Not running updater more than once concurrently!");
	        return;
	    }
	    
	    try {
	        UpdateGtfs.running = true;
	    
	        UpdaterFactory factory = Spring.getBeanOfType(UpdaterFactory.class);
	        factory.update();
	    }
	    finally {
	        UpdateGtfs.running = false;
	    }
	}
}
