package differencing.models;

import java.util.Objects;

public class Instruction {
    // Index
    public String benchmark;
    public String tool;
    public int iteration;
    public String method;
    public int instructionIndex;

    // Non-Index
    public String instruction;
    public Integer position;
    public String sourceFile;
    public Integer sourceLine;

    public Instruction(
        String benchmark,
        String tool,
        int iteration,
        String method,
        int instructionIndex,
        String instruction,
        Integer position,
        String sourceFile,
        Integer sourceLine
    ) {
        this.benchmark = benchmark;
        this.tool = tool;
        this.iteration = iteration;
        this.method = method;
        this.instructionIndex = instructionIndex;
        this.instruction = instruction;
        this.position = position;
        this.sourceFile = sourceFile;
        this.sourceLine = sourceLine;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instruction that = (Instruction) o;
        return iteration == that.iteration
            && instructionIndex == that.instructionIndex
            && benchmark.equals(that.benchmark)
            && tool.equals(that.tool)
            && method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, tool, iteration, method, instructionIndex);
    }
}
