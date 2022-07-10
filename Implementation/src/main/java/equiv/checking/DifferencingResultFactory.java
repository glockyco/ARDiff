package equiv.checking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class DifferencingResultFactory {
    public void persist(File file, DifferencingResult result) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        builder.setPrettyPrinting();
        Gson gson = builder.create();

        String json = gson.toJson(result);

        Files.write(file.toPath(), json.getBytes());
    }

    public DifferencingParameters load(File file) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        builder.setPrettyPrinting();
        Gson gson = builder.create();

        String json = new String(Files.readAllBytes(file.toPath()));

        return gson.fromJson(json, DifferencingParameters.class);
    }

    public DifferencingResult create(DifferencingParameters parameters) throws IOException {
        return new DifferencingResult(
            parameters.getTargetDirectory(),
            this.createExpectedClassification(parameters),
            this.createActualClassification(parameters),
            this.createTimeOutResult(parameters),
            this.createErrorResult(parameters)
        );
    }

    public String createExpectedClassification(DifferencingParameters parameters) {
        return parameters.getTargetDirectory().contains("/Eq/") ? "EQ" : "NEQ";
    }

    public String createActualClassification(DifferencingParameters parameters) throws IOException {
        int eqCount = 0;
        int neqCount = 0;
        int unknownCount = 0;

        for (String answerFile : parameters.getAnswerFiles()) {
            Path answerPath = Paths.get(answerFile);
            String answer = new String(Files.readAllBytes(answerPath));

            if (answer.equals("unsat")) {
                eqCount++;
            } else if (answer.equals("sat")) {
                neqCount++;
            } else if (answer.equals("unknown")) {
                unknownCount++;
            } else {
                throw new RuntimeException("Unknown z3 answer '" + answer + "' in '" + answerFile + "'.");
            }
        }

        if (unknownCount > 0) {
            return "UNKNOWN";
        } else if (neqCount > 0) {
            return "NEQ";
        } else if (eqCount > 0) {
            return "EQ";
        }

        // If we don't have any z3 answers, always return 'UNKNOWN'.
        return "UNKNOWN";
    }

    public boolean createTimeOutResult(DifferencingParameters parameters) throws IOException {
        Path errorFilePath = Paths.get(parameters.getErrorFile());
        if (!Files.exists(errorFilePath)) {
            return false;
        }

        String error = new String(Files.readAllBytes(errorFilePath));
        return error.toLowerCase().contains("timeout");
    }

    public boolean createErrorResult(DifferencingParameters parameters) throws IOException {
        Path errorFilePath = Paths.get(parameters.getErrorFile());
        if (!Files.exists(errorFilePath)) {
            return false;
        }

        String error = new String(Files.readAllBytes(errorFilePath));

        if (error.isEmpty()) {
            return false;
        }

        return !error.toLowerCase().contains("timeout");
    }
}
