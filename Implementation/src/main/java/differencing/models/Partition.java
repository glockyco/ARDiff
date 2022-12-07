package differencing.models;

import differencing.EquivalenceCheckResult;
import differencing.ReachabilityCheckResult;
import differencing.classification.Classification;

import java.util.Objects;

public class Partition {
    // Index
    public String benchmark;
    public String tool;
    public int iteration;
    public int partition;

    // Non-Index
    public Classification result;
    public ReachabilityCheckResult pcResult;
    public EquivalenceCheckResult neqResult;
    public EquivalenceCheckResult eqResult;
    public Boolean hasUif;
    public Boolean hasUifPc;
    public Boolean hasUifV1;
    public Boolean hasUifV2;
    public Integer constraintCount;
    public Float runtime;
    public String errors;

    public Partition(String benchmark, String tool, int iteration, int partition) {
        this(
            benchmark, tool, iteration, partition, null,
            null, null, null,
            null, null, null,
            null, null, null
        );
    }

    public Partition(
        String benchmark,
        String tool,
        int iteration,
        int partition,
        Classification result,
        ReachabilityCheckResult pcResult,
        EquivalenceCheckResult neqResult,
        EquivalenceCheckResult eqResult,
        Boolean hasUifPc,
        Boolean hasUifV1,
        Boolean hasUifV2,
        Integer constraintCount,
        Float runtime,
        String errors
    ) {
        assert result != Classification.ERROR || !errors.isEmpty();

        this.benchmark = benchmark;
        this.tool = tool;
        this.iteration = iteration;
        this.partition = partition;

        this.result = result;
        this.pcResult = pcResult;
        this.neqResult = neqResult;
        this.eqResult = eqResult;
        this.hasUifPc = hasUifPc;
        this.hasUifV1 = hasUifV1;
        this.hasUifV2 = hasUifV2;
        this.constraintCount = constraintCount;
        this.runtime = runtime;
        this.errors = errors;

        if (hasUifPc == null && hasUifV1 == null && hasUifV2 == null) {
            this.hasUif = null;
        } else {
            this.hasUif = (hasUifPc != null && hasUifPc)
                || (hasUifV1 != null && hasUifV1)
                || (hasUifV2 != null && hasUifV2);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Partition partition1 = (Partition) o;
        return iteration == partition1.iteration
            && partition == partition1.partition
            && benchmark.equals(partition1.benchmark)
            && tool.equals(partition1.tool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, iteration, partition);
    }
}
