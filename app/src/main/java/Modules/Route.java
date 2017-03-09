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

    public double calculateVector(int stepNum) {
        double vector = 0;

        // Starting location
        double x1 = steps.get(stepNum).startLocation.longitude;
        double y1 = steps.get(stepNum).startLocation.latitude;

        // Ending location
        double x2 = steps.get(stepNum).endLocation.longitude;
        double y2 = steps.get(stepNum).endLocation.latitude;

        if (x2 >= x1 && y2 >= y1 ) {
            vector = Math.toDegrees(Math.atan(Math.abs(x2-x1)/Math.abs(y2-y1)));
        } else if (x2 > x1 && y2 < y1) {
            vector = 90.0 + Math.toDegrees(Math.atan(Math.abs(y2-y1)/Math.abs(x2-x1)));
        } else if (x2 < x1 && y2 < y1) {
            vector = 180.0 + Math.toDegrees(Math.atan(Math.abs(x2-x1)/Math.abs(y2-y1)));
        } else {
            vector = 270.0 + Math.toDegrees(Math.atan(Math.abs(y2-y1)/Math.abs(x2-x1)));
        }

        return vector;
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
        double tolerance = 20; // m

        return PolyUtil.isLocationOnEdge(location, pointsInLatLng, greatCircleSegments, tolerance);
    }
}
