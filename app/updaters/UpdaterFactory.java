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
