package Modules;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class Step {
    public Distance distance; // x km
    public Duration duration; // x mins
    public LatLng endLocation; // lat, lng
    public String htmlInstruction;// Turn xxx onto xxx Street
    public LatLng lowerThreshold; // lat, lng
    public LatLng startLocation; // lat, lng
    public LatLng upperThreshold; // lat, lng
}
