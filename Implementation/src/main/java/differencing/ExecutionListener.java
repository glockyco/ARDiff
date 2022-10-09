package differencing;

import differencing.models.Instruction;
import differencing.models.Partition;
import differencing.models.PartitionInstruction;
import differencing.models.Run;
import differencing.repositories.InstructionRepository;
import differencing.repositories.PartitionInstructionRepository;
import differencing.repositories.PartitionRepository;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

import java.util.*;
import java.util.stream.Collectors;

public class ExecutionListener extends PropertyListenerAdapter {
    private final DifferencingParameters parameters;

    private final MethodSpec methodToCoverSpec;
    private final MethodSpec areEquivalentSpec;

    // Note: all code that involves ExecutionNodes is only there to make
    // debugging easier by providing a tree view of all instructions executed
    // during the symbolic execution. Look at `roots` in `searchFinished` to
    // see the full execution tree. The actual data that we're collecting is
    // stored in the `benchmark`, `run`, `partitions`, `partitionInstructions`,
    // and `instructions` fields.

    private final List<ExecutionNode> roots = new ArrayList<>();
    private final Map<Integer, ExecutionNode> nodeMap = new HashMap<>();
    private final Map<Integer, Integer> indexMap = new HashMap<>();

    private final Run run;
    private final int version;
    private final Set<Partition> partitions = new HashSet<>();
    private final Set<PartitionInstruction> currentPartitionInstructions = new HashSet<>();
    private final Set<Instruction> currentInstructions = new HashSet<>();

    private Partition currentPartition;

    private ExecutionNode prevNode = null;
    private int prevIndex = -1;

    protected boolean isInMethodToCover = false;

    protected int partitionId =  1;

    public ExecutionListener(Run run, DifferencingParameters parameters, String methodToCover) {
        this.run = run;
        this.parameters = parameters;
        this.methodToCoverSpec = MethodSpec.createMethodSpec(methodToCover);
        this.areEquivalentSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".areEquivalent");

        this.currentPartition = new Partition(this.run.benchmark, this.run.tool, this.partitionId);

