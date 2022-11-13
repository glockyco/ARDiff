package differencing.models;

import differencing.classification.Classification;

import java.util.Objects;

public class Iteration {
    // Index
    public String benchmark;
    public String tool;
    public int iteration;

    // Non-Index
    public Classification result;
    public Boolean hasTimedOut;
    public Boolean isDepthLimited;
    public Boolean hasUif;
    public Float runtime;
    public String errors;

    public Iteration(String benchmark, String tool, int iteration) {
        this(benchmark, tool, iteration, null, null, null, null, null, null);
    }

    public Iteration(
        String benchmark,
        String tool,
        int iteration,
        Classification result,
        Boolean hasTimedOut,
        Boolean isDepthLimited,
        Boolean hasUif,
        Float runtime,
        String errors
    ) {
        assert result != Classification.ERROR || !errors.isEmpty();

        this.benchmark = benchmark;
        this.tool = tool;
        this.iteration = iteration;
        this.result = result;
        this.hasTimedOut = hasTimedOut;
        this.isDepthLimited = isDepthLimited;
        this.hasUif = hasUif;
        this.runtime = runtime;
        this.errors = errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Iteration that = (Iteration) o;
        return iteration == that.iteration
            && benchmark.equals(that.benchmark)
            && tool.equals(that.tool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, iteration);
    }
}
