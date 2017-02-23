package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Modules.ConnectedThread;
import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.Route;

public class VoiceModeActivity extends AppCompatActivity implements OnMapReadyCallback,
        DirectionFinderListener,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        TextToSpeech.OnInitListener {

    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    public static final int TTS_DATA_CODE = 5678;

    public static final Map<Integer, String> transportationModes = ImmutableMap.of(
            1, "walking",
            2, "transit",
            3, "driving"
    );

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

    ConnectedThread connectedThread;

    String activityMode; // Maps or Navigation

    // Maps mode
    int mode; // current transportation mode
    String origin;
    String destination;

    // Navigation mode
    List<Route> mRoutes;
    Route mRoute;
    int mStep = 0;

    // Drawing map
    private List<Marker> originMarkers = new ArrayList<>();
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

    // for Timer to switch to touch screen mode
    int timerDuration = 3000; // ms, timer completion time
    int timerTimeout = 200; // ms, call run every 100 ms
    int timerCount = 0; // ms, keep count of the timer

    // animation of circular progress bar
    int animationDuration = 300; // 100ms = 0.1s
    int resetAnimationDuration = 2000; // reset slowly

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_voice_mode);

        // grab data from MapsActivity
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        this.activityMode = (String) bundle.getSerializable("activity");

        if (this.activityMode.equals("Maps")) {
            // grab data from MapsActivity
            this.connectedThread = (ConnectedThread) bundle.getSerializable("connectedThread");
            this.activityMode = (String) bundle.getSerializable("activity");
            this.origin = (String) bundle.getSerializable("origin");
            this.destination = (String) bundle.getSerializable("destination");
            this.mode = (int) bundle.getSerializable("mode");

            ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
            ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
            ((TextView) findViewById(R.id.origin)).setText("Origin: " + origin); // for debugging
            ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
        } else if (this.activityMode.equals("Navigation")) {
            // grab data from NavigationActivity
            this.connectedThread = (ConnectedThread) bundle.getSerializable("connectedThread");
            this.mRoutes = (List<Route>)bundle.getSerializable("routes");
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getView().setAlpha((float) 0.3);
        mapFragment.getMapAsync(this);

        startTextToSpeechActivity();

        // Find layout elements
        instruction = ((TextView) findViewById(R.id.instruction));

        // Find circular progress bar and set to 0
        circularProgressBar = (CircularProgressBar)findViewById(R.id.progressBar);
        circularProgressBar.setProgressWithAnimation(0, animationDuration);

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
                        circularProgressBar.setProgressWithAnimation(percentage, animationDuration); // Default duration = 1500ms
                        handler.postDelayed(runnable, timerTimeout); // keep calling handler every timerTimeout

                        if (percentage == 100) { // timer has completed
                            mBooleanIsPressed = false;
                            timerCount = 0; // reset timer back to 0
                            circularProgressBar.setProgressWithAnimation(0, animationDuration);
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
                        circularProgressBar.setProgressWithAnimation(0, resetAnimationDuration); // reset progress bar
                        handler.removeCallbacks(runnable);
                    }
                    return false;
                }
                return true;
            }
        });
    }

    public void startTextToSpeechActivity() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_DATA_CODE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Fill the list view with the strings the recognizer thought it
                // could have heard
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                String match = matches.get(0); // best match from the matches

                if (activityMode.equals("Maps")) {
                    if (match.contains("destination")) {
                        // expecting 'Set destination to ____'
                        String[] phrase = match.split(" to ");
                        destination = phrase[1];
                        ((TextView) findViewById(R.id.destination)).setText("Destination: " + destination); // for debugging
                    } else if (match.contains("walking")) {
                        // expecting 'Set to walking'
                        mode = 1;
                        ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
                    } else if (match.contains("public transit")) {
                        // expecting 'Set to public transit'
                        mode = 2;
                        ((TextView) findViewById(R.id.mode)).setText("Mode: " + transportationModes.get(mode)); // for debugging
                    } else if (match.contains("navigation")) {
                        // expecting 'Start navigation'
                        sendDirectionRequest();
                    }
                } else if (activityMode.equals("Navigation")){
                    if (match.contains("repeat")) {
                        // expecting 'repeat direction/instruction'
                        tts(instruction.getText().toString());
                    }
                }

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
        originMarkers = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        for (Route route : mRoutes) {
            mRoute = route;

            // Note: route has a Coordinate instead of LatLng because LatLng is not serializable
            // but the map only takes LatLng
            LatLng startLocation = new LatLng(route.startLocation.latitude, route.startLocation.longitude);
            LatLng endLocation = new LatLng(route.endLocation.latitude, route.endLocation.longitude);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16));

            updateInstruction();

            // Add Markers for origin and destination
//            originMarkers.add(mMap.addMarker(new MarkerOptions()
//                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.location_pin))
//                    .title(route.startAddress)
//                    .position(startLocation)));
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
                LatLng point = new LatLng(route.points.get(i).latitude, route.points.get(i).longitude);
                polylineOptions.add(point);
            }

            polylinePaths.add(mMap.addPolyline(polylineOptions));
        }
    }

    public void updateInstruction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            instruction.setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction, Html.FROM_HTML_MODE_LEGACY));
        } else {
            instruction.setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction));
        }

        tts(instruction.getText().toString());
    }

    private void onNextStep() {
        mStep++;
        updateInstruction();
    }

    public void tts(String speech) {
        if (myHashAlarm != null) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, speech);
            mTts.speak(speech, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        }
    }

    public void transmitVector() {
        double desired_theta = calculateVector();
        String message = "#" + (float) desired_theta + "~";

        byte[] vectorBytes = message.getBytes();

        if (connectedThread != null) { // && connectedThread.isAlive()
            connectedThread.write(vectorBytes);
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
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    public void onBackPressed() {
        // Override the onBackPressed() function: do nothing on back key
        // because we do not want to go back to Maps or Navigation Mode
        return;
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
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }

        // find last known location, and move map to location
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);

        if (mLastLocation != null) {
            LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
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

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (mRoute != null) {
            if (latLng.latitude > mRoute.steps.get(mStep).lowerThreshold.latitude
                    && latLng.latitude < mRoute.steps.get(mStep).upperThreshold.latitude
                    && latLng.longitude > mRoute.steps.get(mStep).lowerThreshold.longitude
                    && latLng.longitude < mRoute.steps.get(mStep).upperThreshold.longitude) {
                onNextStep();
            }
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

        mRoutes = routes;

        drawMap();

        if (mRoute != null) {
            activityMode = "Navigation";
            ((TextView) findViewById(R.id.activity)).setText("Activity Mode: " + activityMode); // for debugging
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
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));

            tts("Voice Mode Activated");
        }

    }
}
