package com.example.adityajunnarkar.gbelt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService extends Service {

    // HC 05 SPP UUID
    private final static UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    static ConnectedThread connectedThread;
    static ConnectingThread connectingThread;
    BluetoothAdapter mBluetoothAdapter;
    static BluetoothDevice BluetoothDeviceForHC05;

    //private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;//mBinder;
    }

    /*public class LocalBinder extends Binder {
        public BluetoothService getService() {
            // Return this instance of BluetoothService so clients can call public methods
            return BluetoothService.this;
        }
    }*/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            BluetoothDeviceForHC05 = intent.getParcelableExtra("HC-05");

            byte[] desiredVector = intent.getByteArrayExtra("vector");
            if(BluetoothDeviceForHC05 != null){
                connectToDevice();
            }

            if (desiredVector!= null && connectedThread != null) { // && connectedThread.isAlive()
                connectedThread.write(desiredVector);
            }

        }
        /*  String stopservice = intent.getStringExtra("stopservice");
        if (stopservice != null && stopservice.length() > 0) {
            stop();
        }*/
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky
        return START_STICKY;
    }


    void manageBluetoothConnection(BluetoothSocket bluetoothSocket){
        connectedThread = new ConnectedThread(bluetoothSocket);
        connectedThread.start();
    }

    void connectToDevice(){
        if (BluetoothDeviceForHC05 != null && connectingThread == null) {
            // Initiate a connection request in a separate thread
            connectingThread = new ConnectingThread(BluetoothDeviceForHC05);
            connectingThread.start();
 /*          Toast.makeText(getApplicationContext(), "Connecting Thread Started",
                                    Toast.LENGTH_LONG).show();*/
        }
    }

    public class ConnectedThread extends Thread implements Runnable {
        private final BluetoothSocket mmSocket;

        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;

            OutputStream tmpOut = null;

            try {

                tmpOut = socket.getOutputStream();
            } catch (IOException e) {

            }
            mmOutStream = tmpOut;
        }

        public void run() {
            //   byte[] buffer = new byte[1024];  // buffer store for the stream
            //   int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs

            while (true) {
//                write("#Hi Hans~".toString().getBytes());
//
//                try {
//                    Thread.sleep(500);
//                } catch(InterruptedException ex) {
//                    Thread.currentThread().interrupt();
//                }
            }
        }

        /* Call this from the main activity to send data to HC 05 */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private class ConnectingThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final BluetoothDevice bluetoothDevice;

        public ConnectingThread(BluetoothDevice device) {

            BluetoothSocket temp = null;
            bluetoothDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                temp = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temp;
            if(temp == null){
/*                Toast.makeText(getApplicationContext(), "Null "+ bluetoothDevice.getName(),
                        Toast.LENGTH_SHORT).show();*/
            }
        }

        public void run() {
            // Cancel any discovery as it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // This will block until it succeeds in connecting to the device
                // through the bluetoothSocket or throws an exception
                bluetoothSocket.connect();

            } catch (IOException connectException) {
                connectException.printStackTrace();
                try {
                    bluetoothSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            }

            // Code to manage the connection in a separate thread
            if(bluetoothSocket.isConnected()) {

                Intent intent = new Intent("intentKey");
                // You can also include some extra data.
                intent.putExtra("key", "hc05-connected");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                manageBluetoothConnection(bluetoothSocket);
            }
        }

        // Cancel an open connection and terminate the thread
        public void cancel() {
            try {
                bluetoothSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
