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

package updaters;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import play.Play;
import play.db.jpa.JPAPlugin;
import play.db.jpa.Transactional;

import deployment.DeploymentPlan;

import models.MetroArea;

/**
 * Generates a deployment plan and dispatches to Deployer for each changed metro area.
 * @author mattwigway
 *
 */
public class DeploymentPlanGeneratorHook implements UpdaterHook {

	@Override
	// this is not read only because rebuild scheduling needs a read-write transaction.
	@Transactional(readOnly=false)
	public void update(Set<MetroArea> areas) {
	    JPAPlugin.startTx(true);
	    
		for (MetroArea area : areas) {
		    area.rebuild();
		}
		JPAPlugin.closeTx(false);
	}
}
