import org.junit.Test;
import soot.Unit;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.tagkit.LineNumberTag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.SootClass;
import soot.SootMethod;
import soot.VoidType;
import soot.jimple.JimpleBody;
import util.ControlFlowUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ProductCodeTransformerTest {

    private Unit createDummyStmt(String name, int lineNumber) {
        Unit stmt = Jimple.v().newAssignStmt(
                Jimple.v().newLocal("var_" + name, IntConstant.v(1).getType()),
                IntConstant.v(1)
        );
        stmt.addTag(new LineNumberTag(lineNumber));
        return stmt;
    }

    private UnitGraph createDummyGraph(List<Unit> units) {
        // Create a dummy class and method without relying on java.lang.Object
        SootClass dummyClass = new SootClass("DummyClass", SootClass.BODIES);
        SootMethod dummyMethod = new SootMethod("dummyMethod", Collections.emptyList(), VoidType.v());
        dummyClass.addMethod(dummyMethod);

        JimpleBody body = Jimple.v().newBody(dummyMethod);
        body.getUnits().addAll(units);
        dummyMethod.setActiveBody(body);

        // ⚡️ Use BriefUnitGraph (NO exception analysis)
        return new BriefUnitGraph(body);
    }

    @Test
    public void testExactMatch() {
        Map<Integer, List<Unit>> map = new HashMap<>();
        Unit stmt29 = createDummyStmt("a", 29);
        map.put(29, Collections.singletonList(stmt29));

        UnitGraph cfg = createDummyGraph(Collections.singletonList(stmt29));

        Unit result = ControlFlowUtil.findBestStmtUsingCPG(cfg, map, 29);
        assertSame("Exact match should return the stmt for line 29", stmt29, result);
    }

    @Test
    public void testForwardFallback() {
        Map<Integer, List<Unit>> map = new HashMap<>();
        Unit stmt30 = createDummyStmt("b", 30);
        map.put(30, Collections.singletonList(stmt30));

        UnitGraph cfg = createDummyGraph(Collections.singletonList(stmt30));

        Unit result = ControlFlowUtil.findBestStmtUsingCPG(cfg, map, 29);
        assertSame("Should fallback forward to line 30", stmt30, result);
    }

    @Test
    public void testBackwardFallback() {
        Map<Integer, List<Unit>> map = new HashMap<>();
        Unit stmt28 = createDummyStmt("c", 28);
        map.put(28, Collections.singletonList(stmt28));

        UnitGraph cfg = createDummyGraph(Collections.singletonList(stmt28));

        Unit result = ControlFlowUtil.findBestStmtUsingCPG(cfg, map, 29);
        assertSame("Should fallback backward to line 28 if no forward found", stmt28, result);
    }

    @Test
    public void testNoMatchFound() {
        Map<Integer, List<Unit>> map = new HashMap<>();
        UnitGraph cfg = createDummyGraph(Collections.emptyList());

        Unit result = ControlFlowUtil.findBestStmtUsingCPG(cfg, map, 29);
        assertNull("Should return null if no match found at all", result);
    }
}