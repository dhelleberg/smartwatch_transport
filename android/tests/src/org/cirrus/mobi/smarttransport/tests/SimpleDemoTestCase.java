/*
 * This file is part of SmartTransport
 *
 * SmartTransport is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SmartTransport is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SmartTransport.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cirrus.mobi.smarttransport.tests;

import android.location.Location;
import android.test.AndroidTestCase;
import android.util.Log;
import de.schildbach.pte.SbbProvider;
import de.schildbach.pte.dto.*;

import java.util.List;

/**
 * Created by dhelleberg on 02/02/14.
 * Used to test simple problems from users
 */
public class SimpleDemoTestCase  extends AndroidTestCase {

    private static final double[] TEST_LOCATION_BIEL_SCHWEITZ = {47.135712, 7.262539};


    public void testSbbProvider() throws Exception
    {
        SbbProvider provider = new SbbProvider(null);
        Location dusLocation = new Location("FakeProvider");
        dusLocation.setLatitude(TEST_LOCATION_BIEL_SCHWEITZ[0]);
        dusLocation.setLongitude(TEST_LOCATION_BIEL_SCHWEITZ[1]);
        NearbyStationsResult nearbyStationsResult = provider.queryNearbyStations(convertLocation(dusLocation), 1000, 10);

        List<de.schildbach.pte.dto.Location> stations = nearbyStationsResult.stations;
        for (de.schildbach.pte.dto.Location station : stations) {
            Log.d("TEST!","station: " + station.name);

            QueryDeparturesResult queryDeparturesResult = provider.queryDepartures(station.id, 10, true);
            List<StationDepartures> stationDepartures = queryDeparturesResult.stationDepartures;
            for (StationDepartures stationDeparture : stationDepartures) {
                Log.d("TEST","Depature: "+stationDeparture);
                List<Departure> departures = stationDeparture.departures;
                for (Departure departure : departures) {
                    Log.d("TEST","dep: line: "+departure.line+ " dest: "+ departure.destination);
                }
            }

        }

    }
    private de.schildbach.pte.dto.Location convertLocation(Location location)
    {
        return new de.schildbach.pte.dto.Location(LocationType.ANY, (int)(location.getLatitude()*1E6), (int)(location.getLongitude()*1E6));
    }
}
