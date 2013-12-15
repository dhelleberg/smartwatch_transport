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
import de.schildbach.pte.*;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;



public class ProviderTestCase extends AndroidTestCase {

    private static final double[] LOCATION_DUSSELDORF = {51.220250,6.793177};
    private static final double[] LOCATION_BERLIN = {52.526110,13.368752};
    private static final double[] LOCATION_BRAUNSCHWEIG = {52.252934,10.538678};
    private static final double[] LOCATION_BREMEN = {53.083481,8.813834};
    private static final double[] LOCATION_AUGSBURG = {48.365374,10.888503};
    private static final double[] LOCATION_MUENCHEN = {48.140232,11.558335  };
    private static final double[] LOCATION_COPENHAGEN = {55.673374,12.561321};
    private static final double[] LOCATION_VIENNA = {48.208174,16.373819};
    private static final double[] LOCATION_STOCKHOLM_GAMLASTAN = {59.323085,18.067843};
    private static final double[] LOCATION_ZUERICH_HBF = {47.378122,8.539317};
    private static final double[] LOCATION_NEUBRANDENBURG  = {53.561461,13.263717};
    private static final double[] LOCATION_KASSEL  = {51.316926,9.491544};

    private List<AbstractNetworkProvider> successProvider;
    List<AbstractNetworkProvider>failedProvider;

    @Override
    protected void setUp() throws Exception {
        Log.v(">>>>>>>>>>>>>>", "SETUP");
        super.setUp();
        //collect failed provider
        failedProvider = new ArrayList<AbstractNetworkProvider>();
        //collect success provider
        successProvider = new ArrayList<AbstractNetworkProvider>();

    }

    public void testBahnProvider() throws Exception
    {
        //test resolve of stations
        BahnProvider provider = new BahnProvider();
        checkProvider(provider, LOCATION_DUSSELDORF[0], LOCATION_DUSSELDORF[1], 5, "Düsseldorf Hauptbahnhof");
    }

    public void testEuropeProvider() throws Exception
    {
        //test resolve of stations
        RtProvider provider = new RtProvider();
        checkProvider(provider, LOCATION_DUSSELDORF[0], LOCATION_DUSSELDORF[1], 2, "Düsseldorf Hbf");
    }

    public void testBerlinBrandendburgProvider() throws Exception
    {
        VbbProvider provider = new VbbProvider();
        checkProvider(provider, LOCATION_BERLIN[0], LOCATION_BERLIN[1], 10, "S+U Berlin Hauptbahnhof");
    }

    public void testMuenchenProvider() throws Exception
    {
        MvvProvider provider = new MvvProvider();
        checkProvider(provider, LOCATION_MUENCHEN[0], LOCATION_MUENCHEN[1], 10, "Hauptbahnhof Haupthalle");
    }

    public void testBraunschweigProvider() throws Exception
    {
        BsvagProvider provider = new BsvagProvider();
        checkProvider(provider, LOCATION_BRAUNSCHWEIG[0], LOCATION_BRAUNSCHWEIG[1], 10, "Hauptbahnhof");
    }

    public void testBremenProvider() throws Exception
    {
        BsagProvider provider = new BsagProvider();
        checkProvider(provider, LOCATION_BREMEN[0], LOCATION_BREMEN[1], 10, "Hauptbahnhof (Central Station)");

    }

    public void testDenmarkProvider() throws Exception
    {
        DsbProvider provider = new DsbProvider();
        checkProvider(provider, LOCATION_COPENHAGEN[0], LOCATION_COPENHAGEN[1], 10, "Hovedbanegården (bus)");
    }

    public void testOebbProvider() throws Exception
    {
        OebbProvider provider = new OebbProvider();
        checkProvider(provider, LOCATION_VIENNA[0], LOCATION_VIENNA[1], 10, "Wien Stephansplatz (Schulerstraße)");
    }

/*
    public void testVorProvider() throws Exception
    {
        VorProvider provider = new VorProvider();
        checkProvider(provider, LOCATION_VIENNA[0], LOCATION_VIENNA[1], 10, "Stephansplatz");
    }
*/

    public void testSeProvider() throws Exception
    {
        SeProvider provider = new SeProvider();
        checkProvider(provider, LOCATION_STOCKHOLM_GAMLASTAN[0], LOCATION_STOCKHOLM_GAMLASTAN[1], 10, "Gamla Stan T-bana");
    }

    public void testAvvProvider() throws Exception
    {
        AvvProvider provider = new AvvProvider();
        checkProvider(provider, LOCATION_AUGSBURG[0], LOCATION_AUGSBURG[1], 10, "Hauptbahnhof");
    }

    public void testVmvProvider() throws Exception
    {
        VmvProvider provider = new VmvProvider();
        checkProvider(provider, LOCATION_NEUBRANDENBURG[0], LOCATION_NEUBRANDENBURG[1], 10, "Bahnhof");
    }

    public void testGvhProvider() throws Exception
    {
        GvhProvider provider = new GvhProvider("");
        checkProvider(provider, LOCATION_BREMEN[0], LOCATION_BREMEN[1], 10, "Hauptbahnhof (Central Station)");
    }

    /* currently failing
    public void testSbbProvider() throws Exception
    {
        SbbProvider provider = new SbbProvider(null);
        checkProvider(provider, LOCATION_ZUERICH_HBF[0], LOCATION_ZUERICH_HBF[1], 10, "Zürich HB");
    }*/


    private void checkProvider(AbstractNetworkProvider provider, double lat, double lon, int expectedStations, String expectedStation) throws Exception {
        Location dusLocation = new Location("FakeProvider");
        dusLocation.setLatitude(lat);
        dusLocation.setLongitude(lon);
        NearbyStationsResult nearbyStationsResult = provider.queryNearbyStations(convertLocation(dusLocation), 1000, 10);
        Assert.assertNotNull("nearbyStationsResult is null provider: "+provider.getClass(), nearbyStationsResult);
        Assert.assertEquals("nearbyStationsResult is not correct " + provider.getClass(), expectedStations, nearbyStationsResult.stations.size());
        Assert.assertEquals("First Station is not correct" + provider.getClass(), expectedStation, nearbyStationsResult.stations.get(0).name);

    }


    private de.schildbach.pte.dto.Location convertLocation(Location location)
    {
        return new de.schildbach.pte.dto.Location(LocationType.ANY, (int)(location.getLatitude()*1E6), (int)(location.getLongitude()*1E6));
    }
}