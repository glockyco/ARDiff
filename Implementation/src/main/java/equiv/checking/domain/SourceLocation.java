package equiv.checking.domain;

import java.util.Objects;

public class SourceLocation implements Model {
    public String filePath;
    public String className;
    public String methodName;
    public int lineNumber;
    public int choice;

    public SourceLocation(String filePath, String className, String methodName, int lineNumber, int choice) {
        this.filePath = filePath;
        this.className = className;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        this.choice = choice;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return this.filePath + ":" + this.lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceLocation that = (SourceLocation) o;
        return lineNumber == that.lineNumber
            && choice == that.choice
            && Objects.equals(filePath, that.filePath)
            & Objects.equals(className, that.className)
            & Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, className, methodName, lineNumber, choice);
    }
}
