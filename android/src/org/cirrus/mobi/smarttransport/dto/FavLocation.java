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

package org.cirrus.mobi.smarttransport.dto;

import com.arconsis.android.datarobot.entity.AutoIncrement;
import com.arconsis.android.datarobot.entity.Column;
import com.arconsis.android.datarobot.entity.Entity;
import com.arconsis.android.datarobot.entity.PrimaryKey;

/**
 * Created by dhelleberg on 03/06/14.
 */
@Entity
public class FavLocation {

    @PrimaryKey
    @AutoIncrement
    @Column
    private Integer _id;

    //Fields I do need for the location class of öffi
    @Column
    public String id;
    @Column
    public int lat;
    @Column
    public int lon;
    @Column
    public String place;
    @Column
    public String name;
/*
    @Column
    public String type;
*/

    public FavLocation(String id, int lat, int lon, String place, String name) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.place = place;
        this.name = name;

    }

    public FavLocation() {
    }
}
