package equiv.checking;

import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DifferencingListener extends PropertyListenerAdapter {
    protected final DifferencingParameters parameters;
    protected final List<MethodSpec> areEquivalentMethods = new ArrayList<>();

    protected int count =  0;

    public DifferencingListener(DifferencingParameters parameters) {
        this.parameters = parameters;

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
        if (!(instructionToExecute instanceof JVMReturnInstruction)) {
            return;
        }

        MethodInfo mi = instructionToExecute.getMethodInfo();

        // Intercept execution when returning from one of the "areEquivalentMethods".
        if (this.areEquivalentMethods.stream().noneMatch(m -> m.matches(mi))) {
            return;
        }

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

        this.count++;

        boolean aIsConcrete = expressionA == null;
        boolean bIsConcrete = expressionB == null;

        String pcString = pcConstraint != null ? pcConstraint.prefix_notation() : "true";
        String aString = aIsConcrete ? valueA.toString() : expressionA.prefix_notation();
        String bString = bIsConcrete ? valueB.toString() : expressionB.prefix_notation();

        String declarationsString = this.getDeclarations(pcConstraint, expressionA, expressionB);

        boolean pcHasUninterpretedFunctions = pcString.contains("UF_");
        boolean aHasUninterpretedFunctions = aString.contains("UF_");
        boolean bHasUninterpretedFunctions = bString.contains("UF_");

        boolean hasUninterpretedFunctions = pcHasUninterpretedFunctions || aHasUninterpretedFunctions || bHasUninterpretedFunctions;

        try {
            this.writeHasUIF(pcHasUninterpretedFunctions, "PC");
            this.writeHasUIF(aHasUninterpretedFunctions, "v1");
            this.writeHasUIF(bHasUninterpretedFunctions, "v2");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String z3PcQuery = "";
        z3PcQuery += declarationsString + "\n\n";
        z3PcQuery += "; Path Condition:\n";
        z3PcQuery += "(assert " + pcString + ")\n\n";
        z3PcQuery += "(check-sat)\n";
        z3PcQuery += "(get-model)\n";

        String z3PcAnswer = this.runQuery(z3PcQuery, "PC");

        if (!z3PcAnswer.equals("sat")) {
            stackFrame.setOperand(0, 1, false);
            return;
        }

        String z3NeqQuery = "";
        z3NeqQuery += declarationsString + "\n\n";
        z3NeqQuery += "; Path Condition:\n";
        z3NeqQuery += "(assert " + pcString + ")\n\n";
        z3NeqQuery += "; Non-Equivalence Check:\n";
        z3NeqQuery += "(assert (not (= " + aString + " " + bString + ")))\n\n";
        z3NeqQuery += "(check-sat)\n";
        z3NeqQuery += "(get-model)\n";

        String z3NeqAnswer = this.runQuery(z3NeqQuery, "NEQ");

        boolean areEquivalent = z3NeqAnswer.equals("unsat");

        if (z3NeqAnswer.equals("sat") && hasUninterpretedFunctions) {
            // If we've found the two results to be non-equivalent,
            // but there were uninterpreted functions in the solver query,
            // provide further information, so we might be able to tell
            // whether the non-equivalence is due to the introduction
            // of the uninterpreted functions or not.

            // Note that this additional query has no effect on how we'll
            // continue with the symbolic execution. This is because we can
            // only become more certain that the results are not equivalent,
            // but cannot find them to be actually equivalent rather than
            // non-equivalent.

            String z3EqQuery = "";
            z3EqQuery += declarationsString + "\n\n";
            z3EqQuery += "; Path Condition:\n";
            z3EqQuery += "(assert " + pcString + ")\n\n";
            z3EqQuery += "; Equivalence Check:\n";
            z3EqQuery += "(assert (= " + aString + " " + bString + "))\n\n";
            z3EqQuery += "(check-sat)\n";
            z3EqQuery += "(get-model)\n";

            this.runQuery(z3EqQuery, "EQ");
        }

        // -------------------------------------------------------
        // Replace the return value of the intercepted method
        // with the result of the equivalence check.

        stackFrame.setOperand(0, Types.booleanToInt(areEquivalent), false);
    }

    private String getDeclarations(Constraint pc, Expression a, Expression b) {
        CreateDeclarationsVisitor visitor = new CreateDeclarationsVisitor();

        if (pc != null) {
            pc.accept(visitor);
        }

        if (a != null) {
            a.accept(visitor);
        }

        if (b != null) {
            b.accept(visitor);
        }

        return visitor.getDeclarations();
    }

    private String runQuery(String z3Query, String name) {
        try {
            Path z3QueryPath = this.writeQuery(z3Query, name);

            String mainCommand = ProjectPaths.z3 +" -smt2 " + z3QueryPath + " -T:1";

            Process z3Process = Runtime.getRuntime().exec(mainCommand);
            BufferedReader in = new BufferedReader(new InputStreamReader(z3Process.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(z3Process.getErrorStream()));

            String z3Answer = in.readLine();
            String z3Model = this.readLines(in);
            String z3Errors = this.readLines(err);

            this.writeAnswer(z3Answer, name);

            if (z3Answer.startsWith("(error")) {
                throw new RuntimeException("z3 Error: " + z3Answer);
            }

            if (z3Answer.equals("sat")) {
                // If the query is satisfiable, the solver provides the corresponding model.
                this.writeModel(z3Model, name);
            }

            if (!z3Errors.isEmpty()) {
                this.writeErrors(z3Errors, name);
            }

            return z3Answer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readLines(BufferedReader reader) throws IOException {
        StringBuilder lines = new StringBuilder();

        String line = "";
        while ((line = reader.readLine()) != null) {
            lines.append(line).append("\n");
        }

        return lines.toString();
    }

    private void writeHasUIF(boolean hasUninterpretedFunctions, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.count + "-HasUIF-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, String.valueOf(hasUninterpretedFunctions).getBytes());
    }

    private Path writeQuery(String query, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.count + "-ToSolve-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, query.getBytes());
        return path;
    }

    private void writeAnswer(String answer, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.count + "-Answer-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, answer.getBytes());
    }

    private void writeModel(String model, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.count + "-Model-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, model.getBytes());
    }

    private void writeErrors(String errors, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.count + "-Errors-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, errors.getBytes());
    }
}
