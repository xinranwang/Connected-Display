package com.xinranwang.connecteddisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.json.JSONException;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private final static String TAG = MainActivity.class.getSimpleName();
	
	final private static char[] hexArray = { '0', '1', '2', '3', '4', '5', '6',
		'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice mDevice = null;
	private String mDeviceAddress;
	private static final int REQUEST_ENABLE_BT = 1;
	private static final long SCAN_PERIOD = 3000;
//	private Dialog mDialog;
	public static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();
//	public static Main instance = null;
	
	TextView weatherTV;
	TextView rssiValue;
	Button sendBtn;
	Button connectBtn;
	
	private boolean flag = true;
	private boolean connState = false;
	private boolean scanFlag = false;
	
	boolean withinRange = false;
	int range = -50;
	
	private RBLService mBluetoothLeService;
	private Map<UUID, BluetoothGattCharacteristic> map = new HashMap<UUID, BluetoothGattCharacteristic>();
	
	
	Weather weather;
	JSONWeatherTask task;
	String city;

	
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((RBLService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};
	
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), "Disconnected",
						Toast.LENGTH_SHORT).show();
				setButtonDisable();
			} else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				Toast.makeText(getApplicationContext(), "Connected",
						Toast.LENGTH_SHORT).show();

				getGattService(mBluetoothLeService.getSupportedGattService());
			//} else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
				//data = intent.getByteArrayExtra(RBLService.EXTRA_DATA);

				//readAnalogInValue(data);
			} else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
				displayData(intent.getStringExtra(RBLService.EXTRA_DATA));
				
				int rssi = Integer.parseInt(intent.getStringExtra(RBLService.EXTRA_DATA));
				if(rssi > range && !withinRange) {
					sendWeather();
					withinRange = true;
				} else if(rssi <= range) {
					withinRange = false;
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		weatherTV = (TextView) this.findViewById(R.id.weather);
		rssiValue = (TextView) this.findViewById(R.id.rssiValue);
		sendBtn = (Button) this.findViewById(R.id.send);
		sendBtn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				sendWeather();
			}
			
		});
		connectBtn = (Button) this.findViewById(R.id.connect);
		connectBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				connectBLE();
			}
		});
		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
		}

		final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}

		Intent gattServiceIntent = new Intent(MainActivity.this,
				RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		
		city = "New York";
		task = new JSONWeatherTask();
		task.execute(new String[]{city});
		
		//connectBLE();

	}
	
	void connectBLE() {
		if (scanFlag == false) {
			scanLeDevice();

			Timer mTimer = new Timer();
			mTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					if (mDevice != null) {
						mDeviceAddress = mDevice.getAddress();
						mBluetoothLeService.connect(mDeviceAddress);
						scanFlag = true;
					} else {
						runOnUiThread(new Runnable() {
							public void run() {
								Toast toast = Toast
										.makeText(
												getApplicationContext(),
												"Couldn't search Ble Shield device!",
												Toast.LENGTH_SHORT);
								toast.setGravity(0, 0, Gravity.CENTER);
								toast.show();
							}
						});
					}
				}
			}, SCAN_PERIOD);
		}

		System.out.println(connState);
		if (connState == false) {
			mBluetoothLeService.connect(mDeviceAddress);
		} else {
			mBluetoothLeService.disconnect();
			mBluetoothLeService.close();
			setButtonDisable();
		}
	}
	
	void sendWeather() {
		int temp = (int) Math.round((weather.temperature.getTemp() - 273.15));
		String condition = weather.currentCondition.getCondition();
		
		BluetoothGattCharacteristic characteristic = map
				.get(RBLService.UUID_BLE_SHIELD_TX);

		String str = temp+"C " + condition;
		byte b = 0x00;
		byte[] tmp = str.getBytes();
		byte[] tx = new byte[tmp.length + 1];
		tx[0] = b;
		for (int i = 1; i < tmp.length + 1; i++) {
			tx[i] = tmp[i - 1];
		}

		characteristic.setValue(tx);
		mBluetoothLeService.writeCharacteristic(characteristic);
		
		weatherTV.setText(str + String.valueOf(System.currentTimeMillis()));
		Log.v(TAG,"Sent: "+ str);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		task = new JSONWeatherTask();
		task.execute(new String[]{city});

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}
	
