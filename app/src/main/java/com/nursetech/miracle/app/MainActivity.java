package com.nursetech.miracle.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.Sets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity {
    private static final int REQUEST_ENABLE_BT = 100;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final String miracleAddress = "00:06:66:4F:E7:80";
    private BluetoothDevice miracle = null;

    private MiracleManager timerManager;
    private ProgressDialog mProgressDlg;

    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> mDeviceList;
    private ArrayAdapter<String> mArrayAdapter;
    private ListView listview;
	private BroadcastReceiver mReceiver;

	private TextView mStatus;
	private Button mScanBtn;
	private Button mPairedBtn;
	private Button mRetryBtn;

    private final BroadcastReceiver mReceiver2 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    showToast("Enabled");

                    showEnabled();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mDeviceList = Sets.newHashSet();
                mProgressDlg.show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mProgressDlg.dismiss();

                Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);

                newIntent.putParcelableArrayListExtra("device.list", new ArrayList<>(mDeviceList));

                startActivity(newIntent);
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                mDeviceList.add(device);

                showToast("Found device " + device.getName());
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
		mStatus = (TextView) findViewById(R.id.status);
		mScanBtn = (Button) findViewById(R.id.btn_scan);
		mPairedBtn = (Button) findViewById(R.id.btn_view_paired);
		listview = (ListView) findViewById(R.id.listView1);
		mRetryBtn = (Button) findViewById(R.id.retry);
		mRetryBtn.setOnClickListener(v -> {
			mRetryBtn.setVisibility(View.GONE);
			setupBluetooth();
		});
		findViewById(R.id.clear).setOnClickListener(v -> {
			mArrayAdapter.clear();
		});
        mArrayAdapter = new ArrayAdapter<>(this,
				android.R.layout.simple_list_item_1, android.R.id.text1);
        listview.setAdapter(mArrayAdapter);
        timerManager = new MiracleManager(this, mArrayAdapter);

        mProgressDlg = new ProgressDialog(this);
        mProgressDlg.setMessage("Scanning...");
        mProgressDlg.setCancelable(false);
        mProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(R.string.cancelled, R.color.red, View.VISIBLE);
                mBluetoothAdapter.cancelDiscovery();
            }
        });
		mStatus.setText(R.string.searching);
        setupBluetooth();
    }

    private void setupBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mDeviceList = mBluetoothAdapter.getBondedDevices();


        if (mBluetoothAdapter == null) {
            Log.e("ERROR", "Bluetooth not supported");
            finish();
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
		mReceiver = getBroadcastReceiver(mBluetoothAdapter, mArrayAdapter);
        // If there are paired devices
        if (mDeviceList.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : mDeviceList) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                if (device.getAddress().equals(miracleAddress)){
                    miracle = device;
                    MY_UUID = miracle.getUuids()[0].getUuid();
                }
            }
        }
		// Register the BroadcastReceiver
		if(miracle != null){
			mArrayAdapter.clear();
		}else {
			IntentFilter bluetoothFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
			bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
			registerReceiver(mReceiver, bluetoothFilter);
			if (mBluetoothAdapter.startDiscovery()) {
				mProgressDlg.show();
			}
		}

		mArrayAdapter.clear();
		ConnectThread connect = new ConnectThread(miracle, timerManager);
		connect.start();
	}


	@NonNull
	private BroadcastReceiver getBroadcastReceiver(final BluetoothAdapter mBluetoothAdapter, final ArrayAdapter<String> mArrayAdapter) {
		return new BroadcastReceiver() {
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					// Device found
					if (BluetoothDevice.ACTION_FOUND.equals(action)) {
						// Get the BluetoothDevice object from the Intent
						BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
						Toast.makeText(getApplicationContext(), "Found " + device.getName(), Toast.LENGTH_LONG).show();
						// Add the name and address to an array adapter to show in a ListView
						mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
						if (device.getAddress().equals(miracleAddress)){
							miracle = device;
							MY_UUID = miracle.getUuids()[0].getUuid();
						}
					}

					else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
						BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
						Toast.makeText(getApplicationContext(), "Found " + device.getName(), Toast.LENGTH_LONG).show();
						//Device is now connected
					}
					else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
						//Done searching
						if(miracle == null) {
							dismissDialog(R.string.not_paired, R.color.red, View.VISIBLE);
						}
					}
					else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
						//Device is about to disconnect
					}
					else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
						//Device has disconnected
					}
				}
			};
	}

	private void dismissDialog(int statusMessage, int textColor, int retryVisibility) {
		mRetryBtn.setVisibility(retryVisibility);
		mProgressDlg.dismiss();
		mStatus.setText(statusMessage);
		mStatus.setTextColor(ContextCompat.getColor(getApplicationContext(), textColor));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
	}

	@Override
	public void onStart() {
		super.onStart();
		//setupBluetooth();
	}


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private Handler mHandler;

        public ConnectThread(BluetoothDevice device, Handler h) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            mProgressDlg.dismiss();
            mHandler = h;
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "Running");
			try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    Log.d(TAG, "failed");
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }

			// Do work to manage the connection (in a separate thread)
            ConnectedThread connected = new ConnectedThread(mmSocket, mHandler);
            connected.start();
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Handler mHandler;

        public ConnectedThread(BluetoothSocket socket, Handler h) {
			mmSocket = socket;
            mHandler = h;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
			runOnUiThread(()-> {
				dismissDialog(R.string.connected, R.color.green, View.INVISIBLE);
			});


			// Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {

			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
					final String line = reader.readLine();
					Message msg = Message.obtain(); // Creates an new Message instance
					msg.obj = line; // Put the string into Message, into "obj" field.
					msg.setTarget(mHandler); // Set the Handler
					msg.sendToTarget(); //Send the message
				} catch (IOException e) {
					break;
				}
			}
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                Log.d("DARIEN", "Sending data");
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                Log.d("DARIEN", "Connection closed");

                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private void showEnabled() {
        mStatus.setText("Bluetooth is On");
        mStatus.setTextColor(Color.BLUE);

        mPairedBtn.setEnabled(true);
        mScanBtn.setEnabled(true);
    }

    private void showDisabled() {
        mStatus.setText("Bluetooth is Off");
        mStatus.setTextColor(Color.RED);

        mPairedBtn.setEnabled(false);
        mScanBtn.setEnabled(false);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}
