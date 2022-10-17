package differencing;

import com.microsoft.z3.Status;
import differencing.classification.Classification;
import differencing.classification.PartitionClassifier;
import differencing.domain.Model;
import differencing.models.Partition;
import differencing.models.Run;
import differencing.repositories.PartitionRepository;
import differencing.transformer.SpfToModelTransformer;
import differencing.transformer.ValueToModelTransformer;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;

import java.util.HashSet;
import java.util.Set;

public class DifferencingListener extends PropertyListenerAdapter {
    private final Run run;
    private final DifferencingParameters parameters;
    private final MethodSpec areEquivalentSpec;
    private final SatisfiabilityChecker satChecker;

    private final ValueToModelTransformer valToModel = new ValueToModelTransformer();
    private final SpfToModelTransformer spfToModel = new SpfToModelTransformer();

    private final Set<Partition> partitions = new HashSet<>();

    private int partitionId =  1;
    private boolean isDepthLimited = false;

    public DifferencingListener(Run run, DifferencingParameters parameters, int solverTimeout) {
        this.run = run;
        this.parameters = parameters;
        this.areEquivalentSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".areEquivalent");
        this.satChecker = new SatisfiabilityChecker(solverTimeout);
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
            Model pcModel = this.spfToModel.transform(pcConstraint);
            boolean hasUifPc = HasUifVisitor.hasUif(pcModel);

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

        Model pcModel = this.spfToModel.transform(pcConstraint);
        Model v1Model = v1IsConcrete ? this.valToModel.transform(v1Value) : this.spfToModel.transform(v1Expression);
        Model v2Model = v2IsConcrete ? this.valToModel.transform(v2Value) : this.spfToModel.transform(v2Expression);

        boolean hasUifPc = HasUifVisitor.hasUif(pcModel);
        boolean hasUifV1 = HasUifVisitor.hasUif(v1Model);
        boolean hasUifV2 = HasUifVisitor.hasUif(v2Model);

        boolean hasUif = hasUifPc || hasUifV1 || hasUifV2;

        Status pcStatus = this.satChecker.checkPc(pcModel);

        String pcAnswer = pcStatus == null ? "" : pcStatus == Status.SATISFIABLE ? "sat" : pcStatus == Status.UNSATISFIABLE ? "unsat" : "unknown";

        if (pcStatus != Status.SATISFIABLE) {
            // -------------------------------------------------------
            // Replace the return value of the intercepted method
            // with the result of the equivalence check.

            stackFrame.setOperand(0, 1, false);

            // -------------------------------------------------------
            // Add partition information to the collected data.

            Classification result = new PartitionClassifier(
                false, false, false, false, false,
                pcAnswer, "", "", hasUifPc, hasUifV1, hasUifV2
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

        Status neqStatus = this.satChecker.checkNeq(pcModel, v1Model, v2Model);

        boolean areEquivalent = neqStatus == Status.UNSATISFIABLE;

        Status eqStatus = null;
        if (neqStatus == Status.SATISFIABLE && hasUif) {
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

            eqStatus = this.satChecker.checkEq(pcModel, v1Model, v2Model);
        }

        // -------------------------------------------------------
        // Replace the return value of the intercepted method
        // with the result of the equivalence check.

        stackFrame.setOperand(0, Types.booleanToInt(areEquivalent), false);

        // -------------------------------------------------------
        // Add partition information to the collected data.

        String neqAnswer = neqStatus == null ? "" : neqStatus == Status.SATISFIABLE ? "sat" : neqStatus == Status.UNSATISFIABLE ? "unsat" : "unknown";
        String eqAnswer = eqStatus == null ? "" : eqStatus == Status.SATISFIABLE ? "sat" : eqStatus == Status.UNSATISFIABLE ? "unsat" : "unknown";

        Classification result = new PartitionClassifier(
            false, false, false, false, false,
            pcAnswer, neqAnswer, eqAnswer, hasUifPc, hasUifV1, hasUifV2
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

    private int getConstraintCount(Constraint pcConstraint) {
        int constraintCount = 0;
        Constraint c = pcConstraint;
        while (c != null) {
            constraintCount++;
            c = c.and;
        }
        return constraintCount;
    }
}
