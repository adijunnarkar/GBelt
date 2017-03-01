package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
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
import com.google.android.gms.vision.text.Text;
import com.google.common.collect.ImmutableMap;
import com.hamondigital.unlock.UnlockBar;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

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
    ProgressDialog progressDialog;
    TextToSpeech mTts;
    HashMap<String, String> myHashAlarm;
    String utteranceId;
    TextView instruction;

    RelativeLayout activity;
    boolean mBooleanIsPressed;
    CircularProgressBar circularProgressBar;
    UnlockBar unlock;

    String activityMode; // Maps or Navigation

    // Maps mode
    int mode; // current transportation mode
    String origin;
    String destination;

    // Navigation mode
    List<Route> mRoutes;
    Route mRoute;
    int mStep = 0;

    int attemptNumber = 1;
    int maxAttempts = 2;

    // Drawing map
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_voice_mode);

        retrieveData();
        retrieveStates();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        checkRecordAudioPermission();

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

        setUpMap();

        startTextToSpeechActivity();

        // Find layout elements for future use
        instruction = ((TextView) findViewById(R.id.instruction));
        circularProgressBar = (CircularProgressBar)findViewById(R.id.progressBar);

        resetProgressBar();

        adjustUnlockBar();

        setUpTouchAndHoldTimer();

        setUpUnlockListener();
    }

    private void retrieveStates() {
        DEBUG = ((MyApplication) this.getApplication()).getDebug();
        TTSDEBUG = ((MyApplication) this.getApplication()).getTTS();
    }

    private void retrieveData() {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        activityMode = (String) bundle.getSerializable("activity");

        // Voice Mode is activated either from the Maps or Navigation activities
        // Each activity passes different bundles
        if (activityMode.equals("Maps")) {
            // grab data from MapsActivity
            activityMode = (String) bundle.getSerializable("activity");
            origin = (String) bundle.getSerializable("origin");
            destination = (String) bundle.getSerializable("destination");
            mode = (int) bundle.getSerializable("mode");

            if (DEBUG) ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
            if (DEBUG) ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
            if (DEBUG) ((TextView) findViewById(R.id.origin)).setText("Origin: " + origin); // for debugging
            if (DEBUG) ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
        } else if (activityMode.equals("Navigation")) {
            // grab data from NavigationActivity
            mRoutes = (List<Route>)bundle.getSerializable("routes");
            mode = (int) bundle.getSerializable("mode");
            mStep = (int) bundle.getSerializable("step");
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

        startActivity(intent);
    }

    public void startNavigation() {
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

        startActivity(intent);
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
        }
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

            updateInstruction();
            tts(instruction.getText().toString());
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
    }

    public void updateMap() {
        // redraw map with new coordinates
        LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
    }

    public void updateInstruction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            instruction.setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction,
                    Html.FROM_HTML_MODE_LEGACY));
        } else {
            instruction.setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction));
        }
    }

    private void onNextStep() {
        // TODO: only call this if it is not the last step
        mStep++;
        updateInstruction();
        tts(instruction.getText().toString());
        transmitVector();
    }

    public void tts(String text) {
        if (myHashAlarm != null && !TTSDEBUG) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        }
    }

    public void transmitVector() {
        // uncomment when we actually test for reals - uncommented haha
        double desired_theta = mRoute.calculateVector(mStep);
        String message = "#" + (float) desired_theta + "~";

        byte[] vectorBytes = message.getBytes();

        Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
        intentBT.putExtra("vector", vectorBytes);
        startService(intentBT);
    }

    @SuppressWarnings("deprecation") // haha haha
    private void sendDirectionRequest() {
        if (origin.equals("Your Location")) {
            origin = mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude();
        }

        if (origin.isEmpty()) {
            mTts.speak("Starting location has not been set", TextToSpeech.QUEUE_FLUSH, null);
            return;
        }
        if (destination.isEmpty()) {
            mTts.speak("Destination has not been set", TextToSpeech.QUEUE_FLUSH, null);
            return;
        }

        try {
            new DirectionFinder(this, origin, destination, transportationModes.get(mode)).execute();
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
        return;
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
            // Check if the user is still on the route
            if (!mRoute.isLocationInPath(point)) {
                recalculateRoute();
                return;
            }

            // Check if the user should move on to the next step
            if (point.latitude > mRoute.steps.get(mStep).lowerThreshold.latitude
                    && point.latitude < mRoute.steps.get(mStep).upperThreshold.latitude
                    && point.longitude > mRoute.steps.get(mStep).lowerThreshold.longitude
                    && point.longitude < mRoute.steps.get(mStep).upperThreshold.longitude) {
                onNextStep();
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
        progressDialog = ProgressDialog.show(this, "Please wait.",
                "Finding direction...", true);
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onDirectionFinderSuccess(List<Route> routes) {
        progressDialog.dismiss();

        mRoutes = routes;

        drawMap();

        if (mRoute != null) {
            activityMode = "Navigation";
            if (DEBUG) ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
        }
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {

                @Override
                public void onUtteranceCompleted(String s) {
                    utteranceId = s;
                }
            });

            mTts.setLanguage(Locale.ENGLISH);

            myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                    String.valueOf(AudioManager.STREAM_ALARM));

            tts("Voice Mode Activated");
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
                        speech.startListening(recognizerIntent);
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
                destination = phrase[1];
                tts("destination set to " + destination);
                if (DEBUG) ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
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
                // expecting 'Start navigation'
                sendDirectionRequest();
            } else if (match.contains("touch screen")) {
                // expecting 'Activate Touch-Screen Mode'
                finish(); // return to previous intent
            } else {
                tts("No commmand found");
            }

        } else if (activityMode.equals("Navigation")){
            if (match.contains("repeat")) {
                // expecting 'repeat direction/instruction'
                tts(instruction.getText().toString());
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

                        Intent intentBT = new Intent(VoiceModeActivity.this, BluetoothService.class);
                        Bundle b = new Bundle();
                        b.putParcelable("HC-05", BluetoothDeviceForHC05);
                        intentBT.putExtras(b);
                        startService(intentBT);

                        tts("Bluetooth established");
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
}
