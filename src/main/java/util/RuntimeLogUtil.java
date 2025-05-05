package util;

import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class RuntimeLogUtil {
    public static boolean DEBUG_MODE = false; // true = verbose, false = minimal

    public static void insertLineExercisedLog(Unit anchorStmt, PatchingChain<Unit> units, String sourceFile, int line, SootMethod logMethod) {
//        System.out.println("Inserting line exercised log for " + sourceFile);
        String message = "{event: EXERCISED, file: " + sourceFile + ", line: " + line + "}";
        InvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(
                logMethod.makeRef(),
                StringConstant.v(message)
        );
        InvokeStmt logStmt = Jimple.v().newInvokeStmt(invokeExpr);
        units.insertBefore(logStmt, anchorStmt);
    }

    public static void insertConditionLog(Local left, Local right, String op,
                                           Chain<Unit> units, Unit anchor,
                                           SootMethod logMethod, Body body) {
        if (!DEBUG_MODE) return;
        if (left == null || right == null) return;
        String className = body.getMethod().getDeclaringClass().getName().replace('.', '/');
        int line = anchor.getJavaSourceStartLineNumber();

        String msg = String.format(
                "{event: CONDITION, file: %s.java, line: %d, OP_left: %s, operator: %s, OP_right: %s}",
                className, line, left.getName(), op, right.getName()
        );

        units.insertBefore(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(msg))
                ),
                anchor
        );
    }

    public static void insertSubconditionCheckedLog(int index, Unit anchor, Chain<Unit> units,
                                                    Body body, SootMethod logMethod) {
        String sourceFile = body.getMethod().getDeclaringClass().getName().replace('.', '/') + ".java";
        int line = anchor.getJavaSourceStartLineNumber();

        String msg = String.format(
                "{event: SUBCONDITION_CHECKED, file: %s, line: %d, index: %d}",
                sourceFile, line, index
        );

        units.insertBefore(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(msg))
                ),
                anchor
        );
    }

    public static void insertRuntimeLog(Local local, boolean isConstant, String eventType, Chain<Unit> units, Unit anchor,
                                        Body body, SootMethod logMethod) {
        if (!RuntimeLogUtil.DEBUG_MODE) return;
        Type type = local.getType();
        SootMethod valueOfMethod = TempVariableUtil.getStringValueOfMethodForType(type);

        if (valueOfMethod != null) {
            Local valueStr = Jimple.v().newLocal("_autogen_val_" + TempVariableUtil.counter.getAndIncrement(), RefType.v("java.lang.String"));
            body.getLocals().add(valueStr);

            units.insertBefore(Jimple.v().newAssignStmt(
                    valueStr,
                    Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), local)
            ), anchor);

            // Build {"event":"VAR","name":"...","value":"..."}
            Local jsonStr = buildJsonLog(
                    "OP_" + eventType,
                    local.getName(),
                    type.toString(),
                    isConstant? "constant" : "variable",
                    valueStr,
                    units, anchor, body
            );

            insertLogOfStringValue(jsonStr, units, anchor, logMethod);

        } else {
            // Fallback for unsupported types
            Local fallbackStr = createStringLocal(
                    "{event: VAR, name: " + local.getName() + ", value: [unsupported type]}",
                    units, anchor, body
            );

            insertLogOfStringValue(fallbackStr, units, anchor, logMethod);
        }
    }

    public static void insertLogOfStringValue(Local strLocal, Chain<Unit> units, Unit anchor, SootMethod logMethod) {
        // Create a static invoke expression: Logger.log(strLocal)
        StaticInvokeExpr logInvoke = Jimple.v().newStaticInvokeExpr(
                logMethod.makeRef(),
                strLocal
        );

        // Create the statement
        InvokeStmt logStmt = Jimple.v().newInvokeStmt(logInvoke);

        // Insert the log statement before the anchor
        units.insertBefore(logStmt, anchor);
    }

    private static Local buildJsonLog(String eventType, String varName, String typeName, String sourceType, Local valueStr,
                                      Chain<Unit> units, Unit anchor, Body body) {
        // Build: {"event":"eventType","name":"varName","type":"typeName","source":"sourceType","value":
        Local prefix = createStringLocal(
                "{\"event\":\"" + eventType +
                        "\",\"name\":\"" + varName +
                        "\",\"type\":\"" + typeName +
                        "\",\"source\":\"" + sourceType +
                        "\",\"value\":\"",
                units, anchor, body
        );

        // prefix + value
        Local partial = concatStrings(prefix, valueStr, units, anchor, body);

        // partial + "\"}"
        Local closingStr = createStringLocal("\"}", units, anchor, body);

        return concatStrings(partial, closingStr, units, anchor, body);
    }


    public static Local concatStrings(Local base, Local append, Chain<Unit> units, Unit anchor, Body body) {
        Local result = Jimple.v().newLocal("_autogen_concat_" + TempVariableUtil.counter.getAndIncrement(), RefType.v("java.lang.String"));
        body.getLocals().add(result);

        units.insertBefore(Jimple.v().newAssignStmt(
                result,
                Jimple.v().newVirtualInvokeExpr(
                        base,
                        Scene.v().getMethod("<java.lang.String: java.lang.String concat(java.lang.String)>").makeRef(),
                        append
                )
        ), anchor);

        return result;
    }

    public static Local createStringLocal(String constant, Chain<Unit> units, Unit anchor, Body body) {
        Local strLocal = Jimple.v().newLocal("_autogen_str_" + TempVariableUtil.counter.getAndIncrement(), RefType.v("java.lang.String"));
        body.getLocals().add(strLocal);

        units.insertBefore(Jimple.v().newAssignStmt(
                strLocal,
                StringConstant.v(constant)
        ), anchor);

        return strLocal;
    }

    public static void insertFieldAccessLog(SootField field, Unit anchor, Chain<Unit> units, SootMethod logMethod) {
        String message = String.format("{event: FIELD_ACCESSED, field: %s}", field.getSignature());
        InvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(
                logMethod.makeRef(),
                StringConstant.v(message)
        );
        InvokeStmt logStmt = Jimple.v().newInvokeStmt(invokeExpr);
        units.insertBefore(logStmt, anchor);
    }
}
