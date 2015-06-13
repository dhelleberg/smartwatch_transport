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

import android.content.Context;
import android.util.Log;

import org.acra.ACRA;

import de.schildbach.pte.NetworkProvider;

/**
 * Created by dhelleberg on 12/06/15.
 */
public class PublicTransportationAPI {

    private static final String TAG = "PublicTransportationAPI";
    private static final String PACKAGE = Constants.PACKAGE ;
    private final Context mContext;
    private NetworkProvider networkProvider;

    public PublicTransportationAPI(Context context) {
        this.mContext = context;
    }


    public String getNetworkForProvider(final String providerClass) {
        //lookup in arrays
        final String[] values = mContext.getResources().getStringArray(R.array.pref_transportNetwork_values);
        //find index in values
        int index = -1;
        for (int i = 0; i < values.length; i++) {
            if(values[i].equals(providerClass))
            {
                index = i;
                break;
            }
        }
        final String network = mContext.getResources().getStringArray(R.array.pref_transportNetwork_Entries)[index];
        return network;
    }

    public PublicNetworkProvider initNetworkProvider(PublicNetworkProvider.ResultCallbacks callbacks, String providerClass) {

        if(BuildConfig.DEBUG)
            Log.v(TAG, "Loading class: " + providerClass);

        //we do need the network as well

        try {
            networkProvider = (NetworkProvider) Class.forName(PACKAGE+providerClass).newInstance();
        } catch (Exception e) {

            Log.e(TAG, "Could not load networkprovider. should not happen", e);
            //maybe it needs additional params
            try {
                networkProvider = (NetworkProvider) Class.forName(PACKAGE+providerClass).getDeclaredConstructor(String.class).newInstance("");
                Log.w(TAG, "second try");
            } catch (Exception e1) {
                e1.printStackTrace();
                Log.e(TAG, "2nd try to load provider failed!",e1);
                ACRA.getErrorReporter().putCustomData("providerClass", providerClass);
                ACRA.getErrorReporter().handleException(e);
            }


        }

        return new PublicNetworkProvider(callbacks, networkProvider);
    }
}
