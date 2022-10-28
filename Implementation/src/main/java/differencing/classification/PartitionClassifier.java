package differencing.classification;

import com.microsoft.z3.Status;

public class PartitionClassifier implements Classifier {
    private final boolean isMissing;
    private final boolean isBaseToolMissing;
    private final boolean isError;
    private final boolean isTimeout;
    private final boolean isDepthLimited;
    private final Status pcStatus;
    private final Status notPcStatus;
    private final Status neqStatus;
    private final Status eqStatus;

    private final Classification classification;

    public PartitionClassifier(
        boolean isMissing,
        boolean isBaseToolMissing,
        boolean isError,
        boolean isTimeout,
        boolean isDepthLimited,
        Status pcStatus,
        Status notPcStatus,
        Status neqStatus,
        Status eqStatus
    ) {
        // Partitions should never have a MISSING or BASE_TOOL_MISSING status.
        // This is because we don't know (and can't know) which partitions
        // should or shouldn't exist for any given run.
        assert !isMissing;
        assert !isBaseToolMissing;

        this.isMissing = false;
        this.isBaseToolMissing = false;
        this.isError = isError;
        this.isTimeout = isTimeout;
        this.isDepthLimited = isDepthLimited;
        this.pcStatus = pcStatus;
        this.notPcStatus = notPcStatus;
        this.neqStatus = neqStatus;
        this.eqStatus = eqStatus;

        this.classification = this.classify();
    }

    @Override
    public Classification getClassification() {
        return this.classification;
    }

    private Classification classify() {
        // pcStatus and notPcStatus MUST be provided.
        assert this.pcStatus != null;
        assert this.notPcStatus != null;

        // If neqStatus is SAT, eqStatus MUST also be provided.
        assert this.neqStatus != Status.SATISFIABLE || this.eqStatus != null;

        if (this.pcStatus == Status.UNSATISFIABLE) {
            // If we know that the partition is unreachable,
            // we can just ignore the partition irrespective
            // of any other information we might have about it.
            return Classification.UNREACHABLE;
        } else if (this.isMissing) {
            return Classification.MISSING;
        } else if (this.isBaseToolMissing) {
            return Classification.BASE_TOOL_MISSING;
        } else if (this.isError) {
            return Classification.ERROR;
        } else if (this.isTimeout) {
            return Classification.TIMEOUT;
        } else if (this.isDepthLimited) {
            return Classification.DEPTH_LIMITED;
        } else {
            // If we have not encountered any errors, timeouts, etc.,
            // we must have some equivalence information about this partition.
            assert this.neqStatus != null;

            if (this.neqStatus == Status.UNKNOWN) {
                // The equivalence information does NOT provide any indication
                // about whether this partition is equivalent or not.
                return Classification.UNKNOWN;
            } else {
                // The equivalence information DOES provide some indication
                // about whether this partition is equivalent or not.
                assert this.neqStatus == Status.SATISFIABLE || this.neqStatus == Status.UNSATISFIABLE;

                if (this.pcStatus == Status.UNKNOWN || this.notPcStatus == Status.UNKNOWN) {
                    // We're unsure whether this partition is reachable.
                    if (this.neqStatus == Status.SATISFIABLE) {
                        // We have indication that this partition is NEQ.
                        return Classification.MAYBE_NEQ;
                    } else {
                        // We have no indication that this partition is NEQ.
                        assert this.neqStatus == Status.UNSATISFIABLE;
                        return Classification.EQ;
                    }
                } else {
                    // We have indication that this partition is reachable.
                    assert this.pcStatus == Status.SATISFIABLE;
                    if (this.notPcStatus == Status.UNSATISFIABLE) {
                        // We're sure that this partition is reachable.
                        if (this.neqStatus == Status.SATISFIABLE) {
                            // We have indication hat this partition is NEQ.
                            if (this.eqStatus == Status.UNSATISFIABLE) {
                                // We're sure that this partition is NEQ.
                                return Classification.NEQ;
                            } else {
                                // We're unsure whether this partition is NEQ.
                                assert this.eqStatus == Status.SATISFIABLE || this.eqStatus == Status.UNKNOWN;
                                return Classification.MAYBE_NEQ;
                            }
                        } else {
                            // We have no indication that this partition is NEQ.
                            assert this.neqStatus == Status.UNSATISFIABLE;
                            return Classification.EQ;
                        }
                    } else {
                        // We're unsure whether this partition is reachable.
                        assert this.notPcStatus == Status.SATISFIABLE;
                        if (this.neqStatus == Status.SATISFIABLE) {
                            // We have indication that this partition is NEQ.
                            return Classification.MAYBE_NEQ;
                        } else {
                            // We have no indication that this partition is NEQ.
                            assert this.neqStatus == Status.UNSATISFIABLE;
                            return Classification.EQ;
                            // Note that classifications on the partition level
                            // never result in MAYBE_EQ. This is because MAYBE_EQ
                            // results only arise if we have a *partial* result
                            // without any indication of non-equivalence. Since we
                            // only include partitions once we fully analyzed them
                            // (or mark them as TIMEOUT, DEPTH_LIMITED, etc. if we
                            // cannot (fully) analyze them) such partial results
                            // cannot arise.
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isMissing() {
        return this.classification == Classification.MISSING;
    }

    @Override
    public boolean isBaseToolMissing() {
        return this.classification == Classification.BASE_TOOL_MISSING;
    }

    @Override
    public boolean isError() {
        return this.classification == Classification.ERROR;
    }

    @Override
    public boolean isUnreachable() {
        return this.classification == Classification.UNREACHABLE;
    }

    @Override
    public boolean isTimeout() {
        return this.classification == Classification.TIMEOUT;
    }

    @Override
    public boolean isDepthLimited() {
        return this.classification == Classification.DEPTH_LIMITED;
    }

    @Override
    public boolean isUnknown() {
        return this.classification == Classification.UNKNOWN;
    }

    @Override
    public boolean isMaybeNeq() {
        return this.classification == Classification.MAYBE_NEQ;
    }

    @Override
    public boolean isMaybeEq() {
        return this.classification == Classification.MAYBE_EQ;
    }

    @Override
    public boolean isNeq() {
        return this.classification == Classification.NEQ;
    }

    @Override
    public boolean isEq() {
        return this.classification == Classification.EQ;
    }
}
