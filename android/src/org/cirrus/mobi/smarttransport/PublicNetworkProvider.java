package org.cirrus.mobi.smarttransport;

import java.io.IOException;
import java.util.List;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;

public class PublicNetworkProvider {
	
	interface ResultCallbacks {
		public void nearbyStationsReceived(NearbyStationsResult result);
		public void DepaturesReceived(QueryDeparturesResult result);
	}

	private ResultCallbacks callbackInterface;
	private NetworkProvider networkProvider;
	private FetchNearByStationsTask fnbst;
	
	public PublicNetworkProvider(ResultCallbacks callbackInterface, NetworkProvider networkProvider)
	{
		this.callbackInterface = callbackInterface;
		this.networkProvider = networkProvider;
	}
	
	public boolean getNearbyStations(Location location) {
		if(fnbst != null) //we already search
			return false;
		fnbst = new FetchNearByStationsTask();
		if(location != null)
			fnbst.execute(location);
		return true;
	}

	private void recievedStations(NearbyStationsResult result) {
		if(this.callbackInterface != null)
			this.callbackInterface.nearbyStationsReceived(result);
	}
	
	public void getDepatures(List<de.schildbach.pte.dto.Location> stations)
	{
		FetchDepaturesTask fetchDepaturesTask = new FetchDepaturesTask();
		fetchDepaturesTask.execute(stations);
	}

	
	class FetchNearByStationsTask extends AsyncTask<Location, Void, NearbyStationsResult>
	{
		public static final String TAG = "SMT/FNBST";
		private static final int MAX_STATIONS = 1;

		@Override
		protected NearbyStationsResult doInBackground(Location... params) {
	
			if(BuildConfig.DEBUG)
				Log.v(TAG, "fetching stations....");
			de.schildbach.pte.dto.Location pteLoc = new de.schildbach.pte.dto.Location(LocationType.ANY, (int)(params[0].getLatitude()*1E6), (int)(params[0].getLongitude()*1E6));
			try {
				NearbyStationsResult nsr = networkProvider.queryNearbyStations(pteLoc, 0, MAX_STATIONS);
				
				if(nsr.status == nsr.status.OK)
				{
					if(BuildConfig.DEBUG)
					{
						Log.v(TAG, "!! Status ok, found "+nsr.stations.size()+ "stations");
						List<de.schildbach.pte.dto.Location> stations = nsr.stations;
						for (de.schildbach.pte.dto.Location station : stations) {
							if(BuildConfig.DEBUG)
								Log.v(TAG, "Station: "+station.id+ " name: "+station.name+ "place "+station.place+ " short "+station.uniqueShortName());
						}
					}
					return nsr;
				}
			} catch (IOException e) {
				Log.e(TAG, "IOException fetching stations");
				e.printStackTrace();
			}
			return null;
		}
		@Override
		protected void onPostExecute(NearbyStationsResult result) {		
			super.onPostExecute(result);
			recievedStations(result);
		}
	}


	class FetchDepaturesTask extends AsyncTask<List<de.schildbach.pte.dto.Location>, QueryDeparturesResult, Void>
	{
		public static final String TAG = "SMT/FDT";

		@Override
		protected Void doInBackground(
				List<de.schildbach.pte.dto.Location>... params) {
			for (de.schildbach.pte.dto.Location station : params[0]) {
				try {
					QueryDeparturesResult qdr = networkProvider.queryDepartures(station.id, 15, true);
					if(BuildConfig.DEBUG)
					{
						if(qdr.status == de.schildbach.pte.dto.QueryDeparturesResult.Status.OK)
						{
							Log.v(TAG, "QDR: Okay Headers: "+qdr.header+" dep: "+qdr.stationDepartures);
						}
						List<StationDepartures> statDep = qdr.stationDepartures;
						for (StationDepartures stationDepartures : statDep) {
							Log.v(TAG, "stationDep: "+stationDepartures);
							List<Departure> depatures = stationDepartures.departures;
							for (Departure departure : depatures) {
								Log.v(TAG, "Depature: "+departure);
							}
						}
						
						
					}
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return null;
		}
	
	}


}
