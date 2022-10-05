package equiv.checking.models;

import java.util.HashMap;
import java.util.Map;

public enum Classification {
    MISSING("MISSING"),
    ERROR("ERROR"),
    TIMEOUT("TIMEOUT"),

    UNREACHABLE("UNREACHABLE"),

    UNKNOWN("UNKNOWN"),

    MAYBE_NEQ("MAYBE_NEQ"),
    MAYBE_EQ("MAYBE_EQ"),

    NEQ("NEQ"),
    EQ("EQ");

    private final String str;

    private static final Map<String, Classification> lookup = new HashMap<>();

    static {
        for (Classification classification : Classification.values()) {
            lookup.put(classification.str, classification);
        }
    }

    Classification(final String str) {
        this.str = str;
    }

    public static Classification get(String str) {
        return lookup.get(str.trim());
    }

    @Override
    public String toString() {
        return str;
    }
}
