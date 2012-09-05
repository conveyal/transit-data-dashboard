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

package models;

/**
 * The type of a FareConfiguration.
 * @author mattwigway
 *
 */
public enum FareServiceType {
    NYC ("org.opentripplanner.routing.impl.NycFareServiceImpl"), 
    SF_BAY_AREA ("org.opentripplanner.routing.impl.SFBayAreaFareServiceImpl"),
    // Used when addition config of default is needed
    DEFAULT ("org.opentripplanner.routing.impl.DefaultFareServiceImpl"),
    TIME_BASED_BIKE_RENTAL ("org.opentripplanner.routing.bike_rental.TimeBasedBikeRentalFareService");
    
    private String classRef;
    
    private FareServiceType(String classRef) {
        this.classRef = classRef;
    }
    
    public String getClassName () {
        return classRef;
    }
}