        assert methodToCover.contains("IoldV") || methodToCover.contains("InewV");
        this.version = methodToCover.contains("IoldV") ? 1 : 2;
    }

    @Override
    public void searchFinished(Search search) {
        InstructionRepository.insertOrUpdate(this.currentInstructions);
        PartitionInstructionRepository.insertOrUpdate(this.currentPartitionInstructions);
    }

    @Override
    public void methodEntered(VM vm, ThreadInfo currentThread, MethodInfo enteredMethod) {
        if (this.methodToCoverSpec.matches(enteredMethod)) {
            this.isInMethodToCover = true;
        }
    }

    @Override
    public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
        if (this.methodToCoverSpec.matches(exitedMethod)) {
            this.isInMethodToCover = false;
        }
    }

    @Override
    public void choiceGeneratorAdvanced(VM vm, ChoiceGenerator<?> currentCG) {
        if (this.isInMethodToCover && !vm.getSystemState().isIgnored()) {
            if (vm.getSearch().isNewState()) {
                this.nodeMap.put(vm.getStateId(), this.prevNode);
                this.indexMap.put(vm.getStateId(), this.prevIndex);
            }
        }
    }

    @Override
    public void stateBacktracked(Search search) {
        this.prevNode = this.nodeMap.get(search.getStateId());
        this.prevIndex = this.indexMap.getOrDefault(search.getStateId(), -1);
        this.isInMethodToCover = this.prevNode != null;
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, gov.nasa.jpf.vm.Instruction instructionToExecute) {
        MethodInfo mi = instructionToExecute.getMethodInfo();
        if (instructionToExecute instanceof JVMReturnInstruction && this.areEquivalentSpec.matches(mi)) {
            PartitionRepository.insertOrUpdate(this.currentPartition);
            InstructionRepository.insertOrUpdate(this.currentInstructions);
            PartitionInstructionRepository.insertOrUpdate(this.currentPartitionInstructions);

            this.partitions.add(this.currentPartition);
            this.partitionId++;

            this.currentPartition = new Partition(this.run.benchmark, this.run.tool, this.partitionId);
            this.currentInstructions.clear();
            this.currentPartitionInstructions.clear();
        }

        if (this.isInMethodToCover & !vm.getSystemState().isIgnored() && !currentThread.isFirstStepInsn()) {
            if (this.methodToCoverSpec.matchesClass(instructionToExecute.getMethodInfo().getClassInfo().getName())) {
                assert vm.getChoiceGenerator() instanceof PCChoiceGenerator;
                PCChoiceGenerator cg = vm.getLastChoiceGeneratorOfType(PCChoiceGenerator.class);

                // -------------------------------------------------------------

                ExecutionNode node = new ExecutionNode(vm.getStateId(), cg.getNextChoice(), instructionToExecute, this.prevNode);
                node.pathCondition = PathCondition.getPC(vm);

                ExecutionNode n = node;
                while (n != null && !n.partitionIds.contains(this.partitionId)) {
                    n.partitionIds.add(this.partitionId);
                    n = n.prev;
                }

                if (this.prevNode == null) {
                    this.roots.add(node);
                } else {
                    this.prevNode.addNext(node);
                }

                this.prevNode = node;
                this.prevIndex++;

                // -------------------------------------------------------------

                Instruction instruction = new Instruction(
                    this.currentPartition.benchmark,
                    mi.getFullName(),
                    instructionToExecute.getInstructionIndex(),
                    instructionToExecute.toString(),
                    instructionToExecute.getPosition(),
                    mi.getSourceFileName(),
                    instructionToExecute.getLineNumber()
                );

                PartitionInstruction partitionInstruction = new PartitionInstruction(
                    this.currentPartition.benchmark,
                    this.currentPartition.tool,
                    this.currentPartition.partition,
                    this.version,
                    instruction.method,
                    instruction.instructionIndex,
                    this.prevIndex,
                    cg.getStateId(),
                    cg.getNextChoice()
                );

                this.currentInstructions.add(instruction);
                this.currentPartitionInstructions.add(partitionInstruction);
            }
        }
    }

    private static class ExecutionNode {
        public final int stateId;
        public final int choiceId;
        public final gov.nasa.jpf.vm.Instruction instruction;
        public final Set<Integer> partitionIds = new HashSet<>();

        public final ExecutionNode prev;
        public List<ExecutionNode> next = new ArrayList<>();

        public PathCondition pathCondition;

        public ExecutionNode(int stateId, int choiceId, gov.nasa.jpf.vm.Instruction instruction, ExecutionNode prev) {
            this.stateId = stateId;
            this.choiceId = choiceId;
            this.instruction = instruction;
            this.prev = prev;
        }

        public List<ExecutionNode> getNext() {
            return this.next;
        }

        public void addNext(ExecutionNode next) {
            this.next.add(next);
        }

        @Override
        public String toString() {
            return this.toString(0);
        }

        private String toString(int level) {
            String outerIndent = String.join("", Collections.nCopies(level * 2, " "));

            StringBuilder sb = new StringBuilder();
            sb.append(outerIndent);
            sb.append("stateId=" + this.stateId);
            sb.append(", choiceId=" + this.choiceId);
            sb.append(", instruction=" + this.instruction.getMnemonic());

            String partitions = this.partitionIds.stream().map(Object::toString).collect(Collectors.joining(","));
            sb.append(", partitionIds=[" + partitions + "]");

            if (this.pathCondition != null && this.pathCondition.header != null) {
                sb.append(", pc=" + this.pathCondition.header.stringPC());
            }

            sb.append("\n");

            if (this.next.size() != 0) {
                sb.append(this.next.stream().map(n -> n.toString(level + 1)).collect(Collectors.joining("")));
            }

            return sb.toString();
        }
    }
}
