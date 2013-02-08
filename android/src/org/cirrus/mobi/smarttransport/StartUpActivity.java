package org.cirrus.mobi.smarttransport;
/**
 *	 This file is part of SmartTransport
 *
 *   SmartTransport is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   SmartTransport is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with SmartTransport.  If not, see <http://www.gnu.org/licenses/>.
 */
import java.io.IOException;
import java.util.List;

import org.cirrus.mobi.smarttransport.PublicNetworkProvider.ResultCallbacks;

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

public class StartUpActivity extends Activity implements ResultCallbacks {

	private LocationManager locationManager;
	private NetworkProvider networkProvider;

	private Location clocation;
	private PublicNetworkProvider publicNetworkProvider;

	public static final String TAG = "SMT/StartUpActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start_up);

		networkProvider = new BahnProvider();
		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		publicNetworkProvider = new PublicNetworkProvider(this, networkProvider);

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



	// Define a listener that responds to location updates
	LocationListener locationListener = new LocationListener() {

		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			clocation = location;
			Log.v(TAG, "got location to: "+location);
			publicNetworkProvider.getNearbyStations(location);
			locationManager.removeUpdates(locationListener);
		};

		public void onStatusChanged(String provider, int status, Bundle extras) {}

		public void onProviderEnabled(String provider) {}

		public void onProviderDisabled(String provider) {}
	};

	@Override
	public void nearbyStationsReceived(NearbyStationsResult result) {
//		publicNetworkProvider.getDepatures(result.stations);
		
	}
	@Override
	public void depaturesReceived(QueryDeparturesResult result) {
		
		
	}



}
