package differencing.models;

import com.microsoft.z3.Status;
import differencing.classification.Classification;

import java.util.Objects;

public class Partition {
    // Index
    public String benchmark;
    public String tool;
    public int partition;

    // Non-Index
    public Classification result;
    public Status pcStatus;
    public Status neqStatus;
    public Status eqStatus;
    public Boolean hasUif;
    public Boolean hasUifPc;
    public Boolean hasUifV1;
    public Boolean hasUifV2;
    public Integer constraintCount;
    public Float runtime;
    public String errors;

    public Partition(String benchmark, String tool, int partition) {
        this(
            benchmark, tool, partition, null,
            null, null, null,
            null, null, null,
            null, null, null
        );
    }

    public Partition(
        String benchmark,
        String tool,
        int partition,
        Classification result,
        Status pcStatus,
        Status neqStatus,
        Status eqStatus,
        Boolean hasUifPc,
        Boolean hasUifV1,
        Boolean hasUifV2,
        Integer constraintCount,
        Float runtime,
        String errors
    ) {
        this.benchmark = benchmark;
        this.tool = tool;
        this.partition = partition;

        this.result = result;
        this.pcStatus = pcStatus;
        this.neqStatus = neqStatus;
        this.eqStatus = eqStatus;
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
        return partition == partition1.partition
            && benchmark.equals(partition1.benchmark)
            && tool.equals(partition1.tool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, partition);
    }
}
