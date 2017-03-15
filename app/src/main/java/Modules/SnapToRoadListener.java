package Modules;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public interface SnapToRoadListener {
    void onSnapToRoadSuccess(List<LatLng> snappedPoints);
}
