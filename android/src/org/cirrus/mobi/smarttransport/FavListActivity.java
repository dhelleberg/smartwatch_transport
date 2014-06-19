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

import android.app.ListActivity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.arconsis.android.datarobot.EntityService;
import de.timroes.android.listview.EnhancedListView;
import org.cirrus.mobi.smarttransport.dto.FavLocation;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class FavListActivity extends ListActivity implements EnhancedListView.OnDismissCallback {

    ArrayAdapter<FavLocation> mArrayAdapter;
    private DataLoader mdataLoader = null;
    private EnhancedListView mEnhancedListView;
    private Context mContext = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fav_list);

        mEnhancedListView = (EnhancedListView) findViewById(android.R.id.list);

        mEnhancedListView.setDismissCallback(this);
        mEnhancedListView.enableSwipeToDismiss();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mdataLoader = new DataLoader(this);
        mdataLoader.execute();

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mdataLoader != null)
            mdataLoader.cancel(false);
    }

    @Override
    public EnhancedListView.Undoable onDismiss(EnhancedListView enhancedListView, int i) {

        EntityService favLocationService = new EntityService(mContext, FavLocation.class);
        FavLocation location = mArrayAdapter.getItem(i);
        favLocationService.delete(location);
        mArrayAdapter.remove(location);
        return null;
    }

    class DataLoader extends AsyncTask<Void, Void, List<FavLocation>> {

        private final Context mContext;

        DataLoader(Context context) {
            this.mContext = context;

        }

        @Override
        protected List<FavLocation> doInBackground(Void... params) {
            EntityService favLocationService = new EntityService(mContext, FavLocation.class);
            List<FavLocation> favLocations = favLocationService.get();
            favLocationService.close();
            return favLocations;
        }

        @Override
        protected void onPostExecute(List<FavLocation> favLocations) {
            if(!isCancelled()) {
                mArrayAdapter = new FavLocationAdapter(mContext, favLocations);
                setListAdapter(mArrayAdapter);
            }
        }
    }


    private class FavLocationAdapter extends ArrayAdapter<FavLocation> {

        private LayoutInflater mLayoutInflator;

        public FavLocationAdapter(Context context, List<FavLocation> favLocations) {
            super(context, R.layout.fav_list_item, R.id.fav_listitem_textview, favLocations);
            mLayoutInflator = getLayoutInflater();
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if(view == null) {
                view = mLayoutInflator.inflate(R.layout.fav_list_item, parent, false);
            }
            TextView textView = (TextView) view.findViewById(R.id.fav_listitem_textview);
            textView.setText(getItem(position).name);

            return view;
        }
    }
}
