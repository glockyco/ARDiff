package equiv.checking;

import equiv.checking.SymbolicExecutionRunner.SMTSummary;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.symbc.numeric.*;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;

public class DifferencingListener extends PropertyListenerAdapter {
    final DifferencingParameters parameters;
    final SMTSummary summary;
    final ArrayList<MethodSpec> areEquivalentMethods = new ArrayList<>();

    public int count =  0;

    public DifferencingListener(DifferencingParameters parameters, SMTSummary summary) {
        this.parameters = parameters;
        this.summary = summary;

        // @TODO: Check if we can make do with fewer method specs.
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(int,int)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(long,long)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(short,short)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(byte,byte)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(float,float)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(double,double)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(boolean,boolean)"));
        // @TODO: Check if method spec for objects works.
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(java.lang.Object,java.lang.Object)"));
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
        if (instructionToExecute instanceof JVMReturnInstruction) {
            MethodInfo mi = instructionToExecute.getMethodInfo();

            // Intercept execution when returning from one of the "areEquivalentMethods".
            for (MethodSpec areEquivalentMethod : this.areEquivalentMethods) {
                if (areEquivalentMethod.matches(mi)) {
                    ThreadInfo threadInfo = vm.getCurrentThread();
                    StackFrame stackFrame = threadInfo.getModifiableTopFrame();
                    LocalVarInfo[] localVars = stackFrame.getLocalVars();

                    // Our areEquivalent methods all have two method parameters
                    // (a and b) and no other local variables, so the total
                    // number of local variables should always be two.
                    assert localVars.length == 2;

                    // -------------------------------------------------------
                    // Get the current symbolic state of the program.

                    // Get the current path condition:
                    PathCondition pathCondition = PathCondition.getPC(vm);
                    Constraint pcConstraint = pathCondition.header;

                    Object[] argumentValues = stackFrame.getArgumentValues(threadInfo);

                    // Get the symbolic value of the first parameter:
                    int slotIndexA = localVars[0].getSlotIndex();
                    Expression expressionA = (Expression) stackFrame.getSlotAttr(slotIndexA);
                    // Get the concrete value of the first parameter:
                    Object valueA = argumentValues[0];

                    // Get the symbolic value of the second parameter:
                    int slotIndexB = localVars[1].getSlotIndex();
                    Expression expressionB = (Expression) stackFrame.getSlotAttr(slotIndexB);
                    // Get the concrete value of the second parameter:
                    Object valueB = argumentValues[1];

                    // -------------------------------------------------------
                    // Check equivalence of the two parameters using an SMT solver.

                    // @TODO: Idea -> Store all states of the old (and new?) version and do alignment when (or after?) executing the new version.
                    // @TODO: Idea -> Think about doing semantic alignment based on branching points (is this shadow SE?).

                    this.count++;
                    System.out.println("Count: " + this.count);

                    boolean areEquivalent;

                    try {
                        boolean aIsConcrete = expressionA == null;
                        boolean bIsConcrete = expressionB == null;

                        String pcString = pcConstraint.prefix_notation();
                        String aString = aIsConcrete ? valueA.toString() : expressionA.prefix_notation();
                        String bString = bIsConcrete ? valueB.toString() : expressionB.prefix_notation();

                        // @TODO: Check if the path condition is satisfiable.

                        String z3Query = "";
                        z3Query += this.summary.declarations + "\n";
                        z3Query += "; Path Condition:\n";
                        z3Query += "(assert " + pcString + ")\n\n";
                        z3Query += "; Equivalence Check:\n";
                        z3Query += "(assert (not (= " + aString + " " + bString + ")))\n\n";
                        z3Query += "(check-sat)\n";
                        z3Query += "(get-model)\n";

                        String filename = parameters.getTargetClassName() + "ToSolve" + this.count + ".txt";
                        java.nio.file.Path z3QueryPath  = java.nio.file.Paths.get(parameters.getTargetDirectory(), filename).toAbsolutePath();

                        Files.write(z3QueryPath, z3Query.getBytes());

                        // @TODO: Check why bessi0-Eq has some satisfiable queries (7, 8, 14, 15).
                        // @TODO: Log the z3 outputs.

                        String mainCommand = Paths.z3 +" -smt2 " + z3QueryPath + " -T:1";

                        Process z3Process = Runtime.getRuntime().exec(mainCommand);
                        BufferedReader in = new BufferedReader(new InputStreamReader(z3Process.getInputStream()));
                        BufferedReader err = new BufferedReader(new InputStreamReader(z3Process.getErrorStream()));
                        String answer = in.readLine();

                        String model = "";
                        String line = "";
                        while ((line = in.readLine()) != null) {
                            model += line+"\n";
                        }

                        // @TODO: Differentiate "sat" (NEQ) vs "unknown" (might be EQ/NEQ).
                        areEquivalent = answer.equals("unsat");

                        // @TODO: Check why ARDiff claims that unreachable-Eq is "unknown".
                        // @TODO: Does abstraction lead to "unknown" equality as often as ARDiff claims?
                        // @TODO: Can we build a non-CEGAR (e.g., AbsInt-based) approach that works better than ARDiff?
                        //  => Check whether a given input partition actually contains abstractions when "unknown" would be reported.
                        // @TODO: Can we produce "better" results by only abstracting unchanged code that cannot be symbolically executed?
                        // @TODO: Can we "infer"/"estimate"/"guess" whether a program is suitable for symbolic execution?

                        System.out.println(z3Query);
                        System.out.println(answer);
                        System.out.println(model);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // -------------------------------------------------------
                    // Replace the return value of the intercepted method
                    // with the result of the equivalence check.

                    stackFrame.setOperand(0, Types.booleanToInt(areEquivalent), false);
                }
            }
        }
    }
}
