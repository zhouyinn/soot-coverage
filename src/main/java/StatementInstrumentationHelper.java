import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.util.Chain;

import java.util.List;

public class StatementInstrumentationHelper {

    public static Value instrumentCondition(Value cond, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        if (cond instanceof Local) {
            Local local = (Local) cond;
            Type type = local.getType();

            if (type instanceof IntType || type instanceof ShortType || type instanceof ByteType || type instanceof LongType) {
                SootMethod logInt = Scene.v().getMethod("<Logger: void logInt(java.lang.String,int)>");
                units.insertBefore(Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logInt.makeRef(), StringConstant.v(local.getName()), local)
                ), anchor);
            } else if (type instanceof BooleanType) {
                SootMethod logBool = Scene.v().getMethod("<Logger: void logBoolean(java.lang.String,boolean)>");
                units.insertBefore(Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logBool.makeRef(), StringConstant.v(local.getName()), local)
                ), anchor);
            } else {
                SootMethod logObj = Scene.v().getMethod("<Logger: void logObject(java.lang.String,java.lang.Object)>");
                units.insertBefore(Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logObj.makeRef(), StringConstant.v(local.getName()), local)
                ), anchor);
            }
            return local;
        }

        if (cond instanceof Constant) {
            SootMethod log = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(log.makeRef(), StringConstant.v("const = " + cond))
            ), anchor);
            return cond;
        }

        if (cond instanceof BinopExpr) {
            BinopExpr binop = (BinopExpr) cond;
            Value left = instrumentCondition(binop.getOp1(), anchor, units, body, logMethod);
            Value right = instrumentCondition(binop.getOp2(), anchor, units, body, logMethod);
            BinopExpr rebuilt = rebuildBinopExpr(binop, left, right);

            if (rebuilt instanceof ConditionExpr) {
                units.insertBefore(Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v("evaluating: " + rebuilt))
                ), anchor);
                return rebuilt;
            } else {
                Local temp = addTempLocal(body, rebuilt.getType());
                units.insertBefore(Jimple.v().newAssignStmt(temp, rebuilt), anchor);
                units.insertBefore(Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(temp + " = " + rebuilt))
                ), anchor);
                return temp;
            }
        }

        if (cond instanceof InvokeExpr) {
            InvokeExpr invoke = (InvokeExpr) cond;
            Local result = addTempLocal(body, cond.getType());
            units.insertBefore(Jimple.v().newAssignStmt(result, invoke), anchor);

            StringBuilder summary = new StringBuilder();
            summary.append(result).append(" = ");
            if (invoke instanceof InstanceInvokeExpr) {
                summary.append(((InstanceInvokeExpr) invoke).getBase()).append(".");
            }
            summary.append(invoke.getMethod().getName()).append("(");
            List<Value> args = invoke.getArgs();
            for (int i = 0; i < args.size(); i++) {
                summary.append(args.get(i));
                if (i < args.size() - 1) summary.append(", ");
            }
            summary.append(")");

            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(summary.toString()))
            ), anchor);
            return result;
        }

        if (cond instanceof UnopExpr) {
            UnopExpr unop = (UnopExpr) cond;
            Value operand = instrumentCondition(unop.getOp(), anchor, units, body, logMethod);
            Value rebuilt;

            if (unop.getClass().getSimpleName().equals("JNegExpr")) {
                rebuilt = Jimple.v().newNegExpr(operand);
            } else {
                rebuilt = Jimple.v().newEqExpr(operand, IntConstant.v(0));
            }

            Local temp = addTempLocal(body, rebuilt.getType());
            units.insertBefore(Jimple.v().newAssignStmt(temp, rebuilt), anchor);
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(temp + " = " + rebuilt))
            ), anchor);
            return temp;
        }

        throw new RuntimeException("Unsupported condition type: " + cond.getClass());
    }

    public static Local addTempLocal(Body body, Type type) {
        String name = "tmp" + System.nanoTime();
        Local local = Jimple.v().newLocal(name, type);
        body.getLocals().add(local);
        return local;
    }

    public static BinopExpr rebuildBinopExpr(BinopExpr original, Value left, Value right) {
        if (original instanceof JGtExpr) return Jimple.v().newGtExpr(left, right);
        if (original instanceof JGeExpr) return Jimple.v().newGeExpr(left, right);
        if (original instanceof JLtExpr) return Jimple.v().newLtExpr(left, right);
        if (original instanceof JLeExpr) return Jimple.v().newLeExpr(left, right);
        if (original instanceof JEqExpr) return Jimple.v().newEqExpr(left, right);
        if (original instanceof JNeExpr) return Jimple.v().newNeExpr(left, right);
        if (original instanceof JAndExpr) return Jimple.v().newAndExpr(left, right);
        if (original instanceof JOrExpr) return Jimple.v().newOrExpr(left, right);
        throw new RuntimeException("Unsupported BinopExpr: " + original.getClass());
    }
}