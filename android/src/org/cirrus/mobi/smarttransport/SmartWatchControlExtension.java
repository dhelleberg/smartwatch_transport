package org.cirrus.mobi.smarttransport;

import java.util.ArrayList;
import java.util.List;

import android.os.AsyncTask;
import com.arconsis.android.datarobot.EntityService;
import com.sonyericsson.extras.liveware.extension.util.control.ControlTouchEvent;
import de.schildbach.pte.dto.*;
import org.acra.ACRA;
import org.cirrus.mobi.smarttransport.PublicNetworkProvider.ResultCallbacks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.sonyericsson.extras.liveware.aef.control.Control;
import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;

import de.schildbach.pte.NetworkProvider;
import org.cirrus.mobi.smarttransport.dto.FavLocation;

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

	private static final int MAX_DEPATURE_ROWS = 3;
    private final PublicTransportationAPI mPublicTransportationAPI;
    private Handler mHandler;


    private Context mContext;
    private int width;
    private int height;
    private Bitmap mBackground;
    private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.RGB_565;
	private static final int STATE_INITIAL = 1;

    private static final int STATE_SEARCHING = 2;
    private static final int STATE_DISPLAY_DATA = 3;
    private static final int STATE_LOADING = 4;
    private static final int STATE_ERROR = 5;
    private static final int STATE_SELECT_PROVIDER = 6;
    private static final int STATE_ERROR_NOPROVIDER = 7;
    private static final int STATE_SELECT_MODE = 8;
    private static final int STATE_NO_FAVS_HELP_TEXT = 9;
    private static final int STATE_SAVED_FAV = 10;

	protected static final String TAG = "SMT/SWCE";


	private int state = STATE_INITIAL;
	private NetworkProvider networkProvider;
	private LocationManager locationManager;
	private PublicNetworkProvider publicNetworkProvider;
	//private NearbyStationsResult mNearbyStationsResult;
	private int mStationIndex;
	private List<QueryDeparturesResult> mQueryDeparturesResults;
	private LayoutInflater mInflater;
	private int mScrollIndex;
    private String mNetwork;
    private SharedPreferences mSharedPref;
    private int mProviderIndex;
    private String mErrorMessage = "";
    private String[] mProviderEntries;
    private List<de.schildbach.pte.dto.Location> stations = null;


    public SmartWatchControlExtension(Context context, String hostAppPackageName, Handler handler) {
		super(context, hostAppPackageName);
		if (handler == null) {
			throw new IllegalArgumentException("handler == null");
		}
		mHandler = handler;
		mContext = context;
		width = getSupportedControlWidth(context);
		height = getSupportedControlHeight(context);

		mQueryDeparturesResults = new ArrayList<QueryDeparturesResult>(0);

        mPublicTransportationAPI = new PublicTransportationAPI(mContext);
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

    private void startSearch()
    {
        state = STATE_SEARCHING;
        mStationIndex = 0;
        if(BuildConfig.DEBUG)
            Log.d(TAG, "start location updates, enabled providers: "+locationManager.getProviders(true));
        if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        else if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        else
        {
            //errorState!
            state = STATE_ERROR_NOPROVIDER;
            ACRA.getErrorReporter().putCustomData("No locationProvider enabled!", locationManager.getProviders(true).toString());
            ACRA.getErrorReporter().handleException(null);

        }
        redraw();
    }

    @Override
    public void onStop() {
        if(publicNetworkProvider != null)
            publicNetworkProvider.cancelRequests();
    }

	@Override
	public void onStart() {
		super.onStart();

        // Acquire a reference to the system Location Manager
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        //should fix NPE seen on ACRA
        this.mQueryDeparturesResults = new ArrayList<QueryDeparturesResult>(0);

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);

        mProviderEntries = mContext.getResources().getStringArray(R.array.pref_transportNetwork_Entries);

        //detect not selected provider
        if(!mSharedPref.contains(mContext.getResources().getString(R.string.pref_publicnetwork)))
        {
            selectProvider();
        }
        else
        {
            //loadProvider from Preferences
            String providerClass = mSharedPref.getString(mContext.getResources().getString(R.string.pref_publicnetwork), mContext.getResources().getString(R.string.pref_transportNetwork_default));
            this.publicNetworkProvider = mPublicTransportationAPI.initNetworkProvider(this, providerClass);
            this.mNetwork = mPublicTransportationAPI.getNetworkForProvider(providerClass);

            //select mode
            selectMode();
            //intial call, kick search
            //startSearch();
        }




	}

    private void selectMode() {
        state = STATE_SELECT_MODE;
        redraw();
    }

    private void selectProvider() {
        state = STATE_SELECT_PROVIDER;
        redraw();
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
			break;
		case STATE_LOADING:
			showLoadingImage();
			break;
        case STATE_ERROR:
            this.mErrorMessage = mContext.getResources().getString(R.string.text_nostations);
            showErrorMessage();
            break;
        case STATE_ERROR_NOPROVIDER:
            this.mErrorMessage = mContext.getResources().getString(R.string.text_nolocationprovider);
            showErrorMessage();
            break;
        case STATE_SELECT_PROVIDER:
            showProviderSelection();
            break;
        case STATE_SELECT_MODE:
            showModeSelection();
            break;
        case STATE_NO_FAVS_HELP_TEXT:
            this.mErrorMessage = mContext.getResources().getString(R.string.text_nofavs);
            showErrorMessage();
            break;
        case STATE_SAVED_FAV:
            this.mErrorMessage = mContext.getResources().getString(R.string.text_savefavs);
            showErrorMessage();
            break;

        }



	}

    private void showModeSelection() {
        mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        LinearLayout selectModeLayout = (LinearLayout) LinearLayout.inflate(mContext, R.layout.mode_selection,null);
        selectModeLayout.setLayoutParams(new LayoutParams(width, height));

        layout(selectModeLayout);
        drawLayout(selectModeLayout);
    }

    private void showProviderSelection() {

        mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        RelativeLayout selectProviderLayout = (RelativeLayout) RelativeLayout.inflate(mContext, R.layout.select_provider,null);
        selectProviderLayout.setLayoutParams(new LayoutParams(width, height));

        TextView selectedProviderText = (TextView) selectProviderLayout.findViewById(R.id.textSelectedProvider);
        selectedProviderText.setText(mProviderEntries[mProviderIndex]);

        layout(selectProviderLayout);
        drawLayout(selectProviderLayout);
    }

    private void drawLayout(ViewGroup selectProviderLayout) {
        // Draw on canvas
        Canvas canvas = new Canvas(mBackground);
        selectProviderLayout.draw(canvas);
        // Send bitmap to accessory
        showBitmap(mBackground);
    }

    private void showErrorMessage() {
        // Create background bitmap for animation.
        mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        RelativeLayout errorLayout = (RelativeLayout)RelativeLayout.inflate(mContext, R.layout.no_stations, null);
        errorLayout.setLayoutParams(new LayoutParams(width, height));

        TextView textViewErrorMsg = (TextView) errorLayout.findViewById(R.id.errortext);
        textViewErrorMsg.setText(mErrorMessage);

        //layout
        layout(errorLayout);

        // Draw on canvas
        drawLayout(errorLayout);


    }


     @Override
	public void onSwipe(int direction) {
		super.onSwipe(direction);
		switch (state) {
		case STATE_DISPLAY_DATA:
			handleSwipeStationView(direction);
			break;
        case STATE_SELECT_PROVIDER:
            handleSwipeSelectProvider(direction);
            break;


		default:
			break;
		}
	}

    private void handleSwipeSelectProvider(int direction) {
        switch (direction) {
            case Control.Intents.SWIPE_DIRECTION_LEFT:
                if(mProviderEntries != null)
                {
                    mProviderIndex--;
                    if(mProviderIndex < 0)
                        mProviderIndex = mProviderEntries.length-1;
                    redraw();
                }
                break;
            case Control.Intents.SWIPE_DIRECTION_RIGHT:
                if(mProviderEntries != null)
                {
                    mProviderIndex++;
                    if(mProviderIndex >= mProviderEntries.length)
                        mProviderIndex = 0;
                    redraw();
                }
                break;
        }
    }

    @Override
    public void onTouch(ControlTouchEvent event) {
        super.onTouch(event);
        switch (event.getAction())
        {
            case Control.Intents.TOUCH_ACTION_PRESS:
                switch (state)
                {
                    case STATE_ERROR:
                        startSearch();
                        break;
                    case STATE_SELECT_PROVIDER:
                        selectCurrentProvider();
                        break;
                    case STATE_SELECT_MODE:
                        //upper or lower selection
                        int y = event.getY();
                        if(y <= (height/ 2))
                            startSearch();
                        else
                            showFavs();
                        break;
                    case STATE_NO_FAVS_HELP_TEXT:
                        startSearch();
                        break;
                }
                break;
            case Control.Intents.TOUCH_ACTION_LONGPRESS:
                switch (state)
                {
                    case STATE_DISPLAY_DATA:
                        addCurrentStationToFavs();
                        break;
                }
                break;

        }
    }

    private void showFavs() {
        //query database, should be async
        EntityService favLocationService = new EntityService(mContext, FavLocation.class);
        List<FavLocation> favLocations = favLocationService.get();
        favLocationService.close();

        if(favLocations != null && favLocations.size() > 0) {
            if(BuildConfig.DEBUG)
                Log.d(TAG, "found: "+favLocations.size()+" favs");
            //set the favs as new result and search
            stations = new ArrayList<de.schildbach.pte.dto.Location>();
            for (int i = 0; i < favLocations.size(); i++) {
                FavLocation favLocation = favLocations.get(i);
                stations.add(new de.schildbach.pte.dto.Location(LocationType.STATION, favLocation.id, favLocation.lat, favLocation.lon, favLocation.place, favLocation.name));
            }

            state = STATE_DISPLAY_DATA;
            redraw();
            // for eacht station, request depatures
            for (de.schildbach.pte.dto.Location station: stations ) {
                publicNetworkProvider.getDepatures(station);
            }

        }
        else {
            //show help text
            state = STATE_NO_FAVS_HELP_TEXT;
            redraw();
        }
    }

    private void addCurrentStationToFavs() {
        if(stations != null) {
            de.schildbach.pte.dto.Location station = stations.get(mStationIndex);
            if(BuildConfig.DEBUG)
                Log.d(TAG, "saving: "+station.name+" id: "+station.id+" type: "+station.type + "lat: "+station.lat+ " lon: "+station.lon);
            EntityService favLocationService = new EntityService(mContext, FavLocation.class);
            FavLocation favLocation = new FavLocation(station.id, station.lat, station.lon, station.place, station.name);
            //is it there already?
            List <FavLocation> result = favLocationService.find("ID = ?",new String[]{station.id},null);
            if(result.size() == 0) {
                favLocationService.save(favLocation);
                showFavSavedScreen();
            }
            else
                Log.d(TAG, favLocation+ " already exists, do not store");
            favLocationService.close();
        }

    }

    private void showFavSavedScreen() {
        state = STATE_SAVED_FAV;
        startVibrator(100,500, 1);
        DelayDisplayChangeTask delayDisplayChangeTask = new DelayDisplayChangeTask();
        delayDisplayChangeTask.execute();
        redraw();

    }

    private void selectCurrentProvider() {
        //get Provider class and write it to prefs etc.
        String providerclass = mContext.getResources().getStringArray(R.array.pref_transportNetwork_values)[mProviderIndex];

        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putString(mContext.getResources().getString(R.string.pref_publicnetwork), providerclass);
        editor.commit();

        this.publicNetworkProvider = mPublicTransportationAPI.initNetworkProvider(this, providerclass);

        startSearch();

    }

    private void handleSwipeStationView(int direction) {
		switch (direction) {
		case Control.Intents.SWIPE_DIRECTION_LEFT:
			if(stations != null)
			{
				mScrollIndex = 0; //reset scroll index in case we switch stations
				mStationIndex--;
				if(mStationIndex < 0)
					mStationIndex = stations.size()-1;
				redraw();
			}
			break;
		case Control.Intents.SWIPE_DIRECTION_RIGHT:
			if(stations != null)
			{
				mScrollIndex = 0; //reset scroll index in case we switch stations
				mStationIndex++;
				if(mStationIndex > stations.size()-1)
					mStationIndex = 0;
				redraw();
			}
			break;

		case Control.Intents.SWIPE_DIRECTION_DOWN:
			mScrollIndex--;
			if(mScrollIndex < 0 )
				mScrollIndex = 0;
			redraw();
			break;

		case Control.Intents.SWIPE_DIRECTION_UP:
			mScrollIndex++;
			redraw();
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
		locatingLayout.setLayoutParams(new LayoutParams(width, height));

        //getTextView and set current network
        TextView textView = (TextView) locatingLayout.findViewById(R.id.networkText);
        textView.setText(mNetwork);


		//layout
        layout(locatingLayout);
		// Draw on canvas
        drawLayout(locatingLayout);

	}


	private void showLoadingImage() {
		// Create background bitmap for animation.
		mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		RelativeLayout loadingLayout = (RelativeLayout)RelativeLayout.inflate(mContext, R.layout.loading, null);
		loadingLayout.setLayoutParams(new LayoutParams(width, height));

        //getTextView and set current network
        TextView textView = (TextView) loadingLayout.findViewById(R.id.networkText);
        textView.setText(mNetwork);

        //layout
        layout(loadingLayout);
		// Draw on canvas
        drawLayout(loadingLayout);

	}

    private void layout(View layout) {
        long startmillis = 0;
        if(BuildConfig.DEBUG)
            startmillis = System.currentTimeMillis();

        layout.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));

        if(BuildConfig.DEBUG)
            Log.d(TAG, "measure took: "+(System.currentTimeMillis()-startmillis));

        if(BuildConfig.DEBUG)
            startmillis = System.currentTimeMillis();

        layout.layout(0, 0, layout.getMeasuredWidth(),
                layout.getMeasuredHeight());
        if(BuildConfig.DEBUG)
            Log.d(TAG, "layout took: "+(System.currentTimeMillis()-startmillis));

    }


    public void showData()
	{
		// Create background bitmap.
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
		if(stations != null)
		{
			if(BuildConfig.DEBUG)
				Log.d(TAG, "mStation index: "+mStationIndex+ " stations size: "+ stations.size());

            LinearLayout stationHeader = (LinearLayout) stationsLayout.findViewById(R.id.station_header);
			de.schildbach.pte.dto.Location station = stations.get(mStationIndex);
			TextView stationName = (TextView) stationsLayout.findViewById(R.id.Station);
			stationName.setText(shortStationName(station));
            layout(stationHeader);

			int lines = stationName.getLineCount();
			departureRows = MAX_DEPATURE_ROWS - lines+2;
			if(BuildConfig.DEBUG)
				Log.d(TAG, "calculated rows: "+ departureRows+ " line count header: "+ lines);
            if(departureRows < 1)
            {
                ACRA.getErrorReporter().putCustomData("Message","Calculated less then one depature row, set to one. height:"+height+" width: "+width+ " lines "+lines+ " depRows: "+departureRows);
                ACRA.getErrorReporter().handleException(null);
                departureRows = 1;

            }
		}
		//depatures
		if(mQueryDeparturesResults.size() > 0)
		{
			int offset = mScrollIndex * (departureRows-1);
			if(BuildConfig.DEBUG)
				Log.d(TAG, "mStation index: "+mStationIndex+ " departure size: "+stations.size()+ "offset: "+offset);

			TableLayout tl = (TableLayout) stationsLayout.findViewById(R.id.departuesTable);
			//check if we have the depatures already...
			if(mQueryDeparturesResults.size() >= mStationIndex+1)
			{

				List<StationDepartures> dep = mQueryDeparturesResults.get(mStationIndex).stationDepartures;

                //filter list for already gone departures first
                filterGoneDepartures(dep);

				for (StationDepartures stationDepartures : dep) {
					List<Departure> depatures = stationDepartures.departures;
					for(int i = 0; i < depatures.size(); i++)
					{
                        if(i+offset >= depatures.size())
                            break;

						Departure depature = depatures.get(i+offset);
						View table = mInflater.inflate(R.layout.table_row_departure, tl, true);

						View row = ((ViewGroup)table).getChildAt(i*2);
						View textView = ((ViewGroup)table).getChildAt((i*2)+1);

						TextView depLine = (TextView) row.findViewById(R.id.depLine);
						depLine.setText(getLineText(depature.line));
						//set color if style exist
						if(depature.line.style != null)
						{
							depLine.setBackgroundColor(depature.line.style.backgroundColor);
							depLine.setTextColor(depature.line.style.foregroundColor);
						}

						TextView depTime = (TextView) row.findViewById(R.id.depTime);
						depTime.setText(getDepartureText(depature));//TODO: delays

						TextView depDest = (TextView) textView.findViewById(R.id.depTarget);
						depDest.setText(depature.destination.name);
						if(i == departureRows-1)
							break;
					}
				}
			}
			else
			{
				//we are still loading depatures, show loading image & Text
				ImageView loadingImage = (ImageView) stationsLayout.findViewById(R.id.loadingimage);
				TextView loadingText = (TextView) stationsLayout.findViewById(R.id.loadingText);
				loadingImage.setVisibility(View.VISIBLE);
				loadingText.setVisibility(View.VISIBLE);
			}

		}
        layout(stationsLayout);

		// Draw on canvas
		Canvas canvas = new Canvas(mBackground);
		stationsLayout.draw(canvas);
		// Send bitmap to accessory
		showBitmap(mBackground);
	}


	private CharSequence getDepartureText(Departure departure) {

		long now = System.currentTimeMillis();
		long planned = (((departure.plannedTime.getTime() - now)/1000)/60);
		String departureTimePlanned = planned+"";
		String departureTimePredict = "-";
		if(departure.predictedTime != null)
		{
			long predict = (((departure.predictedTime.getTime() - now)/1000)/60);
			long delay = predict - planned;
			if(delay > 0)
				departureTimePredict = "+"+delay;
		}

		String depatureTimeText = String.format(mContext.getString(R.string.text_depature_times), departureTimePlanned, departureTimePredict);

		return depatureTimeText;
	}

    private void filterGoneDepartures(List<StationDepartures> departuresList) {
        for (StationDepartures stationDepartures : departuresList) {
            List<Departure> depatures = stationDepartures.departures;
            for(int i = 0; i < depatures.size(); i++)
            {
                if(alreadyDeparted(depatures.get(i)))
                    depatures.remove(i);
            }
        }
    }

    private boolean alreadyDeparted(Departure departure) {
        long now = System.currentTimeMillis();
        long planned = (((departure.plannedTime.getTime() - now)/1000)/60);
        if(departure.predictedTime != null)
        {
            long predict = (((departure.predictedTime.getTime() - now)/1000)/60);
            if(predict <= 0)
                return true;
        }
        if(planned <= 0)
            return true;
        return false;
    }


	private CharSequence getLineText(Line line) {
		String label = line.label.substring(1);//cut off the type like "B" or "T"
		return label;
	}


    private CharSequence shortStationName(de.schildbach.pte.dto.Location station) {
        //try shortname, if not available cut the station-name
        String stationName = "unknown";

        if(!TextUtils.isEmpty(station.uniqueShortName()))
            stationName = station.uniqueShortName();
        else {

            if(!TextUtils.isEmpty(station.name))
            {
                stationName = station.name;
                int index = station.name.indexOf(",");
                if(index > 0)
                    stationName = station.name.substring(0, index);
            }
        }
        return stationName;
    }


	@Override
	public void nearbyStationsReceived(NearbyLocationsResult result) {
		this.mQueryDeparturesResults.clear();
        if(result != null && result.locations != null && result.locations.size() > 0)
        {
            this.stations = result.locations;
            state = STATE_DISPLAY_DATA;
            if(BuildConfig.DEBUG)
                Log.d(TAG, "Found: "+result.locations.size()+" stations");
            redraw();
            // for eacht station, request depatures
            for (de.schildbach.pte.dto.Location station: result.locations ) {
                publicNetworkProvider.getDepatures(station);
            }
        }
        else
        {
            state = STATE_ERROR;
            redraw();
        }

	}


	@Override
	public void departuresReceived(QueryDeparturesResult result) {
		if(this.mQueryDeparturesResults == null)
			this.mQueryDeparturesResults = new ArrayList<QueryDeparturesResult>(0);
		this.mQueryDeparturesResults.add(result);
		redraw();

	}




    // Define a listener that responds to location updates
	LocationListener locationListener = new LocationListener() {

		private Location clocation;

		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			clocation = location;
			Log.v(TAG, "got location to: "+location);
			state = STATE_LOADING;
			redraw();
			publicNetworkProvider.getNearbyStations(location);
			locationManager.removeUpdates(locationListener);
		};

		public void onStatusChanged(String provider, int status, Bundle extras) {}

		public void onProviderEnabled(String provider) {}

		public void onProviderDisabled(String provider) {}
	};

    class DelayDisplayChangeTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(2000); //wait 2 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            state = STATE_DISPLAY_DATA;
            redraw();
        }
    }

}