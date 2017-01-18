package Modules;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.List;

public class Step implements Serializable {
    public Distance distance; // x km
    public Duration duration; // x mins
    public Coordinate endLocation; // lat, lng
    public String htmlInstruction;// Turn xxx onto xxx Street
    public Coordinate lowerThreshold; // lat, lng
    public Coordinate startLocation; // lat, lng
    public Coordinate upperThreshold; // lat, lng
}
