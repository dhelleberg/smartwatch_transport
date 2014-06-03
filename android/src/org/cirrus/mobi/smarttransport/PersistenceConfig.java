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

import android.database.sqlite.SQLiteDatabase;
import com.arconsis.android.datarobot.config.Persistence;
import com.arconsis.android.datarobot.hooks.DbUpdate;
import com.arconsis.android.datarobot.hooks.Update;

/**
 * Created by dhelleberg on 03/06/14.
 */
@Update
@Persistence(dbName = "fav_database.db", dbVersion = 1)
public class PersistenceConfig implements DbUpdate {
    @Override
    public void onUpdate(SQLiteDatabase db, int oldVersion, int newVersion) {
        // here you can put your update code, when changing the database version
    }
}
