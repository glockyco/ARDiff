package equiv.checking.domain;

public interface ModelVisitor {
    void visit(Model model);
    void visit(Expression expression);
    void visit(Operation operation);
    void visit(Operator operator);
    void visit(ConstantInteger constant);
    void visit(ConstantReal constant);
    void visit(ConstantString constant);
    void visit(VariableInteger variable);
    void visit(VariableReal variable);
    void visit(VariableString variable);
    void visit(SymbolicIntegerFunction function);
    void visit(SymbolicRealFunction function);
    void visit(SymbolicStringFunction function);
    void visit(SourceLocation location);
    void visit(Error error);
}
