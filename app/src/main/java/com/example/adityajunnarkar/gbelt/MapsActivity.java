package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableMap;

;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.Route;

import com.hamondigital.unlock.UnlockBar;
import com.hamondigital.unlock.UnlockBar.OnUnlockListener;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        DirectionFinderListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        Serializable,
        OnInitListener {

    GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    BluetoothAdapter mBluetoothAdapter;

    ImageButton btnSearch;
    ImageButton btnBus;
    ImageButton btnWalk;
    EditText etOrigin;
    EditText etDestination;

    ProgressDialog progressDialog;

    Route mRoute;
    Location mLastLocation;
    ImageView speakButton;
    ImageView locationButton;

    TextToSpeech mTts;
    HashMap<String, String> myHashAlarm;
    String utteranceId = "";

    UnlockBar unlock;

    // Voice Recognition Request Codes
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    public static final int TTS_DATA_CODE = 5678;

    public static final Map<Integer, String> transportationModes = ImmutableMap.of(
            1, "walking",
            2, "transit",
            3, "driving"
    );

    int mode = 1; // Default mode to walking

    static BluetoothDevice BluetoothDeviceForHC05;

    //Bluetooth Request Codes
    public static final int ENABLE_BT_REQUEST_CODE = 100;
    public static final int DISCOVERABLE_BT_REQUEST_CODE = 101;

    //Set Duration discovery time to 120 seconds
    public static final int DISCOVERABLE_DURATION = 120;

    BluetoothService localBTService;
    static boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
            checkRecordAudioPermission();
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

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        startTextToSpeechActivity();

        // Retrieve form elements for later use
        etOrigin = (EditText) findViewById(R.id.etOrigin);
        etDestination = (EditText) findViewById(R.id.etDestination);

        retrieveData();

        setUpVoiceRecognitionListener();

        setUpCurrentLocationListener();

        setUpStartNavigationListener();

        setUpTransitModeListeners();

        setUpUnlockListener();

        // because i am too lazy to type it out, remove later
        ((EditText) findViewById(R.id.etOrigin)).setText("Your Location");
        ((EditText) findViewById(R.id.etDestination)).setText("University of Waterloo");
    }

    public void retrieveData() {
        // grab data
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        if (bundle != null) {
            if (bundle.containsKey("origin")) {
                etOrigin.setText((String) bundle.getSerializable("origin"));
            }

            if (bundle.containsKey("destination")) {
                etDestination.setText((String) bundle.getSerializable("destination"));
            }

            if (bundle.containsKey("mode")) {
                mode = (int) bundle.getSerializable("mode");
            }

           /* if (bundle.containsKey("connectedThread")) {
                connectedThread = (ConnectedThread) bundle.getSerializable("connectedThread");
            }*/
        }
    }

    private void setUpUnlockListener() {
        unlock = (UnlockBar) findViewById(R.id.unlock);

        unlock.setOnUnlockListener(new OnUnlockListener() {
            @Override
            public void onUnlock()
            {
                unlock.reset();
                startVoiceMode(); // switch to voice mode => starts voice mode intent
            }
        });
    }

    private void setUpTransitModeListeners() {
        // Set the bus route to half opacity & default to walking
        btnBus = ((ImageButton) findViewById(R.id.bus));
        btnBus.getBackground().setAlpha(128);
        btnWalk = ((ImageButton) findViewById(R.id.walk));
        btnWalk.getBackground().setAlpha(255);

        // Attach listener to change transit mode to bus
        btnBus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnWalk.getBackground().setAlpha(128);
                btnBus.getBackground().setAlpha(255);
                mode = 2;
            }
        });

        // Attach listener to change transit mode to walking
        btnWalk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnBus.getBackground().setAlpha(128);
                btnWalk.getBackground().setAlpha(255);
                mode = 1;
            }
        });
    }

    private void setUpStartNavigationListener() {
        btnSearch = (ImageButton) findViewById(R.id.search);

        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDirectionRequest();
            }
        });
    }

    private void setUpCurrentLocationListener() {
        locationButton = (ImageView) findViewById(R.id.location);

        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etOrigin.setText("Your Location");
            }
        });
    }

    private void setUpVoiceRecognitionListener() {
        speakButton = (ImageView) findViewById(R.id.microphone);

        speakButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceRecognitionActivity();
            }
        });
    }

    private void createNavigationIntent(List<Route> routes) {
        destroyTts();

        Intent intent = new Intent(this, NavigationActivity.class);
        Bundle bundle = new Bundle();

        bundle.putSerializable("routes", (Serializable) routes);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        bundle.putSerializable("step", (Serializable) 0);
        intent.putExtras(bundle);

        String origin = etOrigin.getText().toString();
        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        String destination = etDestination.getText().toString();
        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    public void startVoiceMode() {
        destroyTts();

        Intent intent = new Intent(this, VoiceModeActivity.class);
        Bundle bundle = new Bundle(); // pass bundle to voice mode activity

        bundle.putSerializable("activity", (Serializable) "Maps");
        intent.putExtras(bundle);

        String origin = etOrigin.getText().toString();
        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        String destination = etDestination.getText().toString();
        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    private void destroyTts() {
        if(mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }

    @SuppressWarnings("deprecation") // haha haha
    public void startVoiceRecognitionActivity() {
        String promptLocation = "Enter destination";
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, promptLocation);
        mTts.speak(promptLocation, TextToSpeech.QUEUE_FLUSH, myHashAlarm);

        // wait until utterance is complete before opening speech intent
        while (!utteranceId.equals(promptLocation));

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Speak now");
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    public void startTextToSpeechActivity() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_DATA_CODE);
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

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Fill the list view with the strings the recognizer thought it
            // could have heard
            ArrayList matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            etDestination.setText(matches.get(0).toString());
            // matches is the result of voice input. It is a list of what the
            // user possibly said.
            // Using an if statement for the keyword you want to use allows the
            // use of any activity if keywords match
            // it is possible to set up multiple keywords to use the same
            // activity so more than one word will allow the user
            // to use the activity (makes it so the user doesn't have to
            // memorize words from a list)
            // to use an activity from the voice input information simply use
            // the following format;
            // if (matches.contains("keyword here") { startActivity(new
            // Intent("name.of.manifest.ACTIVITY")

        }

        if (requestCode == TTS_DATA_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                if (mTts == null) {
                    mTts = new TextToSpeech(this, this);
                }
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }

    }

    /** Defines callbacks for service binding, passed to bindService() */
 /*   private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            localBTService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            localBTService = null;
        }
    };
*/
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
                Toast.makeText(getApplicationContext(), "Device Found: "+ bluetoothDevice.getName(),
                        Toast.LENGTH_SHORT).show();


                if (bluetoothDevice.getName() != null) {
                    if (bluetoothDevice.getName().equals("HC-05")) {
                        BluetoothDeviceForHC05 = mBluetoothAdapter.getRemoteDevice(bluetoothDevice.getAddress());

                        Intent intentBT = new Intent(MapsActivity.this, BluetoothService.class);
                        Bundle b = new Bundle();
                        b.putParcelable("HC-05", BluetoothDeviceForHC05);
                        intentBT.putExtras(b);
                        startService(intentBT);
                        // Bind to LocalService
                        //bindService(intentBT, mConnection, Context.BIND_AUTO_CREATE);

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

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTts.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String s) {
                    utteranceId = s;
                }
            });

            mTts.setLanguage(Locale.ENGLISH);

            myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
        }

    }


    public void updateMap() {
        // redraw map with new coordinates
        LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
    }

    private void sendDirectionRequest() {
        String origin = etOrigin.getText().toString();
        String destination = etDestination.getText().toString();

        if (origin.equals("Your Location")) {
            origin = mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude();
        }

        if (origin.isEmpty()) {
            Toast.makeText(this, "Please enter origin address!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destination.isEmpty()) {
            Toast.makeText(this, "Please enter destination address!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            new DirectionFinder(this, origin, destination, transportationModes.get(mode)).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDirectionFinderStart() {
        progressDialog = ProgressDialog.show(this, "Please wait.",
                "Finding direction...", true);
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onDirectionFinderSuccess(List<Route> routes) {
        progressDialog.dismiss();

        for (Route route : routes) {
            mRoute = route;
        }

        if (mRoute != null) {
            createNavigationIntent(routes);
        }
    }

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

        // find last known location, and move map to location
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            updateMap();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        updateMap();
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

    public static final int MY_PERMISSIONS_REQUEST_AUDIO = 100;
    public boolean checkRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_AUDIO);

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_AUDIO);
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

            case MY_PERMISSIONS_REQUEST_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted. Do the
                    // contacts-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                        // Permission granted, not sure if we want to do anything.
                    }

                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    // Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other permissions this app might request.
            // You can add here other case statements according to your requirement.
        }
    }
}
