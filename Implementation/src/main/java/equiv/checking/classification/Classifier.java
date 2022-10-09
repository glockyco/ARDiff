package equiv.checking.classification;

public interface Classifier {
    Classification getClassification();
    boolean isMissing();
    boolean isBaseToolMissing();
    boolean isUnreachable();
    boolean isError();
    boolean isTimeout();
    boolean isDepthLimited();
    boolean isUnknown();
    boolean isMaybeNeq();
    boolean isMaybeEq();
    boolean isNeq();
    boolean isEq();
}
