package src;

import bc.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Ai {
    private GameController gc;
    private Map<UnitType, Set<Unit>> myUnits = new HashMap<>();
    private Map<UnitType, Set<Unit>> garrisonedUnits = new HashMap<>();
    private Map<UnitType, Set<Unit>> enemyUnits = new HashMap<>();
    private Long enemyUnitCount = 0L;
    private Map<Planet, PlanetMap> startingMaps = new HashMap<>();
    private Map<Planet, Map<MapLocation, Boolean>> passabilityMaps = new HashMap<>();

    private Set<MapLocation> karboniteLocations = null;
    private Long round = null;
    private Long karbonite = null;
    private Planet planet = null;
    private ResearchInfo currentResearchInfo = null;
    private Integer msRemaining = null;
    private Team ourTeam = null;
    private Team theirTeam = null;

    //Put no logic here in the constructor, exceptions can't be handled this early
    public Ai(GameController gc) {
        this.gc = gc;
    }

    //initialization method that should only run on the first turn
    private void runOnce() {
        ourTeam = gc.team();
        theirTeam = ourTeam == Team.Red ? Team.Blue : Team.Red;
        karboniteLocations = Util.getInitialKarboniteLocations(gc.startingMap(Planet.Earth));
        for (Planet planet : Planet.values()) {
            startingMaps.put(planet, gc.startingMap(planet));
            passabilityMaps.put(planet, new HashMap<>());
            MapLocation temp;
            for (int i = 0; i < startingMaps.get(planet).getWidth(); i++) {
                for (int j = 0; j < startingMaps.get(planet).getHeight(); j++) {
                    temp = new MapLocation(planet, i, j);
                    passabilityMaps.get(planet).put(temp, startingMaps.get(planet).isPassableTerrainAt(temp) > 0);
                }
            }
        }
        round = gc.round();
        gc.queueResearch(UnitType.Rocket);
        gc.queueResearch(UnitType.Rocket);
//        gc.queueResearch(UnitType.Rocket);
    }

    //Set variables once per round to prevent excessive calls to gc
    private void initialize() {
        if (round == null) {
            runOnce();
        }
        karbonite = gc.karbonite();
        planet = gc.planet();
        myUnits.clear();
        garrisonedUnits.clear();
        enemyUnits.clear();

        VecUnit myUnitsVc = gc.myUnits();
        for (UnitType unitType : UnitType.values()) {
            myUnits.put(unitType, new HashSet<>());
            garrisonedUnits.put(unitType, new HashSet<>());
        }
        for (int i = 0; i < myUnitsVc.size(); i++) {
//            System.out.println("getMyUnits getting unit at index: " + i + " of type " + myUnitsVc.get(i).unitType());
            if (myUnitsVc.get(i).location().isInGarrison()) {
                garrisonedUnits.get(myUnitsVc.get(i).unitType()).add(myUnitsVc.get(i));
            } else if (myUnitsVc.get(i).location().isOnPlanet(planet)) {
                myUnits.get(myUnitsVc.get(i).unitType()).add(myUnitsVc.get(i));
            }
        }

        //TODO: set radius more intelligently, but still cover the whole map
        VecUnit enemyVecUnit = gc.senseNearbyUnitsByTeam(new MapLocation(planet, 0, 0), 1000, theirTeam);
        enemyUnitCount = enemyVecUnit.size();
        for (int i = 0; i < enemyVecUnit.size(); i++) {
            enemyUnits.get(enemyVecUnit.get(i).unitType()).add(enemyVecUnit.get(i));
        }
        currentResearchInfo = gc.researchInfo();
        msRemaining = gc.getTimeLeftMs();
        System.out.println("Current round: " + round + " with " + msRemaining + "ms remaining");
        System.out.println("Karbonite locations remaining: " + karboniteLocations.size());
    }

    private void processUnit(Unit unit) {
        switch (unit.unitType()) {
            //TODO: Make each unit type its own class
            case Worker:
                if (!unit.location().isOnPlanet(planet)) {
                    break;
                }
                if (unit.workerHasActed() != 0) {
                    break;
                }

                UnitType wantedStructure = null;
                if (currentResearchInfo.getLevel(UnitType.Rocket) == 0) {
                    wantedStructure = UnitType.Factory;
                } else if (karboniteLocations.isEmpty() && myUnits.get(UnitType.Rocket).size() == 0) {
                    wantedStructure = UnitType.Rocket;
                } /*else {
                        wantedStructure = (myUnits.get(UnitType.Factory).size() <= 2 * myUnits.get(UnitType.Rocket).size())
                                ? UnitType.Factory : UnitType.Rocket;
                    }*/
                if (wantedStructure != null) {
                    for (Direction direction : Util.getDirections()) {
                        if (karbonite < bc.bcUnitTypeBlueprintCost(wantedStructure)) {
                            break;
                        }
                        System.out.println("Attempting blueprint: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + direction.name());
                        if (gc.canBlueprint(unit.id(), wantedStructure, direction)) {
                            gc.blueprint(unit.id(), wantedStructure, direction);
                            break;
                        }
                    }
                }
                if (unit.workerHasActed() != 0) {
                    break;
                }
                if (attemptToBuild(unit, UnitType.Rocket)) {
                    break;
                }
                if (attemptToBuild(unit, UnitType.Factory)) {
                    break;
                }
                if (myUnits.get(UnitType.Worker).size() < 6 && karbonite > bc.bcUnitTypeReplicateCost(UnitType.Worker)) {
                    for (Direction direction : Util.getDirections()) {
                        System.out.println("Attempting to replicate: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + direction.name());
                        if (gc.canReplicate(unit.id(), direction)) {
                            gc.replicate(unit.id(), direction);
                            break;
                        }
                    }
                }
                MapLocation closestKarbonite = findClosestKarbonite(unit.location().mapLocation());
                if (closestKarbonite != null) {
                    System.out.println("Closest Karbonite to " + unit.location().mapLocation() + " at " + closestKarbonite);
                    if (closestKarbonite.distanceSquaredTo(unit.location().mapLocation()) <= 2) {
                        Direction directionToClosestKarbonite = unit.location().mapLocation().directionTo(closestKarbonite);
                        System.out.println("Attempting to harvest: " + unit.id() + " at " + unit.location().mapLocation()
                                + " in direction " + directionToClosestKarbonite.name());
                        if (gc.canHarvest(unit.id(), directionToClosestKarbonite)) {
                            System.out.println("Actually harvest " + unit.workerHarvestAmount()
                                    + " from " + gc.karboniteAt(closestKarbonite) + " karbonite");
                            gc.harvest(unit.id(), directionToClosestKarbonite);
                            if (gc.karboniteAt(closestKarbonite) <= unit.workerHarvestAmount()) {
                                System.out.println("Completely harvested karbonite from location: " + closestKarbonite);
                                karboniteLocations.remove(closestKarbonite);
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
                    if (distance <= unit.rangerCannotAttackRange()) {
                        //retreat
                        moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                        //then attack
                        if (distance <= unit.attackRange() && gc.canAttack(unit.id(), closestEnemy.id())) {
                            gc.attack(unit.id(), closestEnemy.id());
                        }
                    } else if ((closestEnemy.unitType() == UnitType.Factory || closestEnemy.unitType() == UnitType.Rocket)
                            && distance <= 4 * unit.attackRange() && currentResearchInfo.getLevel(UnitType.Ranger) >= 3) {
                        //TODO: more intelligently determine when to snipe than a multiple of unit range
                        //snipe buildings from distance
                        if (gc.isBeginSnipeReady(unit.id()) && gc.canBeginSnipe(unit.id(), closestEnemy.location().mapLocation())) {
                            gc.beginSnipe(unit.id(), closestEnemy.location().mapLocation());
                        }
                    } else if (distance <= unit.attackRange()) {
                        //attack
                        if (gc.canAttack(unit.id(), closestEnemy.id())) {
                            gc.attack(unit.id(), closestEnemy.id());
                        }
                        //then retreat
                        moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                    } else if (distance <= closestEnemy.attackRange()) {
                        //retreat
                        moveTowards(unit, unit.location().mapLocation().add(retreatDirection));
                    } else {
                        //approach
                        moveTowards(unit, unit.location().mapLocation().add(approachDirection));
                    }
                } else if (planet.equals(Planet.Earth) && !myUnits.get(UnitType.Rocket).isEmpty()) {
                    Unit targetRocket = findMyClosestRocket(unit.location().mapLocation());
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
                //TODO: determine good scaling for how much to outnumber them by
                if (myUnits.get(UnitType.Ranger).size() < Math.max(4, Math.round(1.5 * enemyUnitCount))
                        && karbonite > bc.bcUnitTypeFactoryCost(UnitType.Ranger)) {
                    if (gc.canProduceRobot(unit.id(), UnitType.Ranger)) {
                        gc.produceRobot(unit.id(), UnitType.Ranger);
                    }
                    for (Direction direction : Util.getDirections()) {
                        if (unit.structureGarrison().size() == 0) {
                            break;
                        }
                        if (gc.canUnload(unit.id(), direction)) {
                            gc.unload(unit.id(), direction);
                            Unit unloaded = gc.senseUnitAtLocation(unit.location().mapLocation().add(direction));
                            myUnits.get(unloaded.unitType()).add(unloaded);
                            garrisonedUnits.get(unloaded.unitType()).remove(unloaded);
                            processUnit(unloaded);
                        }
                    }
                }
                break;
            case Rocket:
                if (planet == Planet.Earth && unit.location().isOnPlanet(planet)) {
                    if (round == 749) {
                        System.out.println("Emergency Liftoff!");
                        for (Direction direction : Util.getDirections()) {
                            if (unit.structureGarrison().size() == unit.structureMaxCapacity()) {
                                break;
                            }
                            MapLocation adjacentLocation = unit.location().mapLocation().add(direction);
                            if (gc.hasUnitAtLocation(adjacentLocation)) {
                                Unit adjacentUnit = gc.senseUnitAtLocation(adjacentLocation);
                                if (gc.canLoad(unit.id(), adjacentUnit.id())) {
                                    gc.load(unit.id(), adjacentUnit.id());
                                    myUnits.get(unit.unitType()).remove(unit);
                                    garrisonedUnits.get(unit.unitType()).add(unit);
                                }
                            }
                        }
                        MapLocation target = getRandomValidLocation(startingMaps.get(Planet.Mars));
                        if (gc.canLaunchRocket(unit.id(), target)) {
                            System.out.println("Launching rocket from: " + unit.location().mapLocation() + " to " + target);
                            gc.launchRocket(unit.id(), target);
                        }
                    } else if (unit.structureGarrison().size() == unit.structureMaxCapacity()) {
                        System.out.println("Rocket is full at: " + unit.location().mapLocation());
                        MapLocation target = getRandomValidLocation(startingMaps.get(Planet.Mars));
                        if (gc.canLaunchRocket(unit.id(), target)) {
                            System.out.println("Launching rocket from: " + unit.location().mapLocation() + " to " + target);
                            gc.launchRocket(unit.id(), target);
                        }
                    } else {
                        System.out.println("Rocket is waiting for "
                                + (unit.structureMaxCapacity() - unit.structureGarrison().size())
                                + " more units until launch");
                        for (Direction direction : Util.getDirections()) {
                            if (unit.structureGarrison().size() == unit.structureMaxCapacity()) {
                                break;
                            }
                            MapLocation adjacentLocation = unit.location().mapLocation().add(direction);
                            if (gc.hasUnitAtLocation(adjacentLocation)) {
                                Unit adjacentUnit = gc.senseUnitAtLocation(adjacentLocation);
                                if (gc.canLoad(unit.id(), adjacentUnit.id())) {
                                    gc.load(unit.id(), adjacentUnit.id());
                                    myUnits.get(unit.unitType()).remove(unit);
                                    garrisonedUnits.get(unit.unitType()).add(unit);
                                }
                            }
                        }
                    }
                } else {
                    for (Direction direction : Util.getDirections()) {
                        if (unit.structureGarrison().size() == 0) {
                            break;
                        }
                        if (gc.canUnload(unit.id(), direction)) {
                            gc.unload(unit.id(), direction);
                            Unit unloaded = gc.senseUnitAtLocation(unit.location().mapLocation().add(direction));
                            myUnits.get(unloaded.unitType()).add(unloaded);
                            garrisonedUnits.get(unloaded.unitType()).remove(unloaded);
                            processUnit(unloaded);
                        }
                    }
                }
                break;
        }
    }

    //Called each turn by Player.java
    public void run() {
        initialize();

        //I prefer processing unit types in reverse order: Rocket, Factory, Healer, Mage, Ranger, Knight, Worker
        for (int i = UnitType.values().length - 1; i >= 0; i--) {
            for (Unit unit : myUnits.get(UnitType.values()[i])) {
                processUnit(unit);
            }
        }

        System.out.println("Ending of round: " + round + " with " + msRemaining + "ms remaining");
        round++;
    }

    //Get all of my units of a particular type
    private boolean attemptToBuild(Unit unit, UnitType unitType) throws IllegalArgumentException {
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
        for (Unit structure : getMyUnits(unitType)) {
//            System.out.println("Attempting to build " + unitType.name() + ": " + unit.id() + " at "
//                    + unit.location() + " to " + structure.location());
            if (structure.structureIsBuilt() == 0 && structure.location().isOnPlanet(unit.location().mapLocation().getPlanet())) {
                if (gc.canBuild(unit.id(), structure.id())) {
                    gc.build(unit.id(), structure.id());
                    return true;
                } else if (moveTowards(unit, structure.location().mapLocation())) {
                    return true;
                }
            }
        }
        return false;
    }

    //Get all of my units of a particular type
    private Set<Unit> getMyUnits(UnitType unitType) {
//        System.out.println("getMyUnits called for unit type: " + unitType);
        if (!myUnits.containsKey(unitType)) {
//            System.out.println("getMyUnits size: 0");
            return new HashSet<Unit>();
        }
//        System.out.println("getMyUnits size: " + myUnits.get(unitType).size());
        return myUnits.get(unitType);
    }

    //Move a unit toward the goal location
    private boolean moveTowards(Unit unit, MapLocation goal) throws IllegalArgumentException {
        if (unit.movementHeat() != 0) {
            return false;
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
        System.out.println("Attempting to moveTowards: " + unit.id() + " at "
                + unit.location().mapLocation() + " to " + goal);
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
            System.out.println("performMove result: " + unit.id() + " at "
                    + unit.location().mapLocation() + " moved " + heading.name());
            return true;
        }
        return false;
    }

    //M. find closest karbonite deposit to location
    private MapLocation findClosestKarbonite(MapLocation startLocation) {
        MapLocation closest = null;
        Long distance = null;
        for (MapLocation aKarboniteLocation : karboniteLocations) {
            if (distance == null || startLocation.distanceSquaredTo(aKarboniteLocation) < distance) {
                closest = aKarboniteLocation;
                distance = startLocation.distanceSquaredTo(aKarboniteLocation);
            }
            if (distance == 0) {
                break;
            }
        }
        System.out.println("Find closest Karbonite returning: " + closest + " with " + gc.karboniteAt(closest) + " karbonite");
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
        System.out.println("Find closest unit returning: " + closest);
        return closest;
    }

    private Unit findMyClosestRocket(MapLocation startLocation) {
        Unit closest = null;
        Long distance = null;
            for (Unit unit : myUnits.get(UnitType.Rocket)) {
                if (distance == null || startLocation.distanceSquaredTo(unit.location().mapLocation()) < distance) {
                    closest = unit;
                    distance = startLocation.distanceSquaredTo(unit.location().mapLocation());
                }
                if (distance == 0) {
                    break;
                }
            }
        System.out.println("Find my closest rocket returning: " + closest);
        return closest;
    }

    //TODO: Don't infinite loop when there are no valid locations on the target planet
    //Get a random passable location from the PlanetMap
    private MapLocation getRandomValidLocation(PlanetMap planetMap) {
        MapLocation random;
        do {
            random = new MapLocation(planetMap.getPlanet(),
                    Util.getRandomInt((int) planetMap.getWidth()),
                    Util.getRandomInt((int) planetMap.getHeight()));
            System.out.println("Random location: " + random);
        } while (!passabilityMaps.get(planetMap.getPlanet()).get(random));
        System.out.println("Returning valid random location: " + random);
        return random;
    }
}

