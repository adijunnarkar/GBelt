package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
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

import Modules.Coordinate;
import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.LoadingScreen;
import Modules.Route;
import Modules.SnapToRoad;
import Modules.SnapToRoadListener;
import Modules.Threshold;

public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback,
        DirectionFinderListener,
        SnapToRoadListener,
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

    LoadingScreen loader;

    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

    private List<String> ttsQueue = new ArrayList<>();

    private RelativeLayout returnContent;
    private ImageView directionIndicator;
    private TextView instruction;

    private boolean tripStarted;
    private int mStep;
    private Route mRoute;
    private int mode;

    boolean ttsReady = false;
    boolean recalculating = false;

    String origin;
    String destination;

    UnlockBar unlock;

    // Snap to Road variables
    List<LatLng> mSnappedPoints = new ArrayList<>();
    int mSnappedPointIndex = 1;
    Threshold mSnappedPointThreshold;

    // Global variables across entire application used for debugging:
    boolean DEBUG;
    boolean TTSDEBUG;
    boolean RECALCULATION;

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

        loader.updateLoadingText("Loading, please wait...");
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
        RECALCULATION = ((MyApplication) this.getApplication()).getRecalculation();
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
        mSnappedPointIndex = (int) bundle.getSerializable("snappedPointIndex");
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
            public void onUnlock() {
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

        // for the case where trip has completed
        if (tripStarted) {
            bundle.putSerializable("activity", (Serializable) "Navigation");
            intent.putExtras(bundle);

            bundle.putSerializable("routes", (Serializable) mRoutes);
            intent.putExtras(bundle);

            bundle.putSerializable("step", (Serializable) mStep);
            intent.putExtras(bundle);
        } else {
            bundle.putSerializable("activity", (Serializable) "Maps");
            intent.putExtras(bundle);
        }

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("tripStarted", (Serializable) tripStarted);
        intent.putExtras(bundle);

        bundle.putSerializable("snappedPointIndex", (Serializable) mSnappedPointIndex);
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

        mRoute = null;

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
        if (mTts != null) {
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

            sendSnapToRoadRequest();

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

            TextView tvDuration = (TextView) findViewById(R.id.tvDuration);
            TextView tvDistance = (TextView) findViewById(R.id.tvDistance);

            tvDuration.setText(route.duration.text);
            tvDistance.setText(route.distance.text);

            updateInstruction(mRoute.steps.get(mStep).htmlInstruction);

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
                String speech = "Expected to arrive in " + mRoute.duration.text;
                tts(speech);
                tts(instruction.getText().toString());
            } else {
                tts(instruction.getText().toString());
            }
        }
    }

    private void updateInstruction(String text) {
        if (text.length() > 110) {
            instruction.setTextSize(18);
        } else {
            instruction.setTextSize(22);
        }

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
                if (!mRoute.isLocationInPath(point) && RECALCULATION && !recalculating) {
                    recalculating = true;
                    recalculateRoute();
                    return;
                }

                if (passedSnappedPoint(point)) {
                    onNextSnappedPoint();
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
        mSnappedPointIndex = 1;
        sendDirectionRequest();
    }

    public boolean passedSnappedPoint(LatLng point) {
        if (mSnappedPointThreshold == null)
            return false;

        return point.latitude > mSnappedPointThreshold.endLower.latitude
                && point.latitude < mSnappedPointThreshold.endUpper.latitude
                && point.longitude > mSnappedPointThreshold.endLower.longitude
                && point.longitude < mSnappedPointThreshold.endUpper.longitude;
    }

    public void onNextSnappedPoint() {
        if (!mSnappedPoints.isEmpty() && mSnappedPoints.size() > 4 &&
                mSnappedPointIndex < mSnappedPoints.size() - 1) {
            mSnappedPointIndex++;
            // Calculate threshold for next snapped point
            mSnappedPointThreshold = new Threshold(mSnappedPoints.get(mSnappedPointIndex - 1),
                    mSnappedPoints.get(mSnappedPointIndex));
            transmitVector();
        } else {
            onNextStep();
        }
    }

    public void sendSnapToRoadRequest() {
        mSnappedPoints.clear();

        // Starting location
        double x1 = mRoute.steps.get(mStep).startLocation.longitude;
        double y1 = mRoute.steps.get(mStep).startLocation.latitude;

        // Ending location
        double x2 = mRoute.steps.get(mStep).endLocation.longitude;
        double y2 = mRoute.steps.get(mStep).endLocation.latitude;

        LatLng start = new LatLng(y1, x1);
        LatLng end = new LatLng(y2, x2);

        try {
            new SnapToRoad(this, start, end).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void onNextStep() {
        if (mStep < mRoute.steps.size() - 1) {
            mStep++;
            mSnappedPointIndex = 1;
            updateInstruction(mRoute.steps.get(mStep).htmlInstruction);
            tts(instruction.getText().toString());
            sendSnapToRoadRequest();
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
        destination = "";
        origin = "Your Location";
    }

    public void transmitVector() {
        if (mRoute != null && !mSnappedPoints.isEmpty()) {
            double desired_theta = calculateVector();

            String message = "#" + (float) desired_theta + "~";
            transmission(message);
        }
    }

    public double calculateVector() {
        double vector;
        double x1, y1, x2, y2;

        if (!mSnappedPoints.isEmpty() && mSnappedPoints.size() > 4) {
            // Starting location
            x1 = mSnappedPoints.get(mSnappedPointIndex - 1).longitude;
            y1 = mSnappedPoints.get(mSnappedPointIndex - 1).latitude;

            // Ending location
            x2 = mSnappedPoints.get(mSnappedPointIndex).longitude;
            y2 = mSnappedPoints.get(mSnappedPointIndex).latitude;
        } else {
            Coordinate start = mRoute.steps.get(mStep).startLocation;
            Coordinate end = mRoute.steps.get(mStep).startLocation;

            // Starting location
            x1 = start.longitude;
            y1 = start.latitude;

            // Ending location
            x2 = end.longitude;
            y2 = end.latitude;
        }

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
        if (!ttsReady) {
            ttsQueue.add(text);
            return;
        }

        if (myHashAlarm != null && mTts != null && TTSDEBUG) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            mTts.speak(text, TextToSpeech.QUEUE_ADD, myHashAlarm);
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
            mTts.setLanguage(Locale.ENGLISH);
            myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));

            // tts all in the ttsQueue
            for (String text : ttsQueue ) {
                tts(text);
            }

            // clear the queue
            ttsQueue.clear();
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
        // Note: there is no check to see if a route is found, assumes that if they were able to
        // reach Navigation Activity a route must be available unless they leave their country
        // and lose the available route would be strange.

        mRoutes = route;

        mStep = 0; // restart route
        recalculating = false;

        drawMap();
    }

    @Override
    public void onSnapToRoadSuccess(List<LatLng> snappedPoints) {
        mSnappedPoints.addAll(snappedPoints); // append to mSnappedPoints

        if (!mSnappedPoints.isEmpty() && mSnappedPoints.size() > 4)
            mSnappedPointThreshold = new Threshold(mSnappedPoints.get(mSnappedPointIndex - 1),
                    mSnappedPoints.get(mSnappedPointIndex));
        else {
            Coordinate start = mRoute.steps.get(mStep).startLocation;
            Coordinate end = mRoute.steps.get(mStep).endLocation;

            mSnappedPointThreshold = new Threshold(new LatLng (start.latitude, start.longitude),
                    new LatLng (end.latitude, end.longitude));
        }

        transmitVector();

        LatLng lastPoint = mSnappedPoints.get(mSnappedPoints.size() - 1);

        // if the last point in the snap is not close to the end point of the step, then call
        // snap to road again
        if (!mRoute.steps.get(mStep).stepCompleted(lastPoint)) {
            double x2 = mRoute.steps.get(mStep).endLocation.longitude;
            double y2 = mRoute.steps.get(mStep).endLocation.latitude;
            LatLng endPoint = new LatLng(y2, x2);

            try {
                new SnapToRoad(this, lastPoint, endPoint).execute();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else {
            loader.disableLoading();
        }
    }
}
