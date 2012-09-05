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

import org.junit.*;

import java.io.File;
import java.util.*;

import play.Play;
import play.test.*;
import updaters.FeedStatsCalculator;
import models.*;

public class FeedStatsTest extends UnitTest {

    @Test
    public void testStartEndDates () throws Exception {
        // http://stackoverflow.com/questions/4719891
        File path = new File(Play.applicationPath, "test/gtfs");
     
        // This feed has all service defined by calendar_dates.txt
        File in = new File (path, "datesOnly.zip");
        
        FeedStatsCalculator stats = new FeedStatsCalculator(in);

        // 1 August 2012
        assertEquals(new Date(112, 7, 1), stats.getStartDate());
        // 10 August 2012
        assertEquals(new Date(112, 7, 10), stats.getEndDate());
        
        
        
        // This feed has a calendar.txt with start and end dates that have no
        // service because they are days of the week that said service IDs have no service
        in = new File(path, "calendarShrinking.zip");
        stats = new FeedStatsCalculator(in);
        
        // 15 August 2012
        assertEquals(new Date(112, 7, 16), stats.getStartDate());
        // 16 August 2012
        assertEquals(new Date(112, 7, 17), stats.getEndDate());
        
        
        
        // This feed has a calendar.txt with the first and last days removed by calendar_dates.txt
        in = new File(path, "holesAtStartAndEnd.zip");
        stats = new FeedStatsCalculator(in);
        
        // 2 January 2012
        assertEquals(new Date(112, 0, 2), stats.getStartDate());
        // 30 December 2012
        assertEquals(new Date(112, 11, 30), stats.getEndDate());
    }

}
