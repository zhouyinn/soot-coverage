package util;

import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.concurrent.atomic.AtomicInteger;

public class TempVariableUtil {
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
        RuntimeLogUtil.insertRuntimeLog(temp, original instanceof Constant, eventType, units, anchor, body, logMethod);
        return temp;
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
