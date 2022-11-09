package equiv.checking;

import differencing.StopWatches;
import differencing.classification.Classification;
import differencing.classification.IterationClassifier;
import differencing.models.Iteration;
import differencing.models.Run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OutputParser {
    public static Map<Integer, Iteration> readIterations(Run run, String tool, String errors, boolean isTimeout) throws IOException {
        Path benchmarkPath = Paths.get("..", "benchmarks", run.benchmark);
        Path outputFilePath = benchmarkPath.resolve("outputs").resolve(tool + ".txt");

        if (!outputFilePath.toFile().exists()) {
            boolean isError = !errors.isEmpty();
            assert isError || isTimeout;
            return Collections.singletonMap(1, new Iteration(
                run.benchmark, run.tool, 1,
                isTimeout ? Classification.TIMEOUT : Classification.ERROR,
                isTimeout, null, null,
                StopWatches.getTime("iteration-" + 1),
                errors
            ));
        }

        String outputString = new String(Files.readAllBytes(outputFilePath));
        String[] outputs = outputString.split("--Results--");

        Map<Integer, Iteration> iterations = new HashMap<>();

        for (int i = 1; i < outputs.length; i++) {
            String output = outputs[i];

            Boolean isDepthLimited = isDepthLimited(benchmarkPath, tool, i);
            Boolean hasUif = hasUif(benchmarkPath, tool, i);

            Classification result = new IterationClassifier(
                false, false, !errors.isEmpty(), isTimeout,
                Collections.singletonList(classify(output, isDepthLimited))
            ).getClassification();

            boolean isLastIteration = i == outputs.length - 1;

            iterations.put(i, new Iteration(
                run.benchmark, run.tool, i, result,
                isLastIteration && isTimeout, isDepthLimited, hasUif,
                StopWatches.getTime("iteration-" + i),
                isLastIteration ? errors : ""
            ));
        }

        return iterations;
    }

    private static Classification classify(String output, Boolean isDepthLimited) {
        int index = output.indexOf("Output : ");
        if (index == -1) {
            throw new RuntimeException("Unable to classify output '" + output + "'.");
        }

        String relevantOutput = output.substring(index);
        if (relevantOutput.startsWith("Output : EQUIVALENT")) {
            if (isDepthLimited == null || isDepthLimited) {
                return Classification.MAYBE_EQ;
            } else {
                return Classification.EQ;
            }
        } else if (relevantOutput.startsWith("Output : NOT EQUIVALENT")) {
            return Classification.NEQ;
        } else if (relevantOutput.startsWith("Output : UNKNOWN")) {
            if (relevantOutput.contains("too much abstraction")) {
                return Classification.MAYBE_NEQ;
            } else {
                return Classification.UNKNOWN;
            }
        }

        throw new RuntimeException("Unable to classify output '" + output + "'.");
    }

    private static Boolean isDepthLimited(Path benchmarkPath, String tool, Integer iteration) throws IOException {
        if (iteration == null) {
            return true;
        }

        Path instrumentedPath = benchmarkPath.resolve("instrumented");
        String depthLimitReached = "depth limit reached";

        String oldOutputFileName = "IoldV" + tool + iteration + "JPFOutput.txt";
        Path oldOutputFilePath = instrumentedPath.resolve(oldOutputFileName);
        String newOutputFileName = "InewV" + tool + iteration + "JPFOutput.txt";
        Path newOutputFilePath = instrumentedPath.resolve(newOutputFileName);

        if (!oldOutputFilePath.toFile().exists() || !newOutputFilePath.toFile().exists()) {
            return null;
        }

        String oldOutput = new String(Files.readAllBytes(oldOutputFilePath));
        boolean isOldVDepthLimited = oldOutput.contains(depthLimitReached);
        String newOutput = new String(Files.readAllBytes(newOutputFilePath));
        boolean isNewVDepthLimited = newOutput.contains(depthLimitReached);

        return isOldVDepthLimited || isNewVDepthLimited;
    }

    private static Boolean hasUif(Path benchmarkPath, String tool, int iteration) throws IOException {
        Path toSolvePath = benchmarkPath.resolve("instrumented").resolve("InewV" + tool + iteration + "ToSolve.txt");
        if (!toSolvePath.toFile().exists()) {
            return null;
        }
        String toSolve = new String(Files.readAllBytes(toSolvePath));
        return toSolve.contains("(declare-fun UF_");
    }
}
