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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Modules.Route;


public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        Serializable,
        LocationListener,
        OnInitListener {

    Map<Integer, String> transportationModes = ImmutableMap.of(
            1, "walking",
            2, "transit",
            3, "driving"
    );

    private GoogleMap mMap;
    private List<Route> routes;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    LocationRequest mLocationRequest;

    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    public static final int TTS_DATA_CODE = 5678;

    TextToSpeech mTts;
    HashMap<String, String> myHashAlarm;
    String utteranceId;

    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

    private ImageView directionIndicator;
    private TextView instruction;

    private int mStep;
    private Route mRoute;
    private int mode;

    String origin;
    String destination;

    UnlockBar unlock;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_navigation);

        startTextToSpeechActivity();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // grab data from MapsActivity and VoiceModeActivity
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        routes = (List<Route>)bundle.getSerializable("routes");
        mode = (int) bundle.getSerializable("mode");
        origin = (String) bundle.getSerializable("origin");
        destination = (String) bundle.getSerializable("destination");
        mStep = (int) bundle.getSerializable("step");

        // Show walking or transit icon
        if (mode == 1) { // walking
            ((ImageView) findViewById(R.id.bus)).setVisibility(View.GONE);
        } else if (mode == 2) { // transit
            ((ImageView) findViewById(R.id.walk)).setVisibility(View.GONE);
        }

        setUpDirectionsListener();

        setUpUnlockListener();
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
        destroyTts();

        Intent intent = new Intent(this, VoiceModeActivity.class);
        Bundle bundle = new Bundle(); // pass bundle to voice mode activity

        bundle.putSerializable("activity", (Serializable) "Navigation");
        intent.putExtras(bundle);

        bundle.putSerializable("routes", (Serializable) routes);
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) mode);
        intent.putExtras(bundle);

        bundle.putSerializable("step", (Serializable) mStep);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    public void onBackPressed() {
        destroyTts();

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
    }

    private void destroyTts() {
        if(mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }

    private void drawMap() {
        polylinePaths = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        for (Route route : routes) {
            mRoute = route;

            // Note: route has a Coordinate instead of LatLng because LatLng is not serializable
            // but the map only takes LatLng
            LatLng startLocation = new LatLng(route.startLocation.latitude, route.startLocation.longitude);
            LatLng endLocation = new LatLng(route.endLocation.latitude, route.endLocation.longitude);

            if (mStep == 0) { // first step
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16));
            } else { // not first step
                LatLng currLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16));
            }


            ((TextView) findViewById(R.id.tvDuration)).setText(route.duration.text);
            ((TextView) findViewById(R.id.tvDistance)).setText(route.distance.text);

            updateInstruction();
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
    }

    private void updateInstruction() {
        // Display the first direction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            instruction.setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction, Html.FROM_HTML_MODE_LEGACY));
        } else {
            instruction.setText(Html.fromHtml(mRoute.steps.get(mStep).htmlInstruction));
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

//        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

        if (mRoute != null) {
            if (latLng.latitude > mRoute.steps.get(mStep).lowerThreshold.latitude
                    && latLng.latitude < mRoute.steps.get(mStep).upperThreshold.latitude
                    && latLng.longitude > mRoute.steps.get(mStep).lowerThreshold.longitude
                    && latLng.longitude < mRoute.steps.get(mStep).upperThreshold.longitude) {
                onNextStep();
            }
        }
    }

    public void onNextStep() {
        // TODO: only call this if it is not the last step
        mStep++;
        updateInstruction();
        tts(instruction.getText().toString());
        transmitVector();
    }

    public void transmitVector() {
        // uncomment when we actually test for reals - uncommented this haha
        double desired_theta = calculateVector();
        String message = "#" + (float) desired_theta + "~";

        byte[] vectorBytes = message.getBytes();

        Intent intentBT = new Intent(NavigationActivity.this, BluetoothService.class);
        intentBT.putExtra("vector", vectorBytes);
        startService(intentBT);

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

    public void createDirectionsActivity() {
        destroyTts();
        Intent intent = new Intent(this, DirectionsActivity.class);

        Bundle bundle = new Bundle();

        bundle.putSerializable("route", (Serializable) mRoute);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    public void startTextToSpeechActivity() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_DATA_CODE);
    }

    public void tts(String speech) {
        if (myHashAlarm != null) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, speech);
            mTts.speak(speech, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
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
            mTts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {

                @Override
                public void onUtteranceCompleted(String s) {
                    utteranceId = s;
                }
            });

            mTts.setLanguage(Locale.ENGLISH);

            myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));

            // TODO: hmmmm...this is a bad place to put this, make this nicer
            tts(instruction.getText().toString());
        }
    }
}
