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
 * Are bikes allowed on this agency? Values are YES, NO and WARN, which indicates that we aren't 
 * sure and the user needs to check.
 * @author matthewc
 *
 */
public enum DefaultBikesAllowedType {
    YES, NO, WARN;
}
