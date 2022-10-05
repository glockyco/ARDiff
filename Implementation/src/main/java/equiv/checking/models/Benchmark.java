package equiv.checking.models;

import java.util.Objects;

public class Benchmark {
    // Index
    public String benchmark;

    // Non-Index
    public String expected;

    public Benchmark(String benchmark, String expected) {
        this.benchmark = benchmark;
        this.expected = expected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Benchmark benchmark = (Benchmark) o;
        return this.benchmark.equals(benchmark.benchmark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark);
    }
}
