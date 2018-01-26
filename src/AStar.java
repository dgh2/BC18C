package src;

import bc.Direction;
import bc.MapLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public class AStar {
    private MapAnalyzer mapAnalyzer;

    public AStar(MapAnalyzer mapAnalyzer) {
        this.mapAnalyzer = mapAnalyzer;
    }

    public LinkedList<Direction> path(MapLocation start, MapLocation goal) {
//        System.out.println("Pathing from " + start + " to " + goal);
        if (!mapAnalyzer.areLocationsConnected(start, goal)) {
            System.out.println("Path: No path exists from " + start + " to " + goal);
            return null;
        }
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparing(AStarNode::getTotalCost));
        Set<AStarNode> closed = new HashSet<>();
        AStarNode current;
        AStarNode next;
        Long openedNodeCount = 0L;
        Long expandedNodeCount = 0L;
        open.add(new AStarNode(null, start, goal));
        openedNodeCount++;
//        System.out.println("Opening: " + start + " cost = " + start.distanceSquaredTo(goal));
        do {
            current = open.poll();
            closed.add(current);
//            System.out.println("Expanding: " + current.getHere() + " cost = " + current.getTotalCost());
            expandedNodeCount++;
            for (Direction direction : Util.getDirections()) {
                next = current.getNextNode(direction, goal);
                if (mapAnalyzer.isPassable(next.getHere())
                        && !closed.contains(next)
                        && !open.contains(next)) {
                    open.add(next);
                    openedNodeCount++;
//                    System.out.println("Opening: " + next.getHere() + " cost = " + next.getTotalCost());
                }
            }
        } while (!open.isEmpty() && current.getDistanceSquaredToGoal() > 0);
        //output the path to logs
        System.out.print("Path from " + start + " to " + goal + ":");
        for (Direction step : current.getPath()) {
            System.out.print(" " + step.name());
        }
        System.out.println(" (total opened/expanded nodes: " + openedNodeCount + "/" + expandedNodeCount + ")");
        System.out.println();
        return current.getPath();
    }

    private class AStarNode {
        private MapLocation here;
        private LinkedList<Direction> path = new LinkedList<>();
        private Long distanceSquaredToGoal;
        private Long totalCost;

        AStarNode(AStarNode parent, MapLocation here, MapLocation goal) {
            this.here = here;
            distanceSquaredToGoal = here.distanceSquaredTo(goal);
            if (parent != null) {
                path.addAll(parent.getPath());
                path.add(parent.getHere().directionTo(here));
            }
            totalCost = path.size() + distanceSquaredToGoal;
        }

        AStarNode getNextNode(Direction direction, MapLocation goal) {
            return new AStarNode(this, getHere().add(direction), goal);
        }

        MapLocation getHere() {
            return here;
        }

        Long getDistanceSquaredToGoal() {
            return distanceSquaredToGoal;
        }

        Long getTotalCost() {
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
            return getHere().equals(other.getHere());
        }

        @Override
        public int hashCode() {
            return Objects.hash(String.valueOf(here));
        }
    }
}
