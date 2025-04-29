package util;

import soot.*;
import soot.toolkits.graph.UnitGraph;

import java.util.*;


public class ControlFlowUtil {

    public static Unit findBestStmtUsingCPG(UnitGraph cfg, Map<Integer, List<Unit>> lineToStmts, int targetLine) {
        // 1. Try exact match first
        if (lineToStmts.containsKey(targetLine)) {
            return lineToStmts.get(targetLine).get(0);
        }

        // 2. Build list of all Units with line numbers
        List<Unit> units = new ArrayList<>(cfg.getBody().getUnits());
        Map<Unit, Integer> unitToLine = new HashMap<>();
        for (Unit unit : units) {
            int line = unit.getJavaSourceStartLineNumber();
            if (line > 0) {
                unitToLine.put(unit, line);
            }
        }

        // 3. Find nearest reachable forward Unit (prefer forward first)
        for (Unit unit : units) {
            int line = unitToLine.getOrDefault(unit, -1);
            if (line > 0 && line > targetLine) {
                if (isReachable(cfg, unit)) {
                    return unit;
                }
            }
        }

        // 4. If no forward reachable, try nearest backward reachable Unit
        ListIterator<Unit> reverseIter = units.listIterator(units.size());
        while (reverseIter.hasPrevious()) {
            Unit unit = reverseIter.previous();
            int line = unitToLine.getOrDefault(unit, -1);
            if (line > 0 && line < targetLine) {
                if (isReachable(cfg, unit)) {
                    return unit;
                }
            }
        }

        // 5. Give up
        return null;
    }

    public static boolean isReachable(UnitGraph cfg, Unit target) {
        Set<Unit> visited = new HashSet<>();
        Deque<Unit> worklist = new ArrayDeque<>(cfg.getHeads());

        while (!worklist.isEmpty()) {
            Unit current = worklist.poll();
            if (current.equals(target)) {
                return true;
            }
            if (visited.add(current)) {
                worklist.addAll(cfg.getSuccsOf(current));
            }
        }
        return false;
    }

}
