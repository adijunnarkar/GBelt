package Modules;

import java.io.Serializable;

public class Step implements Serializable {
    public Distance distance; // x km
    public Duration duration; // x mins
    public String htmlInstruction;// Turn xxx onto xxx Street
    public Coordinate lowerThreshold; // lat, lng
    public Coordinate startLocation; // lat, lng
    public Coordinate endLocation; // lat, lng
    public Coordinate upperThreshold; // lat, lng

    public Step(Distance distance, Duration duration, String htmlInstruction,
                Coordinate startLocation, Coordinate endLocation) {
        this.distance = distance;
        this.duration = duration;
        this.htmlInstruction = htmlInstruction;
        this.startLocation = startLocation;
        this.endLocation = endLocation;

        calculateThresholdLatLng();
    }

    public void calculateThresholdLatLng() {
        Coordinate latLng = this.endLocation;

        // current threshold is +- 4 m away from current location
        double dx = 0.004; // km
        double dy = 0.004; // km

        double r_earth = 6371; // km

        double lowerThresholdLat  = latLng.latitude  - (dy / r_earth) * (180 / Math.PI);
        double lowerThresholdLong = latLng.longitude - (dx / r_earth) * (180 / Math.PI) /
                Math.cos(latLng.latitude * Math.PI/180);

        double upperThresholdLat  = latLng.latitude  + (dy / r_earth) * (180 / Math.PI);
        double upperThresholdLong = latLng.longitude + (dx / r_earth) * (180 / Math.PI) /
                Math.cos(latLng.latitude * Math.PI/180);

        Coordinate lowerThreshold = new Coordinate(lowerThresholdLat, lowerThresholdLong);
        Coordinate upperThreshold = new Coordinate(upperThresholdLat, upperThresholdLong);

        this.lowerThreshold = lowerThreshold;
        this.upperThreshold = upperThreshold;
    }
}
