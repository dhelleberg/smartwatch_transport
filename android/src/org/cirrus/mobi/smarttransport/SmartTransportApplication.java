package org.cirrus.mobi.smarttransport;

import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

@ReportsCrashes(formKey = "")
public class SmartTransportApplication extends Application {

	@Override
	public void onCreate() {
		ACRAConfiguration config=ACRA.getNewDefaultConfig(this);
		config.setFormUri(getString(R.string.acra_form_uri));
		config.setFormUriBasicAuthLogin(getString(R.string.acra_form_user));
		config.setFormUriBasicAuthPassword(getString(R.string.acra_form_pwd));
		ACRA.setConfig(config);
		ACRA.init(this);

		super.onCreate();
	}

}
