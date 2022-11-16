package differencing.models;

import differencing.classification.Classification;

import java.util.Objects;

public class Run {
    // Index
    public String benchmark;
    public String tool;

    // Non-Index
    public Classification result;
    public Boolean hasTimedOut;
    public Boolean isDepthLimited;
    public Boolean hasUif;
    public Integer iterationCount;
    public Integer resultIteration;
    public Float runtime;
    public String errors;

    public Run(String benchmark, String tool) {
        this(benchmark, tool, null, null, null, null, null, null, null, null);
    }

    public Run(
        String benchmark,
        String tool,
        Classification result,
        Boolean hasTimedOut,
        Boolean isDepthLimited,
        Boolean hasUif,
        Integer iterationCount,
        Integer resultIteration,
        Float runtime,
        String errors
    ) {
        assert result != Classification.ERROR || !errors.isEmpty();

        this.benchmark = benchmark;
        this.tool = tool;
        this.result = result;
        this.hasTimedOut = hasTimedOut;
        this.isDepthLimited = isDepthLimited;
        this.hasUif = hasUif;
        this.iterationCount = iterationCount;
        this.resultIteration = resultIteration;
        this.runtime = runtime;
        this.errors = errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Run run = (Run) o;
        return benchmark.equals(run.benchmark) && tool.equals(run.tool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool);
    }
}
