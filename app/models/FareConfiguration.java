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
import javax.persistence.OneToOne;

import play.db.jpa.Model;

/**
 * Represents a fare service that should be applied to the graph. They can be chained together.
 * Note that while you can have two initial fare services in different metros point to the same
 * next fare service, such a configuration is not recommended because then changes in the options
 * to one fare service will break the other metro.
 * 
 * @author mattwigway
 */
@Entity
public class FareConfiguration extends Model {
    /**
     * A human-readable comment for this fare service
     */
    public String comment;
    
    /**
     * The type of this fare service.
     */
    public FareServiceType type;
    
    /**
     * The options for this fare service, in XML/Spring IOC format.
     */
    public String springOptions;
    
    /**
     * The next fare service for chained implementations
     */
    @OneToOne
    public FareConfiguration nextFareService;
    
    public String toString() {
        return this.comment;
    }
}
