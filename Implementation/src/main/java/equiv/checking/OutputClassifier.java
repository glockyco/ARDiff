package equiv.checking;

import differencing.classification.Classification;

public class OutputClassifier {
    public static Classification classify(String output) {
        if (output.contains("Output : EQUIVALENT")) {
            return Classification.EQ;
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
}
