package src;

import bc.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Ai {
    private GameController gc;
    private Map<UnitType, Set<Unit>> myUnits = new HashMap<>();
    private Map<UnitType, Set<Unit>> garrisonedUnits = new HashMap<>();
    private Map<UnitType, Set<Unit>> enemyUnits = new HashMap<>();
    private Map<Planet, PlanetMap> startingMaps = new HashMap<>();
    private Map<Planet, Map<MapLocation, Boolean>> passabilityMaps = new HashMap<>();
    private Map<Planet, Integer> passabilityCount = new HashMap<>();

    private Map<MapLocation, Long> karboniteMap = new HashMap<>();
    private Long round = null;
    private Long karbonite = null;
    private Planet planet = null;
    private ResearchInfo currentResearchInfo = null;
    private Integer msRemaining = null;
    private Team ourTeam = null;
    private Team theirTeam = null;
    private Map<Planet, MapAnalyzer> mapAnalyzers = null;

    //Put no logic here in the constructor, exceptions can't be handled this early
    public Ai(GameController gc) {
        this.gc = gc;
    }

    //initialization method that should only run on the first turn
    private void runOnce() {
        Util.reseedRandom(1337);
        planet = gc.planet();
        ourTeam = gc.team();
        theirTeam = ourTeam == Team.Red ? Team.Blue : Team.Red;
        if (planet == Planet.Earth) {
            karboniteMap = Util.getInitialKarboniteAmounts(gc.startingMap(Planet.Earth));
        }
        for (Planet planet : Planet.values()) {
            startingMaps.put(planet, gc.startingMap(planet));
            passabilityMaps.put(planet, new HashMap<>());
            passabilityCount.put(planet, 0);
            MapLocation temp;
            for (int i = 0; i < startingMaps.get(planet).getWidth(); i++) {
                for (int j = 0; j < startingMaps.get(planet).getHeight(); j++) {
                    temp = new MapLocation(planet, i, j);
                    if (startingMaps.get(planet).isPassableTerrainAt(temp) > 0) {
                        passabilityMaps.get(planet).put(temp, true);
                        passabilityCount.put(planet, passabilityCount.get(planet) + 1);
                    } else {
                        passabilityMaps.get(planet).put(temp, false);
                    }
                }
            }
        }
        round = gc.round();
        gc.queueResearch(UnitType.Worker);
        gc.queueResearch(UnitType.Rocket);
        gc.queueResearch(UnitType.Ranger);
        gc.queueResearch(UnitType.Ranger);
        gc.queueResearch(UnitType.Ranger);

        mapAnalyzers = new HashMap<>();
        for (Planet planet : Planet.values()) {
            mapAnalyzers.put(planet, new MapAnalyzer(passabilityMaps.get(planet)));
        }
        AStar aStar = new AStar(mapAnalyzers.get(planet));
        Util.reseedRandom(9001); // get the same set of random valid locations each time
        for (int i = 0; i < 5; i++) {
            aStar.path(getRandomValidLocation(planet), getRandomValidLocation(planet));
        }
    }

    //Set variables once per round to prevent excessive calls to gc
    private void initialize() throws Exception {
        if (round == null) {
            runOnce();
        }
        karbonite = gc.karbonite();
        myUnits.clear();
        garrisonedUnits.clear();
        enemyUnits.clear();
        currentResearchInfo = gc.researchInfo();
        msRemaining = gc.getTimeLeftMs();
        if (msRemaining < 500) {
            throw new Exception("Time running low! (" + msRemaining + "ms remaining)");
        }

        VecUnit myUnitsVc = gc.myUnits();
        for (UnitType unitType : UnitType.values()) {
            myUnits.put(unitType, new HashSet<>());
            garrisonedUnits.put(unitType, new HashSet<>());
            enemyUnits.put(unitType, new HashSet<>());
        }
        for (int i = 0; i < myUnitsVc.size(); i++) {
//            System.out.println("getMyUnits getting unit at index: " + i + " of type " + myUnitsVc.get(i).unitType());
            if (myUnitsVc.get(i).location().isInGarrison()) {
                garrisonedUnits.get(myUnitsVc.get(i).unitType()).add(myUnitsVc.get(i));
            } else if (myUnitsVc.get(i).location().isOnPlanet(planet)) {
                myUnits.get(myUnitsVc.get(i).unitType()).add(myUnitsVc.get(i));
            }
            if (planet == Planet.Mars && myUnitsVc.get(i).location().isOnPlanet(Planet.Mars)) {
                //sense karbonite and add to karboniteMap
                VecMapLocation sensedLocations = gc.allLocationsWithin(myUnitsVc.get(i).location().mapLocation(), myUnitsVc.get(i).visionRange());
                //TODO: (optimization) keep track of all sensedLocations and don't do anything for locations we already sensed this turn
//                Set<String> alreadyChecked = new HashSet<>();
                for (int mli = 0; mli < sensedLocations.size(); mli++) {
//                    if (!alreadyChecked.add(String.valueOf(sensedLocations.get(mli)))) {
//                        //skip this location if it has already been checked (add returns false if the value is already present in the Set)
//                        continue;
//                    }
                    Long karboniteAt = gc.karboniteAt(sensedLocations.get(mli));
                    if (karboniteAt > 0) {
                        karboniteMap.put(sensedLocations.get(mli), karboniteAt);
                    }
                }
            }
        }
        for (MapLocation karboniteLocation : karboniteMap.keySet()) {
            if (gc.canSenseLocation(karboniteLocation)) {
                karboniteMap.put(karboniteLocation, gc.karboniteAt(karboniteLocation));
            }
        }

        //TODO: set radius more intelligently, but still cover the whole map
        VecUnit enemyVecUnit = gc.senseNearbyUnitsByTeam(new MapLocation(planet, 0, 0), 1000, theirTeam);
        for (int i = 0; i < enemyVecUnit.size(); i++) {
            enemyUnits.get(enemyVecUnit.get(i).unitType()).add(enemyVecUnit.get(i));
        }
        System.out.println("Starting round " + round + ", " + msRemaining + "ms remaining");
//        System.out.println("Karbonite locations remaining: " + karboniteMap.keySet().size());
    }

    private void processUnit(Unit unit) {
        Integer unloadedCount;
        Integer garrisonedCount;
        switch (unit.unitType()) {
            //TODO: Make each unit type its own class
            case Worker:
                if (unit.workerHasActed() != 0) {
                    break;
                }
                if (myUnits.get(UnitType.Worker).size() < Math.max(7, Math.round(passabilityCount.get(planet) * .01))
                        && karbonite > bc.bcUnitTypeReplicateCost(UnitType.Worker)) {
                    for (Direction direction : Util.getDirections()) {
                        //System.out.println("Attempting to replicate: " + unit.id() + " at "
                        //        + unit.location().mapLocation() + " to " + direction.name());
                        if (gc.canReplicate(unit.id(), direction)) {
                            gc.replicate(unit.id(), direction);
                            break;
                        }
                    }
                }
                if (planet == Planet.Earth) {
                    Unit closestEnemy = findClosestEnemy(unit.location().mapLocation());
                    if (closestEnemy != null) {
                        Long distance = unit.location().mapLocation().distanceSquaredTo(closestEnemy.location().mapLocation());
                        Long closestEnemyAttackRange = 0L;
                        Direction directionToEnemy = unit.location().mapLocation().directionTo(closestEnemy.location().mapLocation());
                        if (!closestEnemy.unitType().equals(UnitType.Factory)
                                && !closestEnemy.unitType().equals(UnitType.Rocket)) {
                            closestEnemyAttackRange = closestEnemy.attackRange();
                        }
                        if (distance <= closestEnemyAttackRange + 4) {
                            if (!attemptToBuild(unit, UnitType.Rocket, false)) {
                                attemptToBuild(unit, UnitType.Factory, false);
                            }
                            if (moveTowards(unit, unit.location().mapLocation().add(bc.bcDirectionOpposite(directionToEnemy)))) {
                                break;
                            }
                        }
                    }

                    if (attemptToBuild(unit, UnitType.Rocket, true)) {
                        break;
                    }
                    if (attemptToBuild(unit, UnitType.Factory, true)) {
                        break;
                    }

                    UnitType wantedStructure = null;
                    if (currentResearchInfo.getLevel(UnitType.Rocket) != 0
                            && myUnits.get(UnitType.Rocket).size() == 0) {
                        wantedStructure = UnitType.Rocket;
                    } else if (myUnits.get(UnitType.Factory).size() < Math.max(8, Math.round(passabilityCount.get(planet) * .05))) {
                        wantedStructure = UnitType.Factory;
                    } /*else {
                            wantedStructure = (myUnits.get(UnitType.Factory).size() <= 2 * myUnits.get(UnitType.Rocket).size())
                                    ? UnitType.Factory : UnitType.Rocket;
                        }*/
                    if (wantedStructure != null) {
                        for (Direction direction : Util.getDirections()) {
                            if (karbonite < bc.bcUnitTypeBlueprintCost(wantedStructure)) {
                                break;
                            }
                            //System.out.println("Attempting blueprint: " + unit.id() + " at "
                            //        + unit.location().mapLocation() + " to " + direction.name());
                            if (gc.canBlueprint(unit.id(), wantedStructure, direction)) {
                                gc.blueprint(unit.id(), wantedStructure, direction);
                                break;
                            }
                        }
                    }
                } else if (attemptToBuild(unit, UnitType.Rocket, true)) {
                    break;
                }

                MapLocation closestKarbonite = findClosestKarbonite(unit.location().mapLocation());
                if (closestKarbonite != null) {
                    //System.out.println("Closest Karbonite to " + unit.location().mapLocation() + " at " + closestKarbonite);
                    if (gc.canSenseLocation(closestKarbonite)
                            && closestKarbonite.distanceSquaredTo(unit.location().mapLocation()) <= 2) {
                        Direction directionToClosestKarbonite = unit.location().mapLocation().directionTo(closestKarbonite);
//                        System.out.println("Attempting to harvest: " + unit.id() + " at " + unit.location().mapLocation()
//                                + " in direction " + directionToClosestKarbonite.name());
                        if (gc.canHarvest(unit.id(), directionToClosestKarbonite)) {
//                            System.out.println("Harvesting " + Math.min(unit.workerHarvestAmount(), karboniteMap.get(closestKarbonite))
//                                    + " from " + karboniteMap.get(closestKarbonite) + " karbonite at " + closestKarbonite);
                            gc.harvest(unit.id(), directionToClosestKarbonite);
                            karboniteMap.put(closestKarbonite, karboniteMap.get(closestKarbonite) - unit.workerHarvestAmount());
                            if (karboniteMap.get(closestKarbonite) <= 0) {
//                                System.out.println("Completely harvested karbonite from location: " + closestKarbonite);
                                karboniteMap.remove(closestKarbonite);
                            }
                            break;
                        }
                    } else if (moveTowards(unit, closestKarbonite)) {
                        break;
                    }
                }
                if (performMove(unit, Util.getRandomDirection())) {
                    break;
                }
                break;
            case Healer:
                break;
            case Ranger:
                Unit closestEnemy = findClosestEnemy(unit.location().mapLocation());
                if (closestEnemy != null) {
                    Direction approachDirection = unit.location().mapLocation().directionTo(closestEnemy.location().mapLocation());
                    Direction retreatDirection = bc.bcDirectionOpposite(approachDirection);
                    Long distance = unit.location().mapLocation().distanceSquaredTo(closestEnemy.location().mapLocation());
                    //consider preventing our attacks as an attack itself
                    Long closestEnemyAttackRange = unit.rangerCannotAttackRange();
                    if (!closestEnemy.unitType().equals(UnitType.Factory)
                            && !closestEnemy.unitType().equals(UnitType.Rocket)
                            && closestEnemy.attackRange() >= unit.rangerCannotAttackRange()) {
                        closestEnemyAttackRange = closestEnemy.attackRange();
                    }

                    if (unit.attackHeat() >= 10) {
                        //System.out.println("Ranger at " + unit.location().mapLocation() + ": kite, attack is on cooldown");
                        //try to stay just out of the enemy range
                        if (distance > closestEnemyAttackRange + 1) {
                            moveTowards(unit, unit.location().mapLocation().add(approachDirection));
                        } else if (distance < closestEnemyAttackRange + 1) {
                            moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                        }
                    } else if (distance <= unit.rangerCannotAttackRange()) {
//                        System.out.println("Ranger at " + unit.location().mapLocation() + ": retreat");
                        //retreat
                        moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                        //then attack if possible
//                        System.out.println("Ranger at " + unit.location().mapLocation() + ": then attack");
                        if (unit.location().mapLocation().add(retreatDirection).distanceSquaredTo(closestEnemy.location().mapLocation()) <= unit.attackRange()
                                && gc.canAttack(unit.id(), closestEnemy.id())) {
                            gc.attack(unit.id(), closestEnemy.id());
                        }
                    } else if (currentResearchInfo.getLevel(UnitType.Ranger) >= 3
                            && (closestEnemy.unitType() == UnitType.Factory || closestEnemy.unitType() == UnitType.Rocket)
                            && distance >= 4 * unit.attackRange() && currentResearchInfo.getLevel(UnitType.Ranger) >= 3) {
//                        System.out.println("Ranger at " + unit.location().mapLocation() + ": snipe");
                        //TODO: more intelligently determine when to snipe than a multiple of unit range
                        //snipe buildings from distance
                        if (unit.abilityHeat() < 10
                                && gc.isBeginSnipeReady(unit.id())
                                && gc.canBeginSnipe(unit.id(), closestEnemy.location().mapLocation())) {
                            gc.beginSnipe(unit.id(), closestEnemy.location().mapLocation());
                        }
                    } else if (distance <= unit.attackRange()) {
//                        System.out.println("Ranger at " + unit.location().mapLocation() + ": attack");
                        //attack
                        if (gc.canAttack(unit.id(), closestEnemy.id())) {
                            gc.attack(unit.id(), closestEnemy.id());
                        }
//                        System.out.println("Ranger at " + unit.location().mapLocation() + ": then retreat");
                        //then retreat
                        moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                    } else {
//                        System.out.println("Ranger at " + unit.location().mapLocation() + ": kite");
                        //try to stay just out of the enemy range
                        if (distance > closestEnemyAttackRange + 1) {
                            moveTowards(unit, unit.location().mapLocation().add(approachDirection));
                        } else if (distance < closestEnemyAttackRange + 1) {
                            moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                        }
                    }
                } else if (planet.equals(Planet.Earth) && !myUnits.get(UnitType.Rocket).isEmpty()) {
                    Unit targetRocket = findMyClosestUnit(unit.location().mapLocation(), UnitType.Rocket);
                    if (targetRocket != null) {
                        moveTowards(unit, targetRocket.location().mapLocation());
                    }
                } else {
                    //TODO: explore map more intelligently
                    performMove(unit, Util.getRandomDirection());
                }
                break;
            case Knight:
                break;
            case Mage:
                break;
            case Factory:
                if (unit.structureIsBuilt() == 0) {
                    break;
                }
                if (myUnits.get(UnitType.Worker).size() < 4
                        && karbonite > bc.bcUnitTypeFactoryCost(UnitType.Worker)
                        && unit.structureGarrison().size() < unit.structureMaxCapacity()
                        && gc.canProduceRobot(unit.id(), UnitType.Worker)) {
                    gc.produceRobot(unit.id(), UnitType.Worker);
                } else if (myUnits.get(UnitType.Ranger).size() < Math.round(passabilityCount.get(planet) * .5) + 1
                        && karbonite > bc.bcUnitTypeFactoryCost(UnitType.Ranger)
                        && gc.canProduceRobot(unit.id(), UnitType.Ranger)) {
                    //TODO: determine good scaling (above) for how much to outnumber them by
                        gc.produceRobot(unit.id(), UnitType.Ranger);
                }
                unloadedCount = 0;
                for (Direction direction : Util.getDirections()) {
                    if (unit.structureGarrison().size() - unloadedCount == 0) {
//                        if (round >= 750 - Math.max(startingMaps.get(planet).getWidth(), startingMaps.get(planet).getHeight())) {
//                            System.out.println("Disintegrating factory at: " + unit.location().mapLocation());
//                            gc.disintegrateUnit(unit.id());
//                        }
                        break;
                    }
                    if (gc.canUnload(unit.id(), direction)) {
                        gc.unload(unit.id(), direction);
                        Unit unloaded = gc.senseUnitAtLocation(unit.location().mapLocation().add(direction));
//                        myUnits.get(unloaded.unitType()).add(unloaded);
//                        garrisonedUnits.get(unloaded.unitType()).remove(unloaded);
                        processUnit(unloaded);
                        unloadedCount++;
                    }
                }
                break;
            case Rocket:
                if (unit.structureIsBuilt() == 0) {
                    break;
                }
                if (planet == Planet.Earth && unit.location().isOnPlanet(planet)) {
                    if (round == 749 || unit.health() < unit.maxHealth() * .25) {
                        System.out.println("Emergency Liftoff!");
                        garrisonedCount = 0;
                        for (Direction direction : Util.getDirections()) {
                            if (unit.structureGarrison().size() + garrisonedCount == unit.structureMaxCapacity()) {
                                break;
                            }
                            MapLocation adjacentLocation = unit.location().mapLocation().add(direction);
                            if (gc.hasUnitAtLocation(adjacentLocation)) {
                                Unit adjacentUnit = gc.senseUnitAtLocation(adjacentLocation);
                                if (!adjacentUnit.unitType().equals(UnitType.Factory)
                                        && !adjacentUnit.unitType().equals(UnitType.Rocket)
                                        && gc.canLoad(unit.id(), adjacentUnit.id())) {
                                    gc.load(unit.id(), adjacentUnit.id());
//                                    myUnits.get(unit.unitType()).remove(unit);
//                                    garrisonedUnits.get(unit.unitType()).add(unit);
                                    garrisonedCount++;
                                }
                            }
                        }
                        MapLocation target = getRandomValidLocation(Planet.Mars);
                        if (target != null && gc.canLaunchRocket(unit.id(), target)) {
                            System.out.println("Launching rocket from: " + unit.location().mapLocation() + " to " + target);
                            gc.launchRocket(unit.id(), target);
                        }
                    } else {
//                        System.out.println("Rocket is waiting for "
//                                + (unit.structureMaxCapacity() - unit.structureGarrison().size())
//                                + " more units until launch");
                        garrisonedCount = 0;
                        for (Direction direction : Util.getDirections()) {
                            if (unit.structureGarrison().size() + garrisonedCount == unit.structureMaxCapacity()) {
                                MapLocation target = getRandomValidLocation(Planet.Mars);
                                if (target != null && gc.canLaunchRocket(unit.id(), target)) {
                                    System.out.println("Launching rocket from: " + unit.location().mapLocation() + " to " + target);
                                    gc.launchRocket(unit.id(), target);
                                }
                                break;
                            }
                            MapLocation adjacentLocation = unit.location().mapLocation().add(direction);
                            if (gc.hasUnitAtLocation(adjacentLocation)) {
                                Unit adjacentUnit = gc.senseUnitAtLocation(adjacentLocation);
                                if (!adjacentUnit.unitType().equals(UnitType.Factory)
                                        && !adjacentUnit.unitType().equals(UnitType.Rocket)
                                        && gc.canLoad(unit.id(), adjacentUnit.id())) {
                                    gc.load(unit.id(), adjacentUnit.id());
//                                    myUnits.get(unit.unitType()).remove(unit);
//                                    garrisonedUnits.get(unit.unitType()).add(unit);
                                    garrisonedCount++;
                                }
                            }
                        }
                    }
                } else {
                    unloadedCount = 0;
                    for (Direction direction : Util.getDirections()) {
                        if (unit.structureGarrison().size() - unloadedCount == 0) {
//                            if (myUnitCount > 0) {
//                                System.out.println("Disintegrating Rocket at: " + unit.location().mapLocation());
//                                gc.disintegrateUnit(unit.id());
//                            }
                            break;
                        }
                        if (gc.canUnload(unit.id(), direction)) {
                            gc.unload(unit.id(), direction);
                            Unit unloaded = gc.senseUnitAtLocation(unit.location().mapLocation().add(direction));
//                        myUnits.get(unloaded.unitType()).add(unloaded);
//                        garrisonedUnits.get(unloaded.unitType()).remove(unloaded);
                            processUnit(unloaded);
                            unloadedCount++;
                        }
                    }
                }
                break;
        }
    }

    //Called each turn by Player.java
    public void run() throws Exception {
        try {
            initialize();

            //I prefer processing unit types in reverse order: Rocket, Factory, Healer, Mage, Ranger, Knight, Worker
            for (int i = UnitType.values().length - 1; i >= 0; i--) {
                for (Unit unit : myUnits.get(UnitType.values()[i])) {
                    processUnit(unit);
                }
            }
        } finally {
//            System.out.println("Ending of round: " + round + " with " + msRemaining + "ms remaining");
            round++;
        }
    }

    //Get all of my units of a particular type
    private boolean attemptToBuild(Unit unit, UnitType unitType, Boolean allowMove) throws IllegalArgumentException {
        if (unitType != UnitType.Factory && unitType != UnitType.Rocket) {
            throw new IllegalArgumentException(unitType.name() + " can not be built");
        }
        if (unit.unitType() != UnitType.Worker) {
            throw new IllegalArgumentException("Unit " + unit.id() + " is not a worker and cannot build");
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
//        System.out.println("attemptToBuild called with " + unitType.name() + ": " + unit.id() + " to build a " + unitType.name());

        Unit closestStructure = findMyClosestDamagedOrBuildingStructure(unit.location().mapLocation());
        if (closestStructure != null) {
            if (closestStructure.structureIsBuilt() == 0 && gc.canBuild(unit.id(), closestStructure.id())) {
                gc.build(unit.id(), closestStructure.id());
                return true;
            } else if (closestStructure.structureIsBuilt() > 0 && gc.canRepair(unit.id(), closestStructure.id())) {
                gc.repair(unit.id(), closestStructure.id());
                return true;
            } else if (allowMove && moveTowards(unit, closestStructure.location().mapLocation())) {
                return true;
            }
        }
        return false;
    }

    //Move a unit toward the goal location
    private boolean moveTowards(Unit unit, MapLocation goal) throws IllegalArgumentException {
        if (unit.movementHeat() != 0) {
            return false;
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
        if (!mapAnalyzers.get(goal.getPlanet()).areLocationsConnected(unit.location().mapLocation(), goal)) {
//            System.out.println("Cannot move from " + unit.location().mapLocation()
//                    + " to " + goal + " because there is no path");
            return false;
        }
        //System.out.println("Attempting to moveTowards: " + unit.id() + " at "
        //        + unit.location().mapLocation() + " to " + goal);
        if (unit.location().mapLocation().getPlanet() != goal.getPlanet()) {
            throw new IllegalArgumentException("Cannot path from "
                    + unit.location().mapLocation().getPlanet().name() + " to " + goal.getPlanet().name());
        }
        if (unit.unitType() == UnitType.Factory || unit.unitType() == UnitType.Rocket) {
            throw new IllegalArgumentException("Unit " + unit.id() + " of type " + unit.unitType().name() + " cannot move");
        }
        Direction heading = unit.location().mapLocation().directionTo(goal);
        return performMove(unit, heading)
                || performMove(unit, bc.bcDirectionRotateRight(heading))
                || performMove(unit, bc.bcDirectionRotateRight(bc.bcDirectionRotateRight(heading)))
                || performMove(unit, bc.bcDirectionRotateLeft(heading))
                || performMove(unit, bc.bcDirectionRotateLeft(bc.bcDirectionRotateLeft(heading)));
    }

    //move a unit in the given direction
    private boolean performMove(Unit unit, Direction heading) {
        if (unit.movementHeat() != 0) {
            return false;
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
        if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), heading)) {
            gc.moveRobot(unit.id(), heading);
            //System.out.println("performMove result: " + unit.id() + " at "
            //        + unit.location().mapLocation() + " moved " + heading.name());
            return true;
        }
        return false;
    }

    //M. find closest karbonite deposit to location
    private MapLocation findClosestKarbonite(MapLocation startLocation) {
        MapLocation closest = null;
        Long distance = null;
        for (MapLocation aKarboniteLocation : karboniteMap.keySet()) {
            if (distance == null || startLocation.distanceSquaredTo(aKarboniteLocation) < distance) {
                closest = aKarboniteLocation;
                distance = startLocation.distanceSquaredTo(aKarboniteLocation);
            }
            if (distance == 0) {
                break;
            }
        }
//        if (gc.canSenseLocation(closest)) {
//            System.out.println("Find closest Karbonite returning: " + closest + " with " + gc.karboniteAt(closest) + " karbonite");
//        } else {
//            System.out.println("Find closest Karbonite returning out of sight range location: " + closest);
//        }
        return closest;
    }

    //ind closest unit deposit to location
    private Unit findClosestEnemy(MapLocation startLocation) {
        Unit closest = null;
        Long distance = null;
        for (UnitType unitType : UnitType.values()) {
            for (Unit unit : enemyUnits.get(unitType)) {
                if (distance == null || startLocation.distanceSquaredTo(unit.location().mapLocation()) < distance) {
                    closest = unit;
                    distance = startLocation.distanceSquaredTo(unit.location().mapLocation());
                }
                if (distance == 0) {
                    break;
                }
            }
        }
//        if (closest == null) {
//            System.out.println("No close enemies found");
//        } else {
//            System.out.println("Find closest enemy returning a " + closest.unitType() + " at " + closest.location().mapLocation());
//        }
        return closest;
    }

    private Unit findMyClosestUnit(MapLocation startLocation, UnitType searchType) {
        Unit closest = null;
        Long distance = null;
        for (Unit unit : myUnits.get(searchType)) {
            if (distance == null || startLocation.distanceSquaredTo(unit.location().mapLocation()) < distance) {
                closest = unit;
                distance = startLocation.distanceSquaredTo(unit.location().mapLocation());
            }
            if (distance == 0) {
                break;
            }
        }
        //System.out.println("Find my closest unit " + searchType.name() + " returning: " + closest);
        return closest;
    }

    private Unit findMyClosestDamagedOrBuildingStructure(MapLocation startLocation) {
        Unit closest = null;
        Long distance = null;
        for (UnitType unitType : Arrays.asList(UnitType.Rocket, UnitType.Factory)) {
            for (Unit structure : myUnits.get(unitType)) {
                if (distance == null || startLocation.distanceSquaredTo(structure.location().mapLocation()) < distance) {
                    if (structure.structureIsBuilt() == 0 || structure.health() < structure.maxHealth()) {
                        closest = structure;
                        distance = startLocation.distanceSquaredTo(structure.location().mapLocation());
                        if (distance == 0 || startLocation.isAdjacentTo(structure.location().mapLocation())) {
                            break;
                        }
                    }
                }
            }
        }
        //System.out.println("Find my closest damaged or building structure returning: " + closest);
        return closest;
    }

    //Get a random passable location from the PlanetMap
    private MapLocation getRandomValidLocation(Planet planet) {
        List<MapLocation> passableMapLocations = new LinkedList<>();
        for (MapLocation mapLocation : passabilityMaps.get(planet).keySet()) {
            if (passabilityMaps.get(planet).get(mapLocation)) {
                passableMapLocations.add(mapLocation);
            }
        }
        //noinspection UnnecessaryLocalVariable
        MapLocation randomValid = passableMapLocations.get(Util.getRandomInt(passableMapLocations.size()) - 1);
//        System.out.println("Returning valid random location: " + randomValid);
        return randomValid;
    }
}
