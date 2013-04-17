package org.cirrus.mobi.smarttransport;

import android.os.Bundle;
import android.preference.PreferenceActivity;



public class SmartPreferenceActivity extends PreferenceActivity {

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.smart_preferences);
    }
}
