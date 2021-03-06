package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
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
import com.google.common.collect.ImmutableMap;
import com.hamondigital.unlock.UnlockBar;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.LoadingScreen;
import Modules.Route;

public class VoiceModeActivity extends AppCompatActivity implements OnMapReadyCallback,
        DirectionFinderListener,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        TextToSpeech.OnInitListener,
        RecognitionListener,
        Serializable {

    public static final int TTS_DATA_CODE = 5678;

    public static final Map<Integer, String> transportationModes = ImmutableMap.of(
            1, "walking",
            2, "transit",
            3, "driving"
    );

    BluetoothAdapter mBluetoothAdapter;

    static BluetoothDevice BluetoothDeviceForHC05;

    //Bluetooth Request Codes
    public static final int ENABLE_BT_REQUEST_CODE = 100;
    public static final int DISCOVERABLE_BT_REQUEST_CODE = 101;

    //Set Duration discovery time to 120 seconds
    public static final int DISCOVERABLE_DURATION = 120;

    BluetoothService localBTService;
    static boolean mBound = false;

    GoogleMap mMap; // map is not drawn, but we still need it for location and the api
    LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    TextToSpeech mTts;
    HashMap<String, String> myHashAlarm;
    TextView instruction;

    RelativeLayout activity;
    boolean mBooleanIsPressed;
    CircularProgressBar circularProgressBar;
    UnlockBar unlock;
    LoadingScreen loader;

    static BluetoothDevice BluetoothDeviceHDP;
    static BluetoothHeadset mBluetoothHeadset;
    static BluetoothDevice mConnectedHeadset;

    static AudioManager mAudioManager;
    static boolean TTSReady = false;
    String activityMode; // Maps or Navigation

    // Maps mode
    int mode; // current transportation mode
    String origin;
    String destination;

    // Navigation mode
    List<Route> mRoutes;
    Route mRoute = null;
    int mStep = 0;
    boolean tripStarted;

    int attemptNumber = 1;
    int maxAttempts = 2;

    static boolean ttsReady = false;
    boolean recalculating = false;

    // Drawing map
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

    private List<String> ttsQueue = new ArrayList<>();

    // for Timer to switch to touch screen mode
    int timerDuration = 3000; // ms, timer completion time
    int timerTimeout = 200; // ms, call run every 100 ms
    int timerCount = 0; // ms, keep count of the timer

    // animation of circular progress bar
    int animationDuration = 300; // 100ms = 0.1s
    int resetAnimationDuration = 2000; // reset slowly

    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;

    // Global variables across entire application used for debugging:
    boolean DEBUG;
    boolean TTSDEBUG;
    boolean RECALCULATION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().hide();
        setContentView(R.layout.activity_voice_mode);

        retrieveStates();
        retrieveData();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        checkRecordAudioPermission();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter("intentKey"));
        startTextToSpeechActivity();
        setupBluetooth();



        setUpMap();

        // Find layout elements for future use
        instruction = ((TextView) findViewById(R.id.instruction));
        circularProgressBar = (CircularProgressBar)findViewById(R.id.progressBar);

        resetProgressBar();

        adjustUnlockBar();

        setUpLoadingSpinner();

        setUpTouchAndHoldTimer();

        setUpUnlockListener();
    }

    void setupBluetooth(){
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
                checkPairedOrDoDiscovery();

            }
        }

    }

    //This function checks if HC05 is already present as a paired device and stats service to connect to it, if not starts discovery
    public void checkPairedOrDoDiscovery() {

        // To discover remote Bluetooth devices
        if (BluetoothDeviceForHC05 == null || BluetoothDeviceHDP == null) {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            boolean HC05Found = false, headsetFound = false;

            if (pairedDevices.size() > 0) {
                // There are paired devices.
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getAddress().equals(((MyApplication) this.getApplication()).getBTHC05Address())) {
                        HC05Found = true;
                        if(BluetoothDeviceForHC05 == null ) {
                            Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
                            Bundle b = new Bundle();
                            b.putParcelable("HC-05", device);
                            intentBT.putExtras(b);
                            startService(intentBT);
                        }

                    } else if(device.getAddress().equals(((MyApplication) this.getApplication()).getHeadsetAddress())){

                        headsetFound = true;
                        if (BluetoothDeviceHDP == null ) {

                            Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
                            intentBT.putExtra("paired", true);
                            Bundle b = new Bundle();
                            b.putParcelable("hands-free", mBluetoothAdapter.getRemoteDevice(device.getAddress()));
                            intentBT.putExtras(b);
                            startService(intentBT);
                        }
                    }
                }
                // if devices are not paired but bluetooth is enabled
                if (!(HC05Found && headsetFound)) {
                    discoverDevices();
                }
            } else {
                discoverDevices();
            }
        }
    }

    public boolean isHandsFreeDevice(int deviceClass){
        return ((deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE)
                || (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
                || (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET));
    }

    protected void discoverDevices(){

        // Register the BroadcastReceiver for ACTION_FOUND
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(broadcastReceiver, filter);

        // To scan for remote Bluetooth devices
        if (mBluetoothAdapter.startDiscovery()) {

        }

    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            String address = "";
            int deviceClass = 0;
            // Get the BluetoothDevice object from the Intent
            BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if(bluetoothDevice != null) {
                address = bluetoothDevice.getAddress();
                deviceClass = bluetoothDevice.getBluetoothClass().getDeviceClass();
            }

            // Whenever a remote Bluetooth device is found
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {


                Toast.makeText(getApplicationContext(), "Device Found: "+ bluetoothDevice.getName()+" Device class: "+ deviceClass,
                        Toast.LENGTH_SHORT).show();

                //MAC address of HC05
                if (address.equals(((MyApplication) getApplication()).getBTHC05Address())) {

                    Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
                    Bundle b = new Bundle();
                    b.putParcelable("HC-05", mBluetoothAdapter.getRemoteDevice(address));
                    intentBT.putExtras(b);
                    startService(intentBT);

                } else if (address.equals(((MyApplication) getApplication()).getHeadsetAddress())){

                    if (BluetoothDeviceHDP == null ) {
                        Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
                        Bundle b = new Bundle();
                        b.putParcelable("hands-free", mBluetoothAdapter.getRemoteDevice(address));
                        intentBT.putExtras(b);
                        startService(intentBT);
                    }
                }
            } else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                if (address.equals(((MyApplication) getApplication()).getHeadsetAddress())){

                    try {
                        mAudioManager.setMode(AudioManager.MODE_NORMAL);
                        mAudioManager.setBluetoothScoOn(false);
                        mAudioManager.stopBluetoothSco();

                        // Establish connection to the proxy
                        if(mBluetoothHeadset != null){
                            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET,  mBluetoothHeadset);
                        }

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                } else if (address.equals(((MyApplication) getApplication()).getBTHC05Address())) {
                    tts("HC 05 connection lost");
                    Intent intentStopBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
                    intentStopBT.putExtra("Stop HC", true);
                    startService(intentStopBT);
                    checkPairedOrDoDiscovery();
                }
            }
        }
    };

    // Create a BroadcastReceiver for app connected to HC 05 and headset, string is broadcast from BluetoothService class
    // indicating device is connected
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String message = intent.getStringExtra("key");


            if(message.equals("hc05-connected")) {
                if(getCurrentActivity().equals("com.example.adityajunnarkar.gbelt.VoiceModeActivity")) {

                    tts("Bluetooth connection with HC05 established");
                } else {
                    Toast.makeText(getApplicationContext(), "HC-05 is now connected", Toast.LENGTH_LONG).show();
                }
                BluetoothDeviceForHC05 = intent.getParcelableExtra("HC-05");
            } else if(message.equals("hc05-not-connected") && BluetoothDeviceForHC05 == null){
                discoverDevices();
            } else if(message.equals("headset-connected")) {

                if(getCurrentActivity().equals("com.example.adityajunnarkar.gbelt.VoiceModeActivity")) {

                    tts("Bluetooth connection with headset established");

                } else {
                    Toast.makeText(getApplicationContext(), "headset is now connected", Toast.LENGTH_LONG).show();
                }

                BluetoothDeviceHDP = intent.getParcelableExtra("hands-free");
            } else if(message.equals("headset-not-connected") && BluetoothDeviceHDP == null){
                discoverDevices();
            }
        }
    };

    public String getCurrentActivity(){
        ActivityManager am = (ActivityManager) getApplicationContext().getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
        String currentActivity = taskInfo.get(0).topActivity.getClassName();
        return currentActivity;
    }

    /** Defines callbacks for service binding, passed to bindService() */
   /* private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            localBTService = binder.getService();
            Toast.makeText(getApplicationContext(), "Service bound",
            Toast.LENGTH_SHORT).show();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            Toast.makeText(getApplicationContext(), "Service unbound",
                    Toast.LENGTH_SHORT).show();
            localBTService = null;
        }
    };
*/

    private void setUpLoadingSpinner() {
        LinearLayout activityContent = (LinearLayout) findViewById(R.id.activityContent);
        RelativeLayout loadingContent = (RelativeLayout) findViewById(R.id.loadingContent);
        TextView loadingText = (TextView) findViewById(R.id.loadingText);
        ProgressBar spinner = (ProgressBar) findViewById(R.id.loadingProgressBar);
        LinearLayout loadingBg = (LinearLayout) findViewById(R.id.loadingBg);

        loader = new LoadingScreen(activityContent, loadingContent, loadingText, spinner, loadingBg);
        loader.disableLoading();
    }

    private void retrieveStates() {
        DEBUG = ((MyApplication) this.getApplication()).getDebug();
        TTSDEBUG = ((MyApplication) this.getApplication()).getTTS();
        RECALCULATION = ((MyApplication) this.getApplication()).getRecalculation();
    }

    private void retrieveData() {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        activityMode = (String) bundle.getSerializable("activity");

        // Voice Mode is activated either from the Maps or Navigation activities
        // Each activity passes different bundles
        if (activityMode.equals("Maps")) {
            // grab data from MapsActivity
            origin = (String) bundle.getSerializable("origin");
            destination = (String) bundle.getSerializable("destination");
            mode = (int) bundle.getSerializable("mode");
            tripStarted = (boolean) bundle.getSerializable("tripStarted");

            if (DEBUG) ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
            if (DEBUG) ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
            if (DEBUG) ((TextView) findViewById(R.id.origin)).setText("Origin: " + origin); // for debugging
            if (DEBUG) ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
        } else if (activityMode.equals("Navigation")) {
            // grab data from NavigationActivity
            mRoutes = (List<Route>)bundle.getSerializable("routes");
            mode = (int) bundle.getSerializable("mode");
            origin = (String) bundle.getSerializable("origin");
            destination = (String) bundle.getSerializable("destination");
            mStep = (int) bundle.getSerializable("step");
            tripStarted = (boolean) bundle.getSerializable("tripStarted");
        }
    }

    private void setUpMap() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getView().setAlpha((float) 0.3);
        mapFragment.getMapAsync(this);
    }

    private void resetProgressBar() {
        circularProgressBar.setProgressWithAnimation(0, animationDuration);
    }

    private void setUpTouchAndHoldTimer() {
        // Add listener for entire activity screen
        activity = (RelativeLayout) findViewById(R.id.mainContent);

        // Add event for activity
        activity.setOnTouchListener(new View.OnTouchListener()
        {
            private final Handler handler = new Handler();
            private final Runnable runnable = new Runnable() {
                public void run() {
                    if(mBooleanIsPressed)
                    {
                        timerCount += timerTimeout;
                        float percentage = (float) (100.0 * timerCount/timerDuration);
                        circularProgressBar.setProgressWithAnimation(percentage, animationDuration);
                        // keep calling handler every timerTimeout
                        handler.postDelayed(runnable, timerTimeout);

                        if (percentage == 100) { // timer has completed
                            mBooleanIsPressed = false;
                            timerCount = 0; // reset timer back to 0
                            resetProgressBar();
                            handler.removeCallbacks(runnable);
                            startVoiceRecognitionActivity();
                        }
                    }
                }
            };


            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    // Execute your Runnable after timerTimeout milliseconds.
                    // After this timerTimeout it will check if is pressed
                    handler.postDelayed(runnable, timerTimeout);
                    mBooleanIsPressed = true;
                }

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(mBooleanIsPressed) {
                        mBooleanIsPressed = false;
                        timerCount = 0; // reset timer back to 0
                        resetProgressBar();
                        handler.removeCallbacks(runnable);
                    }
                    return false;
                }
                return true;
            }
        });
    }

    private void adjustUnlockBar() {
        // Right now we use the slideToUnlock library for the slider
        // But that has been configured for anything to voice mode but not vice versa
        // So we need to swap the images but there we duplicate the images in app and the
        // SlideToUnlock library
        // TODO: this might be ugly, but set slide to unlock to only use the unlock_thumb and everytime we use it in our app, we have to set the image resource
        TextView textLabel = (TextView) findViewById(R.id.text_label);
        textLabel.setText("Start Touch-Screen Mode");
        textLabel.setTextSize(15);

        ImageView locked = (ImageView) findViewById(R.id.locked);
        ImageView unlocked = (ImageView) findViewById(R.id.unlocked);

        locked.setImageResource(R.drawable.unlock_right);
        unlocked.setImageResource(R.drawable.unlock_left);
    }

    private void setUpUnlockListener() {
        unlock = (UnlockBar) findViewById(R.id.unlock);

        unlock.setOnUnlockListener(new UnlockBar.OnUnlockListener() {
            @Override
            public void onUnlock()
            {
                unlock.reset();

                // return to maps or navigation activity
                if (activityMode.equals("Maps")) {
                    startMaps();
                } else if (activityMode.equals("Navigation")) {
                    startNavigation();
                }
            }
        });
    }

    public void startMaps() {
        transmitStop();
        loader.updateLoadingText("Starting Touch Screen Mode...");
        loader.enableLoading();
        destroyTts();
        destroySpeechRecognizer();

        Intent intent = new Intent(this, MapsActivity.class);
        Bundle bundle = new Bundle();

        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        bundle.putParcelable("headset_connected", BluetoothDeviceHDP);
        intent.putExtras(bundle);

        mRoute = null;

        startActivity(intent);
        finish();
    }

    public void startNavigation() {
        transmitStop();
        loader.updateLoadingText("Starting Touch-Screen Mode...");
        loader.enableLoading();
        destroyTts();
        destroySpeechRecognizer();

        Intent intent = new Intent(this, NavigationActivity.class);
        Bundle bundle = new Bundle();

        bundle.putSerializable("routes", (Serializable) mRoutes);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("step", (Serializable) mStep);
        intent.putExtras(bundle);

        bundle.putSerializable("tripStarted", (Serializable) tripStarted);
        intent.putExtras(bundle);



        bundle.putParcelable("headset_connected", BluetoothDeviceHDP);
        intent.putExtras(bundle);

        startActivity(intent);
        finish();
    }

    public void startTextToSpeechActivity() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_DATA_CODE);
    }

    private void destroyTts() {
        if(mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        ttsReady = false;
    }

    private void destroySpeechRecognizer() {
        if (speech != null) {
            speech.stopListening();
            speech.cancel();
            speech.destroy();
            speech = null;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ENABLE_BT_REQUEST_CODE) {

            // Bluetooth successfully enabled!
            if (resultCode == Activity.RESULT_OK) {

                checkPairedOrDoDiscovery();

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

        if (requestCode == TTS_DATA_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                mTts = new TextToSpeech(this, this);
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }


    private void drawMap() {
        mMap.clear(); // clear the map before drawing anything on it (mainly for redrawing)
        polylinePaths.clear();

        polylinePaths = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        for (Route route : mRoutes) {
            mRoute = route;

            // Note: route has a Coordinate instead of LatLng because LatLng is not serializable
            // but the map only takes LatLng
            LatLng startLocation = new LatLng(route.startLocation.latitude,
                    route.startLocation.longitude);
            LatLng endLocation = new LatLng(route.endLocation.latitude,
                    route.endLocation.longitude);

            if (mStep == 0 || mLastLocation == null) { // first step
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16));
            } else { // not first step
                updateMap();
            }

            updateInstruction(mRoute.steps.get(mStep).htmlInstruction);
            transmitVector();

            // Add Marker for destination
            destinationMarkers.add(mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.location_pin))
                    .title(route.endAddress)
                    .position(endLocation)));

            // Create the polyline
            PolylineOptions polylineOptions = new PolylineOptions().
                    geodesic(true).
                    color(Color.BLUE).
                    width(10);

            // Iterate through the route points to create the polyline
            for (int i = 0; i < route.points.size(); i++) {
                LatLng point = new LatLng(route.points.get(i).latitude,
                        route.points.get(i).longitude);
                polylineOptions.add(point);
            }

            polylinePaths.add(mMap.addPolyline(polylineOptions));
        }

        if (mRoute != null) {
            if (mStep == 0) {
                String speech = "Expected to arrive in " + mRoute.duration.text;
                tts(speech);
                tts(instruction.getText().toString());
            } else {
                tts(instruction.getText().toString());
            }
        }

        loader.disableLoading();
    }

    public void updateMap() {
        // redraw map with new coordinates
        LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
    }

    public void updateInstruction(String text) {
        if (text.length() > 80) {
            instruction.setTextSize(25);
        } else {
            instruction.setTextSize(35);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            instruction.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
        } else {
            instruction.setText(Html.fromHtml(text));
        }
    }

    private void onNextStep() {
        if (mStep < mRoute.steps.size() - 1) {
            mStep++;
            updateInstruction(mRoute.steps.get(mStep).htmlInstruction);
            tts(instruction.getText().toString());
            transmitVector();
        } else {
            updateInstruction("Arrived at destination");
            tts("You have reached your destination");
            finishTrip();
        }
    }

    public void finishTrip() {
        transmitFinish();
        tripStarted = false;
        mRoute = null;
        activityMode = "Maps";
        destination = "";
        origin = "Your Location";
        mRoutes.clear();

        if (DEBUG) ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
        if (DEBUG) ((TextView) findViewById(R.id.origin)).setText("Origin: " + origin); // for debugging
        if (DEBUG) ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
    }

    public void tts(String text) {
        if (!ttsReady) {
            ttsQueue.add(text);
            return;
        }

        if (myHashAlarm != null && mTts != null && TTSDEBUG) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            mTts.speak(text, TextToSpeech.QUEUE_ADD, myHashAlarm);
        }
    }

    public void transmitVector() {
        // uncomment when we actually test for reals - uncommented this haha
        if (mRoute != null) {
            double desired_theta = mRoute.calculateVector(mStep);
            String message = "#" + (float) desired_theta + "~";
            transmission(message);
        }
    }

    public void transmitFinish() {
        if (tripStarted) {
            String message = "#" + "Finish" + "~";
            transmission(message);
        }
    }

    public void transmitStop() {
        if (tripStarted) {
            String message = "#" + "Stop" + "~";
            transmission(message);
        }
    }

    public void transmission(String message) {
        byte[] vectorBytes = message.getBytes();

        Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
        intentBT.putExtra("vector", vectorBytes);
        startService(intentBT);
    }

    @SuppressWarnings("deprecation") // haha haha
    private void sendDirectionRequest() {
        if (origin == null || origin.equals("")) {
            tts("Starting location has not been set");
            return;
        }

        if (destination == null || destination.equals("")) {
            tts("Destination has not been set");
            return;
        }

        // because we want to retain origin and not change it to our current coordinates
        String mOrigin;
        if (origin.equals("Your Location")) {
            if(mLastLocation != null) {
                mOrigin = mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude();
            } else {
                tts("Please turn on your location");
                return;
            }
        } else {
            mOrigin = origin;
        }

        try {
            new DirectionFinder(this, mOrigin, destination,
                    transportationModes.get(mode)).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
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

    @SuppressWarnings("deprecation") // haha haha
    public void startVoiceRecognitionActivity() {
        attemptNumber = 1; // reset attempt number

        if(mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION){
            mAudioManager.setMode(0);
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.stopBluetoothSco();
        }
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000);
        speech.startListening(recognizerIntent);
    }

    public void onBackPressed() {
        // Override the onBackPressed() function: do nothing on back key
        // because we do not want to go back to Maps or Navigation Mode
        finish();
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
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                    mLocationRequest, this);
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
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

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

        if (activityMode.equals("Navigation")) {
            drawMap();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;

        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());

        if (mRoute != null) {
            // Recalculate if the user has started the trip but has drifted off the route
            if (tripStarted) {
                if (!mRoute.isLocationInPath(point) && RECALCULATION && !recalculating) {
                    recalculating = true;
                    recalculateRoute();
                    return;
                }

                if (mRoute.steps.get(mStep).stepCompleted(point)) {
                    onNextStep();
                }

                updateMap();
            } else if (!tripStarted && mRoute.steps.get(mStep).stepStarted(point)) {
                tripStarted = true;
            }
        }
    }

    private void recalculateRoute() {
        // Recalculate route with current location
        origin = "Your Location";
        sendDirectionRequest();
    }

    @Override
    public void onDirectionFinderStart() {
        loader.updateLoadingText("Finding direction...");
        loader.enableLoading();
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onDirectionFinderSuccess(List<Route> routes) {
        loader.disableLoading();

        if (routes.isEmpty()) {
            tts("Sorry, no route was found. Please try another search.");
            return;
        }

        mRoutes = routes;

        mStep = 0; // restart route

        drawMap();

        recalculating = false;

        if (mRoute != null) {
            activityMode = "Navigation";
            if (DEBUG) ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
        }
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            mTts.setLanguage(Locale.ENGLISH);
            myHashAlarm = new HashMap<String, String>();

            if(BluetoothDeviceHDP != null){
               myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                        String.valueOf(AudioManager.STREAM_VOICE_CALL));
              //  mAudioManager.setMode(AudioManager.MODE_CURRENT);
            } else {
                myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                        String.valueOf(AudioManager.STREAM_MUSIC));
            }

            tts("Voice Mode Activated");

            // tts all in the ttsQueue
            for (String text : ttsQueue ) {
                tts(text);
            }

            // clear the queue
            ttsQueue.clear();
        }

    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int error) {
        if (DEBUG) ((TextView) findViewById(R.id.matches)).setText("error: " + error); // for debugging
        if (error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == android.speech.SpeechRecognizer.ERROR_NO_MATCH) {

            if (DEBUG) ((TextView) findViewById(R.id.matches)).setText("error: " + error); // for debugging((TextView) findViewById(R.id.matches)).setText("error: " + error); // for debugging

            // Restart speech recognizer
            if (attemptNumber <= maxAttempts) {
                tts("Sorry, I didn't catch that, please repeat");

                attemptNumber++;

                // wait 3.5 seconds before trying again
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        if(speech != null) {
                            speech.startListening(recognizerIntent);
                        }
                    }
                }, 3700);
            } else {
                // gives up, you need to reinitialize the recognizer
                tts("Sorry, I didn't catch that, try again later");

                destroySpeechRecognizer();
            }
        }
    }

    @Override
    public void onResults(Bundle data) {
        ArrayList<String> matches = data.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);

        String match = matches.get(0); // best match from the matches

        if (DEBUG) ((TextView) findViewById(R.id.matches)).setText("matches: " + match); // for debugging

        if (activityMode.equals("Maps")) {
            if (match.contains("destination")) {
                // expecting 'Set destination to ____'
                String[] phrase = match.split(" to ");
                if(phrase.length == 2) {
                    destination = phrase[1];
                    tts("destination set to " + destination);
                    if (DEBUG) ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
                } else{
                    tts("No command found, make sure you don't skip any required phrase");
                }

            } else if (match.contains("walking")) {
                // expecting 'Set to walking'
                mode = 1;
                tts("mode set to walking");
                if (DEBUG) ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
            } else if (match.contains("public transit")) {
                // expecting 'Set to public transit'
                mode = 2;
                tts("mode set to public transit");
                if (DEBUG) ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
            } else if (match.contains("navigation")) {
                // expecting 'Start navigation' what if they say stop navigation
                if(match.contains("stop")){
                    tts("Navigation has not yet started, please start navigation first");
                } else {
                    sendDirectionRequest();
                }
            } else if (match.contains("touch screen")) {
                // expecting 'Activate Touch-Screen Mode'
                finish(); // return to previous intent
            } else if (match.contains("origin")) {
                // expecting 'Set origin to ____'
                // i.e. 'Set origin to my location'
                String[] phrase = match.split(" to ");
                if(phrase.length == 2) {
                    if (phrase[1].equals("my location")) {
                        origin = "Your Location";
                    } else {
                        origin = phrase[1];
                    }

                    tts("origin set to " + origin);
                    if (DEBUG)
                        ((TextView) findViewById(R.id.origin)).setText("Origin: " + origin); // for debugging
                } else {
                    tts("No command found, make sure you don't skip any required phrase");
                }
            } else {
                tts("No command found");
            }
        } else if (activityMode.equals("Navigation")){
            if (match.contains("repeat")) {
                // expecting 'repeat direction/instruction'
                tts(instruction.getText().toString());
            } else if (match.contains("stop") || match.contains("cancel")) {
                updateInstruction("");
                tts("Navigation stopped");
                finishTrip();
                drawMap();
            } else {
                tts("No command found");
            }
        }

        speech.destroy();
        speech = null;
    }

    @Override
    public void onPartialResults(Bundle bundle) {

    }

    @Override
    public void onEvent(int i, Bundle bundle) {

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

    @Override
    protected void onResume() {
        super.onResume();
        // Register the BroadcastReceiver for ACTION_FOUND
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(broadcastReceiver, filter);

    }

    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(broadcastReceiver);
    }
}
