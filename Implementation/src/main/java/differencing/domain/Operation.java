package differencing.domain;

import java.util.Objects;

public class Operation implements Expression {
    public Expression left;
    public Operator op;
    public Expression right;

    public Operation(
        final Expression left,
        final Operator op,
        final Expression right
    ) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "(" + this.left + " " + this.op + " " + this.right + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation operation = (Operation) o;
        return Objects.equals(left, operation.left)
            && op == operation.op
            && Objects.equals(right, operation.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, op, right);
    }
}
