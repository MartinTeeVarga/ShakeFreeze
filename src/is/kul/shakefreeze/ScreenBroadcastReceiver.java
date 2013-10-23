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
import android.util.Log;

/**
 * Listen to screen on/off to turn off the service and save battery
 * @author Martin Varga
 *
 */
public class ScreenBroadcastReceiver extends BroadcastReceiver {
	
	private BackgroundService service;

	
	public ScreenBroadcastReceiver(BackgroundService service) {
		this.service = service;
	}

	@Override
	public void onReceive(Context ctx, Intent i) {
		Log.d("SFB", "Broadcast received: " + i.getAction());
		
		if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
			service.resume();
		} else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
			service.pause();
		} else {
			throw new IllegalArgumentException("Received wrong intent!");
		}
	}

}
