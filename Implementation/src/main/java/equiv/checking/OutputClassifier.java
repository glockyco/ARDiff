package equiv.checking;

import differencing.classification.Classification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OutputClassifier {
    public static Classification classify(String output, boolean isDepthLimited) {
        if (output.contains("Output : EQUIVALENT")) {
            if (isDepthLimited) {
                return Classification.MAYBE_EQ;
            } else {
                return Classification.EQ;
            }
        } else if (output.contains("Output : NOT EQUIVALENT")) {
            return Classification.NEQ;
        } else if (output.contains("Output : UNKNOWN")) {
            if (output.contains("too much abstraction")) {
                return Classification.MAYBE_NEQ;
            } else {
                return Classification.UNKNOWN;
            }
        }
        throw new RuntimeException("Unable to classify output '" + output + "'.");
    }

    public static boolean isDepthLimited(String instrumentedDir, String tool) throws IOException {
        Path instrumentedPath = Paths.get(instrumentedDir);
        String depthLimitReached = "depth limit reached";

        String oldOutputFileName = "IoldV" + tool + "JPFOutput.txt";
        Path oldOutputFilePath = instrumentedPath.resolve(oldOutputFileName);
        String oldOutput = new String(Files.readAllBytes(oldOutputFilePath));
        boolean isOldVDepthLimited = oldOutput.contains(depthLimitReached);

        String newOutputFileName = "InewV" + tool + "JPFOutput.txt";
        Path newOutputFilePath = instrumentedPath.resolve(newOutputFileName);
        String newOutput = new String(Files.readAllBytes(newOutputFilePath));
        boolean isNewVDepthLimited = newOutput.contains(depthLimitReached);

        return isOldVDepthLimited || isNewVDepthLimited;
    }
}
