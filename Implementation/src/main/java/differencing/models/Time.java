package differencing.models;

import java.util.Objects;

public class Time {
    // Index
    public String benchmark;
    public String tool;
    public String topic;
    public String task;

    // Non-Index
    public Float runtime;
    public Integer step;
    public boolean isMissing;

    public Time(
        String benchmark,
        String tool,
        String topic,
        String task,
        float runtime,
        int step,
        boolean isMissing
    ) {
        this.benchmark = benchmark;
        this.tool = tool;
        this.topic = topic;
        this.task = task;
        this.runtime = runtime;
        this.step = step;
        this.isMissing = isMissing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Time time = (Time) o;
        return benchmark.equals(time.benchmark)
            && tool.equals(time.tool)
            && topic.equals(time.topic)
            && task.equals(time.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, topic, task);
    }
}
