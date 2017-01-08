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
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 100;
    private static final int MESSAGE_READ = 1;
    private static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final String miracleAddress = "00:06:66:4F:E7:80";
    private BluetoothDevice miracle = null;
    private Handler mHandler;
    private MiracleManager timerManager;
    private ProgressDialog mProgressDlg;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mDeviceList;
    ArrayAdapter<String> mArrayAdapter;
    ListView listview;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    showToast("Enabled");

                    showEnabled();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mDeviceList = new HashSet<BluetoothDevice>();

                mProgressDlg.show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mProgressDlg.dismiss();

                Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);

                newIntent.putParcelableArrayListExtra("device.list", new ArrayList<>(mDeviceList));

                startActivity(newIntent);
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                mDeviceList.add(device);

                showToast("Found device " + device.getName());
            }
        }
    };
    private TextView mStatus;
    private Button mScanBtn;
    private Button mPairedBtn;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        mStatus 			= (TextView) findViewById(R.id.tv_status);
        mScanBtn 		= (Button) findViewById(R.id.btn_scan);
        mPairedBtn 			= (Button) findViewById(R.id.btn_view_paired);
        listview = (ListView) findViewById(R.id.listView1);
        mArrayAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1);
        listview.setAdapter(mArrayAdapter);
        timerManager = new MiracleManager(this, mArrayAdapter);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MESSAGE_READ:
                        timerManager.processMessage(message);
                        Log.d("RAW MESSAGE", message.arg1+"");
                        break;
                }
            }
        };
        mProgressDlg = new ProgressDialog(this);
        mProgressDlg.setMessage("Scanning...");
        mProgressDlg.setCancelable(false);
        mProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                mBluetoothAdapter.cancelDiscovery();
            }
        });
        setupBluetooth();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_DOWN)
            timerManager.testNotification();
        return true;
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
        if (miracle == null)
            findMiracle(mBluetoothAdapter, mArrayAdapter);
        if (miracle == null) {
            mArrayAdapter.add("ERROR: CANNOT FIND MIRACLE");
            Log.e("ERROR", "Miracle bluetooth device not found");
            finish();
        }
        mArrayAdapter.clear();
        ConnectThread connect = new ConnectThread(miracle, mHandler);
        connect.start();
    }


    private void findMiracle(BluetoothAdapter mBluetoothAdapter, final ArrayAdapter<String> mArrayAdapter) {
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView
                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    if (device.getAddress().equals(miracleAddress)){
                        miracle = device;
                        MY_UUID = miracle.getUuids()[0].getUuid();
                    }
                }
            }
        };
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        mBluetoothAdapter.startDiscovery();

        //unregisterReceiver(mReceiver);
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private Handler mHandler;

        public ConnectThread(BluetoothDevice device, Handler h) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            Log.d("DARIEN", "Connection thread started");
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
            Log.d("DARIEN", "Running");

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    Log.d("DARIEN", "failed");
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
            Log.d("DARIEN", "CONNECTED THREAD!");
            mmSocket = socket;
            mHandler = h;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

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
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    Log.d("RAW", new String(buffer, "UTF-8").replaceAll("[^A-Za-z0-9(),\\[\\]]", ""));

					mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
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
