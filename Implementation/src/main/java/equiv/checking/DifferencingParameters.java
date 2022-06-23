package equiv.checking;

public class DifferencingParameters {

    private String targetDirectory;
    private String targetNamespace;
    private String targetClassName;

    private String symbolicParameters;
    private String inputParameters;
    private String inputVariables;
    private String inputValues;

    private String oldNamespace;
    private String oldClassName;
    private String oldReturnType;

    private String newNamespace;
    private String newClassName;
    private String newReturnType;

    public DifferencingParameters(
        String targetDirectory,
        String targetNamespace,
        String targetClassName,

        String symbolicParameters,
        String inputParameters,
        String inputVariables,
        String inputValues,

        String oldNamespace,
        String oldClassName,
        String oldReturnType,

        String newNamespace,
        String newClassName,
        String newReturnType
    ) {
        this.targetDirectory = targetDirectory;
        this.targetNamespace = targetNamespace;
        this.targetClassName = targetClassName;

        this.symbolicParameters = symbolicParameters;
        this.inputParameters = inputParameters;
        this.inputVariables = inputVariables;
        this.inputValues = inputValues;

        this.oldNamespace = oldNamespace;
        this.oldClassName = oldClassName;
        this.oldReturnType = oldReturnType;

        this.newNamespace = newNamespace;
        this.newClassName = newClassName;
        this.newReturnType = newReturnType;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }

    public String getTargetClassName() {
        return targetClassName;
    }

    public void setTargetClassName(String targetClassName) {
        this.targetClassName = targetClassName;
    }

    public String getSymbolicParameters() {
        return symbolicParameters;
    }

    public void setSymbolicParameters(String symbolicParameters) {
        this.symbolicParameters = symbolicParameters;
    }

    public String getInputParameters() {
        return inputParameters;
    }

    public void setInputParameters(String inputParameters) {
        this.inputParameters = inputParameters;
    }

    public String getInputVariables() {
        return inputVariables;
    }

    public void setInputVariables(String inputVariables) {
        this.inputVariables = inputVariables;
    }

    public String getInputValues() {
        return inputValues;
    }

    public void setInputValues(String inputValues) {
        this.inputValues = inputValues;
    }

    public String getOldNamespace() {
        return oldNamespace;
    }

    public void setOldNamespace(String oldNamespace) {
        this.oldNamespace = oldNamespace;
    }

    public String getOldClassName() {
        return oldClassName;
    }

    public void setOldClassName(String oldClassName) {
        this.oldClassName = oldClassName;
    }

    public String getOldReturnType() {
        return oldReturnType;
    }

    public void setOldReturnType(String oldReturnType) {
        this.oldReturnType = oldReturnType;
    }

    public String getNewNamespace() {
        return newNamespace;
    }

    public void setNewNamespace(String newNamespace) {
        this.newNamespace = newNamespace;
    }

    public String getNewClassName() {
        return newClassName;
    }

    public void setNewClassName(String newClassName) {
        this.newClassName = newClassName;
    }

    public String getNewReturnType() {
        return newReturnType;
    }

    public void setNewReturnType(String newReturnType) {
        this.newReturnType = newReturnType;
    }
}
