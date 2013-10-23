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

import android.content.Context;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

/**
 * Control system settings
 * 
 * @author Martin Varga
 * 
 */
public class SettingsControl {

	private Context ctx;

	public SettingsControl(Context ctx) {
		this.ctx = ctx;
	}

	public boolean isAutoRotate() {
		try {
			return 1 == android.provider.Settings.System.getInt(
					ctx.getContentResolver(),
					android.provider.Settings.System.ACCELEROMETER_ROTATION);
		} catch (SettingNotFoundException e) {
			Log.w("SF", "Setting not found", e);
			return false; // TODO maybe rather fail?
		}
	}

	public void setAutoRotate(boolean auto) {
		android.provider.Settings.System.putInt(ctx.getContentResolver(),
				android.provider.Settings.System.ACCELEROMETER_ROTATION,
				auto ? 1 : 0);
	}

	public int getUserOrientation() {
		try {
			return android.provider.Settings.System.getInt(
					ctx.getContentResolver(),
					android.provider.Settings.System.USER_ROTATION);
		} catch (SettingNotFoundException e) {
			Log.w("SF", "Setting not found", e);
			return 0;
		}
	}

	public void setUserOrientation(int orientation) {
		android.provider.Settings.System.putInt(ctx.getContentResolver(),
				android.provider.Settings.System.USER_ROTATION, orientation);
	}
}
