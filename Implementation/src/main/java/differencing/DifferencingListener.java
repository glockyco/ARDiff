package differencing;

import differencing.classification.Classification;
import differencing.classification.PartitionClassifier;
import differencing.models.Partition;
import differencing.models.Run;
import differencing.repositories.PartitionRepository;
import equiv.checking.ProjectPaths;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
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
import java.util.HashSet;
import java.util.Set;

public class DifferencingListener extends PropertyListenerAdapter {
    private final Run run;
    private final DifferencingParameters parameters;
    private final MethodSpec areEquivalentSpec;

    private final Set<Partition> partitions = new HashSet<>();

    private int partitionId =  1;
    private boolean isDepthLimited = false;

    public DifferencingListener(Run run, DifferencingParameters parameters) {
        this.run = run;
        this.parameters = parameters;
        this.areEquivalentSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".areEquivalent");
    }

    public Set<Partition> getPartitions() {
        return this.partitions;
    }

    public boolean isDepthLimited() {
        return this.isDepthLimited;
    }

    public boolean hasUif() {
        return this.partitions.stream().anyMatch(p -> p.hasUif);
    }

    @Override
    public void searchConstraintHit(Search search) {
        if (search.getVM().getCurrentThread().isFirstStepInsn()) {
            return;
        }

        if (search.getDepth() >= search.getDepthLimit()) {
            this.isDepthLimited = true;

            PathCondition pathCondition = PathCondition.getPC(search.getVM());
            Constraint pcConstraint = pathCondition.header;
            String pcString = pcConstraint != null ? pcConstraint.prefix_notation() : "true";
            boolean hasUifPc = pcString.contains("UF_");

            // We don't have any v1/2 results, so there can't be any UIFs in them.
            boolean hasUifV1 = false;
            boolean hasUifV2 = false;

            Classification classification = new PartitionClassifier(
                false, false, false, false, true,
                "", "", "", hasUifPc, false, false
            ).getClassification();

            Partition partition = new Partition(
                this.run.benchmark,
                this.run.tool,
                this.partitionId,
                classification,
                hasUifPc,
                hasUifV1,
                hasUifV2,
                this.getConstraintCount(pcConstraint),
                ""
            );

            PartitionRepository.insertOrUpdate(partition);

            this.partitions.add(partition);
            this.partitionId++;
        }
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
        MethodInfo mi = instructionToExecute.getMethodInfo();
        if (!(instructionToExecute instanceof JVMReturnInstruction) || !this.areEquivalentSpec.matches(mi)) {
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
        int v1SlotIndex = localVars[0].getSlotIndex();
        Expression v1Expression = (Expression) stackFrame.getSlotAttr(v1SlotIndex);
        // Get the concrete value of the first parameter:
        Object v1Value = argumentValues[0];

        // Get the symbolic value of the second parameter:
        int v2SlotIndex = localVars[1].getSlotIndex();
        Expression v2Expression = (Expression) stackFrame.getSlotAttr(v2SlotIndex);
        // Get the concrete value of the second parameter:
        Object v2Value = argumentValues[1];

        // -------------------------------------------------------
        // Check equivalence of the two parameters using an SMT solver.

        boolean v1IsConcrete = v1Expression == null;
        boolean v2IsConcrete = v2Expression == null;

        String pcString = pcConstraint != null ? pcConstraint.prefix_notation() : "true";
        String v1String = v1IsConcrete ? v1Value.toString() : v1Expression.prefix_notation();
        String v2String = v2IsConcrete ? v2Value.toString() : v2Expression.prefix_notation();

        String declarationsString = this.getDeclarations(pcConstraint, v1Expression, v2Expression);

        boolean hasUifPc = pcString.contains("UF_");
        boolean hasUifV1 = v1String.contains("UF_");
        boolean hasUifV2 = v2String.contains("UF_");

        boolean hasUif = hasUifPc || hasUifV1 || hasUifV2;

        String z3PcQuery = "";
        z3PcQuery += declarationsString + "\n\n";
        z3PcQuery += "; Path Condition:\n";
        z3PcQuery += "(assert " + pcString + ")\n\n";
        z3PcQuery += "(check-sat)\n";
        z3PcQuery += "(get-model)\n";

        String z3PcAnswer = this.runQuery(z3PcQuery, "PC");

        if (!z3PcAnswer.equals("sat")) {
            // -------------------------------------------------------
            // Replace the return value of the intercepted method
            // with the result of the equivalence check.

            stackFrame.setOperand(0, 1, false);

            // -------------------------------------------------------
            // Add partition information to the collected data.

            Classification result = new PartitionClassifier(
                false, false, false, false, false,
                z3PcAnswer, "", "", hasUifPc, hasUifV1, hasUifV2
            ).getClassification();

            Partition partition = new Partition(
                this.run.benchmark,
                this.run.tool,
                this.partitionId,
                result,
                hasUifPc,
                hasUifV1,
                hasUifV2,
                this.getConstraintCount(pcConstraint),
                ""
            );

            PartitionRepository.insertOrUpdate(partition);

            this.partitions.add(partition);
            this.partitionId++;

            return;
        }

        String z3NeqQuery = "";
        z3NeqQuery += declarationsString + "\n\n";
        z3NeqQuery += "; Path Condition:\n";
        z3NeqQuery += "(assert " + pcString + ")\n\n";
        z3NeqQuery += "; Non-Equivalence Check:\n";
        z3NeqQuery += "(assert (not (= " + v1String + " " + v2String + ")))\n\n";
        z3NeqQuery += "(check-sat)\n";
        z3NeqQuery += "(get-model)\n";

        String z3NeqAnswer = this.runQuery(z3NeqQuery, "NEQ");

        boolean areEquivalent = z3NeqAnswer.equals("unsat");

        String z3EqAnswer = "";
        if (z3NeqAnswer.equals("sat") && hasUif) {
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
            z3EqQuery += "(assert (= " + v1String + " " + v2String + "))\n\n";
            z3EqQuery += "(check-sat)\n";
            z3EqQuery += "(get-model)\n";

            z3EqAnswer = this.runQuery(z3EqQuery, "EQ");
        }

        // -------------------------------------------------------
        // Replace the return value of the intercepted method
        // with the result of the equivalence check.

        stackFrame.setOperand(0, Types.booleanToInt(areEquivalent), false);

        // -------------------------------------------------------
        // Add partition information to the collected data.

        Classification result = new PartitionClassifier(
            false, false, false, false, false,
            z3PcAnswer, z3NeqAnswer, z3EqAnswer, hasUifPc, hasUifV1, hasUifV2
        ).getClassification();

        Partition partition = new Partition(
            this.run.benchmark,
            this.run.tool,
            this.partitionId,
            result,
            hasUifPc,
            hasUifV1,
            hasUifV2,
            this.getConstraintCount(pcConstraint),
            ""
        );

        PartitionRepository.insertOrUpdate(partition);

        this.partitions.add(partition);
        this.partitionId++;
    }

    private String getDeclarations(Constraint pc, Expression v1, Expression v2) {
        CreateDeclarationsVisitor visitor = new CreateDeclarationsVisitor();

        if (pc != null) {
            pc.accept(visitor);
        }

        if (v1 != null) {
            v1.accept(visitor);
        }

        if (v2 != null) {
            v2.accept(visitor);
        }

        return visitor.getDeclarations();
    }

    private int getConstraintCount(Constraint pcConstraint) {
        int constraintCount = 0;
        Constraint c = pcConstraint;
        while (c != null) {
            constraintCount++;
            c = c.and;
        }
        return constraintCount;
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

            if (!z3Errors.isEmpty()) {
                throw new RuntimeException("z3 Error: " + z3Errors);
            }

            if (z3Answer.equals("sat")) {
                // If the query is satisfiable, the solver provides the corresponding model.
                this.writeModel(z3Model, name);
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

    private Path writeQuery(String query, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.partitionId + "-ToSolve-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, query.getBytes());
        return path;
    }

    private void writeAnswer(String answer, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.partitionId + "-Answer-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, answer.getBytes());
    }

    private void writeModel(String model, String name) throws IOException {
        String filename = this.parameters.getTargetClassName() + "-P" + this.partitionId + "-Model-" + name + ".txt";
        Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
        Files.write(path, model.getBytes());
    }
}
