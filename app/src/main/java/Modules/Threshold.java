package Modules;

import com.google.android.gms.maps.model.LatLng;

public class Threshold  {
    public LatLng startLower;
    public LatLng startUpper;
    public LatLng endLower;
    public LatLng endUpper;

    // current threshold is +- 20 m away from current location
    public static final double dx = 0.020; // km
    public static final double dy = 0.020; // km
    public static final double rEarth = 6371; // km

    public Threshold(LatLng start, LatLng end) {
        this.startLower = calculateLowerThreshold(start);
        this.startUpper = calculateUpperThreshold(start);
        this.endLower = calculateLowerThreshold(end);
        this.endUpper = calculateUpperThreshold(end);
    }

    public LatLng calculateLowerThreshold(LatLng latLng) {
        double thresholdLat  = latLng.latitude  - (dy / rEarth) * (180 / Math.PI);
        double thresholdLong = latLng.longitude - (dx / rEarth) * (180 / Math.PI) /
                Math.cos(latLng.latitude * Math.PI/180);

        return new LatLng(thresholdLat, thresholdLong);
    }

    public LatLng calculateUpperThreshold(LatLng latLng) {
        double thresholdLat  = latLng.latitude  + (dy / rEarth) * (180 / Math.PI);
        double thresholdLong = latLng.longitude + (dx / rEarth) * (180 / Math.PI) /
                Math.cos(latLng.latitude * Math.PI/180);

        return new LatLng(thresholdLat, thresholdLong);
    }
}