package Modules;

import java.io.Serializable;

// Using custom Coordinate class because LatLng is not serializable

public class Coordinate implements Serializable {
    public double latitude;
    public double longitude;

    public Coordinate() {
        this.latitude = 0;
        this.longitude = 0;
    }

    public Coordinate(double lat, double lng) {
        this.latitude = lat;
        this.longitude = lng;
    }
}
