package Modules;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Route implements Serializable {
    public Distance distance;
    public Duration duration;
    public String startAddress;
    public String endAddress;
    public Coordinate startLocation;
    public Coordinate endLocation;
    public List<Step> steps;
    public List<Coordinate> points;

    public Route(Distance distance, Duration duration, String startAddress, String endAddress,
                 Coordinate startLocation, Coordinate endLocation, List<Coordinate> points,
                 List<Step> steps) {
        this.distance = distance;
        this.duration = duration;
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.points = points;
        this.steps = steps;
    }

    public boolean isLocationInPath(LatLng location) {
        // LatLng is not Serializable, so we have to initialize it every time to use PolyUtil
        // which is why we made the Coordinate class to begin with
        List<LatLng> pointsInLatLng = new ArrayList<>();;
        for (int i = 0; i < points.size(); i++) {
            LatLng point = new LatLng(points.get(i).latitude, points.get(i).longitude);
            pointsInLatLng.add(point);
        }

        boolean greatCircleSegments = true;
        double tolerance = 35; // m

        return PolyUtil.isLocationOnEdge(location, pointsInLatLng, greatCircleSegments, tolerance);
    }
}
