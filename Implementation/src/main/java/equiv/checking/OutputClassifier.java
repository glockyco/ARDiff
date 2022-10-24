package equiv.checking;

import differencing.classification.Classification;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class OutputClassifier {
    public static Classification classify(Path benchmarkPath, String tool) throws IOException {
        Path outputFilePath = benchmarkPath.resolve("outputs").resolve(tool + ".txt");

        if (!outputFilePath.toFile().exists()) {
            return null;
        }

        String output = new String(Files.readAllBytes(outputFilePath));

        int index = output.lastIndexOf("Output : ");
        if (index == -1) {
            throw new RuntimeException("Unable to classify output '" + output + "'.");
        }

        String lastOutput = output.substring(index);
        if (lastOutput.startsWith("Output : EQUIVALENT")) {
            if (OutputClassifier.isDepthLimited(benchmarkPath, tool)) {
                return Classification.MAYBE_EQ;
            } else {
                return Classification.EQ;
            }
        } else if (lastOutput.startsWith("Output : NOT EQUIVALENT")) {
            return Classification.NEQ;
        } else if (lastOutput.startsWith("Output : UNKNOWN")) {
            if (lastOutput.contains("too much abstraction")) {
                return Classification.MAYBE_NEQ;
            } else {
                return Classification.UNKNOWN;
            }
        }

        throw new RuntimeException("Unable to classify output '" + output + "'.");
    }

    public static Boolean isDepthLimited(Path benchmarkPath, String tool) throws IOException {
        Path instrumentedPath = benchmarkPath.resolve("instrumented");
        String depthLimitReached = "depth limit reached";

        String oldOutputFileName = "IoldV" + tool + "JPFOutput.txt";
        Path oldOutputFilePath = instrumentedPath.resolve(oldOutputFileName);
        String newOutputFileName = "InewV" + tool + "JPFOutput.txt";
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

    public static Boolean hasUif(Path benchmarkPath, String tool) throws IOException {
        Path toSolvePath = benchmarkPath.resolve("instrumented").resolve("InewV" + tool + "ToSolve.txt");
        if (!toSolvePath.toFile().exists()) {
            return null;
        }
        String toSolve = new String(Files.readAllBytes(toSolvePath));
        return toSolve.contains("(declare-fun UF_");
    }

    public static Integer getIterationCount(Path benchmarkPath, String tool) throws IOException {
        Path outputFilePath = benchmarkPath.resolve("outputs").resolve(tool + ".txt");
        if (!outputFilePath.toFile().exists()) {
            return null;
        }
        String output = new String(Files.readAllBytes(outputFilePath));
        int iterationCount = StringUtils.countMatches(output, "Iteration : ");
        // Only ARDiff has iterations. For all other tools, return an iteration count of 1.
        return iterationCount == 0 ? 1 : iterationCount;
    }
}
