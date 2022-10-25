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
import java.util.Objects;
import java.util.Set;

public class DifferencingListener extends PropertyListenerAdapter {
    private final Run run;
    private final DifferencingParameters parameters;
    private final MethodSpec areErrorsEquivalentSpec;
    private final MethodSpec areResultsEquivalentSpec;
    private final MethodSpec runSpec;
    private final SatisfiabilityChecker satChecker;

    private final ValueToModelTransformer valToModel = new ValueToModelTransformer();
    private final SpfToModelTransformer spfToModel = new SpfToModelTransformer();

    private final Set<Partition> partitions = new HashSet<>();

    private int partitionId =  1;
    private Status partitionPcStatus = null;
    private Status partitionNeqStatus = null;
    private Status partitionEqStatus = null;
    private boolean hasPartitionUifPc = false;
    private boolean hasPartitionUifV1 = false;
    private boolean hasPartitionUifV2 = false;
    private Integer partitionPcConstraintCount = null;
    private boolean isPartitionDepthLimited = false;

    public DifferencingListener(Run run, DifferencingParameters parameters, int solverTimeout) {
        this.run = run;
        this.parameters = parameters;
        this.areErrorsEquivalentSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".areErrorsEquivalent");
        this.areResultsEquivalentSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".areResultsEquivalent");
        this.runSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".run");
        this.satChecker = new SatisfiabilityChecker(solverTimeout);
    }

    public Set<Partition> getPartitions() {
        return this.partitions;
    }

    public boolean isDepthLimited() {
        return this.partitions.stream().anyMatch(p -> p.result == Classification.DEPTH_LIMITED);
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
            this.isPartitionDepthLimited = true;
        }
        this.startNextPartition();
    }

    @Override
    public void propertyViolated(Search search) {
        if (search.getVM().getCurrentThread().isFirstStepInsn()) {
            return;
        }
        this.startNextPartition();
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
        if (!(instructionToExecute instanceof JVMReturnInstruction)) {
            return;
        }

        MethodInfo mi = instructionToExecute.getMethodInfo();
        if (this.runSpec.matches(mi)) {
            this.startNextPartition();
        } else if (this.areErrorsEquivalentSpec.matches(mi)) {
            ThreadInfo threadInfo = vm.getCurrentThread();
            StackFrame stackFrame = threadInfo.getModifiableTopFrame();
            LocalVarInfo[] localVars = stackFrame.getLocalVars();

            // Our areErrorsEquivalent methods all have two method parameters
            // (a and b) and no other local variables, so the total
            // number of local variables should always be two.
            assert localVars.length == 2;

            // Get the concrete values of the two parameters:
            Object[] argumentValues = stackFrame.getArgumentValues(threadInfo);
            Object v1Value = argumentValues[0];
            Object v2Value = argumentValues[1];

            String v1Error = v1Value == null ? null : ((DynamicElementInfo) v1Value).getClassInfo().getName();
            String v2Error = v2Value == null ? null : ((DynamicElementInfo) v2Value).getClassInfo().getName();

            boolean areEquivalent = Objects.equals(v1Error, v2Error);

            if (areEquivalent) {
                stackFrame.setOperand(0, 1, false);
            } else {
                this.partitionNeqStatus = Status.SATISFIABLE;
                this.partitionEqStatus = Status.UNSATISFIABLE;
                stackFrame.setOperand(0, 0, false);
            }
        } else if (this.areResultsEquivalentSpec.matches(mi)) {
            ThreadInfo threadInfo = vm.getCurrentThread();
            StackFrame stackFrame = threadInfo.getModifiableTopFrame();
            LocalVarInfo[] localVars = stackFrame.getLocalVars();

            // Our areResultsEquivalent methods all have two method parameters
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

            this.partitionPcConstraintCount = this.getConstraintCount(pcConstraint);

            this.hasPartitionUifPc = HasUifVisitor.hasUif(pcModel);
            this.hasPartitionUifV1 = HasUifVisitor.hasUif(v1Model);
            this.hasPartitionUifV2 = HasUifVisitor.hasUif(v2Model);

            boolean hasUif = this.hasPartitionUifPc || this.hasPartitionUifV1 || this.hasPartitionUifV2;

            this.partitionPcStatus = this.satChecker.checkPc(pcModel);

            if (this.partitionPcStatus == Status.UNKNOWN) {
                // If we don't know whether the partition is reachable,
                // mark it as non-equivalent just to be safe.
                stackFrame.setOperand(0, 0, false);
                return;
            } else if (this.partitionPcStatus == Status.UNSATISFIABLE) {
                // If we know that the partition is UNREACHABLE,
                // mark it as equivalent.
                stackFrame.setOperand(0, 1, false);
                return;
            } else if (this.partitionPcStatus == Status.SATISFIABLE) {
                // If we know that the partition is REACHABLE,
                // continue with the analysis to check whether
                // both programs produce the same result.
            } else {
                // If we encounter an unexpected status,
                // throw an exception to force us to fix this.
                throw new RuntimeException("Unexpected PC status '" + this.partitionPcStatus + "'.");
            }

            this.partitionNeqStatus = this.satChecker.checkNeq(pcModel, v1Model, v2Model);

            if (this.partitionNeqStatus == Status.UNKNOWN) {
                // If we don't know whether the partition is equivalent,
                // mark it as non-equivalent just to be safe.
                stackFrame.setOperand(0, 0, false);
            } else if (this.partitionNeqStatus == Status.UNSATISFIABLE) {
                // If we know that the partition is EQUIVALENT (i.e., NO
                // counterexample was found), mark it as equivalent.
                stackFrame.setOperand(0, 1, false);
            } else if (this.partitionNeqStatus == Status.SATISFIABLE) {
                // If we know that the partition is NOT EQUIVALENT, (i.e., a
                // counterexample was found)mark it as not equivalent.
                stackFrame.setOperand(0, 0, false);

                if (hasUif) {
                    // We've found a counterexample, but it might be caused by
                    // the introduction of uninterpreted functions => check
                    // whether the two programs differ for all possible input
                    // values in this partition. If they do, we can be sure
                    // that the base programs are also NEQ. If they don't, we
                    // can't be sure that the base programs are NEQ, so we
                    // classify them as MAYBE_NEQ instead.
                    this.partitionEqStatus = this.satChecker.checkEq(pcModel, v1Model, v2Model);

                    if (this.partitionEqStatus != Status.UNKNOWN
                        && this.partitionEqStatus != Status.UNSATISFIABLE
                        && this.partitionEqStatus != Status.SATISFIABLE) {
                        // If we encounter an unexpected status,
                        // throw an exception to force us to fix this.
                        throw new RuntimeException("Unexpected EQ status '" + this.partitionEqStatus + "'.");
                    }
                }
            } else {
                // If we encounter an unexpected status,
                // throw an exception to force us to fix this.
                throw new RuntimeException("Unexpected NEQ status '" + this.partitionNeqStatus + "'.");
            }
        }
    }

    private void startNextPartition() {
        if (this.partitionPcStatus == null) {
            PathCondition pathCondition = PathCondition.getPC(VM.getVM());
            Constraint pcConstraint = pathCondition.header;
            Model pcModel = this.spfToModel.transform(pcConstraint);

            this.partitionPcStatus = this.satChecker.checkPc(pcModel);
            this.hasPartitionUifPc = HasUifVisitor.hasUif(pcModel);
            this.partitionPcConstraintCount = this.getConstraintCount(pcConstraint);
        }

        Classification classification = new PartitionClassifier(
            false, false, false, false, this.isPartitionDepthLimited,
            this.partitionPcStatus, this.partitionNeqStatus, this.partitionEqStatus,
            this.hasPartitionUifPc, this.hasPartitionUifV1, this.hasPartitionUifV2
        ).getClassification();

        Partition partition = new Partition(
            this.run.benchmark,
            this.run.tool,
            this.partitionId,
            classification,
            this.hasPartitionUifPc,
            this.hasPartitionUifV1,
            this.hasPartitionUifV2,
            this.partitionPcConstraintCount,
            ""
        );

        PartitionRepository.insertOrUpdate(partition);

        this.partitions.add(partition);
        this.partitionId++;
        this.partitionPcStatus = null;
        this.partitionNeqStatus = null;
        this.partitionEqStatus = null;
        this.hasPartitionUifPc = false;
        this.hasPartitionUifV1 = false;
        this.hasPartitionUifV2 = false;
        this.partitionPcConstraintCount = null;
        this.isPartitionDepthLimited = false;
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
