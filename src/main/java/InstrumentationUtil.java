import soot.*;
import soot.jimple.*;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;
import soot.tagkit.Tag;
import soot.util.Chain;

import java.util.concurrent.atomic.AtomicInteger;


public class InstrumentationUtil {
    static final AtomicInteger counter = new AtomicInteger(1);

    public static Local createTempForValue(Value original, String eventType, Chain<Unit> units, Unit anchor,
                                           Body body, SootMethod logMethod) {
        // 1. Create temp
        Local temp = Jimple.v().newLocal("__autogen_" + counter.getAndIncrement(), original.getType());
        body.getLocals().add(temp);
        // 2. Assign original value into temp (evaluate now)
        AssignStmt assign = Jimple.v().newAssignStmt(temp, original);
        units.insertBefore(assign, anchor);

        // 3. Insert runtime log for temp (log its value immediately)
        insertRuntimeLog(temp, original instanceof Constant, eventType, units, anchor, body, logMethod);
        return temp;
    }

    public static void insertRuntimeLog(Local local, boolean isConstant, String eventType, Chain<Unit> units, Unit anchor,
                                         Body body, SootMethod logMethod) {
        Type type = local.getType();
        SootMethod valueOfMethod = InstrumentationUtil.getStringValueOfMethodForType(type);

        if (valueOfMethod != null) {
            Local valueStr = Jimple.v().newLocal("_autogen_val_" + counter.getAndIncrement(), RefType.v("java.lang.String"));
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
                    "{\"event\":\"VAR\",\"name\":\"" + local.getName() + "\",\"value\":\"[unsupported type]\"}",
                    units, anchor, body
            );

            insertLogOfStringValue(fallbackStr, units, anchor, logMethod);
        }
    }

    private static Local createStringLocal(String constant, Chain<Unit> units, Unit anchor, Body body) {
        Local strLocal = Jimple.v().newLocal("_autogen_str_" + counter.getAndIncrement(), RefType.v("java.lang.String"));
        body.getLocals().add(strLocal);

        units.insertBefore(Jimple.v().newAssignStmt(
                strLocal,
                StringConstant.v(constant)
        ), anchor);

        return strLocal;
    }

    private static Local concatStrings(Local base, Local append, Chain<Unit> units, Unit anchor, Body body) {
        Local result = Jimple.v().newLocal("_autogen_concat_" + counter.getAndIncrement(), RefType.v("java.lang.String"));
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

    public static SootMethod getStringValueOfMethodForType(Type type) {
        SootClass stringClass = Scene.v().getSootClass("java.lang.String");

        // Handle primitive types specially
        if (type instanceof IntType) {
            return stringClass.getMethod("java.lang.String valueOf(int)");
        }
        if (type instanceof LongType) {
            return stringClass.getMethod("java.lang.String valueOf(long)");
        }
        if (type instanceof FloatType) {
            return stringClass.getMethod("java.lang.String valueOf(float)");
        }
        if (type instanceof DoubleType) {
            return stringClass.getMethod("java.lang.String valueOf(double)");
        }
        if (type instanceof BooleanType) {
            return stringClass.getMethod("java.lang.String valueOf(boolean)");
        }
        if (type instanceof CharType) {
            return stringClass.getMethod("java.lang.String valueOf(char)");
        }
        if (type instanceof ByteType) {
            return stringClass.getMethod("java.lang.String valueOf(int)");  // Byte promoted to int
        }
        if (type instanceof ShortType) {
            return stringClass.getMethod("java.lang.String valueOf(int)");  // Short promoted to int
        }

        // For reference types (Object, String, etc.), fallback to toString()
        if (type instanceof RefType || type instanceof ArrayType) {
            return null;  // signal caller to fallback to null-check and toString() manually
        }

        throw new RuntimeException("Unsupported type for logging: " + type);
    }

}
