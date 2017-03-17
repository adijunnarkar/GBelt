package com.example.adityajunnarkar.gbelt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class BluetoothService extends Service {

    // HC 05 SPP UUID
    private final static UUID uuid_HC05 = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final static UUID uuid_headset = UUID.fromString("00001108-0000-1000-8000-00805F9B34FB");

    static ConnectedThread connectedThread;
    static ConnectingThread connectingThread_hc05;
    static ConnectingThread connectingThread_headset;

    BluetoothAdapter mBluetoothAdapter;

    static BluetoothHeadset mBluetoothHeadset;
    static BluetoothDevice mConnectedHeadset;

    static AudioManager mAudioManager;
    static BluetoothDevice BluetoothDeviceForHC05;
    static BluetoothDevice BluetoothDeviceHDP;
    static private boolean mIsCountDownOn;
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
            BluetoothDeviceHDP = intent.getParcelableExtra("hands-free");
            boolean paired_headset = intent.getExtras().getBoolean("paired");
            boolean stopHC = intent.getExtras().getBoolean("Stop HC");

            byte[] desiredVector = intent.getByteArrayExtra("vector");
            if(BluetoothDeviceForHC05 != null){
                connectToHC05();
            }

            if(BluetoothDeviceHDP != null){

                if(!paired_headset) {
                    connectToHeadset();
                } else {
                    if (mBluetoothAdapter.getProfileProxy(getApplicationContext(), mProfileListener, BluetoothProfile.HEADSET)) {
                    }
                }
            }

            if (desiredVector!= null && connectedThread != null) { // && connectedThread.isAlive()
                connectedThread.write(desiredVector);
            }
            if(stopHC){
                connectingThread_hc05 = null;
                connectedThread = null;
                BluetoothDeviceForHC05 = null;
            }

        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky
        return START_STICKY;
    }


    void manageBluetoothConnection(BluetoothSocket bluetoothSocket){
        connectedThread = new ConnectedThread(bluetoothSocket);
        connectedThread.start();
    }

    void connectToHC05(){
        if (BluetoothDeviceForHC05 != null && connectingThread_hc05 == null) {
            // Initiate a connection request in a separate thread
            connectingThread_hc05 = new ConnectingThread(BluetoothDeviceForHC05);
            connectingThread_hc05.start();

        }
    }

    void connectToHeadset(){
        if (BluetoothDeviceHDP != null && connectingThread_headset == null) {
            // Initiate a connection request in a separate thread
            connectingThread_headset = new ConnectingThread(BluetoothDeviceHDP);
            connectingThread_headset.start();

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
                if(device.getAddress().equals(((MyApplication)getApplication()).getBTHC05Address())) {  //hc - 05
                    temp = bluetoothDevice.createRfcommSocketToServiceRecord(uuid_HC05);
                } else {
                    temp = bluetoothDevice.createRfcommSocketToServiceRecord(uuid_headset);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temp;
            if(temp == null){

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
                if(bluetoothDevice.getAddress().equals(((MyApplication)getApplication()).getBTHC05Address())) {
                    Intent intent = new Intent("intentKey");
                    Bundle b = new Bundle();
                    b.putParcelable("HC-05", BluetoothDeviceForHC05);
                    intent.putExtras(b);
                    // You can also include some extra data.
                    intent.putExtra("key", "hc05-connected");

                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                    manageBluetoothConnection(bluetoothSocket);

                } else if(bluetoothDevice.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE){
                    Intent intent = new Intent("intentKey");
                    Bundle b = new Bundle();
                    b.putParcelable("hands-free", BluetoothDeviceHDP);
                    intent.putExtras(b);
                    // You can also include some extra data.
                    intent.putExtra("key", "headset-connected");
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                }
            } else if(bluetoothDevice.getAddress().equals(((MyApplication)getApplication()).getBTHC05Address())){
                Intent intent = new Intent("intentKey");
                // You can also include some extra data.
                intent.putExtra("key", "hc05-not-connected");
                connectingThread_hc05 = null;
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

            } else {
                Intent intent = new Intent("intentKey");
                // You can also include some extra data.
                intent.putExtra("key", "headset-not-connected");
                connectingThread_headset = null;
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
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

    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset) proxy;

                List<BluetoothDevice> btHeadsets = mBluetoothHeadset.getConnectedDevices();
                if (btHeadsets.isEmpty()) {

                   /* Method connect = getConnectMethod();

                    try {
                        connect.setAccessible(true);
                        connect.invoke(proxy, BluetoothDeviceHDP);
                    } catch (InvocationTargetException ex) {
                        ex.printStackTrace();
                        Intent intent = new Intent("intentKey");
                        // You can also include some extra data.
                        intent.putExtra("key", "headset-not-connected");
                        connectingThread_headset = null;
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                        //Log.e(TAG, "Unable to invoke connect(BluetoothDevice) method on proxy. " + ex.toString());
                    } catch (IllegalAccessException ex) {
                        ex.printStackTrace();
                        Intent intent = new Intent("intentKey");
                        // You can also include some extra data.
                        intent.putExtra("key", "headset-not-connected");
                        connectingThread_headset = null;
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                        //Log.e(TAG, "Illegal Access! " + ex.toString());
                    }*/
                    //while (mBluetoothHeadset.getConnectionState(BluetoothDeviceHDP) != BluetoothProfile.STATE_CONNECTED) ;
                    Intent intent = new Intent("intentKey");
                    Bundle b = new Bundle();
                    b.putParcelable("hands-free", BluetoothDeviceHDP);
                    intent.putExtras(b);
                    // You can also include some extra data.
                    intent.putExtra("key", "headset-connected");
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                } else {
                    mConnectedHeadset = btHeadsets.get(0);
                    //BluetoothDeviceHDP = mConnectedHeadset;
                   // while (mBluetoothHeadset.getConnectionState(BluetoothDeviceHDP) != BluetoothProfile.STATE_CONNECTED) ;
                    configureHeadSet();

                    Intent intent = new Intent("intentKey");
                    Bundle b = new Bundle();
                    b.putParcelable("hands-free", BluetoothDeviceHDP);
                    intent.putExtras(b);
                    // You can also include some extra data.
                    intent.putExtra("key", "headset-connected");
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

                }
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                if(mBluetoothHeadset != null) {
                    mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
                    mBluetoothHeadset = null;
                }
            }
        }
    };

    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getConnectMethod () {
        try {
            return BluetoothHeadset.class.getDeclaredMethod("connect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            //Log.e(TAG, "Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    public void configureHeadSet(){
        try {
            // Register the BroadcastReceiver for ACTION_FOUND
           IntentFilter filter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            this.registerReceiver(mHeadsetBroadcastReceiver, filter);
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mAudioManager.setMode(AudioManager.MODE_IN_CALL);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver mHeadsetBroadcastReceiver = new BroadcastReceiver() {

        @SuppressWarnings("synthetic-access")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
                int new_state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.ERROR);
                int prev_state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                        AudioManager.ERROR);
                if ((new_state == AudioManager.SCO_AUDIO_STATE_CONNECTED) && (prev_state == AudioManager.SCO_AUDIO_STATE_CONNECTING))
                {

                } else if ((new_state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) && (prev_state == AudioManager.SCO_AUDIO_STATE_CONNECTING)){

                }

            }
        }
    };

    /**
     * API >= 11
     * Try to connect to audio headset in onTick.
     */
    private CountDownTimer mCountDown11 = new CountDownTimer(10000, 1000)
    {
        @SuppressWarnings("synthetic-access")
        @Override
        public void onTick(long millisUntilFinished)
        {
            // First stick calls always returns false. The second stick
            // always returns true if the countDownInterval is set to 1000.
            // It is somewhere in between 500 to a 1000.
//        	mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
            if(mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset)) {
                System.out.println("Started voice recognition");
            } else {
                System.out.println("Could not start recognition");
            }




        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onFinish()
        {
            // Calls to startVoiceRecognition in onStick are not successful.
            // Should implement something to inform user of this failure
            mIsCountDownOn = false;

        }
    };

}
