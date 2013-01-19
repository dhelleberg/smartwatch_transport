package org.cirrus.mobi.smarttransport;

import java.io.IOException;

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
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;

public class StartUpActivity extends Activity {

	private LocationManager locationManager;
	private NetworkProvider networkProvider;

	private Location clocation;
	private FetchNearByStationsTask fnbst;

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
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);		
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


	class FetchNearByStationsTask extends AsyncTask<Location, Void, NearbyStationsResult>
	{
		public static final String TAG = "SMT/FNBST";

		@Override
		protected NearbyStationsResult doInBackground(Location... params) {
			//kick location updates

			if(BuildConfig.DEBUG)
				Log.v(TAG, "fetching stations....");
			de.schildbach.pte.dto.Location pteLoc = new de.schildbach.pte.dto.Location(LocationType.ANY, (int)(params[0].getLatitude()*1E6), (int)(params[0].getLongitude()*1E6));
			try {
				NearbyStationsResult nsr = networkProvider.queryNearbyStations(pteLoc, 0, 10);
				
				if(nsr.status == nsr.status.OK)
				{
					if(BuildConfig.DEBUG)
						Log.v(TAG, "!! Status ok, found "+nsr.stations.size()+ "stations");	
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}



		// Register the listener with the Location Manager to receive location updates


	}

}
