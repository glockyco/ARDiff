<#-- @ftlvariable name="parameters" type="differencing.DifferencingParameters" -->

<#function isSymbolic type variable>
    <#switch type>
        <#case "int">
            <#return "Debug.isSymbolicInteger(${variable})">
        <#case "long">
            <#return "Debug.isSymbolicLong(${variable})">
        <#case "short">
            <#return "Debug.isSymbolicShort(${variable})">
        <#case "byte">
            <#return "Debug.isSymbolicByte(${variable})">
        <#case "char">
            <#return "Debug.isSymbolicChar(${variable})">
        <#case "double">
            <#return "Debug.isSymbolicReal(${variable})">
        <#case "boolean">
            <#return "Debug.isSymbolicBoolean(${variable})">
        <#case "String">
            <#return "Debug.isSymbolicString(${variable})">
        <#default>
            <#return "false">
    </#switch>
</#function>

<#function toString type variable>
    <#switch type>
        <#case "int">
            <#return "Debug.getSymbolicIntegerValue(${variable})">
        <#case "long">
            <#return "Debug.getSymbolicLongValue(${variable})">
        <#case "short">
            <#return "Debug.getSymbolicShortValue(${variable})">
        <#case "byte">
            <#return "Debug.getSymbolicByteValue(${variable})">
        <#case "char">
            <#return "Debug.getSymbolicCharValue(${variable})">
        <#case "double">
            <#return "Debug.getSymbolicRealValue(${variable})">
        <#case "boolean">
            <#return "Debug.getSymbolicBooleanValue(${variable})">
        <#case "String">
            <#return "Debug.getSymbolicStringValue(${variable})">
        <#default>
            <#return "String.valueOf(${variable})">
    </#switch>
</#function>

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

        String result_old_str = "";
        if (${isSymbolic(parameters.oldReturnType, "result_old")}) {
            result_old_str = ${toString(parameters.oldReturnType, "result_old")};
        } else {
            result_old_str = String.valueOf(result_old);
        }

        String result_new_str = "";
        if (${isSymbolic(parameters.oldReturnType, "result_old")}) {
            result_new_str = ${toString(parameters.newReturnType, "result_new")};
        } else {
            result_new_str = String.valueOf(result_new);
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
                String msg = "result_old (" + error_old + ") != result_new (" + result_new_str + ")";
                throw new DifferentOutputsException(msg);
            } else { // error_old == null & error_new != null
                String msg = "result_old (" + result_old_str + ") != result_new (" + error_new + ")";
                throw new DifferentOutputsException(msg);
            }
        }

        boolean areResultsEquivalent = areEquivalent(result_old, result_new);

        System.out.println("  Results:");
        System.out.println("  - Old: " + result_old_str);
        System.out.println("  - New: " + result_new_str);
        System.out.println("  - Equivalent: " + areResultsEquivalent);

        if (!areResultsEquivalent) {
            String msg = "result_old (" + result_old_str + ") != result_new (" + result_new_str + ")";
            throw new DifferentOutputsException(msg);
        }

        return result_new;
    }

    public static void main(String[] args) {
        ${parameters.targetClassName}.run(${parameters.inputValues});
    }
}
