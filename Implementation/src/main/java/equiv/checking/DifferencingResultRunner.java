package equiv.checking;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DifferencingResultRunner {
    public static void main(String[] args) throws IOException {
        Path parameterFilePath = Paths.get(args[0]);

        DifferencingParameterFactory parameterFactory = new DifferencingParameterFactory();
        DifferencingResultFactory resultFactory = new DifferencingResultFactory();

        DifferencingResult result;
        Path resultFilePath;

        if (parameterFilePath.toFile().exists()) {
            DifferencingParameters parameters = parameterFactory.load(parameterFilePath.toFile());
            result = resultFactory.create(parameters);
            resultFilePath = Paths.get(parameters.getResultFile());
        } else {
            System.out.println("Error: '" + parameterFilePath + "' does not exist.");
            result = resultFactory.create(parameterFilePath.getParent().toString());
            resultFilePath = Paths.get(parameterFilePath.toString().replace("-Parameters.txt", "-Result.txt"));
        }

        resultFactory.persist(resultFilePath.toFile(), result);
    }
}
