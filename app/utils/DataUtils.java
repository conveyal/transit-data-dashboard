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

package utils;

import java.io.StringWriter;
import java.io.IOException;
import java.util.List;
import au.com.bytecode.opencsv.CSVWriter;
import proxies.Proxy;

public class DataUtils {
    public static String encodeCsv(List<Proxy> objects) {
        StringWriter out = new StringWriter(100000);
        CSVWriter writer = new CSVWriter(out);

        // write header
        writer.writeNext(objects.get(0).toHeader());
        
        // loop and write all lines
        for (Proxy object : objects) {
            writer.writeNext(object.toRow());
        }

        // since there are no files involved we don't need to worry about
        // IOExceptions.
        try {
            writer.close();
        } catch (IOException e) {}

        try {
            out.close();
        } catch (IOException e) {}

        return out.toString();
    }
}