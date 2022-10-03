package equiv.checking;

import equiv.checking.domain.Model;
import equiv.checking.transformer.ModelToJsonTransformer;
import equiv.checking.transformer.SpfToModelTransformer;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class PathConditionListener extends PropertyListenerAdapter {
    private final DifferencingParameters parameters;
    private final MethodSpec areEquivalentSpec;

    private final Map<Integer, Map<Integer, PathCondition>> statePcMap = new HashMap<>();
    private final Map<Integer, PathCondition> partitionPcMap = new HashMap<>();

    private int partitionId = 1;

    public PathConditionListener(DifferencingParameters parameters) {
        this.parameters = parameters;
        this.areEquivalentSpec = MethodSpec.createMethodSpec("*.IDiff" + parameters.getToolName() + ".areEquivalent");
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
        MethodInfo mi = instructionToExecute.getMethodInfo();
        if (instructionToExecute instanceof JVMReturnInstruction && this.areEquivalentSpec.matches(mi)) {
            this.partitionPcMap.put(this.partitionId, PathCondition.getPC(vm));
            this.partitionId++;
        }
    }

    @Override
    public void choiceGeneratorProcessed(VM vm, ChoiceGenerator<?> processedCG) {
        if (!(vm.getChoiceGenerator() instanceof PCChoiceGenerator)) {
            return;
        }

        PCChoiceGenerator cg = (PCChoiceGenerator) vm.getChoiceGenerator();

        for (int choice : cg.getChoices()) {
            PathCondition pc = cg.getPC(choice);
            if (pc == null || pc.header == null) {
                continue;
            }

            Map<Integer, PathCondition> choicePcMap = this.statePcMap.getOrDefault(vm.getStateId(), new HashMap<>());
            this.statePcMap.putIfAbsent(vm.getStateId(), choicePcMap);
            assert !choicePcMap.containsKey(choice);
            choicePcMap.put(choice, pc);
        }
    }

    @Override
    public void searchFinished(Search search) {
        try {
            SpfToModelTransformer spfToModelTransformer = new SpfToModelTransformer();
            ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();

            for (Integer partition: this.partitionPcMap.keySet()) {
                PathCondition pc = this.partitionPcMap.get(partition);

                Constraint pcConstraint = pc == null ? null : pc.header;
                Model pcModel = spfToModelTransformer.transform(pcConstraint);
                String pcJson = modelToJsonTransformer.transform(pcModel);

                String filename = this.parameters.getTargetClassName() + "-P" + partition + "-JSON-PC.json";
                Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
                Files.write(path, pcJson.getBytes());
            }

            for (Integer state: this.statePcMap.keySet()) {
                for (Integer choice: this.statePcMap.get(state).keySet()) {
                    PathCondition pc = this.statePcMap.get(state).get(choice);

                    Constraint pcConstraint = pc == null ? null : pc.header;
                    Model pcModel = spfToModelTransformer.transform(pcConstraint);
                    String pcJson = modelToJsonTransformer.transform(pcModel);

                    String filename = this.parameters.getTargetClassName() + "-S" + state + "-C" + choice + "-JSON-PC.json";
                    Path path = Paths.get(this.parameters.getTargetDirectory(), filename).toAbsolutePath();
                    Files.write(path, pcJson.getBytes());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
