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

package controllers;

import models.MetroArea;
import models.NtdAgency;
import play.mvc.Controller;

/**
 * Database migrations
 * 
 * @author mattwigway
 *
 */
public class Migrations extends Controller {
    /**
     * Migrate a database structure from a single metro area per agency to multiple metros per agency
     */
    @SuppressWarnings("deprecation")
    public static void metroAreaSingleToMany () {
        MetroArea metro;
        
        for (NtdAgency agency : NtdAgency.<NtdAgency>findAll()) {
            metro = agency.metroArea;
            
            if (metro == null)
                continue;
            
            agency.metroArea = null;
            
            metro.agencies.add(agency);
            agency.save();
            metro.save();
        }
        
        renderJSON("{\"status\":\"success\"}");
    }
}
