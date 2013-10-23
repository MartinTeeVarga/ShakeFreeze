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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Simple about dialog showing a text file with HTML syntax, inspired by:
 * http://www.techrepublic.com/blog/android-app-builder/a-reusable-about-dialog-for-your-android-apps/
 * @author Martin Varga
 *
 */
public class AboutDialog extends Dialog {

	Context context;

	public AboutDialog(Context context) {
		super(context);
		this.context = context;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialog_about);

		TextView textView = (TextView) findViewById(R.id.about_textview);
		textView.setText(Html.fromHtml(readRawTextFile(R.raw.about)));
		textView.setMovementMethod(LinkMovementMethod.getInstance());
	}

	private String readRawTextFile(int id) {

		BufferedReader br = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(id)));

		String line;

		StringBuilder text = new StringBuilder();
		try {
			while ((line = br.readLine()) != null) {
				text.append(line);
			}
			br.close();
		} catch (IOException e) {
			return null;
		}
		return text.toString();
	}

}
