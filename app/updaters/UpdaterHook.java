package updaters;

import java.util.List;
import java.util.Set;

import models.GtfsFeed;
import models.MetroArea;


/**
 * This interface defines hooks to be executed when feeds change.
 * @author mattwigway
 */
public interface UpdaterHook {
	public void update(Set<MetroArea> areas);
}
