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

package org.cirrus.mobi.smarttransport;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import de.schildbach.pte.dto.NearbyLocationsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;

/**
 * Created by dhelleberg on 12/06/15.
 */
public class PebbleService extends IntentService implements PublicNetworkProvider.ResultCallbacks {

    private static final String TAG = "PebbleService";
    private Context mContext = this;
    private LocationManager locationManager;
    private SharedPreferences mSharedPref;

    private String[] mProviderEntries;
    private PublicTransportationAPI mPublicTransportationAPI;

    public PebbleService() {
        super("PebbleService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);

        mProviderEntries = mContext.getResources().getStringArray(R.array.pref_transportNetwork_Entries);

        mPublicTransportationAPI = new PublicTransportationAPI(this);

        //detect not selected provider
        if(!mSharedPref.contains(mContext.getResources().getString(R.string.pref_publicnetwork)))
        {
//            selectProvider();
        }
        else
        {
            //loadProvider from Preferences
            String providerClass = mSharedPref.getString(mContext.getResources().getString(R.string.pref_publicnetwork), mContext.getResources().getString(R.string.pref_transportNetwork_default));
            mPublicTransportationAPI.initNetworkProvider(this, providerClass);

            //select mode
            //selectMode();
            //intial call, kick search
            //startSearch();
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "got intent "+intent);
    }

    @Override
    public void nearbyStationsReceived(NearbyLocationsResult result) {

    }

    @Override
    public void departuresReceived(QueryDeparturesResult result) {

    }
}
