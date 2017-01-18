package Modules;

import java.io.Serializable;
import java.util.List;

public class Route implements Serializable {
    public Distance distance;
    public Duration duration;
    public String endAddress;
    public Coordinate endLocation;
    public String startAddress;
    public Coordinate startLocation;
    public List<Step> steps;

    public List<Coordinate> points;
}
