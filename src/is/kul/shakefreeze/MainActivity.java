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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ToggleButton;

/** 
 * Single activity app, show everything on this screen
 * @author Martin Varga
 *
 */
public class MainActivity extends Activity {

	private SettingsControl sc;

	private OnSharedPreferenceChangeListener listener;

	private MyServiceConnection msc;

	private ContentObserver autoRotateObserver;

	public static final String TAG = "SFA";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		setContentView(R.layout.activity_shake_freeze);

		sc = new SettingsControl(this);

		serviceCheck();
		autoRotateCheck();

		listener = new MyPreferencesListener();
		autoRotateObserver = new ContentObserver(new Handler()) {
			@Override
			public void onChange(boolean selfChange) {
				boolean autoRotate = sc.isAutoRotate();
				Log.d(TAG,
						"Auto-rotate settings changed, updating autorotate checkbox to "
								+ autoRotate);
				autoRotateCheck();
			}
		};
		getContentResolver()
				.registerContentObserver(
						Settings.System
								.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
						true, autoRotateObserver);

		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(listener);

		initializeView();

	}

	private void initializeView() {

		final ToggleButton tbAutoRotate = (ToggleButton) findViewById(R.id.tb_auto_rotate);
		tbAutoRotate.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				boolean newValue = !sc.isAutoRotate();
				// setting this here will be picked up by the observer, who will set the checked status of the button
				// TODO not nice, figure out something
				sc.setAutoRotate(newValue);
			}
		});
		
		final ToggleButton tbServiceToggle = (ToggleButton) findViewById(R.id.bt_service_toggle);
		tbServiceToggle
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							startService();
							tbAutoRotate.setTextOff(getString(R.string.ui_orientation_control_shake));
							tbAutoRotate.setChecked(tbAutoRotate.isChecked());
						} else {
							stopService();
							tbAutoRotate.setTextOff(getString(R.string.ui_orientation_control_locked));
							tbAutoRotate.setChecked(tbAutoRotate.isChecked());
						}

					}
				});

		if (tbServiceToggle.isChecked()) {
			tbAutoRotate.setTextOff(getString(R.string.ui_orientation_control_shake));
		} else {
			tbAutoRotate.setTextOff(getString(R.string.ui_orientation_control_locked));
		}
		
		View.OnClickListener rotateButtonsOnClickListener = new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				int desiredOrientation;
				switch (v.getId()) {
					case R.id.bt_0_deg:
						desiredOrientation = Surface.ROTATION_0;
						break;
					case R.id.bt_90_deg:
						desiredOrientation = Surface.ROTATION_90;
						break;
					case R.id.bt_180_deg:
						desiredOrientation = Surface.ROTATION_180;
						break;
					case R.id.bt_270_deg:
						desiredOrientation = Surface.ROTATION_270;
						break;
					default:
						throw new IllegalArgumentException("Unknown id tapped");
				}
				
				if (Log.isLoggable(TAG, Log.VERBOSE)) {
					Log.v(TAG, "Manually setting user orientation to " + desiredOrientation);
				}
				sc.setUserOrientation(desiredOrientation);
				
			}
		};
		
		((Button) findViewById(R.id.bt_0_deg)).setOnClickListener(rotateButtonsOnClickListener);
		((Button) findViewById(R.id.bt_90_deg)).setOnClickListener(rotateButtonsOnClickListener);
		((Button) findViewById(R.id.bt_180_deg)).setOnClickListener(rotateButtonsOnClickListener);
		((Button) findViewById(R.id.bt_270_deg)).setOnClickListener(rotateButtonsOnClickListener);
	}

	private static final int MENU_HELP = 0;
	private static final int MENU_ABOUT = 1;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		menu.add(0, MENU_HELP, 0, getString(R.string.help));
		menu.add(0, MENU_ABOUT, 1, getString(R.string.about));

		return true;

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		
			case MENU_HELP:
				HelpDialog help = new HelpDialog(MainActivity.this);
				help.setTitle(getString(R.string.help));
				help.show();
				break;
			case MENU_ABOUT:
				AboutDialog about = new AboutDialog(MainActivity.this);
				about.setTitle(getString(R.string.about));
				about.show();
				break;
			default:
				throw new IllegalArgumentException("Unhandled menu item");
		}
		return true;

	}

	private void startService() {
		Intent serviceIntent = new Intent(this, BackgroundService.class);
		startService(serviceIntent);
		bind();
	}

	private void bind() {
		if (msc == null) {
			msc = new MyServiceConnection();
			bindService(new Intent(this, BackgroundService.class), msc, 0);
		}
	}

	private void stopService() {
		unBind();
		Intent serviceIntent = new Intent(this, BackgroundService.class);
		stopService(serviceIntent);
	}

	private void unBind() {
		if (msc != null) {
			unbindService(msc);
			msc = null;
		}
	}

	private void autoRotateCheck() {
		ToggleButton tbAutoRotate = (ToggleButton) findViewById(R.id.tb_auto_rotate);
		tbAutoRotate.setChecked(sc.isAutoRotate());
	}

	private void serviceCheck() {
		ToggleButton tbServiceToggle = (ToggleButton) findViewById(R.id.bt_service_toggle);
		tbServiceToggle.setChecked(BackgroundService.RUNNING);
		if (BackgroundService.RUNNING) {
			bind();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(listener);
		getContentResolver().unregisterContentObserver(autoRotateObserver);
		unBind();
	}

	@Override
	protected void onResume() {
		super.onResume();
		autoRotateCheck();
		serviceCheck();
	}

	public void notifyService() {
		if (msc != null) {
			Messenger messenger = new Messenger(msc.getServiceHandle());
			Message msg = Message.obtain();
			try {
				messenger.send(msg);
				Log.d(TAG, "Service notified");
			} catch (RemoteException e) {
				Log.w(TAG, "Unable to notify service");
			}
		}

	}

	/**
	 * Listens to changes in my shared preferences
	 * 
	 * @author Martin Varga
	 * 
	 */
	private class MyPreferencesListener implements
			SharedPreferences.OnSharedPreferenceChangeListener {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			Log.d("SFA", "Pref changed: " + key);

			notifyService();
		}

	}

	/**
	 * Gets notified of service connection
	 * 
	 * @author Martin Varga
	 * 
	 */
	private class MyServiceConnection implements ServiceConnection {
		private IBinder serviceHandle;

		public IBinder getServiceHandle() {
			return serviceHandle;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			serviceHandle = service;
			Log.d(TAG, "Service connected");

		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			serviceHandle = null;
			Log.d(TAG, "Service disconnected");
		}

	}


}
