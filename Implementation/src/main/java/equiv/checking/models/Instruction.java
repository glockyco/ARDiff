package equiv.checking.models;

import java.util.Objects;

public class Instruction {
    // Index
    public String benchmark;
    public String method;
    public int instructionIndex;

    // Non-Index
    public String instruction;
    public Integer position;
    public String sourceFile;
    public Integer sourceLine;

    public Instruction(
        String benchmark,
        String method,
        int instructionIndex,
        String instruction,
        Integer position,
        String sourceFile,
        Integer sourceLine
    ) {
        this.benchmark = benchmark;
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
        return instructionIndex == that.instructionIndex
            && benchmark.equals(that.benchmark)
            && method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, method, instructionIndex);
    }
}
