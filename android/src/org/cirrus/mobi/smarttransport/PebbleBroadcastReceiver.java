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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;

import java.util.UUID;

/**
 * Created by dhelleberg on 12/06/15.
 */
public class PebbleBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "PebbleBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Constants.INTENT_APP_RECEIVE)) {
            final UUID receivedUuid = (UUID) intent.getSerializableExtra(Constants.APP_UUID);

            // Pebble-enabled apps are expected to be good citizens and only inspect broadcasts containing their UUID
            if (!Constants.PEBBLE_UUID.equals(receivedUuid)) {
                return;
            }
            final int transactionId = intent.getIntExtra(Constants.TRANSACTION_ID, -1);
            final String jsonData = intent.getStringExtra(Constants.MSG_DATA);
            Log.d(TAG, "sending Intent to service");
            PebbleKit.sendAckToPebble(context, transactionId);
            Intent intentService = new Intent(context, PebbleService.class);
            context.startService(intentService);
        }
    }
}