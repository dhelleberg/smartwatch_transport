package org.cirrus.mobi.smarttransport;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import de.schildbach.pte.BahnProvider;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;

public class StartUpActivity extends Activity {

	private LocationManager locationManager;
	private NetworkProvider networkProvider;

	private Location clocation;
	private FetchNearByStationsTask fnbst = null;

	public static final String TAG = "SMT/StartUpActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start_up);

		networkProvider = new BahnProvider();
		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		/*Button b1 = (Button) findViewById(R.id.button1);
		b1.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getNearbyStations();
			}


		});*/
	}
	@Override
	protected void onStart() {	
		super.onStart();
		//locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);		
	}

	@Override
	protected void onStop() {	
		super.onStop();
		locationManager.removeUpdates(locationListener);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.activity_start_up, menu);
		return true;
	}

	private void getNearbyStations() {
		if(fnbst != null) //we already search
			return;
		fnbst = new FetchNearByStationsTask();
		if(clocation != null)
			fnbst.execute(clocation);
	}

	private void recievedStations(NearbyStationsResult result) {
		//query depatures
		FetchDepaturesTask fetchDepaturesTask = new FetchDepaturesTask();
		fetchDepaturesTask.execute(result.stations);
	}


	// Define a listener that responds to location updates
	LocationListener locationListener = new LocationListener() {

		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			clocation = location;
			Log.v(TAG, "got location to: "+location);
			getNearbyStations();
			locationManager.removeUpdates(locationListener);
		};

		public void onStatusChanged(String provider, int status, Bundle extras) {}

		public void onProviderEnabled(String provider) {}

		public void onProviderDisabled(String provider) {}
	};

	class FetchDepaturesTask extends AsyncTask<List<de.schildbach.pte.dto.Location>, QueryDeparturesResult, Void>
	{

		@Override
		protected Void doInBackground(
				List<de.schildbach.pte.dto.Location>... params) {
			for (de.schildbach.pte.dto.Location station : params[0]) {
				try {
					QueryDeparturesResult qdr = networkProvider.queryDepartures(station.id, 15, true);
					if(BuildConfig.DEBUG)
					{
						if(qdr.status == qdr.status.OK)
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


}
