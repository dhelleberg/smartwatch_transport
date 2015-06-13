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

import java.util.UUID;

/**
 * Created by dhelleberg on 12/06/15.
 */
public class Constants {
    public static final String INTENT_APP_RECEIVE = com.getpebble.android.kit.Constants.INTENT_APP_RECEIVE;
    public static final String APP_UUID = com.getpebble.android.kit.Constants.APP_UUID;;
    public static final String MSG_DATA = com.getpebble.android.kit.Constants.MSG_DATA;
    public static final String TRANSACTION_ID = com.getpebble.android.kit.Constants.TRANSACTION_ID;
    public static final String PACKAGE = "de.schildbach.pte.";

    public static final UUID PEBBLE_UUID = UUID.fromString("53fbc69c-f7dd-4a49-aa5c-a8bc02bf00b2");
}
