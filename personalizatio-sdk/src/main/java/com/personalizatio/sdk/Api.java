package com.personalizatio.sdk;

import android.net.Uri;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
abstract class Api {
	protected static Api instance;

	interface OnApiCallbackListener {
		void onSuccess(String msg);
	}

	public abstract String getApiUrl();

	public static void initialize(Class<?> c) throws InstantiationException, IllegalAccessException {
		instance = (Api) c.newInstance();
	}

	/**
	 * @param method   api
	 * @param params   request
	 * @param listener callback
	 */
	final public static void send(final String request_type, final String method, final Map<String, String> params, @Nullable final OnApiCallbackListener listener) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Uri.Builder builder = Uri.parse(instance.getApiUrl() + method).buildUpon();
					for( Map.Entry<String, String> entry : params.entrySet() ) {
						builder.appendQueryParameter(entry.getKey(), entry.getValue());
					}

					URL url;
					if( request_type.toUpperCase().equals("POST") ) {
						url = new URL(instance.getApiUrl() + method);
					} else {
						url = new URL(builder.build().toString());
					}

					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod(request_type.toUpperCase());
					conn.setConnectTimeout(5000);

					if( request_type.toUpperCase().equals("POST") ) {
						conn.setDoOutput(true);
						conn.setDoInput(true);
						DataOutputStream os = new DataOutputStream(conn.getOutputStream());
						os.writeBytes(builder.build().getQuery());
						os.flush();
						os.close();
					}

					conn.connect();

					SDK.debug(String.valueOf(conn.getResponseCode()) + ": " + request_type.toUpperCase() + " " + builder.build().toString());

					if( listener != null && conn.getResponseCode() == HttpURLConnection.HTTP_OK ) {
						listener.onSuccess(readStream(conn.getInputStream()));
					}

					if( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
						SDK.error(readStream(conn.getErrorStream()));
					}

					conn.disconnect();
				} catch(Exception e) {
					SDK.error(e.getMessage());
					e.printStackTrace();
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

	//REES46
	final static public class R46 extends Api {
		@Override
		public String getApiUrl() {
			return "https://api.rees46.com/";
		}
	}

	//Personaclick
	final static public class PC extends Api {
		public PC() {}
		@Override
		public String getApiUrl() {
			return "https://api.personaclick.com/";
		}
	}

	//Localhost
	final static public class Local extends Api {
		public Local() {}
		@Override
		public String getApiUrl() {
			return "http://192.168.1.8:8080/";
		}
	}
}
