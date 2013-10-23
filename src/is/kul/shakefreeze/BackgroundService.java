/**
 * Shake Freeze, Android app
 * Copyright (C) 2013 Martin Varga <android@kul.is>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package is.kul.shakefreeze;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;

/**
 * Background Shake detector service
 * @author Martin Varga
 *
 */
public class BackgroundService extends Service implements SensorEventListener {
	/** running indicator */
	public static boolean RUNNING = false;
	
	/** LogCat tag */
	private static final String TAG = "SFS";
	/** After screen rotate, do not register shakes for a while */
	private static final long GRACE_PERIOD = 800;
	/** Period to reset shakes when the user stop shaking to reset the shakes */
	private static final long REST_PERIOD = 2000;
	/** Resting position is calculated from AVERAGING last readings **/
	private static final int AVERAGING = 4;
	/** Just an id for the service */
	private static final int MY_ID = 1;

	// TODO min/max values of the shakes - consider moving somewhere
	private static final float shakeDeltaSettingMin = 2;
	private static final float shakeDeltaSettingMax = 32;
	private static final float shakesToRotateSettingMin = 2;
	private static final float shakesToRotateSettingMax = 15;
	
	/** grace period happens after screen rotate to prevent another rotate */
	private boolean gracePeriod = false;
	
	private boolean initialized = false;

	private long timestamp = now();

	// current values
	private float shakeDeltaThreshold = 5f;
	private int shakesToRotate = 3;
	private boolean autoRotate = true;
	private float restingX, restingY, restingZ;
	private int shakesX, shakesY, shakesZ;
	private float lastX, lastY, lastZ;
	private float lastDX, lastDY, lastDZ;

	/** handle to system settings */
	SettingsControl sc;

	/** orientation on app start */
	private int defaultOrientation;

	private SensorManager sensorMgr;
	private Sensor accelerometer;
	
	private Notification.Builder builder;
	private Notification noti;
	
	private ScreenBroadcastReceiver screenBroadcastReceiver;
	private ContentObserver autoRotateObserver;
	
    private Messenger messenger;


