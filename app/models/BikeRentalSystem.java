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

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;

import com.google.gson.annotations.Expose;

import play.data.validation.URL;
import play.db.jpa.Model;

@Entity
public class BikeRentalSystem extends Model {
	/**
	 * The metro area this bike rental system is in.
	 */
	@ManyToOne
	public MetroArea metroArea;
	
	/**
	 * The name of this bike rental system.
	 */
	public String name;
	
	/**
	 * The ID in the CityBik.es database.
	 */
	public Integer cityBikesId;
	
    /**
     * Machine readable problem type requiring human review - picked up in the admin interface.
     */
    @Enumerated(EnumType.STRING)
    public ReviewType review;
	
	/**
	 * The type of this bike rental system.
	 * One of KeolisRennes, Static, CityBik.es or Bixi
	 * For people parsing deployment requests: static indicates that staticBikeRental should be
	 * true on OpenStreetMapGraphBuilderImpl; the others indicate that the correct class should be
	 * used: see https://github.com/openplans/OpenTripPlanner/wiki/Bike-Rental
	 */
	@Enumerated(EnumType.STRING)
	public BikeRentalSystemType type;
	
	/**
	 * The URL to grab updates from.
	 */
	@URL
	public String url;
	
	/**
	 * The currency for the fares.
	 */
	public String currency;
	
	/**
	 * The fare classes
	 */
	@ElementCollection
	public List<String> fareClasses; 
	
	public BikeRentalSystem () {
	    this.metroArea = null;
	}
	
	public String toString () {
	    return name + " (" + type.toString() + " system)"; 
	}
}
