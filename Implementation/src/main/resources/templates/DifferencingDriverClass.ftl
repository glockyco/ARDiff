<#-- @ftlvariable name="parameters" type="differencing.DifferencingParameters" -->

package ${parameters.targetNamespace};

import ${parameters.newNamespace}.${parameters.newClassName};
import ${parameters.oldNamespace}.${parameters.oldClassName};
import gov.nasa.jpf.symbc.Debug;

import java.util.Objects;

class DifferentOutputsException extends RuntimeException {
    public DifferentOutputsException() {
    }

    public DifferentOutputsException(String message) {
        super(message);
    }

    public DifferentOutputsException(String message, Throwable cause) {
        super(message, cause);
    }

    public DifferentOutputsException(Throwable cause) {
        super(cause);
    }

    public DifferentOutputsException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

public class ${parameters.targetClassName} {

    public static boolean areEquivalent(int a, int b) { return false; }
    public static boolean areEquivalent(long a, long b) { return false; }
    public static boolean areEquivalent(short a, short b) { return false; }
    public static boolean areEquivalent(byte a, byte b) { return false; }
    public static boolean areEquivalent(float a, float b) { return false; }
    public static boolean areEquivalent(double a, double b) { return false; }
    public static boolean areEquivalent(boolean a, boolean b) { return false; }
    public static boolean areEquivalent(Object a, Object b) { return false; }

    public static ${parameters.oldReturnType} run(${parameters.inputParameters}) {
        ${parameters.oldReturnType} result_old = ${parameters.oldResultDefaultValue};
        ${parameters.newReturnType} result_new = ${parameters.newResultDefaultValue};

        Throwable error_old = null;
        Throwable error_new = null;

        try {
            result_old = ${parameters.oldClassName}.snippet(${parameters.inputVariables});
        } catch (Throwable e) {
            error_old = e;
        }

        try {
            result_new = ${parameters.newClassName}.snippet(${parameters.inputVariables});
        } catch (Throwable e) {
            error_new = e;
        }

        boolean areErrorsEquivalent = Objects.equals(error_old, error_new);

        System.out.println("Differencing Driver Output:");

        System.out.println("  Errors:");
        System.out.println("  - Old: " + error_old);
        System.out.println("  - New: " + error_new);
        System.out.println("  - Equivalent: " + areErrorsEquivalent);

        if (!areErrorsEquivalent) {
            if (error_old != null && error_new != null) {
                String msg = "result_old (" + error_old + ") != result_new (" + error_new + ")";
                throw new DifferentOutputsException(msg);
            } else if (error_old != null) { // && error_new == null
                String msg = "result_old (" + error_old + ") != result_new";
                throw new DifferentOutputsException(msg);
            } else { // error_old == null & error_new != null
                String msg = "result_old != result_new (" + error_new + ")";
                throw new DifferentOutputsException(msg);
            }
        }

        boolean areResultsEquivalent = areEquivalent(result_old, result_new);

        System.out.println("  Results:");
        System.out.println("  - Equivalent: " + areResultsEquivalent);

        if (!areResultsEquivalent) {
            String msg = "result_old != result_new";
            throw new DifferentOutputsException(msg);
        }

        return result_new;
    }

    public static void main(String[] args) {
        ${parameters.targetClassName}.run(${parameters.inputValues});
    }
}
