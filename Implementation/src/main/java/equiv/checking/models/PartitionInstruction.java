package equiv.checking.models;

import java.util.Objects;

public class PartitionInstruction {
    // Index
    public String benchmark;
    public String tool;
    public int partition;
    public int version;
    public String method;
    public int instructionIndex;
    public int executionIndex;

    // Non-Index
    public Integer state;
    public Integer choice;

    public PartitionInstruction(
        String benchmark,
        String tool,
        int partition,
        int version,
        String method,
        int instructionIndex,
        int executionIndex,
        Integer state,
        Integer choice
    ) {
        this.benchmark = benchmark;
        this.tool = tool;
        this.partition = partition;
        this.version = version;
        this.method = method;
        this.instructionIndex = instructionIndex;
        this.executionIndex = executionIndex;
        this.state = state;
        this.choice = choice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartitionInstruction that = (PartitionInstruction) o;
        return partition == that.partition
            && version == that.version
            && instructionIndex == that.instructionIndex
            && executionIndex == that.executionIndex
            && benchmark.equals(that.benchmark)
            && tool.equals(that.tool)
            && method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, partition, version, method, instructionIndex, executionIndex);
    }
}
