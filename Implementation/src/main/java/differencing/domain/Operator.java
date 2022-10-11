package differencing.domain;

import java.util.HashMap;
import java.util.Map;

public enum Operator implements Model {

    EQ("=="),
    NE("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),

    PLUS("+"),
    MINUS("-"),
    MUL("*"),
    DIV("/"),
    POW("**"),
    MOD("%"),

    AND("&"),
    OR("|"),
    XOR("^"),

    EQUALS("equals"),
    NOTEQUALS("notequals"),
    EQUALSIGNORECASE("equalsignorecase"),
    NOTEQUALSIGNORECASE("notequalsignorecase"),
    STARTSWITH("startswith"),
    NOTSTARTSWITH("notstartswith"),
    ENDSWITH("endswith"),
    NOTENDSWITH("notendswith"),
    CONTAINS("contains"),
    NOTCONTAINS("notcontains"),
    ISINTEGER("isinteger"),
    NOTINTEGER("notinteger"),
    ISFLOAT("isfloat"),
    NOTFLOAT("notfloat"),
    ISLONG("islong"),
    NOTLONG("notlong"),
    ISDOUBLE("isdouble"),
    NOTDOUBLE("notdouble"),
    ISBOOLEAN("isboolean"),
    NOTBOOLEAN("notboolean"),
    EMPTY("empty"),
    NOTEMPTY("notempty"),
    MATCHES("matches"),
    NOMATCHES("nomatches"),
    REGIONMATCHES("regionmatches"),
    NOREGIONMATCHES("noregionmatches"),

    SQRT("sqrt");

    private final String symbol;

    private static final Map<String, Operator> lookup = new HashMap<String, Operator>();

    static {
        for (Operator op : Operator.values()) {
            lookup.put(op.symbol, op);
        }
    }

    Operator(final String symbol) {
        this.symbol = symbol;
    }

    public static Operator get(String symbol) {
        return lookup.get(symbol.trim());
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
