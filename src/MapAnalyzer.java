package src;

import bc.MapLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapAnalyzer {
    private Map<MapLocation, Boolean> passabilityMap;
    private Map<String, MapNode> connectivityMap = new HashMap<>();
    private Integer distinctAreaCount = 0;

    public MapAnalyzer(Map<MapLocation, Boolean> passabilityMap) {
        this.passabilityMap = passabilityMap;
        generateConnectivityMap();
    }

    public Integer getDistinctAreaCount() {
        return distinctAreaCount;
    }

    public boolean isPassable(MapLocation location) {
        return getConnectivity(location) != null;
    }

    public boolean areLocationsConnected(MapLocation locationA, MapLocation locationB) {
        Integer connectivityIdA = getConnectivity(locationA);
        Integer connectivityIdB = getConnectivity(locationB);
        return connectivityIdA != null && connectivityIdB != null && connectivityIdA.equals(connectivityIdB);
    }

    //private, internal methods and classes below

    private Map<String, MapNode> generateConnectivityMap() {
        distinctAreaCount = 0;
        for (MapLocation location : passabilityMap.keySet()) {
            if (passabilityMap.get(location)) {
                connectivityMap.put(String.valueOf(location), new MapNode());
            }
        }
        for (MapLocation unprocessedLocation : passabilityMap.keySet()) {
            if (hasNode(unprocessedLocation) && getConnectivity(unprocessedLocation) == null) {
                distinctAreaCount++;
                //flood fill, adding locations to connectivityMap with connectivityId
                Set<MapLocation> open = new HashSet<>(Collections.singletonList(unprocessedLocation));
                MapLocation currentLocation;
                while (!open.isEmpty()) {
                    open.remove(currentLocation = open.iterator().next());
                    connectivityMap.get(String.valueOf(currentLocation)).setConnectivityId(distinctAreaCount);
//                    System.out.println(currentLocation + " (current) has Connectivity: " + distinctAreaCount);
                    for (MapLocation neighbor : Util.getNeighbors(currentLocation)) {
                        if (hasNode(neighbor) && getConnectivity(neighbor) == null) {
                            open.add(neighbor);
                            connectivityMap.get(String.valueOf(neighbor)).setConnectivityId(distinctAreaCount);
//                            System.out.println(neighbor + " (neighbor) has Connectivity: " + distinctAreaCount);
                        }
                    }
                }
            }
        }
        System.out.println("Distinct area count: " + distinctAreaCount);
        return connectivityMap;
    }

    private MapNode getNode(MapLocation location) {
        return location == null ? null : connectivityMap.get(String.valueOf(location));
    }

    private Boolean hasNode(MapLocation location) {
        return getNode(location) != null;
    }

    private Integer getConnectivity(MapLocation location) {
        return hasNode(location) ? getNode(location).getConnectivityId() : null;
    }

    private class MapNode {
        private Integer connectivityId = null;

        Integer getConnectivityId() {
            return connectivityId;
        }

        void setConnectivityId(Integer connectivityId) {
            this.connectivityId = connectivityId;
        }
    }
}
