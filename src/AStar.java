package src;

import bc.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public class AStar {
    private final MapAnalyzer mapAnalyzer;

    private MapLocation goal;

    public AStar(MapAnalyzer mapAnalyzer) {
        this.mapAnalyzer = mapAnalyzer;
    }

    public LinkedList<Direction> path(MapLocation start, MapLocation goal) {
//        System.out.println("Pathing from " + start + " to " + goal);
        if (!mapAnalyzer.areLocationsConnected(start, goal)) {
            System.out.println("Path: No path exists from " + start + " to " + goal);
            return null;
        }
        this.goal = goal;
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparing(AStarNode::getTotalCost));
        Set<AStarNode> closed = new HashSet<>();
        AStarNode current;
        Long openedNodeCount = 0L;
        open.add(new AStarNode(null, start));
        openedNodeCount++;
//        System.out.println("Opening: " + start + " cost = " + Util.distanceBetween(start, goal));
        do {
            current = open.poll();
            closed.add(current);
//            System.out.println("Expanding: " + current.getHere() + " cost = " + current.getTotalCost());
//            Direction currentDirection = current.getPath().isEmpty() ? null : current.getPath().getLast();
            for (Direction direction : Util.getDirections()) {
                AStarNode next = current.getNextNode(direction);
                if (mapAnalyzer.isPassable(next.getHere())
                        && noCheaperPathExists(next, closed)
                        && noCheaperPathExists(next, open)) {
                    open.add(next);
                    openedNodeCount++;
//                    System.out.println("Opening: " + next.getHere() + " cost = " + next.getTotalCost());
                }
            }
        } while (!open.isEmpty() && current.getDistanceToGoal() > 0);
        //output the path to logs
        System.out.print("Path from " + start + " to " + goal + ":");
        for (Direction step : current.getPath()) {
            System.out.print(" " + step.name());
        }
        System.out.println(" (total expanded/opened nodes: " + closed.size() + "/" + openedNodeCount + ")");
        return current.getPath();
    }

    private boolean noCheaperPathExists(AStarNode current, Collection<AStarNode> collection) {
        for (AStarNode node : collection) {
            if (current.equals(node) && node.getTotalCost() <= current.getTotalCost()) {
                return false;
            }
        }
        return true;
    }

    private class AStarNode {
        private MapLocation here;
        private LinkedList<Direction> path = new LinkedList<>();
        private double distanceToGoal;
        private double totalCost;

        AStarNode(AStarNode parent, MapLocation here) {
            this.here = here;
            distanceToGoal = Util.distanceBetween(here, goal);
            if (parent != null) {
                path.addAll(parent.getPath());
                path.add(parent.getHere().directionTo(here));
            }
            totalCost = Util.getPathCost(path) + distanceToGoal;
        }

        AStarNode getNextNode(Direction direction) {
            return new AStarNode(this, getHere().add(direction));
        }

        MapLocation getHere() {
            return here;
        }

        double getDistanceToGoal() {
            return distanceToGoal;
        }

        double getTotalCost() {
            return totalCost;
        }

        LinkedList<Direction> getPath() {
            return path;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AStarNode)) {
                return false;
            }
            AStarNode other = (AStarNode) obj;
            return String.valueOf(getHere()).equals(String.valueOf(other.getHere()));
        }

        @Override
        public int hashCode() {
            return Objects.hash(String.valueOf(here));
        }
    }
}
