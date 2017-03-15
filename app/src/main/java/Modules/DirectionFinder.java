package Modules;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DirectionFinder implements Serializable {
    private static final String DIRECTION_URL_API = "https://maps.googleapis.com/maps/api/directions/json?";
    private static final String GOOGLE_API_KEY = "AIzaSyBi7VkQHCcjkKUgBKKcapNDKjSEz-XsZwI";
    private DirectionFinderListener listener;
    private String origin;
    private String destination;
    private String mode;

    public DirectionFinder(DirectionFinderListener listener, String origin, String destination, String mode) {
        this.listener = listener;
        this.origin = origin;
        this.destination = destination;
        this.mode = mode;
    }

    public void execute() throws UnsupportedEncodingException {
        listener.onDirectionFinderStart();
        new DownloadRawData().execute(createUrl());
    }

    private String createUrl() throws UnsupportedEncodingException {
        String urlOrigin = URLEncoder.encode(origin, "utf-8");
        String urlDestination = URLEncoder.encode(destination, "utf-8");
        String urlMode = URLEncoder.encode(mode, "utf-8");
        Long tsLong = System.currentTimeMillis()/1000;
        String departure_time = tsLong.toString();

        return DIRECTION_URL_API + "origin=" + urlOrigin + "&destination=" + urlDestination +
                "&departure_time=" + departure_time + "&key=" + GOOGLE_API_KEY + "&mode=" + urlMode;
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

        List<Route> routes = new ArrayList<Route>();
        JSONObject jsonData = new JSONObject(data);
        JSONArray jsonRoutes = jsonData.getJSONArray("routes");
        for (int i = 0; i < jsonRoutes.length(); i++) {
            JSONObject jsonRoute = jsonRoutes.getJSONObject(i);
            List<Step> steps = new ArrayList<Step>();

            JSONObject overview_polylineJson = jsonRoute.getJSONObject("overview_polyline");
            JSONArray jsonLegs = jsonRoute.getJSONArray("legs");
            JSONObject jsonLeg = jsonLegs.getJSONObject(0);
            JSONObject jsonDistance = jsonLeg.getJSONObject("distance");
            JSONObject jsonDuration = jsonLeg.getJSONObject("duration");
            JSONObject jsonEndLocation = jsonLeg.getJSONObject("end_location");
            JSONObject jsonStartLocation = jsonLeg.getJSONObject("start_location");
            JSONArray jsonSteps = jsonLeg.getJSONArray("steps");

            for (int j = 0; j < jsonSteps.length(); j++) {
                JSONObject jsonStep = jsonSteps.getJSONObject(j);

                JSONObject jsonStepDistance = jsonStep.getJSONObject("distance");
                JSONObject jsonStepDuration = jsonStep.getJSONObject("duration");
                JSONObject jsonStepEndLocation = jsonStep.getJSONObject("end_location");
                String jsonStepHtmlInstruction = jsonStep.getString("html_instructions");
                JSONObject jsonStepStartLocation = jsonStep.getJSONObject("start_location");

                Distance distance = new Distance(jsonStepDistance.getString("text"),
                        jsonStepDistance.getInt("value"));
                Duration duration = new Duration(jsonStepDuration.getString("text"),
                        jsonStepDuration.getInt("value"));
                Coordinate startLocation = new Coordinate(jsonStepStartLocation.getDouble("lat"),
                        jsonStepStartLocation.getDouble("lng"));
                Coordinate endLocation = new Coordinate(jsonStepEndLocation.getDouble("lat"),
                        jsonStepEndLocation.getDouble("lng"));

                Step step = new Step(distance, duration, jsonStepHtmlInstruction, startLocation,
                        endLocation);

                steps.add(step);
            }

            Distance distance = new Distance(jsonDistance.getString("text"), jsonDistance.getInt("value"));
            Duration duration = new Duration(jsonDuration.getString("text"), jsonDuration.getInt("value"));
            String startAddress = jsonLeg.getString("start_address");
            String endAddress = jsonLeg.getString("end_address");
            Coordinate startLocation = new Coordinate(jsonStartLocation.getDouble("lat"), jsonStartLocation.getDouble("lng"));
            Coordinate endLocation = new Coordinate(jsonEndLocation.getDouble("lat"), jsonEndLocation.getDouble("lng"));
            List<Coordinate> points = decodePolyLine(overview_polylineJson.getString("points"));

            Route route = new Route(distance, duration, startAddress, endAddress, startLocation,
                    endLocation, points, steps);

            routes.add(route);
        }

        listener.onDirectionFinderSuccess(routes);
    }

    private List<Coordinate> decodePolyLine(final String poly) {
        int len = poly.length();
        int index = 0;
        List<Coordinate> decoded = new ArrayList<Coordinate>();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = poly.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = poly.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            decoded.add(new Coordinate(
                    lat / 100000d, lng / 100000d
            ));
        }

        return decoded;
    }
}