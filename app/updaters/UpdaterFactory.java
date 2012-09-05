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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.MetroArea;

public class UpdaterFactory {
	private List<Updater> updaters;
	private List<UpdaterHook> hooks;
	
	public List<UpdaterHook> getHooks() {
		return hooks;
	}
	
	public void setHooks(List<UpdaterHook> hooks) {
		this.hooks = hooks;
	}
	
	public List<Updater> getUpdaters() {
		return updaters;
	}
	
	public void setUpdaters(List<Updater> updaters) {
		this.updaters = updaters;
	}
	
	public void update () {
		Set<MetroArea> metros = new HashSet<MetroArea>();
		
		for (Updater updater : updaters) {
			metros.addAll(updater.update());
		}
		
		for (UpdaterHook hook : hooks) {
			hook.update(metros);
		}
	}
}
