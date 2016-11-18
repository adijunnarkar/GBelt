package Modules;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;
import Modules.Step;

public class Route {
    public Distance distance;
    public Duration duration;
    public String endAddress;
    public LatLng endLocation;
    public String startAddress;
    public LatLng startLocation;
    public List<Step> steps;

    public List<LatLng> points;
}
