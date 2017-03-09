package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.text.Html;

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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.LoadingScreen;
import Modules.Route;

public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback,
        DirectionFinderListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        Serializable,
        LocationListener,
        OnInitListener {

    public static final Map<Integer, String> transportationModes = ImmutableMap.of(
            1, "walking",
            2, "transit",
            3, "driving"
    );

    private GoogleMap mMap;
    private List<Route> mRoutes;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    public static final int TTS_DATA_CODE = 1234;

    TextToSpeech mTts;
    HashMap<String, String> myHashAlarm;
    String utteranceId = "";

    LoadingScreen loader;

    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

    private RelativeLayout returnContent;
    private ImageView directionIndicator;
    private TextView instruction;

    private boolean tripStarted;
    private int mStep;
    private Route mRoute;
    private int mode;

    boolean ttsReady = false;

    String origin;
    String destination;

    UnlockBar unlock;

    // Global variables across entire application used for debugging:
    boolean DEBUG;
    boolean TTSDEBUG;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_navigation);

        retrieveStates();
        retrieveData();

        startTextToSpeechActivity();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Show walking or transit icon
        if (mode == 1) { // walking
            ((ImageView) findViewById(R.id.bus)).setVisibility(View.GONE);
        } else if (mode == 2) { // transit
            ((ImageView) findViewById(R.id.walk)).setVisibility(View.GONE);
        }

        setUpLoadingSpinner();

        setUpDirectionsListener();

        setUpReturnListener();

        setUpUnlockListener();

        loader.enableLoading();
    }

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
    }

    private void retrieveData() {
        // grab data from MapsActivity and VoiceModeActivity
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        mRoutes = (List<Route>)bundle.getSerializable("routes");
        mode = (int) bundle.getSerializable("mode");
        origin = (String) bundle.getSerializable("origin");
        destination = (String) bundle.getSerializable("destination");
        mStep = (int) bundle.getSerializable("step");
        tripStarted = (boolean) bundle.getSerializable("tripStarted");
    }

    private void setUpDirectionsListener() {
        directionIndicator = (ImageView) findViewById(R.id.directionIndicator);
        directionIndicator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createDirectionsActivity();
            }
        });

        instruction = (TextView) findViewById(R.id.instruction);
        instruction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createDirectionsActivity();
            }
        });
    }


    private void setUpReturnListener() {
        returnContent = (RelativeLayout) findViewById(R.id.returnContent);
        returnContent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void setUpUnlockListener() {
        unlock = (UnlockBar) findViewById(R.id.unlock);

        unlock.setOnUnlockListener(new UnlockBar.OnUnlockListener() {
            @Override
            public void onUnlock()
            {
                unlock.reset();
                startVoiceMode(); // switch to voice mode => starts voice mode intent
            }
        });
    }

    public void startVoiceMode() {
        transmitStop();
        loader.updateLoadingText("Starting Voice Mode...");
        loader.enableLoading();
        destroyTts();

        Intent intent = new Intent(this, VoiceModeActivity.class);
        Bundle bundle = new Bundle(); // pass bundle to voice mode activity

        bundle.putSerializable("activity", (Serializable) "Navigation");
        intent.putExtras(bundle);

        bundle.putSerializable("routes", (Serializable) mRoutes);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("step", (Serializable) mStep);
        intent.putExtras(bundle);

        bundle.putSerializable("tripStarted", (Serializable) tripStarted);
        intent.putExtras(bundle);

        startActivity(intent);
        finish();
    }

    public void createDirectionsActivity() {
        loader.updateLoadingText("Loading...");
        loader.enableLoading();

        Intent intent = new Intent(this, DirectionsActivity.class);
        Bundle bundle = new Bundle();

        bundle.putSerializable("route", (Serializable) mRoute);
        intent.putExtras(bundle);

        startActivity(intent);
        loader.disableLoading();
    }

    public void onBackPressed() {
        transmitStop();
        loader.updateLoadingText("");
        loader.enableLoading();
        destroyTts();

        mRoute= null;

        // should only ever go back to Maps Activity even if it returned from voice mode
        Intent intent = new Intent(this, MapsActivity.class);
        Bundle bundle = new Bundle(); // pass bundle to voice mode activity

        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        startActivity(intent);
        finish();
    }

    private void destroyTts() {
        if(mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        ttsReady = false;
    }

    private void updateMap() {
        if (mLastLocation != null) {
            LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
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
            LatLng startLocation = new LatLng(route.startLocation.latitude, route.startLocation.longitude);
            LatLng endLocation = new LatLng(route.endLocation.latitude, route.endLocation.longitude);

            if (mStep == 0 || mLastLocation == null) { // first step
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16));
            } else { // not first step
                updateMap();
            }

            TextView tvDuration = (TextView) findViewById(R.id.tvDuration);
            TextView tvDistance = (TextView) findViewById(R.id.tvDistance);

            tvDuration.setText(route.duration.text);
            tvDistance.setText(route.distance.text);

            updateInstruction(mRoute.steps.get(mStep).htmlInstruction);
            transmitVector();

            // Add Markers for origin and destination
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


        if (mRoute != null) {
            if (mStep == 0) {
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        // wait 1 seconds to make sure tts is initialized
                        String speech = "Expected to arrive in " + mRoute.duration.text;
                        tts(speech);
                        // wait until utterance is complete before other tts's
                        // need the while before tts
                        while (!utteranceId.equals(speech)) ;
                        tts(instruction.getText().toString());
                    }
                }, 2000);
            } else {
                tts(instruction.getText().toString());
            }
        }

        loader.disableLoading();
    }

    private void updateInstruction(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            instruction.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
        } else {
            instruction.setText(Html.fromHtml(text));
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;

        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());

        if (mRoute != null) {
            // Recalculate if the user has started the trip but has drifted off the route
            if (tripStarted) {
                if (!mRoute.isLocationInPath(point) && false) {
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

    public void onNextStep() {
        if (mStep < mRoute.steps.size() - 1) {
            mStep++;
            updateInstruction(mRoute.steps.get(mStep).htmlInstruction);
            tts(instruction.getText().toString());
            transmitVector();
        } else {
            updateInstruction("Arrived at destination");
            tts("You have reached your destination");
            tripStarted = false; // cause trip has ended
            transmitStop();
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

    public void transmitStop() {
        String message = "#" + "Stop" + "~";
        transmission(message);
    }

    public void transmission(String message) {
        byte[] vectorBytes = message.getBytes();

        Intent intentBT = new Intent(NavigationActivity.this, BluetoothService.class);
        intentBT.putExtra("vector", vectorBytes);
        startService(intentBT);
    }

    public void startTextToSpeechActivity() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_DATA_CODE);
    }

    public void tts(String text) {
        while(!ttsReady);
        if (myHashAlarm != null && mTts!= null && TTSDEBUG) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        }
    }

    @SuppressWarnings("deprecation") // haha haha
    private void sendDirectionRequest() {
        if (origin == null || origin.equals("")) {
            tts("Starting location has not been set");
            return;
        }

        // because we want to retain origin and not change it to our current coordinates
        String mOrigin;
        if (origin.equals("Your Location")) {
            mOrigin = mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude();
        } else {
            mOrigin = origin;
        }

        try {
            new DirectionFinder(this, mOrigin, destination, transportationModes.get(mode)).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        drawMap();
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
    public void onConnected(@Nullable Bundle bundle) {
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
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            mTts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {

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

    @Override
    public void onDirectionFinderStart() {
        loader.updateLoadingText("Recalculating...");
        loader.enableLoading();

        tts("Recalculating");
    }

    @Override
    public void onDirectionFinderSuccess(List<Route> route) {
        loader.disableLoading();
        mRoutes = route;

        mStep = 0; // restart route

        drawMap();
    }
}