	public BackgroundService() {
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private Notification createNotificatonApi16(PendingIntent pi, int icon, int content) {
		if (builder == null) {
			builder = new Notification.Builder(this);
		}
		noti = builder
		    .setContentTitle(getString(R.string.n_title))
		    .setContentText(getString(content))
			.setSmallIcon(icon)
			.setOngoing(true)
			.setContentIntent(pi)
			.build();
		return noti;
	}
	
	
	@SuppressWarnings("deprecation")
	private Notification createNotificaton(PendingIntent pi, int icon, int content) {
		Notification noti = new Notification(icon, 
				getString(R.string.n_title), 
				System.currentTimeMillis());
		noti.setLatestEventInfo(this, getString(R.string.n_title), getString(content), pi);
		noti.flags |= Notification.FLAG_ONGOING_EVENT;
		return noti;
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void updateNotificatonApi16(int icon, int content) {
		NotificationManager notiMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notiMgr.notify(MY_ID, builder
				.setSmallIcon(icon)
				.setContentText(getString(content))
				.build());
	}

	@SuppressWarnings("deprecation")
	private Notification updateNotificaton(int icon, int content, PendingIntent pi) {
		noti.setLatestEventInfo(this, getString(R.string.n_title), getString(content), pi);
		return noti;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		sc = new SettingsControl(this);
		defaultOrientation = sc.getUserOrientation();
		
		autoRotateObserver = new ContentObserver(new Handler()) {
	        @Override
	        public void onChange(boolean selfChange) {
	        	boolean autoRotate = sc.isAutoRotate();
				if (Log.isLoggable(TAG, Log.DEBUG)) {
					Log.d(TAG, "Auto-rotate settings changed, updating notification to " + autoRotate);
				}
	        	updateNotification(autoRotate);
	        }
		};
		
		getContentResolver().registerContentObserver(Settings.System.getUriFor
				(Settings.System.ACCELEROMETER_ROTATION),
				true, autoRotateObserver);
		
		screenBroadcastReceiver = new ScreenBroadcastReceiver(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(screenBroadcastReceiver, filter);
		
		PendingIntent pi = createPendingIntent();

		boolean autoRotate = sc.isAutoRotate();

		if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
		    noti = createNotificatonApi16(pi,
		    		getIcon(autoRotate),
		    		getContentText(autoRotate));
		} else{
			noti = createNotificaton(pi,
					getIcon(autoRotate),
					getContentText(autoRotate));
		}
		
		startForeground(MY_ID, noti);
		BackgroundService.RUNNING = true;
		
		sensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		registerAccelerometerListener();
		
		updateSettings();
		Log.i(TAG, "Service started");

		return START_STICKY;

	}

	private PendingIntent createPendingIntent() {
		Intent i = new Intent(this, MainActivity.class);

		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
		return pi;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO not sure what to do here
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		BackgroundService.RUNNING = false;
		unregisterAccelerometerListener();
		unregisterReceiver(screenBroadcastReceiver);
		getContentResolver().unregisterContentObserver(autoRotateObserver);
		// return to pre-service orientation
		sc.setUserOrientation(defaultOrientation);

		if (Log.isLoggable(TAG, Log.INFO)) {
			Log.i(TAG, "Service destroyed, set the default orientation: " + defaultOrientation);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (messenger == null) {
			messenger = new Messenger(new MessageHandler(this));
		}
		return messenger.getBinder();
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (gracePeriod && now() - timestamp < GRACE_PERIOD) {  // we are in grace period, do not register any movement
			return;
		}
		
		gracePeriod = false;

		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];
		
		if (!initialized) {
			resetShakes();
			autoRotate = sc.isAutoRotate();
			Log.v(TAG, "Autorotate is: " + autoRotate);
			
			restingX = x;
			restingY = y;
			restingZ = z;
			
			initialized = true;
		} else {
			float deltaX = Math.abs(lastX - x);
			float deltaY = Math.abs(lastY - y);
			float deltaZ = Math.abs(lastZ - z);
			
			if (!autoRotate && deltaX > deltaY && deltaX > deltaZ) {
				// deltaX is the largest shake and larger than the threshold
				if (deltaX > shakeDeltaThreshold || lastDX > 0) {
					// it is also larger than the threshold or this is continuation of the movement
					if (deltaX > lastDX) {
						// shake is growing
						lastDX = deltaX;
					} else if (lastDX > -1) {
						// shake is smaller, the ball hit the wall and going back, but we did not register the hit yet
						shakesX++;
						lastDX = -1;
						Log.d(TAG, "Shake X detected");
					} else {
						// we have registered the hit on the wall and now waiting for another ding on the other side
						lastDX = 0;
					}
					timestamp = now();	
				}
			} else if (!autoRotate && deltaY > deltaZ) {
				// deltaY is the largest shake 
				if (deltaY > shakeDeltaThreshold || lastDY > 0) {
					// it is also larger than the threshold or this is continuation of the movement
					if (deltaY > lastDY) {
						// shake is growing
						lastDY = deltaY;
					} else if (lastDY > -1) {
						// shake is smaller, the ball hit the wall and going back, but we did not register the hit yet
						shakesY++;
						lastDY = -1;
						Log.d(TAG, "Shake Y detected");
					} else {
						// we have registered the hit on the wall and now waiting for another ding on the other side
						lastDY = 0;
					}
					timestamp = now();
				}
				
			} else if (deltaZ > shakeDeltaThreshold || lastDZ > 0) { // deltaZ is the largest shake and larger than the threshold
				// it is also larger than the threshold or this is continuation of the movement
				if (deltaZ > lastDZ) {
					// shake is growing
					lastDZ = deltaZ;
				} else if (lastDZ > -1) {
					// shake is smaller, the ball hit the wall and going back, but we did not register the hit yet
					shakesZ++;
					lastDZ = -1;
					Log.d(TAG, "Shake Z detected");
				} else {
					// we have registered the hit on the wall and now waiting for another ding on the other side
					lastDZ = 0;
				}
				timestamp = now();	
			} else {
				// calculate resting position - when user is not shaking
				restingX = (restingX * (AVERAGING-1) + x) / AVERAGING;
				restingY = (restingY * (AVERAGING-1) + y) / AVERAGING;
				restingZ = (restingZ * (AVERAGING-1) + z) / AVERAGING;
				// if user did not shake for long time, reset shakes
				if (now() - timestamp > REST_PERIOD) {
					timestamp = now();
					resetShakes();
				}
			}
			
			if (!autoRotate && shakesX >= shakesToRotate) {
				sc.setUserOrientation(restingX > 0 ? Surface.ROTATION_90 : Surface.ROTATION_270);
				resetShakes();
				gracePeriod = true;
				Log.d(TAG, "X shaken");
			} else if (!autoRotate && shakesY >= shakesToRotate) {
				sc.setUserOrientation(restingY > 0 ? Surface.ROTATION_0 : Surface.ROTATION_180);
				resetShakes();				
				gracePeriod = true;
				Log.d(TAG, "Y shaken");
			} else if (shakesZ >= shakesToRotate) {
				autoRotate = !autoRotate;
				sc.setAutoRotate(autoRotate);
				//updateNotification(autoRotate);
				resetShakes();
				gracePeriod = true;
				Log.d(TAG, "Z shaken");
			} 
		}
		
		lastX = x;
		lastY = y;
		lastZ = z;
		
	}

	private void updateNotification(boolean autoRotate) {
		if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
		    updateNotificatonApi16(
		    		getIcon(autoRotate),
		    		getContentText(autoRotate)); 
		} else{
			updateNotificaton(
		    		getIcon(autoRotate),
		    		getContentText(autoRotate),
		    		createPendingIntent());
		}
		
	}

	private int getContentText(boolean autoRotate) {
		return autoRotate ? R.string.n_content_tilt : R.string.n_content_shake;
	}

	private int getIcon(boolean autoRotate) {
		return autoRotate ? R.drawable.ic_notification_tilt : R.drawable.ic_notification_shake;
	}

	private long now() {
		return System.currentTimeMillis();
	}
	
	private void resetShakes() {
		if (shakesX > 0 || shakesY > 0 || shakesZ > 0) {
			Log.v(TAG, "Shake counter reset");
			shakesX = 0; 
			shakesY = 0; 
			shakesZ = 0;
		}
	}
	
	
	
	protected void resume() {
		Log.d(TAG, "ShakeFreeze resumed");
		registerAccelerometerListener();

	}

	private void registerAccelerometerListener() {
		sensorMgr.registerListener(this, accelerometer,
				SensorManager.SENSOR_DELAY_UI);
	}

	protected void pause() {
		Log.d(TAG, "ShakeFreeze paused");
		unregisterAccelerometerListener();
	}

	private void unregisterAccelerometerListener() {
		sensorMgr.unregisterListener(this);
	}
	
	/**
	 * Updates current values from the shared preferences
	 */
	public void updateSettings() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		// don't worry about the defaults here, we are setting them at the first launch using the values in defaults.xml
		float sens = sp.getFloat(getString(R.string.pref_sensitivity_key), 0.5f);
		float shakes = sp.getFloat(getString(R.string.pref_shakes_to_rotate_key), 0.5f);
		
		shakeDeltaThreshold = (shakeDeltaSettingMax - shakeDeltaSettingMin) * (1 - sens) + shakeDeltaSettingMin;
		shakesToRotate = Math.round((shakesToRotateSettingMax - shakesToRotateSettingMin) * shakes + shakesToRotateSettingMin);
		
		if (Log.isLoggable(TAG, Log.INFO)) {
			Log.i(TAG, "New settings; delta: " + shakeDeltaThreshold + "; shakes: " + shakesToRotate);
		}
	}
	

	/**
	 * Handles messaged from the MainActivity
	 * @author Martin Varga
	 *
	 */
	private static class MessageHandler extends Handler {
		
		BackgroundService service;
		
		public MessageHandler(BackgroundService service) {
			super();
			this.service = service;
		}

		@Override
		public void handleMessage(Message msg) {
			// the only message received is to refresh settings
			Log.d(TAG, "Service notified");
			service.updateSettings();
			service.autoRotate = service.sc.isAutoRotate();
			super.handleMessage(msg);
		}
		
	}

}
