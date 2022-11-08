package differencing;

import DSE.DSEInstrumentation;
import GradDiff.GradDiffInstrumentation;
import SE.SEInstrumentation;
import equiv.checking.ChangeExtractor;
import equiv.checking.SourceInstrumentation;

public class InstrumentationFactory {
    public static SourceInstrumentation create(
        String toolName,
        int timeout,
        ChangeExtractor changeExtractor
    ) {
        switch (toolName) {
            case "SE":
                return new SEInstrumentation(
                    toolName,
                    changeExtractor.getJavaFileDirectory(),
                    changeExtractor.getOldVClassFile(),
                    changeExtractor.getNewVClassFile(),
                    changeExtractor.getOldVJavaFile(),
                    changeExtractor.getNewVJavaFile(),
                    timeout * 1000
                );
            case "DSE":
                return new DSEInstrumentation(
                    toolName,
                    changeExtractor.getJavaFileDirectory(),
                    changeExtractor.getOldVClassFile(),
                    changeExtractor.getNewVClassFile(),
                    changeExtractor.getOldVJavaFile(),
                    changeExtractor.getNewVJavaFile(),
                    timeout * 1000
                );
            case "ARDiff":
                return new GradDiffInstrumentation(
                    toolName,
                    changeExtractor.getJavaFileDirectory(),
                    changeExtractor.getOldVClassFile(),
                    changeExtractor.getNewVClassFile(),
                    changeExtractor.getOldVJavaFile(),
                    changeExtractor.getNewVJavaFile(),
                    timeout * 1000,
                    true,
                    true,
                    true,
                    true,
                    "H123"
                );
            default:
                throw new RuntimeException("Cannot create instrumentation for unknown tool '" + toolName + "'.");
        }
    }
}
