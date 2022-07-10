package equiv.checking;

public class DifferencingResult {
    private final String benchmarkDirectory;
    private final String expectedClassification;
    private final String actualClassification;
    private final boolean didTimeOut;
    private final boolean didError;

    public DifferencingResult(
        String benchmarkDirectory,
        String expectedClassification,
        String actualClassification,
        boolean didTimeOut,
        boolean didError
    ) {
        this.benchmarkDirectory = benchmarkDirectory;
        this.expectedClassification = expectedClassification;
        this.actualClassification = actualClassification;
        this.didTimeOut = didTimeOut;
        this.didError = didError;
    }

    public String getBenchmarkDirectory() {
        return benchmarkDirectory;
    }

    public String getExpectedClassification() {
        return expectedClassification;
    }

    public String getActualClassification() {
        return actualClassification;
    }

    public boolean didTimeOut() {
        return didTimeOut;
    }

    public boolean didError() {
        return didError;
    }
}
