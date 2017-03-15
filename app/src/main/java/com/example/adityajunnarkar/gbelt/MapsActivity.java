package com.example.adityajunnarkar.gbelt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.LoadingScreen;
import Modules.Route;

import com.hamondigital.unlock.UnlockBar;
import com.hamondigital.unlock.UnlockBar.OnUnlockListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    ImageButton btnSearch;
    ImageButton btnBus;
    ImageButton btnWalk;
    AutoCompleteTextView etOrigin;
    AutoCompleteTextView etDestination;
    ImageView deleteOrigin;
    ImageView deleteDestination;
    ImageView speakButton;
    ImageView locationButton;

    Route mRoute;
    Location mLastLocation;

    TextToSpeech mTts;
    HashMap<String, String> myHashAlarm;
    String utteranceId = "";

    UnlockBar unlock;
    LoadingScreen loader;

    // Voice Recognition Request Codes
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    public static final int TTS_DATA_CODE = 5678;

    public static final Map<Integer, String> transportationModes = ImmutableMap.of(
            1, "walking",
            2, "transit",
            3, "driving"
    );

    int mode = 1; // Default mode to walking

    // Global variables across entire application used for debugging:
    boolean DEBUG;
    boolean TTSDEBUG;

    //For setting up GET URL for Google Places API to retrieve predictions
    private static final String PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";
    private static final String TYPE_AUTOCOMPLETE = "/autocomplete";
    private static final String OUT_JSON = "/json";
    private static final String PLACES_API_KEY = "AIzaSyBitOAspRvs7YXu4xLf-cHMnTXFvqS_Sx8";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        startTextToSpeechActivity();

        // Retrieve form elements for later use
        etOrigin = (AutoCompleteTextView) findViewById(R.id.etOrigin);
        etDestination = (AutoCompleteTextView) findViewById(R.id.etDestination);

        setupAutoComplete();

        retrieveStates();

        retrieveData();

        setupUI(findViewById(R.id.activityContent));

        setUpLoadingSpinner();

        setUpVoiceRecognitionListener();

        setUpCurrentLocationListener();

        setUpStartNavigationListener();

        setUpTransitModeListeners();

        setUpUnlockListener();

        setUpBackSpaceListeners();
    }

    private void setupAutoComplete(){

        etOrigin.setAdapter(new GooglePlacesAutocompleteAdapter(this, R.layout.list_items));

        etOrigin.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get data associated with the specified position
                // in the list (AdapterView)
                hideSoftKeyboard(MapsActivity.this);
                updateOriginButtons();
            }
        });

        etOrigin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateOriginButtons();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        etDestination.setAdapter(new GooglePlacesAutocompleteAdapter(this, R.layout.list_items));

        etDestination.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get data associated with the specified position
                // in the list (AdapterView)
                hideSoftKeyboard(MapsActivity.this);
            }
        });

        etDestination.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateDestButtons();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        //search for directions when user selects the enter button on virtual keyboard
        etDestination.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (event == null) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        sendDirectionRequest();
                        // Let system handle all other null KeyEvents
                        return false;
                    }
                }
                return false;
            }
        });
    }

    private void setupUI(View view) {
        // Set up touch listener for non-text box views to hide keyboard.
        if (!(view instanceof AutoCompleteTextView)) {
            view.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    hideSoftKeyboard(MapsActivity.this);
                    updateOriginButtons();
                    updateDestButtons();
                    return false;
                }
            });
        }

        //If a layout container, iterate over children and seed recursion.
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View innerView = ((ViewGroup) view).getChildAt(i);
                setupUI(innerView);
            }
        }
    }

    private static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(
                activity.getCurrentFocus().getWindowToken(), 0);
    }

    private void updateOriginButtons() {
        String origin = etOrigin.getText().toString();

        if (deleteOrigin != null && locationButton!= null) {
            if (origin.isEmpty()) {
                deleteOrigin.setVisibility(View.GONE);
                locationButton.setVisibility(View.VISIBLE);
            } else {
                locationButton.setVisibility(View.GONE);
                deleteOrigin.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateDestButtons() {
        String destination = etDestination.getText().toString();

        if (deleteDestination != null && speakButton!= null) {
            if (destination.isEmpty()) {
                deleteDestination.setVisibility(View.GONE);
                speakButton.setVisibility(View.VISIBLE);
            } else {
                speakButton.setVisibility(View.GONE);
                deleteDestination.setVisibility(View.VISIBLE);
            }
        }
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

    private void setUpBackSpaceListeners() {
        deleteOrigin = (ImageView) findViewById(R.id.deleteOrigin);
        deleteDestination = (ImageView) findViewById(R.id.deleteDestination);

        updateOriginButtons();
        updateDestButtons();

        deleteOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etOrigin.setText("");
                updateOriginButtons();
            }
        });

        deleteDestination.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etDestination.setText("");
                updateDestButtons();
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

    class GooglePlacesAutocompleteAdapter extends ArrayAdapter implements Filterable {
        private ArrayList<String> resultList;

        public GooglePlacesAutocompleteAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public int getCount() {
            return resultList.size();
        }

        @Override
        public String getItem(int index) {
            return (String) resultList.get(index);
        }

        @Override
        public Filter getFilter() {
            Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        resultList = autocomplete(constraint.toString());

                        // Assign the data to the FilterResults
                        filterResults.values = resultList;
                        filterResults.count = resultList.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            };
            return filter;
        }

        private ArrayList autocomplete(String input) {
            ArrayList resultList = null;

            HttpURLConnection conn = null;
            StringBuilder jsonResults = new StringBuilder();
            try {
                StringBuilder sb = new StringBuilder(PLACES_API_BASE + TYPE_AUTOCOMPLETE + OUT_JSON);
                sb.append("?key=" + PLACES_API_KEY );
                sb.append("&components=country:ca");
                sb.append("&input=" + URLEncoder.encode(input, "utf8"));

                URL url = new URL(sb.toString());
                conn = (HttpURLConnection) url.openConnection();
                InputStreamReader in = new InputStreamReader(conn.getInputStream());

                // Load the results into a StringBuilder
                int read;
                char[] buff = new char[1024];
                while ((read = in.read(buff)) != -1) {
                    jsonResults.append(buff, 0, read);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return resultList;
            } catch (IOException e) {
                e.printStackTrace();
                return resultList;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            try {
                // Create a JSON object hierarchy from the results
                JSONObject jsonObj = new JSONObject(jsonResults.toString());
                JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

                // Extract the Place descriptions from the results
                resultList = new ArrayList(predsJsonArray.length());
                for (int i = 0; i < predsJsonArray.length(); i++) {
                    resultList.add(predsJsonArray.getJSONObject(i).getString("description"));
                }
            } catch (JSONException e) {
                //Log.e(LOG_TAG, "Cannot process JSON results", e);
            }

            return resultList;
        }
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
                updateOriginButtons();
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

        bundle.putSerializable("tripStarted", (Serializable) false);
        intent.putExtras(bundle);

        String origin = etOrigin.getText().toString();
        bundle.putSerializable("origin", (Serializable) origin);
        intent.putExtras(bundle);

        String destination = etDestination.getText().toString();
        bundle.putSerializable("destination", (Serializable) destination);
        intent.putExtras(bundle);

        bundle.putSerializable("snappedPointIndex", (Serializable) 1);
        intent.putExtras(bundle);

        startActivity(intent);
        finish();
    }

    public void startVoiceMode() {
        loader.updateLoadingText("Starting Voice Mode...");
        loader.enableLoading();
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

        bundle.putSerializable("tripStarted", (Serializable) false);
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
    }

    public void tts(String text) {
        if (myHashAlarm != null && TTSDEBUG) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        }
    }

    @SuppressWarnings("deprecation") // haha haha
    public void startVoiceRecognitionActivity() {
        String promptLocation = "Enter destination";
        tts(promptLocation);

        if(TTSDEBUG) {
            // wait until utterance is complete before opening speech intent
            while (!utteranceId.equals(promptLocation)) ;

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    "Speak now");
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        }
    }

    public void startTextToSpeechActivity() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_DATA_CODE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Fill the list view with the strings the recognizer thought it
            // could have heard
            ArrayList matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            etDestination.setText(matches.get(0).toString());
            updateDestButtons();
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
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
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

        if (origin != null && origin.equals("Your Location")) {
            if(mLastLocation != null) {
                origin = mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude();
            } else {
                Toast.makeText(this, "Your Location is not found! ", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (origin == null || origin.equals("")) {
            Toast.makeText(this, "Please enter origin address!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (destination == null || destination.equals("")) {
            Toast.makeText(this, "Please enter destination address!", Toast.LENGTH_SHORT).show();
            return;
        }

        loader.enableLoading();

        try {
            new DirectionFinder(this, origin, destination, transportationModes.get(mode)).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDirectionFinderStart() {
        loader.updateLoadingText("Finding direction...");
//        progressDialog = ProgressDialog.show(this, "Please wait.",
//                "Finding direction...", true);
    }

    @SuppressWarnings("deprecation") // haha haha
    @Override
    public void onDirectionFinderSuccess(List<Route> routes) {
        if (routes.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("No route found. Sorry, your search appears to be outside our " +
                    "current coverage area for " + transportationModes.get(mode) + ".")
                    .setCancelable(false)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            loader.disableLoading();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();

            return;
        }


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
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }
}
