package equiv.checking.domain;

public interface Model {
    void accept(ModelVisitor visitor);
}
