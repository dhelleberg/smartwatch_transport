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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;


public class SmartPreferenceActivity extends PreferenceActivity implements OnPreferenceClickListener {


    private static final String TAG = "SMT/SmartPreferenceActivity";

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.smart_preferences);

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		final Preference transportPreference = getPreferenceManager().findPreference(getString(R.string.pref_publicnetwork)); 
		transportPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				final String network = getNetworkForProvider((String) newValue);		
				transportPreference.setSummary(network);
				
				return true;
			}
		});
		
		
		final String providerClass = sharedPref.getString(getString(R.string.pref_publicnetwork), getString(R.string.pref_transportNetwork_default));
		final String network = getNetworkForProvider(providerClass);		
		transportPreference.setSummary(network);
		
		final Preference feedbackPreference = getPreferenceManager().findPreference(
				getString(R.string.pref_feedback));
		feedbackPreference.setOnPreferenceClickListener(this);

        final Preference donatePreference = getPreferenceManager().findPreference(
                getString(R.string.pref_donate));
        donatePreference.setOnPreferenceClickListener(this);
    }



	private String getNetworkForProvider(final String providerClass) {
		//lookup in arrays
		final String[] values = getResources().getStringArray(R.array.pref_transportNetwork_values);
		//find index in values
		int index = -1;
		for (int i = 0; i < values.length; i++) {
			if(values[i].equals(providerClass))
			{	
				index = i;
				break;
			}
		}
		final String network = getResources().getStringArray(R.array.pref_transportNetwork_Entries)[index];
		return network;
	}


	@Override
	public boolean onPreferenceClick(Preference preference) {

		if(preference.getKey().equals(getString(R.string.pref_feedback))) {
			final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
			emailIntent.setType("message/rfc822");
			emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{getString(R.string.feedback_mailaddress)});
			emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, R.string.feedback_subject );  			
			startActivity(Intent.createChooser(emailIntent, getString(R.string.feedback_mailsend)));

			return true;
		}
        else if(preference.getKey().equals(getString(R.string.pref_donate)))
        {
            if(BuildConfig.DEBUG)
                Log.v(TAG, "clicked on donate");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialog_notimplemented);
            builder.show();
            return true;
        }
		return false;
	}	
}
