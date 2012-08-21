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
			
			out += area.name;
		}
		
		Logger.info("Updated areas: " + out);
	}
}
