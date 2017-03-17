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
    // First Key: AIzaSyBi7VkQHCcjkKUgBKKcapNDKjSEz-XsZwI
    // Second Key: AIzaSyCKkaeFF0jmNamkkew9FriLm5lXCX2Ds7A
    // Third key: AIzaSyAFWqZ8U8EMPhK6s-tSZvA6jfooTF6Y_Qk
    // Fourth key: AIzaSyDhf5EssS_WVMqWJw_Rw5U0ecBEIzT2o1w
    private static final String API_KEY = "AIzaSyCAH0fuLmYXS2FGEdLsgzYKUSjMXFOu8C0";
    private static final String TAG = SnapToRoad.class.getSimpleName();
    private SnapToRoadListener listener;
    private LatLng start;
    private LatLng end;

    private static List<LatLng> snappedPoints;

    private String tempURL = "https://roads.googleapis.com/v1/snapToRoads?path=";
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