package equiv.checking;

import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

public class DifferencingParameters implements Serializable {
    private final String directory;
    private final String z3Declarations;
    private final MethodDescription oldMethodDescription;
    private final MethodDescription newMethodDescription;
    private final MethodDescription diffMethodDescription;

    public DifferencingParameters(
        String directory,
        String z3Declarations,
        MethodDescription oldMethodDescription,
        MethodDescription newMethodDescription,
        MethodDescription diffMethodDescription
    ) {
        this.directory = directory;
        this.z3Declarations = z3Declarations.trim();
        this.oldMethodDescription = oldMethodDescription;
        this.newMethodDescription = newMethodDescription;
        this.diffMethodDescription = diffMethodDescription;
    }

    public String getZ3Declarations() { return this.z3Declarations; }

    public String getTargetDirectory() {
        return directory;
    }

    public String getTargetNamespace() {
        return this.diffMethodDescription.getNamespace();
    }

    public String getTargetClassName() {
        return this.diffMethodDescription.getClassName();
    }

    public String getSymbolicParameters() {
        int parameterCount = this.diffMethodDescription.getParameters().size();
        return String.join("#", Collections.nCopies(parameterCount, "sym"));
    }

    public String getInputParameters() {
        return this.diffMethodDescription.getParameters().stream()
            .map(parameter -> parameter.getDataType() + " " + parameter.getName())
            .collect(Collectors.joining(", "));
    }

    public String getInputVariables() {
        return this.diffMethodDescription.getParameters().stream()
            .map(MethodParameterDescription::getName)
            .collect(Collectors.joining(", "));
    }

    public String getInputValues() {
        return this.diffMethodDescription.getParameters().stream()
            .map(MethodParameterDescription::getPlaceholderValue)
            .collect(Collectors.joining(", "));
    }

    public String getOldNamespace() {
        return this.oldMethodDescription.getNamespace();
    }

    public String getOldClassName() {
        return this.oldMethodDescription.getClassName();
    }

    public String getOldReturnType() {
        return this.oldMethodDescription.getResult().getDataType();
    }

    public String getOldResultDefaultValue() {
        return this.oldMethodDescription.getResult().getPlaceholderValue();
    }

    public String getNewNamespace() {
        return this.newMethodDescription.getNamespace();
    }

    public String getNewClassName() {
        return this.newMethodDescription.getClassName();
    }

    public String getNewReturnType() {
        return this.newMethodDescription.getResult().getDataType();
    }

    public String getNewResultDefaultValue() {
        return this.newMethodDescription.getResult().getPlaceholderValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DifferencingParameters that = (DifferencingParameters) o;
        return Objects.equals(directory, that.directory)
            && Objects.equals(z3Declarations, that.z3Declarations)
            && Objects.equals(oldMethodDescription, that.oldMethodDescription)
            && Objects.equals(newMethodDescription, that.newMethodDescription)
            && Objects.equals(diffMethodDescription, that.diffMethodDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            directory,
            z3Declarations,
            oldMethodDescription,
            newMethodDescription,
            diffMethodDescription
        );
    }
}


