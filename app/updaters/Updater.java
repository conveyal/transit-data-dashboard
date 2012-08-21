package updaters;

import java.util.Set;

import models.MetroArea;

/**
 * An updater that fetches GTFS from somewhere and calls hooks on the changed DB.
 * @author mattwigway
 *
 */
public interface Updater {
	public Set<MetroArea> update();
}
