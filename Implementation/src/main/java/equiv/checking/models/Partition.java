package equiv.checking.models;

import java.util.Objects;

public class Partition {
    // Index
    public String benchmark;
    public String tool;
    public int partition;

    // Non-Index
    public String result;
    public Boolean hasSucceeded;
    public Boolean isDepthLimited;
    public Boolean hasUif;
    public Boolean hasUifPc;
    public Boolean hasUifV1;
    public Boolean hasUifV2;
    public Integer constraintCount;
    public String errors;

    public Partition(String benchmark, String tool, int partition) {
        this(benchmark, tool, partition, null, null, null, null, null, null, null, null, null);
    }

    public Partition(
        String benchmark,
        String tool,
        int partition,
        String result,
        Boolean hasSucceeded,
        Boolean isDepthLimited,
        Boolean hasUif,
        Boolean hasUifPc,
        Boolean hasUifV1,
        Boolean hasUifV2,
        Integer constraintCount,
        String errors
    ) {
        this.benchmark = benchmark;
        this.tool = tool;
        this.partition = partition;

        this.result = result;
        this.hasSucceeded = hasSucceeded;
        this.isDepthLimited = isDepthLimited;
        this.hasUif = hasUif;
        this.hasUifPc = hasUifPc;
        this.hasUifV1 = hasUifV1;
        this.hasUifV2 = hasUifV2;
        this.constraintCount = constraintCount;
        this.errors = errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Partition partition1 = (Partition) o;
        return partition == partition1.partition
            && benchmark.equals(partition1.benchmark)
            && tool.equals(partition1.tool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, partition);
    }
}
