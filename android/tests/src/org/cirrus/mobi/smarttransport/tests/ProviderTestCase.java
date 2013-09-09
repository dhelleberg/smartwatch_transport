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
import de.schildbach.pte.BahnProvider;
import de.schildbach.pte.VbbProvider;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import junit.framework.Assert;

public class ProviderTestCase extends AndroidTestCase
{

    private static final double[] LOCATION_DUSSELDORF = {51.220250,6.793177};
    private static final double[] LOCATION_BERLIN = {52.526110,13.368752};

    @Override
    protected void setUp() throws Exception {
        Log.v(">>>>>>>>>>>>>>", "SETUP");
        super.setUp();

    }

    public void testBahnProvider() throws Exception
    {
        //test Berlin as Location
        Location dusLocation = new Location("FakeProvider");
        dusLocation.setLatitude(LOCATION_DUSSELDORF[0]);
        dusLocation.setLongitude(LOCATION_DUSSELDORF[1]);
        //test resolve of stations
        BahnProvider bahnProvider = new BahnProvider();
        NearbyStationsResult nearbyStationsResult = bahnProvider.queryNearbyStations(convertLoction(dusLocation), 1000, 10);
        Assert.assertNotNull("nearbyStationsResult is null", nearbyStationsResult);
        Log.v(">>>>>", "found: " + nearbyStationsResult.stations.size() + " stations");
        Assert.assertEquals("nearbyStationsResult is not correct",5, nearbyStationsResult.stations.size());
        Assert.assertEquals("First Station is not DDorf HBF", "Düsseldorf Hauptbahnhof",nearbyStationsResult.stations.get(0).name);
    }

    public void testBerlinBrandendburgProvider() throws Exception
    {
        //test Berlin as Location
        Location dusLocation = new Location("FakeProvider");
        dusLocation.setLatitude(LOCATION_BERLIN[0]);
        dusLocation.setLongitude(LOCATION_BERLIN[1]);
        //test resolve of stations
        VbbProvider provider = new VbbProvider();
        NearbyStationsResult nearbyStationsResult = provider.queryNearbyStations(convertLoction(dusLocation), 1000, 10);
        Assert.assertNotNull("nearbyStationsResult is null", nearbyStationsResult);
        Log.v(">>>>>", "found: "+nearbyStationsResult.stations.size()+" stations");
        Assert.assertEquals("nearbyStationsResult is not correct",10, nearbyStationsResult.stations.size());
        Assert.assertEquals("First Station is not correct", "S+U Berlin Hauptbahnhof",nearbyStationsResult.stations.get(0).name);
    }



    private de.schildbach.pte.dto.Location convertLoction(Location location)
    {
        return new de.schildbach.pte.dto.Location(LocationType.ANY, (int)(location.getLatitude()*1E6), (int)(location.getLongitude()*1E6));
    }
}