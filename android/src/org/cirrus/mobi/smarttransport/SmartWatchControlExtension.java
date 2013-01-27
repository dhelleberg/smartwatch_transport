package org.cirrus.mobi.smarttransport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.LinearLayout;

import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;

public class SmartWatchControlExtension extends ControlExtension {

	private Handler mHandler;
	private int width;
	private int height;
	private Bitmap mBackground;
	private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.RGB_565;



	public SmartWatchControlExtension(Context context, String hostAppPackageName, Handler handler) {
		super(context, hostAppPackageName);
	        if (handler == null) {
	            throw new IllegalArgumentException("handler == null");
	        }
	        mHandler = handler;
	        width = getSupportedControlWidth(context);
	        height = getSupportedControlHeight(context);
	  
	}
	

    /**
     * Get supported control width.
     *
     * @param context The context.
     * @return the width.
     */
    public static int getSupportedControlWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.smart_watch_control_width);
    }

    /**
     * Get supported control height.
     *
     * @param context The context.
     * @return the height.
     */
    public static int getSupportedControlHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.smart_watch_control_height);
    }
    
    @Override
    public void onResume() {    
    	super.onResume();
    	showBitmap();
    }
	
	public void showBitmap()
	{
		// Create background bitmap for animation.
		mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG); // Set default density to avoid scaling. background.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		//LinearLayout root = new LinearLayout(mContext); root.setLayoutParams(new LayoutParams(width, height));
		mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		LinearLayout sampleLayout = (LinearLayout)LinearLayout.inflate(mContext, R.layout.smartwatch_stations, null);
		Log.v("YYYYYYYYYYY", "W:"+width+ "h: "+height);
		sampleLayout.measure(width, height); 
		sampleLayout.layout(0, 0, sampleLayout.getMeasuredWidth(),
				sampleLayout.getMeasuredHeight());
		// Draw on canvas
		Canvas canvas = new Canvas(mBackground);
		sampleLayout.draw(canvas);
		// Send bitmap to accessory
		showBitmap(mBackground);
	}

}
