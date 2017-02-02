package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.Route;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        SensorEventListener,
        DirectionFinderListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    LocationRequest mLocationRequest;
    BluetoothAdapter mBluetoothAdapter;

    private Compass compass;
    private SensorManager sensorManager;
    private Sensor gsensor;
    private Sensor msensor;
    float azimuth = 0;

    private Button btnFindPath;
    private EditText etOrigin;
    private EditText etDestination;
    private List<Marker> originMarkers = new ArrayList<>();
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();
    private ProgressDialog progressDialog;

    private int mStep = 0;
    private Route mRoute;

    static BluetoothDevice BluetoothDeviceForHC05;

    static ConnectedThread connectedThread;
    static ConnectingThread connectingThread;

    //Bluetooth Request Codes
    public static final int ENABLE_BT_REQUEST_CODE = 100;
    public static final int DISCOVERABLE_BT_REQUEST_CODE = 101;

    //Set Duration discovery time to 120 seconds
    public static final int DISCOVERABLE_DURATION = 120;

    // HC 05 SPP UUID
    private final static UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        } else {
            // Any valid Bluetooth operations
            if (!mBluetoothAdapter.isEnabled()) {
                // A dialog will appear requesting user permission to enable Bluetooth
                Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent, ENABLE_BT_REQUEST_CODE);
            } else {
                //Toast.makeText(getApplicationContext(), "Your device has already been enabled." +
                                //"\n" + "Scanning for remote Bluetooth devices...",
                        //Toast.LENGTH_SHORT).show();

                // To discover remote Bluetooth devices
                discoverDevices();

            }
        }

        compass = new Compass(this);
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        gsensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        msensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, gsensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, msensor,
                SensorManager.SENSOR_DELAY_NORMAL);

        compass.start();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // because i am too lazy to type it out
        ((EditText) findViewById(R.id.etOrigin)).setText("339 King Street North, Waterloo");
        ((EditText) findViewById(R.id.etDestination)).setText("University of Waterloo");

        btnFindPath = (Button) findViewById(R.id.btnFindPath);
        etOrigin = (EditText) findViewById(R.id.etOrigin);
        etDestination = (EditText) findViewById(R.id.etDestination);

        btnFindPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendRequest();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == ENABLE_BT_REQUEST_CODE) {

            // Bluetooth successfully enabled!
            if (resultCode == Activity.RESULT_OK) {
                //Toast.makeText(getApplicationContext(), "Ha! Bluetooth is now enabled." +
                                //"\n" + "Scanning for remote Bluetooth devices...",
                        //Toast.LENGTH_SHORT).show();

                // To discover remote Bluetooth devices
                discoverDevices();

            } else { // RESULT_CANCELED as user refused or failed to enable Bluetooth
                //Toast.makeText(getApplicationContext(), "Bluetooth is not enabled.",
                        //Toast.LENGTH_SHORT).show();

            }
        } else if (requestCode == DISCOVERABLE_BT_REQUEST_CODE){

            if (resultCode == DISCOVERABLE_DURATION){
/*                Toast.makeText(getApplicationContext(), "Your device is now discoverable by other devices for " +
                                DISCOVERABLE_DURATION + " seconds",
                        Toast.LENGTH_SHORT).show();*/
            } else {
/*                Toast.makeText(getApplicationContext(), "Fail to enable discoverability on your device.",
                        Toast.LENGTH_SHORT).show();*/
            }
        }
    }

    protected void discoverDevices(){
        // To scan for remote Bluetooth devices
        if (mBluetoothAdapter.startDiscovery()) {
/*            Toast.makeText(getApplicationContext(), "Discovering other bluetooth devices...",
                    Toast.LENGTH_SHORT).show();*/
        } else {
/*            Toast.makeText(getApplicationContext(), "Discovery failed to start.",
                    Toast.LENGTH_SHORT).show();*/
        }

        // Register the BroadcastReceiver for ACTION_FOUND
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(broadcastReceiver, filter);
    }

    protected void makeDiscoverable(){
        // Make local device discoverable
        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        startActivityForResult(discoverableIntent, DISCOVERABLE_BT_REQUEST_CODE);
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // Whenever a remote Bluetooth device is found
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // Get the BluetoothDevice object from the Intent
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                Toast.makeText(getApplicationContext(), "Device Found: "+ bluetoothDevice.getName(),
//                        Toast.LENGTH_SHORT).show();


                if (bluetoothDevice.getName() != null) {
                    if (bluetoothDevice.getName().equals("HC-05")) {
                        BluetoothDeviceForHC05 = mBluetoothAdapter.getRemoteDevice(bluetoothDevice.getAddress());
                        if (BluetoothDeviceForHC05 != null && connectingThread == null) {
                            // Initiate a connection request in a separate thread
                            connectingThread = new ConnectingThread(BluetoothDeviceForHC05);
                            connectingThread.start();
 /*                           Toast.makeText(getApplicationContext(), "Connecting Thread Started",
                                    Toast.LENGTH_LONG).show();*/
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        // Register the BroadcastReceiver for ACTION_FOUND
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(broadcastReceiver, filter);

      }

    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(broadcastReceiver);
    }


    /* @Override
       protected void onDestroy() {
          super.onDestroy();
          this.unregisterReceiver(broadcastReceiver);

       }
   */
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

    void manageBluetoothConnection(BluetoothSocket bluetoothSocket){
        connectedThread = new ConnectedThread(bluetoothSocket);
        connectedThread.start();
    }

    private class ConnectedThread extends Thread {
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
//            Toast.makeText(this, "Hi Hans sent", Toast.LENGTH_SHORT).show();
            while (true) {
//                write("#Hi dstar~".toString().getBytes());
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

    private void sendRequest() {
        String origin = etOrigin.getText().toString();
        String destination = etDestination.getText().toString();
        String mode = "walking"; // by default, set to walking for now

        if (origin.isEmpty()) {
            Toast.makeText(this, "Please enter origin address!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destination.isEmpty()) {
            Toast.makeText(this, "Please enter destination address!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            new DirectionFinder(this, origin, destination, mode).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDirectionFinderStart() {
        progressDialog = ProgressDialog.show(this, "Please wait.",
                "Finding direction..!", true);

        if (originMarkers != null) {
            for (Marker marker : originMarkers) {
                marker.remove();
            }
        }

        if (destinationMarkers != null) {
            for (Marker marker : destinationMarkers) {
                marker.remove();
            }
        }

        if (polylinePaths != null) {
            for (Polyline polyline:polylinePaths ) {
                polyline.remove();
            }
        }
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onDirectionFinderSuccess(List<Route> routes) {
        progressDialog.dismiss();
        polylinePaths = new ArrayList<>();
        originMarkers = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        for (Route route : routes) {
            mRoute = route;
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(route.startLocation, 16));
            ((TextView) findViewById(R.id.tvDuration)).setText(route.duration.text);
            ((TextView) findViewById(R.id.tvDistance)).setText(route.distance.text);

            // Display the first direction
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ((TextView) findViewById(R.id.instruction)).setText(Html.fromHtml(route.steps.get(mStep).htmlInstruction, Html.FROM_HTML_MODE_LEGACY));
            } else {
                ((TextView) findViewById(R.id.instruction)).setText(Html.fromHtml(route.steps.get(mStep).htmlInstruction));
            }

            // Add Markers for origin and destination
            originMarkers.add(mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.start_blue))
                    .title(route.startAddress)
                    .position(route.startLocation)));
            destinationMarkers.add(mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.end_green))
                    .title(route.endAddress)
                    .position(route.endLocation)));

            // Create the polyline
            PolylineOptions polylineOptions = new PolylineOptions().
                    geodesic(true).
                    color(Color.BLUE).
                    width(10);

            // Iterate through the route points to create the polyline
            for (int i = 0; i < route.points.size(); i++)
                polylineOptions.add(route.points.get(i));

            polylinePaths.add(mMap.addPolyline(polylineOptions));
        }

        // transmit the desired theta to Arduino
        if (mRoute != null) {
            transmitVector();
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

    }


    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {

        mLastLocation = location;
        if (mCurrLocationMarker != null) {
            mCurrLocationMarker.remove();
        }

        //Place current location marker
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
//        MarkerOptions markerOptions = new MarkerOptions();
//        markerOptions.position(latLng);
//        markerOptions.title("Current Position");
//        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA));
//        mCurrLocationMarker = mMap.addMarker(markerOptions);

        //move map camera
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

        if (mRoute != null) {
//            Toast.makeText(this, "Current LatLong: " + latLng.latitude + ", " + latLng.longitude +
//                    " Theshold: " + mRoute.steps.get(mStep).upperThreshold.latitude + ", " + mRoute.steps.get(mStep).upperThreshold.longitude
//                    , Toast.LENGTH_LONG).show();
            if (latLng.latitude > mRoute.steps.get(mStep).lowerThreshold.latitude
                    && latLng.latitude < mRoute.steps.get(mStep).upperThreshold.latitude
                    && latLng.longitude > mRoute.steps.get(mStep).lowerThreshold.longitude
                    && latLng.longitude < mRoute.steps.get(mStep).upperThreshold.longitude) {
                onNextStep();
            }
        }

        //stop location updates - spent two hours trying to figure out why onLocationCalled wasn't being called
        // ALL BECAAUSE OF THIS EVIL SNIPPET!!!
//        if (mGoogleApiClient != null) {
//            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
//        }
    }

    public void onNextStep() {
        // TODO: only call this if it is not the last step
        mStep++;
        // Display the first direction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ((TextView) findViewById(R.id.instruction)).setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction, Html.FROM_HTML_MODE_LEGACY));
        } else {
            ((TextView) findViewById(R.id.instruction)).setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction));
        }
    }

    public double calculateVector() {
        double vector = 0;

        // Starting location
        double x1 = mRoute.steps.get(mStep).startLocation.longitude;
        double y1 = mRoute.steps.get(mStep).startLocation.latitude;

        // Ending location
        double x2 = mRoute.steps.get(mStep).endLocation.longitude;
        double y2 = mRoute.steps.get(mStep).endLocation.latitude;

        ((TextView) findViewById(R.id.azimuth)).setText("x1: " + x1 + " y1: " + y1 + " x2: " + x2 + " y2: " + y2);
        // TODO: rethink this with the >= and <= stuff
        if (x2 >= x1 && y2 >= y1 ) {
            vector = Math.toDegrees(Math.atan(Math.abs(x2-x1)/Math.abs(y2-y1)));
        } else if (x2 > x1 && y2 < y1) {
            vector = 90.0 + Math.toDegrees(Math.atan(Math.abs(y2-y1)/Math.abs(x2-x1)));
        } else if (x2 < x1 && y2 < y1) {
            vector = 180.0 + Math.toDegrees(Math.atan(Math.abs(x2-x1)/Math.abs(y2-y1)));
        } else {
            vector = 270.0 + Math.toDegrees(Math.atan(Math.abs(y2-y1)/Math.abs(x2-x1)));
        }

        return vector;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public boolean checkLocationPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted. Do the
                    // contacts-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }

                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    //Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other permissions this app might request.
            // You can add here other case statements according to your requirement.
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ;
        //azimuth = compass.getAzimuth();
        //((TextView) findViewById(R.id.azimuth)).setText("Azimuth: " + azimuth);

        //if (mRoute != null) {
            //transmitVector();
        //}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // hehe do nothing. it's complaining about not having this
    }

    public float calculateTheta() {
        float theta = 0;

        float theta1 = azimuth;
        float theta2 = (float) calculateVector(); // actually might want to save vector as a global variable so we don't calculate it every time

        if (theta1 > theta2) {
            theta = 360 - (theta1-theta2);
        } else {
            theta = theta2 - theta1;
        }

        return theta;
    }

    public void transmitVector() {
        float desired_theta = 0;

        //float theta = calculateTheta();
        //((TextView) findViewById(R.id.beltVector)).setText("Belt Theta: " + theta);

        desired_theta = (float) calculateVector();
        ((TextView) findViewById(R.id.beltVector)).setText("Belt Theta: " + desired_theta);
        String message = "#" + desired_theta + "~";

        byte[] vectorBytes = message.getBytes();

        if (connectedThread != null) { // && connectedThread.isAlive()
//            Toast.makeText(this, "Transmitting", Toast.LENGTH_LONG).show();
            connectedThread.write(vectorBytes);
        }
    }

}
