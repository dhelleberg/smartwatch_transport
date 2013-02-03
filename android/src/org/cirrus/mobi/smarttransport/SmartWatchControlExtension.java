package org.cirrus.mobi.smarttransport;

import java.util.Iterator;
import java.util.List;

import org.cirrus.mobi.smarttransport.PublicNetworkProvider.ResultCallbacks;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.sonyericsson.extras.liveware.aef.control.Control;
import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;

import de.schildbach.pte.BahnProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
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
public class SmartWatchControlExtension extends ControlExtension implements ResultCallbacks {

	private Handler mHandler;
	private Context mContext;
	private int width;
	private int height;
	private Bitmap mBackground;
	private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.RGB_565;

	private static final int STATE_INITIAL = 1;
	private static final int STATE_SEARCHING = 2;
	private static final int STATE_DISPLAY_DATA = 3;
	protected static final String TAG = "SMT/SWCE";
	
	private int state = STATE_INITIAL;
	private BahnProvider networkProvider;
	private LocationManager locationManager;
	private PublicNetworkProvider publicNetworkProvider;
	private NearbyStationsResult mNearbyStationsResult;
	private int mStationIndex;
	private QueryDeparturesResult mQueryDeparturesResult;
	private LayoutInflater mInflater;

	public SmartWatchControlExtension(Context context, String hostAppPackageName, Handler handler) {
		super(context, hostAppPackageName);
		if (handler == null) {
			throw new IllegalArgumentException("handler == null");
		}
		mHandler = handler;
		mContext = context;
		width = getSupportedControlWidth(context);
		height = getSupportedControlHeight(context);

	}


	/**
	 * Get supported control width.
	 *
	 * @param context The context.
	 * @return the width.
	 */
	public static int getSupportedControlWidth(Context context) {
		return context.getResources().getDimensionPixelSize(R.dimen.smart_watch_control_width);
	}

	/**
	 * Get supported control height.
	 *
	 * @param context The context.
	 * @return the height.
	 */
	public static int getSupportedControlHeight(Context context) {
		return context.getResources().getDimensionPixelSize(R.dimen.smart_watch_control_height);
	}
	
	@Override
	public void onStart() {	
		super.onStart();
		//intial call, kick search
		state = STATE_SEARCHING;
		
		mStationIndex = 0;
		
		//TODO: get this from config
		networkProvider = new BahnProvider();
		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
		publicNetworkProvider = new PublicNetworkProvider(this, networkProvider);

		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		
		mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

	}
	
	@Override
	public void onPause() {
		super.onPause();
		locationManager.removeUpdates(locationListener);
	}

	/**
	 * this method is expected to display a bitmap
	 */
	@Override
	public void onResume() {    
		super.onResume();
		redraw();
	}

	private void redraw() {
		switch (state) {
		case STATE_SEARCHING:
			showSearchImage();
			break;
		case STATE_DISPLAY_DATA:
			showData();
		}
		
	}


	@Override
	public void onSwipe(int direction) {	
		super.onSwipe(direction);
		switch (state) {
		case STATE_DISPLAY_DATA:
			handleSwipe(direction);
			break;

		default:
			break;
		}
	}
	
	private void handleSwipe(int direction) {
		switch (direction) {
		case Control.Intents.SWIPE_DIRECTION_LEFT:
			if(mNearbyStationsResult != null)
			{
				mStationIndex--;
				if(mStationIndex < 0)
					mStationIndex = mNearbyStationsResult.stations.size()-1;
				redraw();
			}
			break;
		case Control.Intents.SWIPE_DIRECTION_RIGHT:
			if(mNearbyStationsResult != null)
			{
				mStationIndex++;
				if(mStationIndex > mNearbyStationsResult.stations.size()-1)
					mStationIndex = 0;
				redraw();
			}
			
			break;
			
		default:
			break;
		}
		
	}


	private void showSearchImage() {
		// Create background bitmap for animation.
		mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		RelativeLayout locatingLayout = (RelativeLayout)RelativeLayout.inflate(mContext, R.layout.locating, null);
		// Draw on canvas
		Canvas canvas = new Canvas(mBackground);
		locatingLayout.draw(canvas);
		// Send bitmap to accessory
		showBitmap(mBackground);
		
	}


	public void showData()
	{	
		// Create background bitmap for animation.
		mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		//LinearLayout root = new LinearLayout(mContext); root.setLayoutParams(new LayoutParams(width, height));
		//mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		LinearLayout stationsLayout = (LinearLayout)LinearLayout.inflate(mContext, R.layout.smartwatch_stations, null);
		stationsLayout.setLayoutParams(new LayoutParams(width, height));
		if(BuildConfig.DEBUG)
			Log.d(TAG, "Using: w:"+width+" h: "+height);
		//fill Data
		//station name
		int departureRows = -1;
		if(mNearbyStationsResult != null)
		{
			de.schildbach.pte.dto.Location station = mNearbyStationsResult.stations.get(mStationIndex);
			TextView stationName = (TextView) stationsLayout.findViewById(R.id.Station);
			stationName.setText(shortStationName(station));
			int lines = stationName.getLineCount();
			departureRows = 4 - lines;
		}
		//depatures
		if(mQueryDeparturesResult != null)
		{
			//how many rows can we insert
			TableLayout tl = (TableLayout) stationsLayout.findViewById(R.id.departuesTable);
			List<Departure> departures = mQueryDeparturesResult.stationDepartures.get(mStationIndex).departures;			
			for(int i = 0; i < departureRows; i++)
			{
				View table = mInflater.inflate(R.layout.table_row_departure, tl, true);
				
				View row = ((ViewGroup)table).getChildAt(i*2);
				View textView = ((ViewGroup)table).getChildAt((i*2)+1);
				
				Departure currDep = departures.get(i);
				TextView depLine = (TextView) row.findViewById(R.id.depLine);
				depLine.setText(currDep.line.label);
				
				TextView depTime = (TextView) row.findViewById(R.id.depTime);
				depTime.setText(currDep.plannedTime+"");//TODO: delays
				
				TextView depDest = (TextView) textView.findViewById(R.id.depTarget);
				depDest.setText(currDep.destination.name);
				
				//tl.addView(row);
			}
		}
		stationsLayout.measure(width, height); 
		stationsLayout.layout(0, 0, stationsLayout.getMeasuredWidth(),
				stationsLayout.getMeasuredHeight());

		// Draw on canvas
		Canvas canvas = new Canvas(mBackground);
		stationsLayout.draw(canvas);
		// Send bitmap to accessory
		showBitmap(mBackground);
	}


	private CharSequence shortStationName(de.schildbach.pte.dto.Location station) {
		String stationName = "unknown";
		if(!TextUtils.isEmpty(station.name))
		{
			stationName = station.name;
			int index = station.name.indexOf(",");
			if(index > 0)
				stationName = station.name.substring(0, index);
		}
		return stationName;
	}


	@Override
	public void nearbyStationsReceived(NearbyStationsResult result) {
		this.mNearbyStationsResult = result;
		state = STATE_DISPLAY_DATA;
		redraw();
		
		publicNetworkProvider.getDepatures(result.stations);
	}


	@Override
	public void depaturesReceived(QueryDeparturesResult result) {
		this.mQueryDeparturesResult = result;
		redraw();
		
	}

	// Define a listener that responds to location updates
	LocationListener locationListener = new LocationListener() {

		private Location clocation;

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

}