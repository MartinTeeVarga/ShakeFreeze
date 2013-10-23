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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent i) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		boolean start = sp.getBoolean(context.getResources().getString(R.string.pref_start_on_boot_key), false);
		Log.v("SBBBR", "Boot completed event, starting service: " + start);
		if (start) {
			Intent serviceIntent = new Intent(context, BackgroundService.class);
			context.startService(serviceIntent);
		}
	}

}
