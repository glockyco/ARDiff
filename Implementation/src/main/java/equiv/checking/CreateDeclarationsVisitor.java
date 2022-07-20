package equiv.checking;

import gov.nasa.jpf.symbc.numeric.*;
import gov.nasa.jpf.symbc.string.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateDeclarationsVisitor extends ConstraintExpressionVisitor {
    private final Map<String, String> declarations = new HashMap<>();

    private final Map<Class<?>, String> typeMap = new HashMap<Class<?>, String>() {{
        put(int.class, "Int");
        put(long.class, "Int");
        put(short.class, "Int");
        put(byte.class, "Int");
        put(float.class, "Real");
        put(double.class, "Real");
        put(boolean.class, "Int");
    }};

    public String getDeclarations() {
        return this.declarations.values().stream().sorted().collect(Collectors.joining("\n"));
    }

    @Override
    public void preVisit(SymbolicInteger expr) {
        String name = expr.getName();
        if (!this.declarations.containsKey(name)) {
            String declaration = "(declare-fun " + expr.getName() + " () Int)";
            this.declarations.put(name, declaration);
        }
    }

    @Override
    public void preVisit(SymbolicIntFunction expr) {
        String name = expr.getName();
        if (!this.declarations.containsKey(name)) {
            String arguments = Arrays.stream(expr.argTypes)
                .map(this.typeMap::get)
                .collect(Collectors.joining(" "));
            String declaration = "(declare-fun " + name + " (" + arguments + ") Int)";
            this.declarations.put(name, declaration);
        }
    }

    @Override
    public void preVisit(SymbolicReal expr) {
        String name = expr.getName();
        if (!this.declarations.containsKey(name)) {
            String declaration = "(declare-fun " + name + "() Real)";
            this.declarations.put(name, declaration);
        }
    }

    @Override
    public void preVisit(SymbolicRealFunction expr) {
        String name = expr.getName();
        if (!this.declarations.containsKey(name)) {
            String arguments = Arrays.stream(expr.argTypes)
                .map(this.typeMap::get)
                .collect(Collectors.joining(" "));
            String declaration = "(declare-fun " + name + " (" + arguments + ") Real)";
            this.declarations.put(name, declaration);
        }
    }

    @Override
    public void preVisit(MathRealExpression expr) {
        String name = expr.op.toString();

        if (this.declarations.containsKey(name)) {
            return;
        }

        switch (expr.op) {
            case POW:
                this.declarations.put(name, "(define-fun pow ((a Real) (b Real)) Real (^ a b))");
                break;
            case SQRT:
                this.declarations.put(name, "(define-fun sqrt ((x Real)) Real (^ 0.5 x)) ");
                break;
            case EXP:
                this.declarations.put(name, "(define-fun exp ((x Real)) Real (^ 2.718281828459045 x)) ");
                break;
            case LOG:
                this.declarations.put(name, "(declare-fun log (Real) Real)");
                break;
            case SIN:
            case COS:
            case ASIN:
            case ACOS:
            case ATAN:
            case ATAN2:
            case TAN:
                break;
        }
    }

    @Override
    public void preVisit(StringConstant expr) {
    }

    @Override
    public void preVisit(StringSymbolic expr) {
    }

    @Override
    public void preVisit(SymbolicStringFunction expr) {
    }

    @Override
    public void preVisit(StringExpression expr) {
    }
}
