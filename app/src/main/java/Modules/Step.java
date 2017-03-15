package Modules;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

public class Step implements Serializable {
    public Distance distance; // x km
    public Duration duration; // x mins
    public String htmlInstruction;// Turn xxx onto xxx Street
    public Coordinate startLocation; // lat, lng
    public Coordinate endLocation; // lat, lng
    public Coordinate startLowerThreshold; // lat, lng
    public Coordinate startUpperThreshold; // lat, lng

    // current threshold is +- 25 m away from current location
    public static final double dx = 0.025; // km
    public static final double dy = 0.025; // km

    public static final double rEarth = 6371; // km

    public Step(Distance distance, Duration duration, String htmlInstruction,
                Coordinate startLocation, Coordinate endLocation) {
        this.distance = distance;
        this.duration = duration;
        this.htmlInstruction = htmlInstruction;
        this.startLocation = startLocation;
        this.endLocation = endLocation;

        calculateThresholds();
    }

    public void calculateThresholds() {
        this.startLowerThreshold = calculateLowerThreshold(this.startLocation);
        this.startUpperThreshold = calculateUpperThreshold(this.startLocation);
    }

    public Coordinate calculateLowerThreshold(Coordinate latLng) {
        double lowerThresholdLat  = latLng.latitude  - (dy / rEarth) * (180 / Math.PI);
        double lowerThresholdLong = latLng.longitude - (dx / rEarth) * (180 / Math.PI) /
                Math.cos(latLng.latitude * Math.PI/180);

        return new Coordinate(lowerThresholdLat, lowerThresholdLong);
    }

    public Coordinate calculateUpperThreshold(Coordinate latLng) {
        double upperThresholdLat  = latLng.latitude  + (dy / rEarth) * (180 / Math.PI);
        double upperThresholdLong = latLng.longitude + (dx / rEarth) * (180 / Math.PI) /
                Math.cos(latLng.latitude * Math.PI/180);

        return new Coordinate(upperThresholdLat, upperThresholdLong);
    }

    public boolean stepStarted(LatLng point) {
        return point.latitude > startLowerThreshold.latitude
                && point.latitude < startUpperThreshold.latitude
                && point.longitude > startLowerThreshold.longitude
                && point.longitude < startUpperThreshold.longitude;
    }
}
