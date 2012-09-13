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
 * A ReviewType handles the options for when the action is ambiguous and must be reviewed by a human.
 * @author matthewc
 *
 */
public enum ReviewType {
    NONE ("No review needed"),
    NO_AGENCY ("Feed matches no agency"),
    AGENCY_MULTIPLE_AREAS ("Agency matches multiple metro areas"),
    NO_METRO ("Agency matches no metros.");
    
    private String description;
    
    private ReviewType (String options) {
        this.description = options;
    }
    
    public String toString () {
        return description;
    }
}
