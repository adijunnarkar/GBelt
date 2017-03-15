package Modules;

import android.os.AsyncTask;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class SnapToRoad {
    private static final String API_KEY = "AIzaSyBi7VkQHCcjkKUgBKKcapNDKjSEz-XsZwI";
    private static final String TAG = SnapToRoad.class.getSimpleName();
    private SnapToRoadListener listener;
    private LatLng start;
    private LatLng end;

    private static List<LatLng> snappedPoints;

    private static final int READ_TIME_OUT = 10000;
    private static final int CONNECT_TIME_OUT = 15000;
    private static final int TIME_OUT = 2000;

    private String tempURL = tempURL = "https://roads.googleapis.com/v1/snapToRoads?path=";
    private String FOOTER = "&interpolate=true&key=" + API_KEY;

    public SnapToRoad(SnapToRoadListener listener, LatLng start, LatLng end) {
        this.snappedPoints = new ArrayList<>();
        this.listener = listener;
        this.start = start;
        this.end = end;
        tempURL += start.latitude + "," + start.longitude + "|";
        tempURL += end.latitude + "," + end.longitude;
        tempURL += FOOTER;
    }

    public void execute() throws UnsupportedEncodingException {
        new DownloadRawData().execute(tempURL);
    }

    private class DownloadRawData extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String link = params[0];
            try {
                URL url = new URL(link);
                InputStream is = url.openConnection().getInputStream();
                StringBuffer buffer = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                return buffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                parseJSon(res);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseJSon(String data) throws JSONException {
        if (data == null)
            return;

        JSONObject jsonData = new JSONObject(data);
        JSONArray jsonSnappedPoints = jsonData.getJSONArray("snappedPoints");
        for (int i = 0; i < jsonSnappedPoints.length(); i++) {
            JSONObject jsonSnappedPoint = jsonSnappedPoints.getJSONObject(i);
            JSONObject jsonLocation = jsonSnappedPoint.getJSONObject("location");
            double latitude = jsonLocation.getDouble("latitude");
            double longitude = jsonLocation.getDouble("longitude");
            snappedPoints.add(new LatLng(latitude, longitude));
        }

        listener.onSnapToRoadSuccess(snappedPoints);
    }
}