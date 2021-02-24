package com.personalizatio;

import android.net.Uri;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
public final class Api {
	private static Api instance;
	private String url;

	public abstract static class OnApiCallbackListener {
		public abstract void onSuccess(JSONObject response);
		public void onError(int code, String msg) {}
	}

	private Api(String url) {
		this.url = url;
	}

	static void initialize(String url) {
		instance = new Api(url);
	}

	/**
	 * @param method   api
	 * @param params   request
	 * @param listener callback
	 */
	public static void send(final String request_type, final String method, final Map<String, String> params, @Nullable final OnApiCallbackListener listener) {
		Thread thread = new Thread(() -> {
			try {
				Uri.Builder builder = Uri.parse(instance.url + method).buildUpon();
				for( Map.Entry<String, String> entry : params.entrySet() ) {
					builder.appendQueryParameter(entry.getKey(), entry.getValue());
				}

				URL url;
				if( request_type.toUpperCase().equals("POST") ) {
					url = new URL(instance.url + method);
				} else {
					url = new URL(builder.build().toString());
				}

				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestProperty("User-Agent", "Personalizatio SDK " + BuildConfig.FLAVOR.toUpperCase() + ", v" + BuildConfig.VERSION_NAME);
				conn.setRequestMethod(request_type.toUpperCase());
				conn.setConnectTimeout(5000);

				if( request_type.toUpperCase().equals("POST") ) {
					conn.setDoOutput(true);
					conn.setDoInput(true);
					BufferedWriter os = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8));
					os.write(builder.build().getQuery());
					os.flush();
					os.close();
				}

				conn.connect();

				SDK.debug(conn.getResponseCode() + ": " + request_type.toUpperCase() + " " + builder.build().toString());

				if( listener != null && conn.getResponseCode() == HttpURLConnection.HTTP_OK ) {
					listener.onSuccess(new JSONObject(readStream(conn.getInputStream())));
				}

				if( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
					String error = readStream(conn.getErrorStream());
					SDK.error(error);
					if( listener != null ) {
						listener.onError(conn.getResponseCode(), error);
					}
				}

				conn.disconnect();
			} catch(ConnectException e) {
				SDK.error(e.getMessage());
				if( listener != null ) {
					listener.onError(504, e.getMessage());
				}
			} catch(Exception e) {
				SDK.error(e.getMessage(), e);
				if( listener != null ) {
					listener.onError(-1, e.getMessage());
				}
			}
		});

		thread.start();
	}

	private static String readStream(InputStream in) {
		BufferedReader reader = null;
		StringBuffer response = new StringBuffer();
		try {
			reader = new BufferedReader(new InputStreamReader(in));
			String line = "";
			while( (line = reader.readLine()) != null ) {
				response.append(line);
			}
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			if( reader != null ) {
				try {
					reader.close();
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		return response.toString();
	}
}
