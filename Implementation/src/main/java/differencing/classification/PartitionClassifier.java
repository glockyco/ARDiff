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
            // some (non-)equivalence information MUST also be provided.
            assert this.neqStatus != null;

            // If neqStatus is SAT or UNKNOWN, eqStatus MUST also be provided.
            assert this.neqStatus == Status.UNSATISFIABLE || this.eqStatus != null;

            // This partition is either REACHABLE or MAYBE_REACHABLE.
            // If it is REACHABLE, NEQ results stay NEQ.
            // If it is MAYBE_REACHABLE, NEQ results become MAYBE_NEQ.
            boolean isReachable = pcStatus == Status.SATISFIABLE && notPcStatus == Status.UNSATISFIABLE;

            if (neqStatus == Status.UNSATISFIABLE) {
                // We have no indication that this partition is NEQ.
                return Classification.EQ;
            } else if (neqStatus == Status.SATISFIABLE) {
                // We have indication that this partition is NEQ.
                if (eqStatus == Status.UNSATISFIABLE) {
                    // We have no indication that this partition is EQ.
                    // NEQ = t && EQ = f => NEQ.
                    if (isReachable) {
                        // We know that this partition is reachable.
                        // NEQ && REACHABLE => NEQ
                        return Classification.NEQ;
                    } else {
                        // We're unsure whether this partition is reachable.
                        // NEQ && MAYBE_REACHABLE => MAYBE_NEQ
                        return Classification.MAYBE_NEQ;
                    }
                } else {
                    // We have indication that this partition is EQ,
                    // or we're unsure whether this partition is EQ.
                    // NEQ = t && (EQ = t || EQ = u) => MAYBE_NEQ
                    assert eqStatus == Status.SATISFIABLE || eqStatus == Status.UNKNOWN;
                    return Classification.MAYBE_NEQ;
                }
            } else {
                // We're unsure whether this partition is NEQ.
                assert this.neqStatus == Status.UNKNOWN;
                if (eqStatus == Status.UNSATISFIABLE) {
                    // We have no indication that this partition is EQ.
                    // NEQ = u && EQ = f => NEQ.
                    if (isReachable) {
                        // We know that this partition is reachable.
                        // NEQ && REACHABLE => NEQ
                        return Classification.NEQ;
                    } else {
                        // We're unsure whether this partition is reachable.
                        // NEQ && MAYBE_REACHABLE => MAYBE_NEQ
                        return Classification.MAYBE_NEQ;
                    }
                } else if (eqStatus == Status.SATISFIABLE) {
                    // We have indication that this partition is EQ.
                    // NEQ = u && EQ = t => MAYBE_EQ
                    return Classification.MAYBE_EQ;
                } else {
                    // We're unsure whether this partition is EQ.
                    // NEQ = u && EQ = u => UNKNOWN
                    assert eqStatus == Status.UNKNOWN;
                    return Classification.UNKNOWN;
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
