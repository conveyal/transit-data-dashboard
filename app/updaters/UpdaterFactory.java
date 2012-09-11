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

import lombok.Data;
import models.MetroArea;

public class UpdaterFactory {
	/**
     * @return the updaters
     */
    public List<Updater> getUpdaters() {
        return updaters;
    }

    /**
     * @param updaters the updaters to set
     */
    public void setUpdaters(List<Updater> updaters) {
        this.updaters = updaters;
    }

    /**
     * @return the hooks
     */
    public List<UpdaterHook> getHooks() {
        return hooks;
    }

    /**
     * @param hooks the hooks to set
     */
    public void setHooks(List<UpdaterHook> hooks) {
        this.hooks = hooks;
    }

    /**
     * @return the storer
     */
    public FeedStorer getStorer() {
        return storer;
    }

    /**
     * @param storer the storer to set
     */
    public void setStorer(FeedStorer storer) {
        this.storer = storer;
    }

    private List<Updater> updaters;
	private List<UpdaterHook> hooks;
	private FeedStorer storer;
	
	public void update () {
		Set<MetroArea> metros = new HashSet<MetroArea>();
		
		for (Updater updater : updaters) {
			metros.addAll(updater.update(storer));
		}
		
		for (UpdaterHook hook : hooks) {
			hook.update(metros);
		}
	}
}
