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

import java.util.Set;

import play.Logger;

import models.MetroArea;

/**
 * An updater hook that just logs all the updated metros.
 * @author mattwigway
 *
 */
public class LoggingUpdaterHook implements UpdaterHook {

	@Override
	public void update(Set<MetroArea> areas) {
		String out = "";
		boolean needsComma = false;
		for (MetroArea area : areas) {
			if (needsComma)
				out += ", ";
			else
				needsComma = true;
			
			out += area.toString();
		}
		
		Logger.info("Updated areas: " + out);
	}
}