//	@Override
//	protected void onPause() {
//		super.onPause();
//		mBluetoothLeService.disconnect();
//		mBluetoothLeService.close();
//		setButtonDisable();
//	}
	
	private void displayData(String data) {
		if (data != null) {
			rssiValue.setText(data);
		}
	}
	
	private void setButtonEnable() {
		flag = true;
		connState = true;

//		digitalOutBtn.setEnabled(flag);
//		AnalogInBtn.setEnabled(flag);
//		servoSeekBar.setEnabled(flag);
//		PWMSeekBar.setEnabled(flag);
		connectBtn.setText("Disconnect");
	}

	private void setButtonDisable() {
		flag = false;
		connState = false;

//		digitalOutBtn.setEnabled(flag);
//		AnalogInBtn.setEnabled(flag);
//		servoSeekBar.setEnabled(flag);
//		PWMSeekBar.setEnabled(flag);
		connectBtn.setText("Connect");
	}
	
	private void startReadRssi() {
		new Thread() {
			public void run() {

				while (flag) {
					mBluetoothLeService.readRssi();
					try {
						sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
		}.start();
	}
	
	private void getGattService(BluetoothGattService gattService) {
		if (gattService == null)
			return;

		setButtonEnable();
		startReadRssi();

		BluetoothGattCharacteristic characteristic = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);
		map.put(characteristic.getUuid(), characteristic);


		BluetoothGattCharacteristic characteristicRx = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
		mBluetoothLeService.setCharacteristicNotification(characteristicRx,
				true);
		mBluetoothLeService.readCharacteristic(characteristicRx);
	}
	
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

		return intentFilter;
	}
	
	private void scanLeDevice() {
		new Thread() {

			@Override
			public void run() {
				mBluetoothAdapter.startLeScan(mLeScanCallback);

				try {
					Thread.sleep(SCAN_PERIOD);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				mBluetoothAdapter.stopLeScan(mLeScanCallback);
			}
		}.start();
	}
	
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
				final byte[] scanRecord) {

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					byte[] serviceUuidBytes = new byte[16];
					String serviceUuid = "";
					for (int i = 32, j = 0; i >= 17; i--, j++) {
						serviceUuidBytes[j] = scanRecord[i];
					}
					serviceUuid = bytesToHex(serviceUuidBytes);
					if (stringToUuidString(serviceUuid).equals(
							RBLGattAttributes.BLE_SHIELD_SERVICE
									.toUpperCase(Locale.ENGLISH))) {
						mDevice = device;
					}
				}
			});
		}
	};
	
	private String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	private String stringToUuidString(String uuid) {
		StringBuffer newString = new StringBuffer();
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(0, 8));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(8, 12));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(12, 16));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(16, 20));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(20, 32));

		return newString.toString();
	}
	
	@Override
	protected void onStop() {
		super.onStop();

		flag = false;

		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mServiceConnection != null)
			unbindService(mServiceConnection);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT
				&& resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}
	
	private class JSONWeatherTask extends AsyncTask<String, Void, Weather> {

		@Override
		protected Weather doInBackground(String... params) {
			weather = new Weather();
			String data = ( (new WeatherHttpClient()).getWeatherData(params[0]));

			try {
				weather = JSONWeatherParser.getWeather(data);

			} catch (JSONException e) {				
				e.printStackTrace();
			}
			return weather;

	}
		@Override
		protected void onPostExecute(Weather weather) {			
			super.onPostExecute(weather);

//			cityText.setText(weather.location.getCity() + "," + weather.location.getCountry());
//			condDescr.setText(weather.currentCondition.getCondition() + "(" + weather.currentCondition.getDescr() + ")");
//			temp.setText("" + Math.round((weather.temperature.getTemp() - 273.15)) + "¡C");
//			hum.setText("" + weather.currentCondition.getHumidity() + "%");
//			press.setText("" + weather.currentCondition.getPressure() + " hPa");
//			windSpeed.setText("" + weather.wind.getSpeed() + " mps");
//			windDeg.setText("" + weather.wind.getDeg() + "¡");

		}


	}

}
