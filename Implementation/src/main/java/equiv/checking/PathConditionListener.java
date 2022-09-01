package equiv.checking;

import equiv.checking.domain.Model;
import equiv.checking.domain.SourceLocation;
import equiv.checking.transformer.ModelToJsonTransformer;
import equiv.checking.transformer.SpfToModelTransformer;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathConditionListener extends PropertyListenerAdapter {
    protected final DifferencingParameters parameters;
    protected final List<MethodSpec> areEquivalentMethods = new ArrayList<>();

    protected Constraint previousConstraint = null;
    protected Map<Constraint, SourceLocation> constraintLocations = new HashMap<>();

    protected int count =  0;

    public PathConditionListener(DifferencingParameters parameters) {
        this.parameters = parameters;

        // @TODO: Check if we can make do with fewer method specs.
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(int,int)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(long,long)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(short,short)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(byte,byte)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(float,float)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(double,double)"));
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(boolean,boolean)"));
        // @TODO: Check if method spec for objects works.
        this.areEquivalentMethods.add(MethodSpec.createMethodSpec("*.areEquivalent(java.lang.Object,java.lang.Object)"));
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
        // @TODO: Reduce code duplication across DifferencingListener + PathConditionListener.
        if (!(instructionToExecute instanceof JVMReturnInstruction)) {
            return;
        }

        MethodInfo mi = instructionToExecute.getMethodInfo();

        // Intercept execution when returning from one of the "areEquivalentMethods".
        if (this.areEquivalentMethods.stream().noneMatch(m -> m.matches(mi))) {
            return;
        }

        ThreadInfo threadInfo = vm.getCurrentThread();
        StackFrame stackFrame = threadInfo.getModifiableTopFrame();
        LocalVarInfo[] localVars = stackFrame.getLocalVars();

        // Our areEquivalent methods all have two method parameters
        // (a and b) and no other local variables, so the total
        // number of local variables should always be two.
        assert localVars.length == 2;

        // -------------------------------------------------------

        this.count++;

        try {
            PathCondition pathCondition = PathCondition.getPC(vm);
            Constraint pcConstraint = pathCondition.header;

            SpfToModelTransformer spfToModelTransformer = new SpfToModelTransformer(this.constraintLocations);
            ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();

            Model pcModel = spfToModelTransformer.transform(pcConstraint);
            String pcJson = modelToJsonTransformer.transform(pcModel);

            String filename = this.parameters.getTargetClassName() + "-P" + this.count + "-JSON-PC.json";
            Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
            Files.write(path, pcJson.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void instructionExecuted(VM vm, ThreadInfo currentThread, Instruction nextInstruction, Instruction executedInstruction) {
        PathCondition pathCondition = PathCondition.getPC(vm);

        if (pathCondition == null || pathCondition.header == null) {
            return;
        }

        Constraint currentConstraint = pathCondition.header;

        // Deliberately check for identity rather than equality because
        // equivalent constraints might be produced by different instructions.
        if (currentConstraint == previousConstraint) {
            return;
        }

        MethodInfo mi = executedInstruction.getMethodInfo();
        PCChoiceGenerator choiceGenerator = vm.getLastChoiceGeneratorOfType(PCChoiceGenerator.class);

        SourceLocation location = new SourceLocation(
            mi.getSourceFileName(),
            mi.getClassName(),
            mi.getFullName(),
            executedInstruction.getLineNumber(),
            choiceGenerator.getNextChoice()
        );

        this.constraintLocations.put(currentConstraint, location);
        previousConstraint = currentConstraint;
    }
}
