import bc.MapLocation;
import bc.PlanetMap;

import java.util.Random;
import java.util.HashSet;
import java.util.Set;

class Util {
    static Random rand = new Random();

    //Do not allow creating instances of this class
    private Util() {}

    static int getRandomInt(int min, int max) {
        return rand.nextInt(1 + max - min) + min;
    }

    static Set<MapLocation> getInitialKarboniteLocations(PlanetMap planetMap) {
        Set<MapLocation> initialKarboniteLocations = new HashSet<>();
        MapLocation temp;
        for (int i = 0; i < planetMap.getWidth(); i++) {
            for (int j = 0; j < planetMap.getHeight(); j++) {
                temp = new MapLocation(planetMap.getPlanet(), i, j);
                if (planetMap.initialKarboniteAt(temp) > 0) {
                    initialKarboniteLocations.add(temp);
                }
            }
        }
        return initialKarboniteLocations;
    }

    static MapLocation getRandomValidLocation(PlanetMap planetMap) {
        MapLocation random;
        do {
            random = new MapLocation(planetMap.getPlanet(), getRandomInt(0, (int) planetMap.getWidth()), (int) planetMap.getHeight());
        } while (planetMap.isPassableTerrainAt(random) != 0);
        return random;
    }
}
