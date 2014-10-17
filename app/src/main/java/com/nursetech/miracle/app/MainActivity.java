package com.nursetech.miracle.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;


public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 100;
    private static final int MESSAGE_READ = 1;
    private static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final String miracleAddress = "00:06:66:4F:E7:80";
    private BluetoothDevice miracle = null;
    private Handler mHandler;
    private MiracleManager timerManager;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> pairedDevices;
    ArrayAdapter<String> mArrayAdapter;
    ListView listview;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
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
                        //Log.d("DARIEN", message.arg1+"");
                        //Log.d("RAW", message.toString());
                        break;
                }
            }
        };
        setupBluetooth();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_DOWN)
            timerManager.testNotification();
        return true;
    }


    @SuppressWarnings("unused")
    private void setupBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        pairedDevices = mBluetoothAdapter.getBondedDevices();


        if (mBluetoothAdapter == null) {
            Log.e("ERROR", "Bluetooth not supported");
            finish();
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
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
                    //Log.d("RAW", new String(buffer, "ASCII").substring(0,6));
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
}
