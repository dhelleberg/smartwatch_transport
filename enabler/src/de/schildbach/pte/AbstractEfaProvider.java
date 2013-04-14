/*
 * Copyright 2010-2013 the original author or authors.
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

package de.schildbach.pte;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import de.schildbach.pte.dto.Connection;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Fare;
import de.schildbach.pte.dto.Fare.Type;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryConnectionsContext;
import de.schildbach.pte.dto.QueryConnectionsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.ResultHeader;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.exception.InvalidDataException;
import de.schildbach.pte.exception.ParserException;
import de.schildbach.pte.exception.ProtocolException;
import de.schildbach.pte.exception.SessionExpiredException;
import de.schildbach.pte.util.ParserUtils;
import de.schildbach.pte.util.XmlPullUtil;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractEfaProvider extends AbstractNetworkProvider
{
	protected static final String DEFAULT_DEPARTURE_MONITOR_ENDPOINT = "XSLT_DM_REQUEST";
	protected static final String DEFAULT_TRIP_ENDPOINT = "XSLT_TRIP_REQUEST2";
	protected static final String DEFAULT_STOPFINDER_ENDPOINT = "XML_STOPFINDER_REQUEST";
	protected static final String DEFAULT_COORD_ENDPOINT = "XML_COORD_REQUEST";

	protected static final String SERVER_PRODUCT = "efa";

	private final String departureMonitorEndpoint;
	private final String tripEndpoint;
	private final String stopFinderEndpoint;
	private final String coordEndpoint;

	private String additionalQueryParameter = null;
	private boolean canAcceptPoiId = false;
	private boolean needsSpEncId = false;
	private boolean includeRegionId = true;
	private Charset requestUrlEncoding = ISO_8859_1;
	private String httpReferer = null;
	private String httpRefererTrip = null;
	private boolean httpPost = false;
	private boolean suppressPositions = false;
	private boolean useRouteIndexAsConnectionId = true;
	private boolean useLineRestriction = true;

	private final XmlPullParserFactory parserFactory;

	private static class Context implements QueryConnectionsContext
	{
		private final String context;

		private Context(final String context)
		{
			this.context = context;
		}

		public boolean canQueryLater()
		{
			return context != null;
		}

		public boolean canQueryEarlier()
		{
			return false; // TODO enable earlier querying
		}

		@Override
		public String toString()
		{
			return getClass().getName() + "[" + context + "]";
		}
	}

	public AbstractEfaProvider(final String apiBase)
	{
		this(apiBase, null, null, null, null);
	}

	public AbstractEfaProvider(final String apiBase, final String departureMonitorEndpoint, final String tripEndpoint,
			final String stopFinderEndpoint, final String coordEndpoint)
	{
		this(apiBase + (departureMonitorEndpoint != null ? departureMonitorEndpoint : DEFAULT_DEPARTURE_MONITOR_ENDPOINT), //
				apiBase + (tripEndpoint != null ? tripEndpoint : DEFAULT_TRIP_ENDPOINT), //
				apiBase + (stopFinderEndpoint != null ? stopFinderEndpoint : DEFAULT_STOPFINDER_ENDPOINT), //
				apiBase + (coordEndpoint != null ? coordEndpoint : DEFAULT_COORD_ENDPOINT));
	}

	private AbstractEfaProvider(final String departureMonitorEndpoint, final String tripEndpoint, final String stopFinderEndpoint,
			final String coordEndpoint)
	{
		try
		{
			parserFactory = XmlPullParserFactory.newInstance(System.getProperty(XmlPullParserFactory.PROPERTY_NAME), null);
		}
		catch (final XmlPullParserException x)
		{
			throw new RuntimeException(x);
		}

		this.departureMonitorEndpoint = departureMonitorEndpoint;
		this.tripEndpoint = tripEndpoint;
		this.stopFinderEndpoint = stopFinderEndpoint;
		this.coordEndpoint = coordEndpoint;
	}

	protected void setRequestUrlEncoding(final Charset requestUrlEncoding)
	{
		this.requestUrlEncoding = requestUrlEncoding;
	}

	protected void setHttpReferer(final String httpReferer)
	{
		this.httpReferer = httpReferer;
		this.httpRefererTrip = httpReferer;
	}

	public void setHttpRefererTrip(final String httpRefererTrip)
	{
		this.httpRefererTrip = httpRefererTrip;
	}

	protected void setHttpPost(final boolean httpPost)
	{
		this.httpPost = httpPost;
	}

	protected void setIncludeRegionId(final boolean includeRegionId)
	{
		this.includeRegionId = includeRegionId;
	}

	protected void setSuppressPositions(final boolean suppressPositions)
	{
		this.suppressPositions = suppressPositions;
	}

	protected void setUseRouteIndexAsConnectionId(final boolean useRouteIndexAsConnectionId)
	{
		this.useRouteIndexAsConnectionId = useRouteIndexAsConnectionId;
	}

	protected void setUseLineRestriction(final boolean useLineRestriction)
	{
		this.useLineRestriction = useLineRestriction;
	}

	protected void setCanAcceptPoiId(final boolean canAcceptPoiId)
	{
		this.canAcceptPoiId = canAcceptPoiId;
	}

	protected void setNeedsSpEncId(final boolean needsSpEncId)
	{
		this.needsSpEncId = needsSpEncId;
	}

	protected void setAdditionalQueryParameter(final String additionalQueryParameter)
	{
		this.additionalQueryParameter = additionalQueryParameter;
	}

	protected TimeZone timeZone()
	{
		return TimeZone.getTimeZone("Europe/Berlin");
	}

	private final void appendCommonRequestParams(final StringBuilder uri, final String outputFormat)
	{
		uri.append("?outputFormat=").append(outputFormat);
		uri.append("&coordOutputFormat=WGS84");
		if (additionalQueryParameter != null)
			uri.append('&').append(additionalQueryParameter);
	}

	protected List<Location> jsonStopfinderRequest(final Location constraint) throws IOException
	{
		final StringBuilder parameters = new StringBuilder();
		appendCommonRequestParams(parameters, "JSON");
		parameters.append("&locationServerActive=1");
		if (includeRegionId)
			parameters.append("&regionID_sf=1"); // prefer own region
		appendLocation(parameters, constraint, "sf");
		if (constraint.type == LocationType.ANY)
			// 1=place 2=stop 4=street 8=address 16=crossing 32=poi 64=postcode
			parameters.append("&anyObjFilter_sf=").append(2 + 4 + 8 + 16 + 32 + 64);
		parameters.append("&anyMaxSizeHitList=500");

		final StringBuilder uri = new StringBuilder(stopFinderEndpoint);
		if (!httpPost)
			uri.append(parameters);

		// System.out.println(uri);
		// System.out.println(parameters);

		final CharSequence page = ParserUtils.scrape(uri.toString(), httpPost ? parameters.substring(1) : null, UTF_8, null);

		try
		{
			final List<Location> results = new ArrayList<Location>();

			final JSONObject head = new JSONObject(page.toString());
			final JSONObject stopFinder = head.optJSONObject("stopFinder");
			final JSONArray stops;
			if (stopFinder == null)
			{
				stops = head.getJSONArray("stopFinder");
			}
			else
			{
				final JSONObject points = stopFinder.optJSONObject("points");
				if (points != null)
				{
					final JSONObject stop = points.getJSONObject("point");
					final Location location = parseJsonStop(stop);
					results.add(location);
					return results;
				}

				stops = stopFinder.getJSONArray("points");
			}

			final int nStops = stops.length();

			for (int i = 0; i < nStops; i++)
			{
				final JSONObject stop = stops.optJSONObject(i);
				final Location location = parseJsonStop(stop);
				results.add(location);
			}

			return results;
		}
		catch (final JSONException x)
		{
			throw new RuntimeException("cannot parse: '" + page + "' on " + uri, x);
		}
	}

	private Location parseJsonStop(final JSONObject stop) throws JSONException
	{
		String type = stop.getString("type");
		if ("any".equals(type))
			type = stop.getString("anyType");
		final String name = normalizeLocationName(stop.getString("object"));
		final JSONObject ref = stop.getJSONObject("ref");
		String place = ref.getString("place");
		if (place != null && place.length() == 0)
			place = null;
		final String coords = ref.optString("coords", null);
		final int lat;
		final int lon;
		if (coords != null)
		{
			final String[] coordParts = coords.split(",");
			lat = Math.round(Float.parseFloat(coordParts[1]));
			lon = Math.round(Float.parseFloat(coordParts[0]));
		}
		else
		{
			lat = 0;
			lon = 0;
		}

		if ("stop".equals(type))
			return new Location(LocationType.STATION, stop.getInt("stateless"), lat, lon, place, name);
		else if ("poi".equals(type))
			return new Location(LocationType.POI, 0, lat, lon, place, name);
		else if ("crossing".equals(type))
			return new Location(LocationType.ADDRESS, 0, lat, lon, place, name);
		else if ("street".equals(type) || "address".equals(type) || "singlehouse".equals(type))
			return new Location(LocationType.ADDRESS, 0, lat, lon, place, normalizeLocationName(stop.getString("name")));
		else
			throw new JSONException("unknown type: " + type);
	}

	protected List<Location> xmlStopfinderRequest(final Location constraint) throws IOException
	{
		final StringBuilder parameters = new StringBuilder();
		appendCommonRequestParams(parameters, "XML");
		parameters.append("&locationServerActive=1");
		if (includeRegionId)
			parameters.append("&regionID_sf=1"); // prefer own region
		appendLocation(parameters, constraint, "sf");
		if (constraint.type == LocationType.ANY)
		{
			if (needsSpEncId)
				parameters.append("&SpEncId=0");
			// 1=place 2=stop 4=street 8=address 16=crossing 32=poi 64=postcode
			parameters.append("&anyObjFilter_sf=").append(2 + 4 + 8 + 16 + 32 + 64);
			parameters.append("&reducedAnyPostcodeObjFilter_sf=64&reducedAnyTooManyObjFilter_sf=2");
			parameters.append("&useHouseNumberList=true");
		}

		final StringBuilder uri = new StringBuilder(stopFinderEndpoint);
		if (!httpPost)
			uri.append(parameters);

		// System.out.println(uri);
		// System.out.println(parameters);

		InputStream is = null;
		try
		{
			is = ParserUtils.scrapeInputStream(uri.toString(), httpPost ? parameters.substring(1) : null, null, httpReferer, null, 3);

			final XmlPullParser pp = parserFactory.newPullParser();
			pp.setInput(is, null);
			enterItdRequest(pp);

			final List<Location> results = new ArrayList<Location>();

			XmlPullUtil.enter(pp, "itdStopFinderRequest");

			XmlPullUtil.require(pp, "itdOdv");
			if (!"sf".equals(pp.getAttributeValue(null, "usage")))
				throw new IllegalStateException("cannot find <itdOdv usage=\"sf\" />");
			XmlPullUtil.enter(pp, "itdOdv");

			XmlPullUtil.require(pp, "itdOdvPlace");
			XmlPullUtil.next(pp);

			XmlPullUtil.require(pp, "itdOdvName");
			final String nameState = pp.getAttributeValue(null, "state");
			XmlPullUtil.enter(pp, "itdOdvName");

			if (XmlPullUtil.test(pp, "itdMessage"))
				XmlPullUtil.next(pp);

			if ("identified".equals(nameState) || "list".equals(nameState))
			{
				while (XmlPullUtil.test(pp, "odvNameElem"))
					results.add(processOdvNameElem(pp, null));
			}
			else if ("notidentified".equals(nameState))
			{
				// do nothing
			}
			else
			{
				throw new RuntimeException("unknown nameState '" + nameState + "' on " + uri);
			}

			XmlPullUtil.exit(pp, "itdOdvName");

			XmlPullUtil.exit(pp, "itdOdv");

			XmlPullUtil.exit(pp, "itdStopFinderRequest");

			return results;
		}
		catch (final XmlPullParserException x)
		{
			throw new ParserException(x);
		}
		finally
		{
			if (is != null)
				is.close();
		}
	}

	protected NearbyStationsResult xmlCoordRequest(final int lat, final int lon, final int maxDistance, final int maxStations) throws IOException
	{
		final StringBuilder parameters = new StringBuilder();
		appendCommonRequestParams(parameters, "XML");
		parameters.append("&coord=").append(String.format(Locale.ENGLISH, "%2.6f:%2.6f:WGS84", latLonToDouble(lon), latLonToDouble(lat)));
		parameters.append("&coordListOutputFormat=STRING");
		parameters.append("&max=").append(maxStations != 0 ? maxStations : 50);
		parameters.append("&inclFilter=1&radius_1=").append(maxDistance != 0 ? maxDistance : 1320);
		parameters.append("&type_1=STOP"); // ENTRANCE, BUS_POINT, POI_POINT

		final StringBuilder uri = new StringBuilder(coordEndpoint);
		if (!httpPost)
			uri.append(parameters);

		// System.out.println(uri);
		// System.out.println(parameters);

		InputStream is = null;
		try
		{
			is = ParserUtils.scrapeInputStream(uri.toString(), httpPost ? parameters.substring(1) : null, null, httpReferer, null, 3);

			final XmlPullParser pp = parserFactory.newPullParser();
			pp.setInput(is, null);
			final ResultHeader header = enterItdRequest(pp);

			XmlPullUtil.enter(pp, "itdCoordInfoRequest");

			XmlPullUtil.enter(pp, "itdCoordInfo");

			XmlPullUtil.enter(pp, "coordInfoRequest");
			XmlPullUtil.exit(pp, "coordInfoRequest");

			final List<Location> stations = new ArrayList<Location>();

			if (XmlPullUtil.test(pp, "coordInfoItemList"))
			{
				XmlPullUtil.enter(pp, "coordInfoItemList");

				while (XmlPullUtil.test(pp, "coordInfoItem"))
				{
					if (!"STOP".equals(pp.getAttributeValue(null, "type")))
						throw new RuntimeException("unknown type");

					final int id = XmlPullUtil.intAttr(pp, "id");
					final String name = normalizeLocationName(XmlPullUtil.attr(pp, "name"));
					final String place = normalizeLocationName(XmlPullUtil.attr(pp, "locality"));

					XmlPullUtil.enter(pp, "coordInfoItem");

					// FIXME this is always only one coordinate
					final Point coord = processItdPathCoordinates(pp).get(0);

					XmlPullUtil.exit(pp, "coordInfoItem");

					stations.add(new Location(LocationType.STATION, id, coord.lat, coord.lon, place, name));
				}

				XmlPullUtil.exit(pp, "coordInfoItemList");
			}

			return new NearbyStationsResult(header, stations);
		}
		catch (final XmlPullParserException x)
		{
			throw new ParserException(x);
		}
		finally
		{
			if (is != null)
				is.close();
		}
	}

	public List<Location> autocompleteStations(final CharSequence constraint) throws IOException
	{
		return jsonStopfinderRequest(new Location(LocationType.ANY, 0, null, constraint.toString()));
	}

	private String processItdOdvPlace(final XmlPullParser pp) throws XmlPullParserException, IOException
	{
		if (!XmlPullUtil.test(pp, "itdOdvPlace"))
			throw new IllegalStateException("expecting <itdOdvPlace />");

		final String placeState = XmlPullUtil.attr(pp, "state");

		XmlPullUtil.enter(pp, "itdOdvPlace");
		String place = null;
		if ("identified".equals(placeState))
		{
			if (XmlPullUtil.test(pp, "odvPlaceElem"))
			{
				XmlPullUtil.enter(pp, "odvPlaceElem");
				place = normalizeLocationName(pp.getText());
				XmlPullUtil.exit(pp, "odvPlaceElem");
			}
		}
		XmlPullUtil.exit(pp, "itdOdvPlace");

		return place;
	}

	private Location processOdvNameElem(final XmlPullParser pp, final String defaultPlace) throws XmlPullParserException, IOException
	{
		if (!XmlPullUtil.test(pp, "odvNameElem"))
			throw new IllegalStateException("expecting <odvNameElem />");

		final String anyType = pp.getAttributeValue(null, "anyType");
		final String idStr = pp.getAttributeValue(null, "id");
		final String stopIdStr = pp.getAttributeValue(null, "stopID");
		final String poiIdStr = pp.getAttributeValue(null, "poiID");
		final String streetIdStr = pp.getAttributeValue(null, "streetID");
		final String place = !"loc".equals(anyType) ? normalizeLocationName(pp.getAttributeValue(null, "locality")) : null;
		final String name = normalizeLocationName(pp.getAttributeValue(null, "objectName"));

		final String mapName = pp.getAttributeValue(null, "mapName");
		final int lat;
		final int lon;
		if (mapName == null || mapName.length() == 0)
		{
			lat = 0;
			lon = 0;
		}
		else if ("WGS84".equals(mapName))
		{
			lat = Math.round(XmlPullUtil.floatAttr(pp, "y"));
			lon = Math.round(XmlPullUtil.floatAttr(pp, "x"));
		}
		else
		{
			throw new IllegalStateException("unknown mapName: " + mapName);
		}

		LocationType type;
		int id;
		if ("stop".equals(anyType))
		{
			type = LocationType.STATION;
			id = Integer.parseInt(idStr);
		}
		else if ("poi".equals(anyType) || "poiHierarchy".equals(anyType))
		{
			type = LocationType.POI;
			id = Integer.parseInt(idStr);
		}
		else if ("loc".equals(anyType))
		{
			type = LocationType.ANY;
			id = 0;
		}
		else if ("postcode".equals(anyType) || "street".equals(anyType) || "crossing".equals(anyType) || "address".equals(anyType)
				|| "singlehouse".equals(anyType) || "buildingname".equals(anyType))
		{
			type = LocationType.ADDRESS;
			id = 0;
		}
		else if (stopIdStr != null)
		{
			type = LocationType.STATION;
			id = Integer.parseInt(stopIdStr);
		}
		else if (poiIdStr != null)
		{
			type = LocationType.POI;
			id = Integer.parseInt(poiIdStr);
		}
		else if (stopIdStr == null && idStr == null && (lat != 0 || lon != 0))
		{
			type = LocationType.ADDRESS;
			id = 0;
		}
		else if (streetIdStr != null)
		{
			type = LocationType.ADDRESS;
			id = Integer.parseInt(streetIdStr);
		}
		else
		{
			throw new IllegalArgumentException("unknown type: " + anyType + " " + idStr + " " + stopIdStr);
		}

		XmlPullUtil.enter(pp, "odvNameElem");
		final String longName = normalizeLocationName(pp.getText());
		XmlPullUtil.exit(pp, "odvNameElem");

		return new Location(type, id, lat, lon, place != null ? place : defaultPlace, name != null ? name : longName);
	}

	private Location processItdOdvAssignedStop(final XmlPullParser pp) throws XmlPullParserException, IOException
	{
		final int id = Integer.parseInt(pp.getAttributeValue(null, "stopID"));

		final String mapName = pp.getAttributeValue(null, "mapName");
		final int lat;
		final int lon;
		if (mapName == null || mapName.length() == 0)
		{
			lat = 0;
			lon = 0;
		}
		else if ("WGS84".equals(mapName))
		{
			lat = Math.round(XmlPullUtil.floatAttr(pp, "y"));
			lon = Math.round(XmlPullUtil.floatAttr(pp, "x"));
		}
		else
		{
			throw new IllegalStateException("unknown mapName: " + mapName);
		}

		final String place = normalizeLocationName(XmlPullUtil.attr(pp, "place"));

		XmlPullUtil.enter(pp, "itdOdvAssignedStop");
		final String name = normalizeLocationName(pp.getText());
		XmlPullUtil.exit(pp, "itdOdvAssignedStop");

		return new Location(LocationType.STATION, id, lat, lon, place, name);
	}

	public NearbyStationsResult queryNearbyStations(final Location location, final int maxDistance, final int maxStations) throws IOException
	{
		if (location.hasLocation())
			return xmlCoordRequest(location.lat, location.lon, maxDistance, maxStations);

		if (location.type != LocationType.STATION)
			throw new IllegalArgumentException("cannot handle: " + location.type);

		if (!location.hasId())
			throw new IllegalArgumentException("at least one of stationId or lat/lon must be given");

		return nearbyStationsRequest(location.id, maxStations);
	}

	private NearbyStationsResult nearbyStationsRequest(final int stationId, final int maxStations) throws IOException
	{
		final StringBuilder parameters = new StringBuilder();
		appendCommonRequestParams(parameters, "XML");
		parameters.append("&type_dm=stop&name_dm=").append(stationId);
		parameters.append("&itOptionsActive=1");
		parameters.append("&ptOptionsActive=1");
		parameters.append("&useProxFootSearch=1");
		parameters.append("&mergeDep=1");
		parameters.append("&useAllStops=1");
		parameters.append("&mode=direct");

		final StringBuilder uri = new StringBuilder(departureMonitorEndpoint);
		if (!httpPost)
			uri.append(parameters);

		// System.out.println(uri);
		// System.out.println(parameters);

		InputStream is = null;
		try
		{
			is = ParserUtils.scrapeInputStream(uri.toString(), httpPost ? parameters.substring(1) : null, null, httpReferer, "NSC_", 3);

			final XmlPullParser pp = parserFactory.newPullParser();
			pp.setInput(is, null);
			final ResultHeader header = enterItdRequest(pp);

			if (!XmlPullUtil.jumpToStartTag(pp, null, "itdOdv") || !"dm".equals(pp.getAttributeValue(null, "usage")))
				throw new IllegalStateException("cannot find <itdOdv usage=\"dm\" />");
			XmlPullUtil.enter(pp, "itdOdv");

			final String place = processItdOdvPlace(pp);

			XmlPullUtil.require(pp, "itdOdvName");
			final String nameState = pp.getAttributeValue(null, "state");
			XmlPullUtil.enter(pp, "itdOdvName");
			if ("identified".equals(nameState))
			{
				final Location ownLocation = processOdvNameElem(pp, place);
				final Location ownStation = ownLocation.type == LocationType.STATION ? ownLocation : null;

				final List<Location> stations = new ArrayList<Location>();

				if (XmlPullUtil.jumpToStartTag(pp, null, "itdOdvAssignedStops"))
				{
					XmlPullUtil.enter(pp, "itdOdvAssignedStops");
					while (XmlPullUtil.test(pp, "itdOdvAssignedStop"))
					{
						final String parsedMapName = pp.getAttributeValue(null, "mapName");
						if (parsedMapName != null && parsedMapName.length() != 0)
						{
							final int parsedLocationId = XmlPullUtil.intAttr(pp, "stopID");
							// final String parsedLongName = normalizeLocationName(XmlPullUtil.attr(pp,
							// "nameWithPlace"));
							final String parsedPlace = normalizeLocationName(XmlPullUtil.attr(pp, "place"));
							final int parsedLon = Math.round(XmlPullUtil.floatAttr(pp, "x"));
							final int parsedLat = Math.round(XmlPullUtil.floatAttr(pp, "y"));
							XmlPullUtil.enter(pp, "itdOdvAssignedStop");
							final String parsedName = normalizeLocationName(pp.getText());
							XmlPullUtil.exit(pp, "itdOdvAssignedStop");

							if (!"WGS84".equals(parsedMapName))
								throw new IllegalStateException("unknown mapName: " + parsedMapName);

							final Location newStation = new Location(LocationType.STATION, parsedLocationId, parsedLat, parsedLon, parsedPlace,
									parsedName);
							if (!stations.contains(newStation))
								stations.add(newStation);
						}
						else
						{
							if (!pp.isEmptyElementTag())
							{
								XmlPullUtil.enter(pp, "itdOdvAssignedStop");
								XmlPullUtil.exit(pp, "itdOdvAssignedStop");
							}
							else
							{
								XmlPullUtil.next(pp);
							}
						}
					}
				}

				if (ownStation != null && !stations.contains(ownStation))
					stations.add(ownStation);

				if (maxStations == 0 || maxStations >= stations.size())
					return new NearbyStationsResult(header, stations);
				else
					return new NearbyStationsResult(header, stations.subList(0, maxStations));
			}
			else if ("list".equals(nameState))
			{
				final List<Location> stations = new ArrayList<Location>();

				if (XmlPullUtil.test(pp, "itdMessage"))
					XmlPullUtil.next(pp);
				while (XmlPullUtil.test(pp, "odvNameElem"))
				{
					final Location newLocation = processOdvNameElem(pp, place);
					if (newLocation.type == LocationType.STATION && !stations.contains(newLocation))
						stations.add(newLocation);
				}

				return new NearbyStationsResult(header, stations);
			}
			else if ("notidentified".equals(nameState))
			{
				return new NearbyStationsResult(header, NearbyStationsResult.Status.INVALID_STATION);
			}
			else
			{
				throw new RuntimeException("unknown nameState '" + nameState + "' on " + uri);
			}
			// XmlPullUtil.exit(pp, "itdOdvName");
		}
		catch (final XmlPullParserException x)
		{
			throw new ParserException(x);
		}
		finally
		{
			if (is != null)
				is.close();
		}
	}

	private static final Pattern P_LINE_IRE = Pattern.compile("IRE\\d+");
	private static final Pattern P_LINE_RE = Pattern.compile("RE\\d+");
	private static final Pattern P_LINE_RB = Pattern.compile("RB\\d+");
	private static final Pattern P_LINE_VB = Pattern.compile("VB\\d+");
	private static final Pattern P_LINE_OE = Pattern.compile("OE\\d+");
	private static final Pattern P_LINE_R = Pattern.compile("R\\d+(/R\\d+|\\(z\\))?");
	private static final Pattern P_LINE_U = Pattern.compile("U\\d+");
	private static final Pattern P_LINE_S = Pattern.compile("^(?:%)?(S\\d+)");
	private static final Pattern P_LINE_NUMBER = Pattern.compile("\\d+");
	private static final Pattern P_LINE_Y = Pattern.compile("\\d+Y");
	private static final Pattern P_LINE_SEV = Pattern.compile("SEV.*");

	protected String parseLine(final String mot, String symbol, final String name, final String longName, final String trainType,
			final String trainNum, final String trainName)
	{
		if (mot == null)
		{
			if (trainName != null)
			{
				final String str = name != null ? name : "";
				if (trainName.equals("S-Bahn"))
					return 'S' + str;
				if (trainName.equals("U-Bahn"))
					return 'U' + str;
				if (trainName.equals("Straßenbahn"))
					return 'T' + str;
				if (trainName.equals("Badner Bahn"))
					return 'T' + str;
				if (trainName.equals("Stadtbus"))
					return 'B' + str;
				if (trainName.equals("Citybus"))
					return 'B' + str;
				if (trainName.equals("Regionalbus"))
					return 'B' + str;
				if (trainName.equals("ÖBB-Postbus"))
					return 'B' + str;
				if (trainName.equals("Autobus"))
					return 'B' + str;
				if (trainName.equals("Discobus"))
					return 'B' + str;
				if (trainName.equals("Nachtbus"))
					return 'B' + str;
				if (trainName.equals("Anrufsammeltaxi"))
					return 'B' + str;
				if (trainName.equals("Ersatzverkehr"))
					return 'B' + str;
				if (trainName.equals("Vienna Airport Lines"))
					return 'B' + str;
			}

			throw new IllegalStateException("cannot normalize mot='" + mot + "' symbol='" + symbol + "' name='" + name + "' long='" + longName
					+ "' trainType='" + trainType + "' trainNum='" + trainNum + "' trainName='" + trainName + "'");
		}

		final int t = Integer.parseInt(mot);

		if (t == 0)
		{
			final String[] parts = longName.split(" ", 3);
			final String type = parts[0];
			final String num = parts.length >= 2 ? parts[1] : null;
			final String str = type + (num != null ? num : "");

			if (type.equals("EC")) // Eurocity
				return 'I' + str;
			if (type.equals("EN")) // Euronight
				return 'I' + str;
			if (type.equals("IC")) // Intercity
				return 'I' + str;
			if ("InterCity".equals(type))
				return 'I' + str;
			if (type.equals("ICE")) // Intercity Express
				return 'I' + str;
			if (type.equals("X")) // InterConnex
				return 'I' + str;
			if (type.equals("CNL")) // City Night Line
				return 'I' + str;
			if (type.equals("THA")) // Thalys
				return 'I' + str;
			if (type.equals("TGV")) // TGV
				return 'I' + str;
			if (type.equals("RJ")) // railjet
				return 'I' + str;
			if ("WB".equals(type)) // westbahn
				return 'R' + str;
			if (type.equals("OEC")) // ÖBB-EuroCity
				return 'I' + str;
			if (type.equals("OIC")) // ÖBB-InterCity
				return 'I' + str;
			if (type.equals("HT")) // First Hull Trains, GB
				return 'I' + str;
			if (type.equals("MT")) // Müller Touren, Schnee Express
				return 'I' + str;
			if (type.equals("HKX")) // Hamburg-Koeln-Express
				return 'I' + str;
			if (type.equals("DNZ")) // Nachtzug Basel-Moskau
				return 'I' + str;
			if ("INT".equals(type)) // SVV
				return 'I' + name;
			if ("IXB".equals(type)) // ICE International
				return 'I' + name;
			if ("SC".equals(type)) // SuperCity, Tschechien
				return 'I' + name;
			if ("ECB".equals(type)) // EC, Verona-München
				return 'I' + name;
			if ("ES".equals(type)) // Eurostar Italia
				return 'I' + name;
			if ("Eurocity".equals(trainName)) // Liechtenstein
				return 'I' + name;
			if ("EuroNight".equals(trainName)) // Liechtenstein
				return 'I' + name;
			if ("railjet".equals(trainName)) // Liechtenstein
				return 'I' + name;
			if ("ÖBB InterCity".equals(trainName)) // Liechtenstein
				return 'I' + name;

			if (type.equals("IR")) // Interregio
				return 'R' + str;
			if ("InterRegio".equals(type))
				return 'R' + str;
			if (type.equals("IRE")) // Interregio-Express
				return 'R' + str;
			if (P_LINE_IRE.matcher(type).matches())
				return 'R' + str;
			if (type.equals("RE")) // Regional-Express
				return 'R' + str;
			if (type.equals("R-Bahn")) // Regional-Express, VRR
				return 'R' + str;
			if ("RB-Bahn".equals(type)) // Vogtland
				return 'R' + str;
			if (type.equals("REX")) // RegionalExpress, Österreich
				return 'R' + str;
			if ("EZ".equals(type)) // ÖBB ErlebnisBahn
				return 'R' + str;
			if (P_LINE_RE.matcher(type).matches())
				return 'R' + str;
			if (type.equals("RB")) // Regionalbahn
				return 'R' + str;
			if (P_LINE_RB.matcher(type).matches())
				return 'R' + str;
			if (type.equals("R")) // Regionalzug
				return 'R' + str;
			if (P_LINE_R.matcher(type).matches())
				return 'R' + str;
			if (type.equals("Bahn"))
				return 'R' + str;
			if (type.equals("Regionalbahn"))
				return 'R' + str;
			if (type.equals("D")) // Schnellzug
				return 'R' + str;
			if (type.equals("E")) // Eilzug
				return 'R' + str;
			if (type.equals("S")) // ~Innsbruck
				return 'R' + str;
			if (type.equals("WFB")) // Westfalenbahn
				return 'R' + str;
			if ("Westfalenbahn".equals(type)) // Westfalenbahn
				return 'R' + name;
			if (type.equals("NWB")) // NordWestBahn
				return 'R' + str;
			if (type.equals("NordWestBahn"))
				return 'R' + str;
			if (type.equals("ME")) // Metronom
				return 'R' + str;
			if (type.equals("ERB")) // eurobahn
				return 'R' + str;
			if (type.equals("CAN")) // cantus
				return 'R' + str;
			if (type.equals("HEX")) // Veolia Verkehr Sachsen-Anhalt
				return 'R' + str;
			if (type.equals("EB")) // Erfurter Bahn
				return 'R' + str;
			if (type.equals("EBx")) // Erfurter Bahn Express
				return 'R' + str;
			if (type.equals("MRB")) // Mittelrheinbahn
				return 'R' + str;
			if (type.equals("ABR")) // ABELLIO Rail NRW
				return 'R' + str;
			if (type.equals("NEB")) // Niederbarnimer Eisenbahn
				return 'R' + str;
			if (type.equals("OE")) // Ostdeutsche Eisenbahn
				return 'R' + str;
			if (P_LINE_OE.matcher(type).matches())
				return 'R' + str;
			if (type.equals("MR")) // Märkische Regiobahn
				return 'R' + str;
			if (type.equals("OLA")) // Ostseeland Verkehr
				return 'R' + str;
			if (type.equals("UBB")) // Usedomer Bäderbahn
				return 'R' + str;
			if (type.equals("EVB")) // Elbe-Weser
				return 'R' + str;
			if (type.equals("PEG")) // Prignitzer Eisenbahngesellschaft
				return 'R' + str;
			if (type.equals("RTB")) // Rurtalbahn
				return 'R' + str;
			if (type.equals("STB")) // Süd-Thüringen-Bahn
				return 'R' + str;
			if (type.equals("HTB")) // Hellertalbahn
				return 'R' + str;
			if (type.equals("VBG")) // Vogtlandbahn
				return 'R' + str;
			if (type.equals("VB")) // Vogtlandbahn
				return 'R' + str;
			if (P_LINE_VB.matcher(type).matches())
				return 'R' + str;
			if (type.equals("VX")) // Vogtland Express
				return 'R' + str;
			if (type.equals("CB")) // City-Bahn Chemnitz
				return 'R' + str;
			if (type.equals("VEC")) // VECTUS Verkehrsgesellschaft
				return 'R' + str;
			if (type.equals("HzL")) // Hohenzollerische Landesbahn
				return 'R' + str;
			if (type.equals("OSB")) // Ortenau-S-Bahn
				return 'R' + str;
			if (type.equals("SBB")) // SBB
				return 'R' + str;
			if (type.equals("MBB")) // Mecklenburgische Bäderbahn Molli
				return 'R' + str;
			if (type.equals("OS")) // Regionalbahn
				return 'R' + str;
			if (type.equals("SP"))
				return 'R' + str;
			if (type.equals("Dab")) // Daadetalbahn
				return 'R' + str;
			if (type.equals("FEG")) // Freiberger Eisenbahngesellschaft
				return 'R' + str;
			if (type.equals("ARR")) // ARRIVA
				return 'R' + str;
			if (type.equals("HSB")) // Harzer Schmalspurbahn
				return 'R' + str;
			if (type.equals("SBE")) // Sächsisch-Böhmische Eisenbahngesellschaft
				return 'R' + str;
			if (type.equals("ALX")) // Arriva-Länderbahn-Express
				return 'R' + str;
			if (type.equals("EX")) // ALX verwandelt sich
				return 'R' + str;
			if (type.equals("MEr")) // metronom regional
				return 'R' + str;
			if (type.equals("AKN")) // AKN Eisenbahn
				return 'R' + str;
			if (type.equals("ZUG")) // Regionalbahn
				return 'R' + str;
			if (type.equals("SOE")) // Sächsisch-Oberlausitzer Eisenbahngesellschaft
				return 'R' + str;
			if (type.equals("VIA")) // VIAS
				return 'R' + str;
			if (type.equals("BRB")) // Bayerische Regiobahn
				return 'R' + str;
			if (type.equals("BLB")) // Berchtesgadener Land Bahn
				return 'R' + str;
			if (type.equals("HLB")) // Hessische Landesbahn
				return 'R' + str;
			if (type.equals("NOB")) // NordOstseeBahn
				return 'R' + str;
			if (type.equals("WEG")) // Wieslauftalbahn
				return 'R' + str;
			if (type.equals("NBE")) // Nordbahn Eisenbahngesellschaft
				return 'R' + str;
			if (type.equals("VEN")) // Rhenus Veniro
				return 'R' + str;
			if (type.equals("DPN")) // Nahreisezug
				return 'R' + str;
			if (type.equals("SHB")) // Schleswig-Holstein-Bahn
				return 'R' + str;
			if (type.equals("RBG")) // Regental Bahnbetriebs GmbH
				return 'R' + str;
			if (type.equals("BOB")) // Bayerische Oberlandbahn
				return 'R' + str;
			if (type.equals("SWE")) // Südwestdeutsche Verkehrs AG
				return 'R' + str;
			if (type.equals("VE")) // Vetter
				return 'R' + str;
			if (type.equals("SDG")) // Sächsische Dampfeisenbahngesellschaft
				return 'R' + str;
			if (type.equals("PRE")) // Pressnitztalbahn
				return 'R' + str;
			if (type.equals("VEB")) // Vulkan-Eifel-Bahn
				return 'R' + str;
			if (type.equals("neg")) // Norddeutsche Eisenbahn Gesellschaft
				return 'R' + str;
			if (type.equals("AVG")) // Felsenland-Express
				return 'R' + str;
			if (type.equals("ABG")) // Anhaltische Bahngesellschaft
				return 'R' + str;
			if (type.equals("LGB")) // Lößnitzgrundbahn
				return 'R' + str;
			if (type.equals("LEO")) // Chiemgauer Lokalbahn
				return 'R' + str;
			if (type.equals("WTB")) // Weißeritztalbahn
				return 'R' + str;
			if (type.equals("P")) // Kasbachtalbahn, Wanderbahn im Regental, Rhön-Zügle
				return 'R' + str;
			if (type.equals("ÖBA")) // Eisenbahn-Betriebsgesellschaft Ochsenhausen
				return 'R' + str;
			if (type.equals("MBS")) // Montafonerbahn
				return 'R' + str;
			if (type.equals("EGP")) // EGP - die Städtebahn GmbH
				return 'R' + str;
			if (type.equals("SBS")) // Städtebahn Sachsen, EGP - die Städtebahn GmbH
				return 'R' + str;
			if (type.equals("SES")) // Städtebahn Sachsen Express, EGP - die Städtebahn GmbH
				return 'R' + str;
			if (type.equals("SB")) // Städtebahn Sachsen
				return 'R' + str;
			if (type.equals("agi")) // agilis
				return 'R' + str;
			if (type.equals("ag")) // agilis
				return 'R' + str;
			if (type.equals("as")) // agilis-Schnellzug
				return 'R' + str;
			if (type.equals("agilis")) // agilis
				return 'R' + str;
			if (type.equals("agilis-Schnellzug")) // agilis-Schnellzug
				return 'R' + str;
			if (type.equals("TLX")) // Trilex (Vogtlandbahn)
				return 'R' + str;
			if (type.equals("DBG")) // Döllnitzbahn
				return 'R' + str;
			if (type.equals("MSB")) // Mainschleifenbahn
				return 'R' + str;
			if (type.equals("BE")) // Grensland-Express, Niederlande
				return 'R' + str;
			if (type.equals("MEL")) // Museums-Eisenbahn Losheim
				return 'R' + str;
			if (type.equals("Abellio-Zug")) // Abellio
				return 'R' + str;
			if ("erx".equals(type)) // erixx
				return 'R' + str;
			if ("SWEG-Zug".equals(type)) // Südwestdeutschen Verkehrs-Aktiengesellschaft, evtl. S-Bahn?
				return 'R' + str;
			if (type.equals("KBS")) // Kursbuchstrecke
				return 'R' + str;
			if (type.equals("Zug"))
				return 'R' + str;
			if (type.equals("ÖBB"))
				return 'R' + str;
			if (type.equals("CAT")) // City Airport Train Wien
				return 'R' + str;
			if (type.equals("DZ")) // Dampfzug, STV
				return 'R' + str;
			if (type.equals("CD"))
				return 'R' + str;
			if (type.equals("PR"))
				return 'R' + str;
			if (type.equals("KD")) // Koleje Dolnośląskie (Niederschlesische Eisenbahn)
				return 'R' + str;
			if (type.equals("VIAMO"))
				return 'R' + str;
			if (type.equals("SE")) // Southeastern, GB
				return 'R' + str;
			if (type.equals("SW")) // South West Trains, GB
				return 'R' + str;
			if (type.equals("SN")) // Southern, GB
				return 'R' + str;
			if (type.equals("NT")) // Northern Rail, GB
				return 'R' + str;
			if (type.equals("CH")) // Chiltern Railways, GB
				return 'R' + str;
			if (type.equals("EA")) // National Express East Anglia, GB
				return 'R' + str;
			if (type.equals("FC")) // First Capital Connect, GB
				return 'R' + str;
			if (type.equals("GW")) // First Great Western, GB
				return 'R' + str;
			if (type.equals("XC")) // Cross Country, GB, evtl. auch highspeed?
				return 'R' + str;
			if (type.equals("HC")) // Heathrow Connect, GB
				return 'R' + str;
			if (type.equals("HX")) // Heathrow Express, GB
				return 'R' + str;
			if (type.equals("GX")) // Gatwick Express, GB
				return 'R' + str;
			if (type.equals("C2C")) // c2c, GB
				return 'R' + str;
			if (type.equals("LM")) // London Midland, GB
				return 'R' + str;
			if (type.equals("EM")) // East Midlands Trains, GB
				return 'R' + str;
			if (type.equals("VT")) // Virgin Trains, GB, evtl. auch highspeed?
				return 'R' + str;
			if (type.equals("SR")) // ScotRail, GB, evtl. auch long-distance?
				return 'R' + str;
			if (type.equals("AW")) // Arriva Trains Wales, GB
				return 'R' + str;
			if (type.equals("WS")) // Wrexham & Shropshire, GB
				return 'R' + str;
			if (type.equals("TP")) // First TransPennine Express, GB, evtl. auch long-distance?
				return 'R' + str;
			if (type.equals("GC")) // Grand Central, GB
				return 'R' + str;
			if (type.equals("IL")) // Island Line, GB
				return 'R' + str;
			if ("FCC".equals(type)) // First Capital Connect, GB
				return 'R' + str;
			if ("LE".equals(type)) // Greater Anglia, GB
				return 'R' + str;
			if (type.equals("BR")) // ??, GB
				return 'R' + str;
			if (type.equals("OO")) // ??, GB
				return 'R' + str;
			if (type.equals("XX")) // ??, GB
				return 'R' + str;
			if (type.equals("XZ")) // ??, GB
				return 'R' + str;
			if (type.equals("DB-Zug")) // VRR
				return 'R' + name;
			if (type.equals("DB"))
				return 'R' + name;
			if (type.equals("Regionalexpress")) // VRR
				return 'R' + name;
			if ("CAPITOL".equals(name)) // San Francisco
				return 'R' + name;
			if ("Train".equals(trainName) || "Train".equals(type)) // San Francisco
				return "R" + name;
			if ("Regional Train :".equals(longName))
				return "R";
			if ("Regional Train".equals(trainName)) // Melbourne
				return "R" + name;
			if ("Regional".equals(type)) // Melbourne
				return "R" + name;
			if (type.equals("ATB")) // Autoschleuse Tauernbahn
				return 'R' + name;
			if ("Chiemsee-Bahn".equals(type))
				return 'R' + name;
			if ("Regionalzug".equals(trainName)) // Liechtenstein
				return 'R' + name;
			if ("RegionalExpress".equals(trainName)) // Liechtenstein
				return 'R' + name;
			if ("Ostdeutsche".equals(type)) // Bayern
				return 'R' + type;
			if ("Südwestdeutsche".equals(type)) // Bayern
				return 'R' + type;
			if ("Mitteldeutsche".equals(type)) // Bayern
				return 'R' + type;
			if ("Norddeutsche".equals(type)) // Bayern
				return 'R' + type;
			if ("Hellertalbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Veolia".equals(type)) // Bayern
				return 'R' + type;
			if ("vectus".equals(type)) // Bayern
				return 'R' + type;
			if ("Hessische".equals(type)) // Bayern
				return 'R' + type;
			if ("Niederbarnimer".equals(type)) // Bayern
				return 'R' + type;
			if ("Rurtalbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Rhenus".equals(type)) // Bayern
				return 'R' + type;
			if ("Mittelrheinbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Hohenzollerische".equals(type)) // Bayern
				return 'R' + type;
			if ("Städtebahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Ortenau-S-Bahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Daadetalbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Mainschleifenbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Nordbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Harzer".equals(type)) // Bayern
				return 'R' + type;
			if ("cantus".equals(type)) // Bayern
				return 'R' + type;
			if ("DPF".equals(type)) // Bayern, Vogtland-Express
				return 'R' + type;
			if ("Freiberger".equals(type)) // Bayern
				return 'R' + type;
			if ("metronom".equals(type)) // Bayern
				return 'R' + type;
			if ("Prignitzer".equals(type)) // Bayern
				return 'R' + type;
			if ("Sächsisch-Oberlausitzer".equals(type)) // Bayern
				return 'R' + type;
			if ("Ostseeland".equals(type)) // Bayern
				return 'R' + type;
			if ("NordOstseeBahn".equals(type)) // Bayern
				return 'R' + type;
			if ("ELBE-WESER".equals(type)) // Bayern
				return 'R' + type;
			if ("TRILEX".equals(type)) // Bayern
				return 'R' + type;
			if ("Schleswig-Holstein-Bahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Vetter".equals(type)) // Bayern
				return 'R' + type;
			if ("Dessau-Wörlitzer".equals(type)) // Bayern
				return 'R' + type;
			if ("NATURPARK-EXPRESS".equals(type)) // Bayern
				return 'R' + type;
			if ("Usedomer".equals(type)) // Bayern
				return 'R' + type;
			if ("Märkische".equals(type)) // Bayern
				return 'R' + type;
			if ("Vulkan-Eifel-Bahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Kandertalbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("RAD-WANDER-SHUTTLE".equals(type)) // Bayern, Hohenzollerische Landesbahn
				return 'R' + type;
			if ("RADEXPRESS".equals(type)) // Bayern, RADEXPRESS EYACHTÄLER
				return 'R' + type;
			if ("Dampfzug".equals(type)) // Bayern
				return 'R' + type;
			if ("Wutachtalbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Grensland-Express".equals(type)) // Bayern
				return 'R' + type;
			if ("Mecklenburgische".equals(type)) // Bayern
				return 'R' + type;
			if ("Bentheimer".equals(type)) // Bayern
				return 'R' + type;
			if ("Pressnitztalbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Regental".equals(type)) // Bayern
				return 'R' + type;
			if ("Döllnitzbahn".equals(type)) // Bayern
				return 'R' + type;
			if ("Schneeberg".equals(type)) // VOR
				return 'R' + type;
			if ("FLZ".equals(type)) // Stainzer Flascherlzug
				return 'R' + type;
			if ("FTB".equals(type)) // Feistritztalbahn
				return 'R' + type;
			if ("DWE".equals(type)) // Dessau-Wörlitzer Eisenbahn
				return 'R' + type;
			if ("KTB".equals(type)) // Kandertalbahn
				return 'R' + type;
			if ("UEF".equals(type)) // Ulmer Eisenbahnfreunde
				return 'R' + type;
			if ("CBC".equals(type)) // City-Bahn Chemnitz
				return 'R' + type;
			if ("Regionalzug".equals(type))
				return 'R' + type;
			if ("RR".equals(type)) // RR 371 Horehronec / RR 404 Vltava / RR 922 Josef Skupa
				return 'R' + type;
			if ("ZAB1/766".equals(type))
				return "R" + name;
			if ("ZAB2/768".equals(type))
				return "R" + name;

			if ("BSB".equals(type)) // Breisgau-S-Bahn
				return 'S' + str;
			if ("BSB-Zug".equals(type)) // Breisgau-S-Bahn
				return 'S' + str;
			if ("Breisgau-S-Bahn".equals(type)) // Bayern
				return 'S' + type;
			if ("RSB".equals(type)) // Schnellbahn Wien
				return 'S' + type;
			if ("RER".equals(type)) // Réseau Express Régional, Frankreich
				return 'S' + str;
			if ("LO".equals(type)) // London Overground, GB
				return 'S' + str;
			if ("A".equals(name) || "B".equals(name) || "C".equals(name)) // SES
				return 'S' + str;
			final Matcher m = P_LINE_S.matcher(name);
			if (m.find())
				return 'S' + m.group(1);

			if (P_LINE_U.matcher(type).matches())
				return 'U' + str;
			if ("Underground".equals(type)) // London Underground, GB
				return 'U' + str;
			if ("Millbrae / Richmond".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Richmond / Millbrae".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Fremont / RIchmond".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Richmond / Fremont".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Pittsburg Bay Point / SFO".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("SFO / Pittsburg Bay Point".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Dublin Pleasanton / Daly City".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Daly City / Dublin Pleasanton".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Fremont / Daly City".equals(name)) // San Francisco, BART
				return 'U' + name;
			if ("Daly City / Fremont".equals(name)) // San Francisco, BART
				return 'U' + name;

			if (type.equals("RT")) // RegioTram
				return 'T' + str;
			if (type.equals("STR")) // Nordhausen
				return 'T' + str;
			if ("California Cable Car".equals(name)) // San Francisco
				return 'T' + name;
			if ("Muni".equals(type)) // San Francisco
				return 'T' + name;
			if ("Cable".equals(type)) // San Francisco
				return 'T' + name;
			if ("Muni Rail".equals(trainName)) // San Francisco
				return 'T' + name;
			if ("Cable Car".equals(trainName)) // San Francisco
				return 'T' + name;

			if ("BUS".equals(type) || "Bus".equals(type))
				return 'B' + str;
			if (P_LINE_SEV.matcher(type).matches())
				return 'B' + str;
			if ("Bex".equals(type)) // Bayern Express
				return 'B' + str;
			if ("Ersatzverkehr".equals(type)) // Rhein-Ruhr
				return 'B' + str;
			if ("Bus replacement".equals(trainName)) // Transport Line
				return 'B' + str;

			if ("HBL".equals(type)) // Hamburg Hafenfähre
				return 'F' + str;

			if ("AST".equals(type)) // Anruf-Sammel-Taxi
				return 'P' + str;

			if (type.length() == 0)
				return "?";
			if (P_LINE_NUMBER.matcher(type).matches())
				return "?" + ParserUtils.firstNotEmpty(symbol, name);
			if (P_LINE_Y.matcher(name).matches())
				return "?" + ParserUtils.firstNotEmpty(symbol, name);
			if ("Sonderverkehr Red Bull".equals(name))
				return "?" + name;

			throw new IllegalStateException("cannot normalize mot='" + mot + "' symbol='" + symbol + "' name='" + name + "' long='" + longName
					+ "' trainType='" + trainType + "' trainNum='" + trainNum + "' trainName='" + trainName + "' type='" + type + "' str='" + str
					+ "'");
		}

		if (t == 1)
		{
			final Matcher m = P_LINE_S.matcher(name);
			if (m.find())
				return 'S' + m.group(1);
			else
				return 'S' + name;
		}

		if (t == 2)
			return 'U' + name;

		if (t == 3 || t == 4)
			return 'T' + name;

		if (t == 5 || t == 6 || t == 7 || t == 10)
		{
			if (name.equals("Schienenersatzverkehr"))
				return "BSEV";
			else
				return 'B' + name;
		}

		if (t == 8)
			return 'C' + name;

		if (t == 9)
			return 'F' + name;

		if (t == 11 || t == -1)
			return '?' + ParserUtils.firstNotEmpty(symbol, name);

		throw new IllegalStateException("cannot normalize mot='" + mot + "' symbol='" + symbol + "' name='" + name + "' long='" + longName
				+ "' trainType='" + trainType + "' trainNum='" + trainNum + "' trainName='" + trainName + "'");
	}

	public QueryDeparturesResult queryDepartures(final int stationId, final int maxDepartures, final boolean equivs) throws IOException
	{
		final StringBuilder parameters = new StringBuilder();
		appendCommonRequestParams(parameters, "XML");
		parameters.append("&type_dm=stop");
		parameters.append("&name_dm=").append(stationId);
		parameters.append("&useRealtime=1");
		parameters.append("&mode=direct");
		parameters.append("&ptOptionsActive=1");
		parameters.append("&deleteAssignedStops_dm=").append(equivs ? '0' : '1');
		parameters.append("&mergeDep=1"); // merge departures
		if (maxDepartures > 0)
			parameters.append("&limit=").append(maxDepartures);

		final StringBuilder uri = new StringBuilder(departureMonitorEndpoint);
		if (!httpPost)
			uri.append(parameters);

		// System.out.println(uri);
		// System.out.println(parameters);

		InputStream is = null;
		try
		{
			is = ParserUtils.scrapeInputStream(uri.toString(), httpPost ? parameters.substring(1) : null, null, httpReferer, null, 3);

			final XmlPullParser pp = parserFactory.newPullParser();
			pp.setInput(is, null);
			final ResultHeader header = enterItdRequest(pp);

			XmlPullUtil.enter(pp, "itdDepartureMonitorRequest");

			if (!XmlPullUtil.test(pp, "itdOdv") || !"dm".equals(XmlPullUtil.attr(pp, "usage")))
				throw new IllegalStateException("cannot find <itdOdv usage=\"dm\" />");
			XmlPullUtil.enter(pp, "itdOdv");

			final String place = processItdOdvPlace(pp);

			XmlPullUtil.require(pp, "itdOdvName");
			final String nameState = pp.getAttributeValue(null, "state");
			XmlPullUtil.enter(pp, "itdOdvName");
			if ("identified".equals(nameState))
			{
				final QueryDeparturesResult result = new QueryDeparturesResult(header);

				final Location location = processOdvNameElem(pp, place);
				result.stationDepartures.add(new StationDepartures(location, new LinkedList<Departure>(), new LinkedList<LineDestination>()));

				XmlPullUtil.exit(pp, "itdOdvName");

				if (XmlPullUtil.test(pp, "itdOdvAssignedStops"))
				{
					XmlPullUtil.enter(pp, "itdOdvAssignedStops");
					while (XmlPullUtil.test(pp, "itdOdvAssignedStop"))
					{
						final Location assignedLocation = processItdOdvAssignedStop(pp);
						if (findStationDepartures(result.stationDepartures, assignedLocation.id) == null)
							result.stationDepartures.add(new StationDepartures(assignedLocation, new LinkedList<Departure>(),
									new LinkedList<LineDestination>()));
					}
					XmlPullUtil.exit(pp, "itdOdvAssignedStops");
				}

				XmlPullUtil.exit(pp, "itdOdv");

				if (XmlPullUtil.test(pp, "itdDateTime"))
					XmlPullUtil.next(pp);

				if (XmlPullUtil.test(pp, "itdDMDateTime"))
					XmlPullUtil.next(pp);

				if (XmlPullUtil.test(pp, "itdDateRange"))
					XmlPullUtil.next(pp);

				if (XmlPullUtil.test(pp, "itdTripOptions"))
					XmlPullUtil.next(pp);

				if (XmlPullUtil.test(pp, "itdMessage"))
					XmlPullUtil.next(pp);

				final Calendar plannedDepartureTime = new GregorianCalendar(timeZone());
				final Calendar predictedDepartureTime = new GregorianCalendar(timeZone());

				XmlPullUtil.require(pp, "itdServingLines");
				if (!pp.isEmptyElementTag())
				{
					XmlPullUtil.enter(pp, "itdServingLines");
					while (XmlPullUtil.test(pp, "itdServingLine"))
					{
						final String assignedStopIdStr = pp.getAttributeValue(null, "assignedStopID");
						final int assignedStopId = assignedStopIdStr != null ? Integer.parseInt(assignedStopIdStr) : 0;
						final String destinationName = normalizeLocationName(pp.getAttributeValue(null, "direction"));
						final String destinationIdStr = pp.getAttributeValue(null, "destID");
						final int destinationId = (destinationIdStr != null && destinationIdStr.length() > 0) ? Integer.parseInt(destinationIdStr)
								: 0;
						final Location destination = new Location(destinationId > 0 ? LocationType.STATION : LocationType.ANY,
								destinationId > 0 ? destinationId : 0, null, destinationName);
						final LineDestination line = new LineDestination(processItdServingLine(pp), destination);

						StationDepartures assignedStationDepartures;
						if (assignedStopId == 0)
							assignedStationDepartures = result.stationDepartures.get(0);
						else
							assignedStationDepartures = findStationDepartures(result.stationDepartures, assignedStopId);

						if (assignedStationDepartures == null)
							assignedStationDepartures = new StationDepartures(new Location(LocationType.STATION, assignedStopId),
									new LinkedList<Departure>(), new LinkedList<LineDestination>());

						if (!assignedStationDepartures.lines.contains(line))
							assignedStationDepartures.lines.add(line);
					}
					XmlPullUtil.exit(pp, "itdServingLines");
				}
				else
				{
					XmlPullUtil.next(pp);
				}

				XmlPullUtil.require(pp, "itdDepartureList");
				if (!pp.isEmptyElementTag())
				{
					XmlPullUtil.enter(pp, "itdDepartureList");
					while (XmlPullUtil.test(pp, "itdDeparture"))
					{
						final int assignedStopId = XmlPullUtil.intAttr(pp, "stopID");

						StationDepartures assignedStationDepartures = findStationDepartures(result.stationDepartures, assignedStopId);
						if (assignedStationDepartures == null)
						{
							final String mapName = pp.getAttributeValue(null, "mapName");
							final int lat;
							final int lon;

							if (mapName == null || mapName.length() == 0)
							{
								lat = 0;
								lon = 0;
							}
							else if ("WGS84".equals(mapName))
							{
								lat = Math.round(XmlPullUtil.floatAttr(pp, "y"));
								lon = Math.round(XmlPullUtil.floatAttr(pp, "x"));
							}
							else
							{
								throw new IllegalStateException("unknown mapName: " + mapName);
							}
							// final String name = normalizeLocationName(XmlPullUtil.attr(pp, "nameWO"));

							assignedStationDepartures = new StationDepartures(new Location(LocationType.STATION, assignedStopId, lat, lon),
									new LinkedList<Departure>(), new LinkedList<LineDestination>());
						}

						final String position;
						if (!suppressPositions)
							position = normalizePlatform(pp.getAttributeValue(null, "platform"), pp.getAttributeValue(null, "platformName"));
						else
							position = null;

						XmlPullUtil.enter(pp, "itdDeparture");

						XmlPullUtil.require(pp, "itdDateTime");
						plannedDepartureTime.clear();
						processItdDateTime(pp, plannedDepartureTime);

						predictedDepartureTime.clear();
						if (XmlPullUtil.test(pp, "itdRTDateTime"))
							processItdDateTime(pp, predictedDepartureTime);

						if (XmlPullUtil.test(pp, "itdFrequencyInfo"))
							XmlPullUtil.next(pp);

						XmlPullUtil.require(pp, "itdServingLine");
						final boolean isRealtime = pp.getAttributeValue(null, "realtime").equals("1");
						final String destinationName = normalizeLocationName(pp.getAttributeValue(null, "direction"));
						final String destinationIdStr = pp.getAttributeValue(null, "destID");
						final int destinationId = destinationIdStr != null ? Integer.parseInt(destinationIdStr) : 0;
						final Location destination = new Location(destinationId > 0 ? LocationType.STATION : LocationType.ANY,
								destinationId > 0 ? destinationId : 0, null, destinationName);
						final Line line = processItdServingLine(pp);

						if (isRealtime && !predictedDepartureTime.isSet(Calendar.HOUR_OF_DAY))
							predictedDepartureTime.setTimeInMillis(plannedDepartureTime.getTimeInMillis());

						XmlPullUtil.exit(pp, "itdDeparture");

						final Departure departure = new Departure(plannedDepartureTime.getTime(),
								predictedDepartureTime.isSet(Calendar.HOUR_OF_DAY) ? predictedDepartureTime.getTime() : null, line, position,
								destination, null, null);
						assignedStationDepartures.departures.add(departure);
					}

					XmlPullUtil.exit(pp, "itdDepartureList");
				}
				else
				{
					XmlPullUtil.next(pp);
				}

				return result;
			}
			else if ("notidentified".equals(nameState) || "list".equals(nameState))
			{
				return new QueryDeparturesResult(header, QueryDeparturesResult.Status.INVALID_STATION);
			}
			else
			{
				throw new RuntimeException("unknown nameState '" + nameState + "' on " + uri);
			}
		}
		catch (final XmlPullParserException x)
		{
			throw new ParserException(x);
		}
		finally
		{
			if (is != null)
				is.close();
		}
	}

	private StationDepartures findStationDepartures(final List<StationDepartures> stationDepartures, final int id)
	{
		for (final StationDepartures stationDeparture : stationDepartures)
			if (stationDeparture.location.id == id)
				return stationDeparture;

		return null;
	}

	private Location processItdPointAttributes(final XmlPullParser pp)
	{
		final int id = Integer.parseInt(pp.getAttributeValue(null, "stopID"));

		final String place = normalizeLocationName(pp.getAttributeValue(null, "locality"));
		String name = normalizeLocationName(pp.getAttributeValue(null, "nameWO"));
		if (name == null)
			name = normalizeLocationName(pp.getAttributeValue(null, "name"));

		final String mapName = pp.getAttributeValue(null, "mapName");
		final int lat;
		final int lon;
		if (mapName == null || mapName.length() == 0)
		{
			lat = 0;
			lon = 0;
		}
		else if ("WGS84".equals(mapName))
		{
			lat = Math.round(XmlPullUtil.floatAttr(pp, "y"));
			lon = Math.round(XmlPullUtil.floatAttr(pp, "x"));
		}
		else
		{
			throw new IllegalStateException("unknown mapName: " + mapName);
		}

		return new Location(LocationType.STATION, id, lat, lon, place, name);
	}

	private boolean processItdDateTime(final XmlPullParser pp, final Calendar calendar) throws XmlPullParserException, IOException
	{
		XmlPullUtil.enter(pp);
		calendar.clear();
		final boolean success = processItdDate(pp, calendar);
		if (success)
			processItdTime(pp, calendar);
		XmlPullUtil.exit(pp);

		return success;
	}

	private boolean processItdDate(final XmlPullParser pp, final Calendar calendar) throws XmlPullParserException, IOException
	{
		XmlPullUtil.require(pp, "itdDate");
		final int year = Integer.parseInt(pp.getAttributeValue(null, "year"));
		final int month = Integer.parseInt(pp.getAttributeValue(null, "month")) - 1;
		final int day = Integer.parseInt(pp.getAttributeValue(null, "day"));
		final int weekday = Integer.parseInt(pp.getAttributeValue(null, "weekday"));
		XmlPullUtil.next(pp);

		if (weekday < 0)
			return false;
		if (year == 0)
			return false;
		if (year < 1900 || year > 2100)
			throw new InvalidDataException("invalid year: " + year);
		if (month < 0 || month > 11)
			throw new InvalidDataException("invalid month: " + month);
		if (day < 1 || day > 31)
			throw new InvalidDataException("invalid day: " + day);

		calendar.set(Calendar.YEAR, year);
		calendar.set(Calendar.MONTH, month);
		calendar.set(Calendar.DAY_OF_MONTH, day);
		return true;
	}

	private void processItdTime(final XmlPullParser pp, final Calendar calendar) throws XmlPullParserException, IOException
	{
		XmlPullUtil.require(pp, "itdTime");
		calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(pp.getAttributeValue(null, "hour")));
		calendar.set(Calendar.MINUTE, Integer.parseInt(pp.getAttributeValue(null, "minute")));
		XmlPullUtil.next(pp);
	}

	private Line processItdServingLine(final XmlPullParser pp) throws XmlPullParserException, IOException
	{
		XmlPullUtil.require(pp, "itdServingLine");
		final String slMotType = pp.getAttributeValue(null, "motType");
		final String slSymbol = pp.getAttributeValue(null, "symbol");
		final String slNumber = pp.getAttributeValue(null, "number");
		final String slStateless = pp.getAttributeValue(null, "stateless");
		final String slTrainType = pp.getAttributeValue(null, "trainType");
		final String slTrainName = pp.getAttributeValue(null, "trainName");
		final String slTrainNum = pp.getAttributeValue(null, "trainNum");

		XmlPullUtil.enter(pp, "itdServingLine");
		String itdTrainName = null;
		String itdTrainType = null;
		String itdMessage = null;
		if (XmlPullUtil.test(pp, "itdTrain"))
		{
			itdTrainName = pp.getAttributeValue(null, "name");
			itdTrainType = pp.getAttributeValue(null, "type");
			if (!pp.isEmptyElementTag())
			{
				XmlPullUtil.enter(pp, "itdTrain");
				XmlPullUtil.exit(pp, "itdTrain");
			}
			else
			{
				XmlPullUtil.next(pp);
			}
		}
		if (XmlPullUtil.test(pp, "itdNoTrain"))
		{
			itdTrainName = pp.getAttributeValue(null, "name");
			itdTrainType = pp.getAttributeValue(null, "type");
			if (!pp.isEmptyElementTag())
			{
				XmlPullUtil.enter(pp, "itdNoTrain");
				final String text = pp.getText();
				if (itdTrainName.toLowerCase().contains("ruf") && text.toLowerCase().contains("ruf"))
					itdMessage = text;
				XmlPullUtil.exit(pp, "itdNoTrain");
			}
			else
			{
				XmlPullUtil.next(pp);
			}
		}
		XmlPullUtil.exit(pp, "itdServingLine");

		final String trainType = ParserUtils.firstNotEmpty(slTrainType, itdTrainType);
		final String trainName = ParserUtils.firstNotEmpty(slTrainName, itdTrainName);

		final String label = parseLine(slMotType, slSymbol, slNumber, slNumber, trainType, slTrainNum, trainName);

		return new Line(slStateless, label, lineStyle(label), itdMessage);
	}

	private static final Pattern P_STATION_NAME_WHITESPACE = Pattern.compile("\\s+");

	protected String normalizeLocationName(final String name)
	{
		if (name == null || name.length() == 0)
			return null;

		return P_STATION_NAME_WHITESPACE.matcher(name).replaceAll(" ");
	}

	protected static double latLonToDouble(final int value)
	{
		return (double) value / 1000000;
	}

	protected String xsltTripRequestParameters(final Location from, final Location via, final Location to, final Date date, final boolean dep,
			final int numConnections, final Collection<Product> products, final WalkSpeed walkSpeed, final Accessibility accessibility,
			final Set<Option> options)
	{
		final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
		final DateFormat TIME_FORMAT = new SimpleDateFormat("HHmm", Locale.US);

		final StringBuilder uri = new StringBuilder();
		appendCommonRequestParams(uri, "XML");

		uri.append("&sessionID=0");
		uri.append("&requestID=0");
		uri.append("&language=de");

		appendCommonXsltTripRequest2Params(uri);

		appendLocation(uri, from, "origin");
		appendLocation(uri, to, "destination");
		if (via != null)
			appendLocation(uri, via, "via");

		uri.append("&itdDate=").append(ParserUtils.urlEncode(DATE_FORMAT.format(date)));
		uri.append("&itdTime=").append(ParserUtils.urlEncode(TIME_FORMAT.format(date)));
		uri.append("&itdTripDateTimeDepArr=").append(dep ? "dep" : "arr");

		uri.append("&calcNumberOfTrips=").append(numConnections);

		uri.append("&ptOptionsActive=1"); // enable public transport options
		uri.append("&itOptionsActive=1"); // enable individual transport options
		uri.append("&changeSpeed=").append(WALKSPEED_MAP.get(walkSpeed));

		if (accessibility == Accessibility.BARRIER_FREE)
			uri.append("&imparedOptionsActive=1").append("&wheelchair=on").append("&noSolidStairs=on");
		else if (accessibility == Accessibility.LIMITED)
			uri.append("&imparedOptionsActive=1").append("&wheelchair=on").append("&lowPlatformVhcl=on").append("&noSolidStairs=on");

		if (products != null)
		{
			uri.append("&includedMeans=checkbox");

			boolean hasI = false;
			for (final Product p : products)
			{
				if (p == Product.HIGH_SPEED_TRAIN || p == Product.REGIONAL_TRAIN)
				{
					uri.append("&inclMOT_0=on");
					if (p == Product.HIGH_SPEED_TRAIN)
						hasI = true;
				}

				if (p == Product.SUBURBAN_TRAIN)
					uri.append("&inclMOT_1=on");

				if (p == Product.SUBWAY)
					uri.append("&inclMOT_2=on");

				if (p == Product.TRAM)
					uri.append("&inclMOT_3=on&inclMOT_4=on");

				if (p == Product.BUS)
					uri.append("&inclMOT_5=on&inclMOT_6=on&inclMOT_7=on");

				if (p == Product.ON_DEMAND)
					uri.append("&inclMOT_10=on");

				if (p == Product.FERRY)
					uri.append("&inclMOT_9=on");

				if (p == Product.CABLECAR)
					uri.append("&inclMOT_8=on");
			}

			uri.append("&inclMOT_11=on"); // TODO always show 'others', for now

			// workaround for highspeed trains: fails when you want highspeed, but not regional
			if (useLineRestriction && !hasI)
				uri.append("&lineRestriction=403"); // means: all but ice
		}

		if (options != null && options.contains(Option.BIKE))
			uri.append("&bikeTakeAlong=1");

		uri.append("&locationServerActive=1");
		uri.append("&useRealtime=1");
		uri.append("&useProxFootSearch=1"); // walk if it makes journeys quicker
		uri.append("&nextDepsPerLeg=1"); // next departure in case previous was missed

		return uri.toString();
	}

	private String commandLink(final String sessionId, final String requestId)
	{
		final StringBuilder uri = new StringBuilder(tripEndpoint);

		uri.append("?sessionID=").append(sessionId);
		uri.append("&requestID=").append(requestId);
		appendCommonXsltTripRequest2Params(uri);

		return uri.toString();
	}

	private static final void appendCommonXsltTripRequest2Params(final StringBuilder uri)
	{
		uri.append("&coordListOutputFormat=STRING");
		uri.append("&calcNumberOfTrips=4");
	}

	public QueryConnectionsResult queryConnections(final Location from, final Location via, final Location to, final Date date, final boolean dep,
			final int numConnections, final Collection<Product> products, final WalkSpeed walkSpeed, final Accessibility accessibility,
			final Set<Option> options) throws IOException
	{

		final String parameters = xsltTripRequestParameters(from, via, to, date, dep, numConnections, products, walkSpeed, accessibility, options);

		final StringBuilder uri = new StringBuilder(tripEndpoint);
		if (!httpPost)
			uri.append(parameters);

		// System.out.println(uri);
		// System.out.println(parameters);

		InputStream is = null;
		try
		{
			is = ParserUtils.scrapeInputStream(uri.toString(), httpPost ? parameters.substring(1) : null, null, httpRefererTrip, "NSC_", 3);
			return queryConnections(uri.toString(), is);
		}
		catch (final XmlPullParserException x)
		{
			throw new ParserException(x);
		}
		catch (final RuntimeException x)
		{
			throw new RuntimeException("uncategorized problem while processing " + uri, x);
		}
		finally
		{
			if (is != null)
				is.close();
		}
	}

	private static final Pattern P_SESSION_EXPIRED = Pattern.compile("Your session has expired");

	public QueryConnectionsResult queryMoreConnections(final QueryConnectionsContext contextObj, final boolean later, final int numConnections)
			throws IOException
	{
		final Context context = (Context) contextObj;
		final String commandUri = context.context;
		final StringBuilder uri = new StringBuilder(commandUri);
		uri.append("&command=").append(later ? "tripNext" : "tripPrev");

		InputStream is = null;
		try
		{
			is = new BufferedInputStream(ParserUtils.scrapeInputStream(uri.toString(), null, null, httpRefererTrip, "NSC_", 3));
			is.mark(512);

			return queryConnections(uri.toString(), is);
		}
		catch (final XmlPullParserException x)
		{
			throw new ParserException(x);
		}
		catch (final ProtocolException x) // must be html content
		{
			is.reset();
			final BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			String line;
			while ((line = reader.readLine()) != null)
				if (P_SESSION_EXPIRED.matcher(line).find())
					throw new SessionExpiredException();

			throw x;
		}
		catch (final RuntimeException x)
		{
			throw new RuntimeException("uncategorized problem while processing " + uri, x);
		}
		finally
		{
			if (is != null)
				is.close();
		}
	}

	private QueryConnectionsResult queryConnections(final String uri, final InputStream is) throws XmlPullParserException, IOException
	{
		// System.out.println(uri);

		final XmlPullParser pp = parserFactory.newPullParser();
		pp.setInput(is, null);
		final ResultHeader header = enterItdRequest(pp);
		final Object context = header.context;

		if (XmlPullUtil.test(pp, "itdLayoutParams"))
			XmlPullUtil.next(pp);

		XmlPullUtil.require(pp, "itdTripRequest");
		final String requestId = XmlPullUtil.attr(pp, "requestID");
		XmlPullUtil.enter(pp, "itdTripRequest");

		if (XmlPullUtil.test(pp, "itdMessage"))
		{
			final int code = XmlPullUtil.intAttr(pp, "code");
			if (code == -4000) // no connection
				return new QueryConnectionsResult(header, QueryConnectionsResult.Status.NO_CONNECTIONS);
			XmlPullUtil.next(pp);
		}
		if (XmlPullUtil.test(pp, "itdPrintConfiguration"))
			XmlPullUtil.next(pp);
		if (XmlPullUtil.test(pp, "itdAddress"))
			XmlPullUtil.next(pp);

		// parse odv name elements
		List<Location> ambiguousFrom = null, ambiguousTo = null, ambiguousVia = null;
		Location from = null, via = null, to = null;

		while (XmlPullUtil.test(pp, "itdOdv"))
		{
			final String usage = XmlPullUtil.attr(pp, "usage");
			XmlPullUtil.enter(pp, "itdOdv");

			final String place = processItdOdvPlace(pp);

			if (!XmlPullUtil.test(pp, "itdOdvName"))
				throw new IllegalStateException("cannot find <itdOdvName /> inside " + usage);
			final String nameState = XmlPullUtil.attr(pp, "state");
			XmlPullUtil.enter(pp, "itdOdvName");
			if (XmlPullUtil.test(pp, "itdMessage"))
				XmlPullUtil.next(pp);

			if ("list".equals(nameState))
			{
				if ("origin".equals(usage))
				{
					ambiguousFrom = new ArrayList<Location>();
					while (XmlPullUtil.test(pp, "odvNameElem"))
						ambiguousFrom.add(processOdvNameElem(pp, place));
				}
				else if ("via".equals(usage))
				{
					ambiguousVia = new ArrayList<Location>();
					while (XmlPullUtil.test(pp, "odvNameElem"))
						ambiguousVia.add(processOdvNameElem(pp, place));
				}
				else if ("destination".equals(usage))
				{
					ambiguousTo = new ArrayList<Location>();
					while (XmlPullUtil.test(pp, "odvNameElem"))
						ambiguousTo.add(processOdvNameElem(pp, place));
				}
				else
				{
					throw new IllegalStateException("unknown usage: " + usage);
				}
			}
			else if ("identified".equals(nameState))
			{
				if (!XmlPullUtil.test(pp, "odvNameElem"))
					throw new IllegalStateException("cannot find <odvNameElem /> inside " + usage);

				if ("origin".equals(usage))
					from = processOdvNameElem(pp, place);
				else if ("via".equals(usage))
					via = processOdvNameElem(pp, place);
				else if ("destination".equals(usage))
					to = processOdvNameElem(pp, place);
				else
					throw new IllegalStateException("unknown usage: " + usage);
			}
			else if ("notidentified".equals(nameState))
			{
				if ("origin".equals(usage))
					return new QueryConnectionsResult(header, QueryConnectionsResult.Status.UNKNOWN_FROM);
				else if ("via".equals(usage))
					// return new QueryConnectionsResult(header, QueryConnectionsResult.Status.UNKNOWN_VIA);
					throw new UnsupportedOperationException();
				else if ("destination".equals(usage))
					return new QueryConnectionsResult(header, QueryConnectionsResult.Status.UNKNOWN_TO);
				else
					throw new IllegalStateException("unknown usage: " + usage);
			}
			XmlPullUtil.exit(pp, "itdOdvName");
			XmlPullUtil.exit(pp, "itdOdv");
		}

		if (ambiguousFrom != null || ambiguousTo != null || ambiguousVia != null)
			return new QueryConnectionsResult(header, ambiguousFrom, ambiguousVia, ambiguousTo);

		XmlPullUtil.enter(pp, "itdTripDateTime");
		XmlPullUtil.enter(pp, "itdDateTime");
		XmlPullUtil.require(pp, "itdDate");
		if (!pp.isEmptyElementTag())
		{
			XmlPullUtil.enter(pp, "itdDate");
			if (XmlPullUtil.test(pp, "itdMessage"))
			{
				final String message = XmlPullUtil.nextText(pp, null, "itdMessage");

				if ("invalid date".equals(message))
					return new QueryConnectionsResult(header, QueryConnectionsResult.Status.INVALID_DATE);
				else
					throw new IllegalStateException("unknown message: " + message);
			}
			XmlPullUtil.exit(pp, "itdDate");
		}
		else
		{
			XmlPullUtil.next(pp);
		}
		XmlPullUtil.exit(pp, "itdDateTime");

		final Calendar time = new GregorianCalendar(timeZone());
		final List<Connection> connections = new ArrayList<Connection>();

		if (XmlPullUtil.jumpToStartTag(pp, null, "itdRouteList"))
		{
			XmlPullUtil.enter(pp, "itdRouteList");

			while (XmlPullUtil.test(pp, "itdRoute"))
			{
				final String id = useRouteIndexAsConnectionId ? pp.getAttributeValue(null, "routeIndex") + "-"
						+ pp.getAttributeValue(null, "routeTripIndex") : null;
				final int numChanges = XmlPullUtil.intAttr(pp, "changes");
				XmlPullUtil.enter(pp, "itdRoute");

				while (XmlPullUtil.test(pp, "itdDateTime"))
					XmlPullUtil.next(pp);
				if (XmlPullUtil.test(pp, "itdMapItemList"))
					XmlPullUtil.next(pp);

				XmlPullUtil.enter(pp, "itdPartialRouteList");
				final List<Connection.Part> parts = new LinkedList<Connection.Part>();
				Location firstDepartureLocation = null;
				Location lastArrivalLocation = null;

				boolean cancelled = false;

				while (XmlPullUtil.test(pp, "itdPartialRoute"))
				{
					final String partialRouteType = XmlPullUtil.attr(pp, "type");
					final int distance = XmlPullUtil.optIntAttr(pp, "distance", 0);
					XmlPullUtil.enter(pp, "itdPartialRoute");

					XmlPullUtil.test(pp, "itdPoint");
					if (!"departure".equals(pp.getAttributeValue(null, "usage")))
						throw new IllegalStateException();
					final Location departureLocation = processItdPointAttributes(pp);
					if (firstDepartureLocation == null)
						firstDepartureLocation = departureLocation;
					final String departurePosition;
					if (!suppressPositions)
						departurePosition = normalizePlatform(pp.getAttributeValue(null, "platform"), pp.getAttributeValue(null, "platformName"));
					else
						departurePosition = null;
					XmlPullUtil.enter(pp, "itdPoint");
					if (XmlPullUtil.test(pp, "itdMapItemList"))
						XmlPullUtil.next(pp);
					XmlPullUtil.require(pp, "itdDateTime");
					processItdDateTime(pp, time);
					final Date departureTime = time.getTime();
					final Date departureTargetTime;
					if (XmlPullUtil.test(pp, "itdDateTimeTarget"))
					{
						processItdDateTime(pp, time);
						departureTargetTime = time.getTime();
					}
					else
					{
						departureTargetTime = null;
					}
					XmlPullUtil.exit(pp, "itdPoint");

					XmlPullUtil.test(pp, "itdPoint");
					if (!"arrival".equals(pp.getAttributeValue(null, "usage")))
						throw new IllegalStateException();
					final Location arrivalLocation = processItdPointAttributes(pp);
					lastArrivalLocation = arrivalLocation;
					final String arrivalPosition;
					if (!suppressPositions)
						arrivalPosition = normalizePlatform(pp.getAttributeValue(null, "platform"), pp.getAttributeValue(null, "platformName"));
					else
						arrivalPosition = null;
					XmlPullUtil.enter(pp, "itdPoint");
					if (XmlPullUtil.test(pp, "itdMapItemList"))
						XmlPullUtil.next(pp);
					XmlPullUtil.require(pp, "itdDateTime");
					processItdDateTime(pp, time);
					final Date arrivalTime = time.getTime();
					final Date arrivalTargetTime;
					if (XmlPullUtil.test(pp, "itdDateTimeTarget"))
					{
						processItdDateTime(pp, time);
						arrivalTargetTime = time.getTime();
					}
					else
					{
						arrivalTargetTime = null;
					}
					XmlPullUtil.exit(pp, "itdPoint");

					XmlPullUtil.test(pp, "itdMeansOfTransport");
					final String productName = pp.getAttributeValue(null, "productName");
					if ("IT".equals(partialRouteType) || "Fussweg".equals(productName) || "Taxi".equals(productName))
					{
						final int min = (int) (arrivalTime.getTime() - departureTime.getTime()) / 1000 / 60;
						final boolean transfer = "Taxi".equals(productName);

						XmlPullUtil.enter(pp, "itdMeansOfTransport");
						XmlPullUtil.exit(pp, "itdMeansOfTransport");

						if (XmlPullUtil.test(pp, "itdStopSeq"))
							XmlPullUtil.next(pp);

						if (XmlPullUtil.test(pp, "itdFootPathInfo"))
							XmlPullUtil.next(pp);

						List<Point> path = null;
						if (XmlPullUtil.test(pp, "itdPathCoordinates"))
							path = processItdPathCoordinates(pp);

						if (parts.size() > 0 && parts.get(parts.size() - 1) instanceof Connection.Footway)
						{
							final Connection.Footway lastFootway = (Connection.Footway) parts.remove(parts.size() - 1);
							if (path != null && lastFootway.path != null)
								path.addAll(0, lastFootway.path);
							parts.add(new Connection.Footway(lastFootway.min + min, distance, lastFootway.transfer || transfer,
									lastFootway.departure, arrivalLocation, path));
						}
						else
						{
							parts.add(new Connection.Footway(min, distance, transfer, departureLocation, arrivalLocation, path));
						}
					}
					else if ("gesicherter Anschluss".equals(productName) || "nicht umsteigen".equals(productName)) // type97
					{
						// ignore

						XmlPullUtil.enter(pp, "itdMeansOfTransport");
						XmlPullUtil.exit(pp, "itdMeansOfTransport");
					}
					else if ("PT".equals(partialRouteType))
					{
						final String destinationName = normalizeLocationName(pp.getAttributeValue(null, "destination"));
						final String destinationIdStr = pp.getAttributeValue(null, "destID");
						final int destinationId = (destinationIdStr != null && destinationIdStr.length() > 0) ? Integer.parseInt(destinationIdStr)
								: 0;
						final Location destination = new Location(destinationId > 0 ? LocationType.STATION : LocationType.ANY,
								destinationId > 0 ? destinationId : 0, null, destinationName);
						final String lineLabel;
						final String motSymbol = pp.getAttributeValue(null, "symbol");
						if ("AST".equals(motSymbol))
						{
							lineLabel = "BAST";
						}
						else
						{
							final String motType = pp.getAttributeValue(null, "motType");
							final String motShortName = pp.getAttributeValue(null, "shortname");
							final String motName = pp.getAttributeValue(null, "name");
							final String motTrainName = pp.getAttributeValue(null, "trainName");
							final String motTrainType = pp.getAttributeValue(null, "trainType");

							lineLabel = parseLine(motType, motSymbol, motShortName, motName, motTrainType, motShortName, motTrainName);
						}
						XmlPullUtil.enter(pp, "itdMeansOfTransport");
						XmlPullUtil.require(pp, "motDivaParams");
						final String lineId = XmlPullUtil.attr(pp, "network") + ':' + XmlPullUtil.attr(pp, "line") + ':'
								+ XmlPullUtil.attr(pp, "supplement") + ':' + XmlPullUtil.attr(pp, "direction") + ':'
								+ XmlPullUtil.attr(pp, "project");
						XmlPullUtil.exit(pp, "itdMeansOfTransport");

						final Integer departureDelay;
						final Integer arrivalDelay;
						if (XmlPullUtil.test(pp, "itdRBLControlled"))
						{
							departureDelay = XmlPullUtil.optIntAttr(pp, "delayMinutes", 0);
							arrivalDelay = XmlPullUtil.optIntAttr(pp, "delayMinutesArr", 0);

							cancelled |= (departureDelay == -9999 || arrivalDelay == -9999);

							XmlPullUtil.next(pp);
						}
						else
						{
							departureDelay = null;
							arrivalDelay = null;
						}

						boolean lowFloorVehicle = false;
						String message = null;
						if (XmlPullUtil.test(pp, "itdInfoTextList"))
						{
							if (!pp.isEmptyElementTag())
							{
								XmlPullUtil.enter(pp, "itdInfoTextList");
								while (XmlPullUtil.test(pp, "infoTextListElem"))
								{
									XmlPullUtil.enter(pp, "infoTextListElem");
									final String text = pp.getText();
									if ("Niederflurwagen soweit verfügbar".equals(text)) // KVV
										lowFloorVehicle = true;
									else if (text != null && text.toLowerCase().contains("ruf")) // RufBus, RufTaxi
										message = text;
									XmlPullUtil.exit(pp, "infoTextListElem");
								}
								XmlPullUtil.exit(pp, "itdInfoTextList");
							}
							else
							{
								XmlPullUtil.next(pp);
							}
						}

						if (XmlPullUtil.test(pp, "itdFootPathInfo"))
							XmlPullUtil.next(pp);
						if (XmlPullUtil.test(pp, "infoLink"))
							XmlPullUtil.next(pp);

						List<Stop> intermediateStops = null;
						if (XmlPullUtil.test(pp, "itdStopSeq"))
						{
							XmlPullUtil.enter(pp, "itdStopSeq");
							intermediateStops = new LinkedList<Stop>();
							while (XmlPullUtil.test(pp, "itdPoint"))
							{
								final Location stopLocation = processItdPointAttributes(pp);

								final String stopPosition;
								if (!suppressPositions)
									stopPosition = normalizePlatform(pp.getAttributeValue(null, "platform"),
											pp.getAttributeValue(null, "platformName"));
								else
									stopPosition = null;

								XmlPullUtil.enter(pp, "itdPoint");
								XmlPullUtil.require(pp, "itdDateTime");

								final Date plannedStopArrivalTime;
								final Date predictedStopArrivalTime;
								if (processItdDateTime(pp, time))
								{
									plannedStopArrivalTime = time.getTime();
									if (arrivalDelay != null)
									{
										time.add(Calendar.MINUTE, arrivalDelay);
										predictedStopArrivalTime = time.getTime();
									}
									else
									{
										predictedStopArrivalTime = null;
									}
								}
								else
								{
									plannedStopArrivalTime = null;
									predictedStopArrivalTime = null;
								}

								final Date plannedStopDepartureTime;
								final Date predictedStopDepartureTime;
								if (XmlPullUtil.test(pp, "itdDateTime") && processItdDateTime(pp, time))
								{
									plannedStopDepartureTime = time.getTime();
									if (departureDelay != null)
									{
										time.add(Calendar.MINUTE, departureDelay);
										predictedStopDepartureTime = time.getTime();
									}
									else
									{
										predictedStopDepartureTime = null;
									}
								}
								else
								{
									plannedStopDepartureTime = null;
									predictedStopDepartureTime = null;
								}

								final Stop stop = new Stop(stopLocation, plannedStopArrivalTime, predictedStopArrivalTime, stopPosition, null,
										plannedStopDepartureTime, predictedStopDepartureTime, stopPosition, null);

								intermediateStops.add(stop);

								XmlPullUtil.exit(pp, "itdPoint");
							}
							XmlPullUtil.exit(pp, "itdStopSeq");

							// remove first and last, because they are not intermediate
							final int size = intermediateStops.size();
							if (size >= 2)
							{
								if (intermediateStops.get(size - 1).location.id != arrivalLocation.id)
									throw new IllegalStateException();
								intermediateStops.remove(size - 1);

								if (intermediateStops.get(0).location.id != departureLocation.id)
									throw new IllegalStateException();
								intermediateStops.remove(0);
							}
						}

						List<Point> path = null;
						if (XmlPullUtil.test(pp, "itdPathCoordinates"))
							path = processItdPathCoordinates(pp);

						boolean wheelChairAccess = false;
						if (XmlPullUtil.test(pp, "genAttrList"))
						{
							XmlPullUtil.enter(pp, "genAttrList");
							while (XmlPullUtil.test(pp, "genAttrElem"))
							{
								XmlPullUtil.enter(pp, "genAttrElem");
								XmlPullUtil.enter(pp, "name");
								final String name = pp.getText();
								XmlPullUtil.exit(pp, "name");
								XmlPullUtil.enter(pp, "value");
								final String value = pp.getText();
								XmlPullUtil.exit(pp, "value");
								XmlPullUtil.exit(pp, "genAttrElem");

								// System.out.println("genAttrElem: name='" + name + "' value='" + value + "'");

								if ("PlanWheelChairAccess".equals(name) && "1".equals(value))
									wheelChairAccess = true;
							}
							XmlPullUtil.exit(pp, "genAttrList");
						}

						if (XmlPullUtil.test(pp, "nextDeps"))
						{
							XmlPullUtil.enter(pp, "nextDeps");
							while (XmlPullUtil.test(pp, "itdDateTime"))
							{
								processItdDateTime(pp, time);
								/* final Date nextDepartureTime = */time.getTime();
							}
							XmlPullUtil.exit(pp, "nextDeps");
						}

						final Set<Line.Attr> lineAttrs = new HashSet<Line.Attr>();
						if (wheelChairAccess || lowFloorVehicle)
							lineAttrs.add(Line.Attr.WHEEL_CHAIR_ACCESS);
						final Line line = new Line(lineId, lineLabel, lineStyle(lineLabel), lineAttrs);

						final Stop departure = new Stop(departureLocation, true, departureTargetTime != null ? departureTargetTime : departureTime,
								departureTargetTime != null ? departureTime : null, departurePosition, null);
						final Stop arrival = new Stop(arrivalLocation, false, arrivalTargetTime != null ? arrivalTargetTime : arrivalTime,
								arrivalTime != null ? arrivalTime : null, arrivalPosition, null);

						parts.add(new Connection.Trip(line, destination, departure, arrival, intermediateStops, path, message));
					}
					else
					{
						throw new IllegalStateException("unknown type: '" + partialRouteType + "' '" + productName + "'");
					}

					XmlPullUtil.exit(pp, "itdPartialRoute");
				}

				XmlPullUtil.exit(pp, "itdPartialRouteList");

				final List<Fare> fares = new ArrayList<Fare>(2);
				if (XmlPullUtil.test(pp, "itdFare"))
				{
					if (!pp.isEmptyElementTag())
					{
						XmlPullUtil.enter(pp, "itdFare");
						if (XmlPullUtil.test(pp, "itdSingleTicket"))
						{
							final String net = XmlPullUtil.attr(pp, "net");
							final Currency currency = parseCurrency(XmlPullUtil.attr(pp, "currency"));
							final String fareAdult = XmlPullUtil.attr(pp, "fareAdult");
							final String fareChild = XmlPullUtil.attr(pp, "fareChild");
							final String unitName = XmlPullUtil.attr(pp, "unitName");
							final String unitsAdult = XmlPullUtil.attr(pp, "unitsAdult");
							final String unitsChild = XmlPullUtil.attr(pp, "unitsChild");
							final String levelAdult = pp.getAttributeValue(null, "levelAdult");
							final boolean hasLevelAdult = levelAdult != null && levelAdult.length() > 0;
							final String levelChild = pp.getAttributeValue(null, "levelChild");
							final boolean hasLevelChild = levelChild != null && levelChild.length() > 0;
							if (fareAdult != null && fareAdult.length() > 0)
								fares.add(new Fare(net, Type.ADULT, currency, Float.parseFloat(fareAdult), hasLevelAdult ? null : unitName,
										hasLevelAdult ? levelAdult : unitsAdult));
							if (fareChild != null && fareChild.length() > 0)
								fares.add(new Fare(net, Type.CHILD, currency, Float.parseFloat(fareChild), hasLevelChild ? null : unitName,
										hasLevelChild ? levelChild : unitsChild));

							if (!pp.isEmptyElementTag())
							{
								XmlPullUtil.enter(pp, "itdSingleTicket");
								if (XmlPullUtil.test(pp, "itdGenericTicketList"))
								{
									XmlPullUtil.enter(pp, "itdGenericTicketList");
									while (XmlPullUtil.test(pp, "itdGenericTicketGroup"))
									{
										final Fare fare = processItdGenericTicketGroup(pp, net, currency);
										if (fare != null)
											fares.add(fare);
									}
									XmlPullUtil.exit(pp, "itdGenericTicketList");
								}
								XmlPullUtil.exit(pp, "itdSingleTicket");
							}
							else
							{
								XmlPullUtil.next(pp);
							}
						}
						XmlPullUtil.exit(pp, "itdFare");
					}
					else
					{
						XmlPullUtil.next(pp);
					}
				}

				XmlPullUtil.exit(pp, "itdRoute");

				final Connection connection = new Connection(id, firstDepartureLocation, lastArrivalLocation, parts, fares.isEmpty() ? null : fares,
						null, numChanges);

				if (!cancelled)
					connections.add(connection);
			}

			XmlPullUtil.exit(pp, "itdRouteList");

			return new QueryConnectionsResult(header, uri, from, via, to, new Context(commandLink((String) context, requestId)), connections);
		}
		else
		{
			return new QueryConnectionsResult(header, QueryConnectionsResult.Status.NO_CONNECTIONS);
		}
	}

	private List<Point> processItdPathCoordinates(final XmlPullParser pp) throws XmlPullParserException, IOException
	{
		final List<Point> path = new LinkedList<Point>();

		XmlPullUtil.enter(pp, "itdPathCoordinates");

		XmlPullUtil.enter(pp, "coordEllipsoid");
		final String ellipsoid = pp.getText();
		XmlPullUtil.exit(pp, "coordEllipsoid");

		if (!"WGS84".equals(ellipsoid))
			throw new IllegalStateException("unknown ellipsoid: " + ellipsoid);

		XmlPullUtil.enter(pp, "coordType");
		final String type = pp.getText();
		XmlPullUtil.exit(pp, "coordType");

		if (!"GEO_DECIMAL".equals(type))
			throw new IllegalStateException("unknown type: " + type);

		XmlPullUtil.enter(pp, "itdCoordinateString");
		for (final String coordStr : pp.getText().split(" +"))
		{
			final String[] coordsStr = coordStr.split(",");
			path.add(new Point(Math.round(Float.parseFloat(coordsStr[1])), Math.round(Float.parseFloat(coordsStr[0]))));
		}
		XmlPullUtil.exit(pp, "itdCoordinateString");

		XmlPullUtil.exit(pp, "itdPathCoordinates");

		return path;
	}

	private Fare processItdGenericTicketGroup(final XmlPullParser pp, final String net, final Currency currency) throws XmlPullParserException,
			IOException
	{
		XmlPullUtil.enter(pp, "itdGenericTicketGroup");

		Type type = null;
		float fare = 0;

		while (XmlPullUtil.test(pp, "itdGenericTicket"))
		{
			XmlPullUtil.enter(pp, "itdGenericTicket");

			XmlPullUtil.enter(pp, "ticket");
			final String key = pp.getText().trim();
			XmlPullUtil.exit(pp, "ticket");

			String value = null;
			XmlPullUtil.require(pp, "value");
			if (!pp.isEmptyElementTag())
			{
				XmlPullUtil.enter(pp, "value");
				value = pp.getText();
				if (value != null)
					value = value.trim();
				XmlPullUtil.exit(pp, "value");
			}
			else
			{
				XmlPullUtil.next(pp);
			}

			if (key.equals("FOR_RIDER"))
			{
				final String typeStr = value.split(" ")[0].toUpperCase();
				if (typeStr.equals("REGULAR"))
					type = Type.ADULT;
				else
					type = Type.valueOf(typeStr);
			}
			else if (key.equals("PRICE"))
			{
				fare = Float.parseFloat(value) * (currency.getCurrencyCode().equals("USD") ? 0.01f : 1);
			}

			XmlPullUtil.exit(pp, "itdGenericTicket");
		}

		XmlPullUtil.exit(pp, "itdGenericTicketGroup");

		if (type != null)
			return new Fare(net, type, currency, fare, null, null);
		else
			return null;
	}

	private Currency parseCurrency(final String currencyStr)
	{
		if (currencyStr.equals("US$"))
			return Currency.getInstance("USD");
		if (currencyStr.equals("Dirham"))
			return Currency.getInstance("AED");
		return Currency.getInstance(currencyStr);
	}

	private static final Pattern P_PLATFORM = Pattern.compile("#?(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern P_PLATFORM_NAME = Pattern.compile("(?:Gleis|Gl\\.|Bstg\\.)?\\s*" + //
			"(\\d+)\\s*" + //
			"(?:([A-Z])\\s*(?:-\\s*([A-Z]))?)?", Pattern.CASE_INSENSITIVE);

	private static final String normalizePlatform(final String platform, final String platformName)
	{
		if (platform != null && platform.length() > 0)
		{
			final Matcher m = P_PLATFORM.matcher(platform);
			if (m.matches())
			{
				return Integer.toString(Integer.parseInt(m.group(1)));
			}
			else
			{
				return platform;
			}
		}

		if (platformName != null && platformName.length() > 0)
		{
			final Matcher m = P_PLATFORM_NAME.matcher(platformName);
			if (m.matches())
			{
				final String simple = Integer.toString(Integer.parseInt(m.group(1)));
				if (m.group(2) != null && m.group(3) != null)
					return simple + m.group(2) + "-" + m.group(3);
				else if (m.group(2) != null)
					return simple + m.group(2);
				else
					return simple;
			}
			else
			{
				return platformName;
			}
		}

		return null;
	}

	private void appendLocation(final StringBuilder uri, final Location location, final String paramSuffix)
	{
		if (canAcceptPoiId && location.type == LocationType.POI && location.hasId())
		{
			uri.append("&type_").append(paramSuffix).append("=poiID");
			uri.append("&name_").append(paramSuffix).append("=").append(location.id);
		}
		else if ((location.type == LocationType.POI || location.type == LocationType.ADDRESS) && location.hasLocation())
		{
			uri.append("&type_").append(paramSuffix).append("=coord");
			uri.append("&name_").append(paramSuffix).append("=")
					.append(String.format(Locale.ENGLISH, "%.6f:%.6f", location.lon / 1E6, location.lat / 1E6)).append(":WGS84");
		}
		else
		{
			uri.append("&type_").append(paramSuffix).append("=").append(locationTypeValue(location));
			uri.append("&name_").append(paramSuffix).append("=").append(ParserUtils.urlEncode(locationValue(location), requestUrlEncoding));
		}
	}

	protected static final String locationTypeValue(final Location location)
	{
		final LocationType type = location.type;
		if (type == LocationType.STATION)
			return "stop";
		if (type == LocationType.ADDRESS)
			return "any"; // strange, matches with anyObjFilter
		if (type == LocationType.POI)
			return "poi";
		if (type == LocationType.ANY)
			return "any";
		throw new IllegalArgumentException(type.toString());
	}

	protected static final String locationValue(final Location location)
	{
		if ((location.type == LocationType.STATION || location.type == LocationType.POI) && location.hasId())
			return Integer.toString(location.id);
		else
			return location.name;
	}

	protected static final Map<WalkSpeed, String> WALKSPEED_MAP = new HashMap<WalkSpeed, String>();

	static
	{
		WALKSPEED_MAP.put(WalkSpeed.SLOW, "slow");
		WALKSPEED_MAP.put(WalkSpeed.NORMAL, "normal");
		WALKSPEED_MAP.put(WalkSpeed.FAST, "fast");
	}

	private ResultHeader enterItdRequest(final XmlPullParser pp) throws XmlPullParserException, IOException
	{
		if (pp.getEventType() != XmlPullParser.START_DOCUMENT)
			throw new IllegalStateException("start of document expected");

		try
		{
			pp.next();
		}
		catch (final XmlPullParserException x)
		{
			if (x.getMessage().startsWith("Expected a quoted string"))
				throw new ProtocolException("html");
		}

		if (pp.getEventType() == XmlPullParser.DOCDECL)
			pp.next();

		if (XmlPullUtil.test(pp, "html"))
			throw new ProtocolException("html");

		XmlPullUtil.require(pp, "itdRequest");

		final String serverVersion = XmlPullUtil.attr(pp, "version");
		final String now = XmlPullUtil.attr(pp, "now");
		final String sessionId = XmlPullUtil.attr(pp, "sessionID");

		final Calendar serverTime = new GregorianCalendar(timeZone());
		ParserUtils.parseIsoDate(serverTime, now.substring(0, 10));
		ParserUtils.parseEuropeanTime(serverTime, now.substring(11));

		final ResultHeader header = new ResultHeader(SERVER_PRODUCT, serverVersion, serverTime.getTimeInMillis(), sessionId);

		XmlPullUtil.enter(pp, "itdRequest");

		if (XmlPullUtil.test(pp, "clientHeaderLines"))
			XmlPullUtil.next(pp);

		if (XmlPullUtil.test(pp, "itdVersionInfo"))
			XmlPullUtil.next(pp);

		if (XmlPullUtil.test(pp, "itdInfoLinkList"))
			XmlPullUtil.next(pp);

		if (XmlPullUtil.test(pp, "serverMetaInfo"))
			XmlPullUtil.next(pp);

		return header;
	}
}
