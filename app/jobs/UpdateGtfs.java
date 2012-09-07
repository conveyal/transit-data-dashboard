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

import play.db.jpa.NoTransaction;
import play.jobs.Job;
import updaters.UpdaterFactory;
import play.modules.spring.Spring;

/**
 * Run all the updaters and call all their hooks.
 * @author mattwigway
 *
 */
//@Every('24h')
@NoTransaction
public class UpdateGtfs extends Job {
	public void doJob () {
		UpdaterFactory factory = Spring.getBeanOfType(UpdaterFactory.class);
		factory.update();
	}
}
