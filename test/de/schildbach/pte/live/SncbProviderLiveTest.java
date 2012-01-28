/*
 * Copyright 2010, 2011 the original author or authors.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.pte.live;

import java.util.Date;
import java.util.List;

import org.junit.Test;

import de.schildbach.pte.NetworkProvider.Accessibility;
import de.schildbach.pte.NetworkProvider.WalkSpeed;
import de.schildbach.pte.SncbProvider;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryConnectionsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;

/**
 * @author Andreas Schildbach
 */
public class SncbProviderLiveTest extends AbstractProviderLiveTest
{
	private final SncbProvider provider = new SncbProvider();
	private static final String ALL_PRODUCTS = "IRSUTBFC";

	@Test
	public void nearbyStations() throws Exception
	{
		final NearbyStationsResult result = provider.queryNearbyStations(new Location(LocationType.STATION, 100080), 0, 0);

		System.out.println(result.stations.size() + "  " + result.stations);
	}

	@Test
	public void nearbyStationsByCoordinate() throws Exception
	{
		final NearbyStationsResult result = provider.queryNearbyStations(new Location(LocationType.ADDRESS, 50748017, 3407118), 0, 0);

		System.out.println(result.stations.size() + "  " + result.stations);
	}

	@Test
	public void queryDepartures() throws Exception
	{
		final QueryDeparturesResult result = provider.queryDepartures(100080, 0, false);

		print(result);
	}

	@Test
	public void autocompleteIncomplete() throws Exception
	{
		final List<Location> autocompletes = provider.autocompleteStations("Brussel S");

		print(autocompletes);
	}

	@Test
	public void autoCompleteAddress() throws Exception
	{
		final List<Location> autocompletes = provider.autocompleteStations("Rue Paul Janson 9, 1030 Bruxelles");

		print(autocompletes);
	}

	@Test
	public void shortConnection() throws Exception
	{
		final QueryConnectionsResult result = provider.queryConnections(new Location(LocationType.STATION, 8821006, "Antwerpen", "Centraal"), null,
				new Location(LocationType.STATION, 8813003, "Brussel", "Centraal"), new Date(), true, null, WalkSpeed.FAST, Accessibility.NEUTRAL);
		System.out.println(result.status + "  " + result.connections);

		if (result.context != null)
		{
			final QueryConnectionsResult moreResult = provider.queryMoreConnections(result.context);
			System.out.println(moreResult.status + " " + moreResult.connections);
		}
	}

	@Test
	public void longConnection() throws Exception
	{
		final QueryConnectionsResult result = provider.queryConnections(new Location(LocationType.STATION, 207280, "Brussel", "Wannecouter"), null,
				new Location(LocationType.STATION, 207272, "Brussel", "Stadion"), new Date(), true, null, WalkSpeed.FAST, Accessibility.NEUTRAL);
		System.out.println(result.status + "  " + result.connections);

		if (result.context != null)
		{
			final QueryConnectionsResult moreResult = provider.queryMoreConnections(result.context);
			System.out.println(moreResult.status + " " + moreResult.connections);
		}
	}

	@Test
	public void connectionFromAddress() throws Exception
	{
		final QueryConnectionsResult result = provider.queryConnections(new Location(LocationType.ADDRESS, 0, null,
				"Bruxelles - Haren, Rue Paul Janson 9"), null, new Location(LocationType.STATION, 8500010, null, "Basel"), new Date(), true,
				ALL_PRODUCTS, WalkSpeed.NORMAL, Accessibility.NEUTRAL);
		System.out.println(result.status + "  " + result.connections);

		if (result.context != null)
		{
			final QueryConnectionsResult moreResult = provider.queryMoreConnections(result.context);
			System.out.println(moreResult.status + " " + moreResult.connections);
		}
	}
}
