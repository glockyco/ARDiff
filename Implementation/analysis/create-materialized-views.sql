DROP TABLE IF EXISTS mv_run_features;
DROP TABLE IF EXISTS mv_iteration_features;
DROP TABLE IF EXISTS mv_partition_features;
DROP TABLE IF EXISTS mv_line_features;
DROP TABLE IF EXISTS mv_partition_line;
DROP TABLE IF EXISTS mv_line;

---

CREATE TABLE IF NOT EXISTS mv_line
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration TEXT NOT NULL,
    source_file TEXT NOT NULL,
    source_line INTEGER NOT NULL,
    ---
    '#_instructions' INTEGER NOT NULL,
    ---
    PRIMARY KEY (benchmark, tool, iteration, source_file, source_line),
    FOREIGN KEY (benchmark, tool, iteration)
        REFERENCES iteration(benchmark, tool, iteration)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mv_partition_line
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    partition INTEGER NOT NULL,
    source_file TEXT NOT NULL,
    source_line INTEGER NOT NULL,
    ---
    PRIMARY KEY (benchmark, tool, iteration, partition, source_file, source_line),
    FOREIGN KEY (benchmark, tool, iteration, partition)
        REFERENCES partition(benchmark, tool, iteration, partition)
        ON DELETE CASCADE,
    FOREIGN KEY (benchmark, tool, iteration, source_file, source_line)
        REFERENCES mv_line(benchmark, tool, iteration, source_file, source_line)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mv_line_features
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    source_file TEXT NOT NULL,
    source_line INTEGER NOT NULL,
    ---
    is_base BOOLEAN NOT NULL,
    is_diff BOOLEAN NOT NULL,
    ---
    '#_instructions' INTEGER NOT NULL,
    ---
    '#_partitions' INTEGER NOT NULL,
    '#_partitions_EQ' INTEGER NOT NULL,
    '#_partitions_NEQ' INTEGER NOT NULL,
    '#_partitions_UNDECIDED' INTEGER NOT NULL,
    '#_partitions_MAYBE_EQ' INTEGER NOT NULL,
    '#_partitions_MAYBE_NEQ' INTEGER NOT NULL,
    '#_partitions_UNKNOWN' INTEGER NOT NULL,
    '#_partitions_DEPTH_LIMITED' INTEGER NOT NULL,
    ---
    has_EQ BOOLEAN NOT NULL,
    has_NEQ BOOLEAN NOT NULL,
    has_UNDECIDED BOOLEAN NOT NULL,
    has_MAYBE_EQ BOOLEAN NOT NULL,
    has_MAYBE_NEQ BOOLEAN NOT NULL,
    has_UNKNOWN BOOLEAN NOT NULL,
    has_DEPTH_LIMITED BOOLEAN NOT NULL,
    ---
    is_non_mixed BOOLEAN NOT NULL,
    has_only_EQ BOOLEAN NOT NULL,
    has_only_NEQ BOOLEAN NOT NULL,
    has_only_UNDECIDED BOOLEAN NOT NULL,
    ---
    is_mixed BOOLEAN NOT NULL,
    is_mixed_EQ_NEQ BOOLEAN NOT NULL,
    is_mixed_EQ_UNDECIDED BOOLEAN NOT NULL,
    is_mixed_NEQ_UNDECIDED BOOLEAN NOT NULL,
    is_mixed_EQ_NEQ_UNDECIDED BOOLEAN NOT NULL,
    ---
    PRIMARY KEY (benchmark, tool, iteration, source_file, source_line),
    FOREIGN KEY (benchmark, tool, iteration, source_file, source_line)
        REFERENCES mv_line(benchmark, tool, iteration, source_file, source_line)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mv_partition_features
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    partition INTEGER NOT NULL,
    ---
    result TEXT, -- Can be NULL.
    pc_status INTEGER, -- Can be NULL.
    neq_status INTEGER, -- Can be NULL.
    eq_status INTEGER, -- Can be NULL.
    has_uif BOOLEAN, -- Can be NULL.
    has_uif_pc BOOLEAN, -- Can be NULL.
    has_uif_v1 BOOLEAN, -- Can be NULL.
    has_uif_v2 BOOLEAN, -- Can be NULL.
    constraint_count INTEGER, -- Can be NULL.
    runtime REAL, -- Can be NULL.
    errors TEXT, -- Can be NULL.
    ---
    is_base BOOLEAN NOT NULL,
    is_diff BOOLEAN NOT NULL,
    ---
    "#_lines_iteration" INTEGER NOT NULL,
    "#_lines_partition" INTEGER NOT NULL,
    "%_line_coverage" REAL NOT NULL,
    "#_instructions_partition" INTEGER NOT NULL,
    ---
    PRIMARY KEY (benchmark, tool, iteration, partition),
    FOREIGN KEY (benchmark, tool, iteration, partition)
        REFERENCES partition(benchmark, tool, iteration, partition)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mv_iteration_features
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    ---
    has_timed_out BOOLEAN NOT NULL,
    is_depth_limited BOOLEAN, -- Can be NULL (if result is TIMEOUT or ERROR).
    has_uif BOOLEAN, -- Can be NULL (if result is TIMEOUT or ERROR).
    runtime REAL, -- Can be NULL (if result is BASE_TOOL_MISSING).
    errors TEXT NOT NULL,
    ---
    is_base BOOLEAN NOT NULL,
    is_diff BOLEAN NOT NULL,
    ---
    expected TEXT NOT NULL,
    result TEXT NOT NULL,
    ---
    is_correct BOOLEAN NOT NULL,
    is_incorrect BOOLEAN NOT NULL,
    is_undecided BOOLEAN NOT NULL,
    ---
    is_fully_analyzed BOOLEAN NOT NULL,
    ---
    is_reducible BOOLEAN NOT NULL,
    are_partitions_reducible BOOLEAN NOT NULL,
    are_lines_reducible BOOLEAN NOT NULL,
    ---
    has_EQ BOOLEAN NOT NULL,
    has_NEQ BOOLEAN NOT NULL,
    has_UNDECIDED BOOLEAN NOT NULL,
    ---
    is_non_mixed BOOLEAN NOT NULL,
    has_only_EQ BOOLEAN NOT NULL,
    has_only_NEQ BOOLEAN NOT NULL,
    has_only_UNDECIDED BOOLEAN NOT NULL,
    ---
    is_mixed BOOLEAN NOT NULL,
    is_mixed_EQ_NEQ BOOLEAN NOT NULL,
    is_mixed_EQ_UNDECIDED BOOLEAN NOT NULL,
    is_mixed_NEQ_UNDECIDED BOOLEAN NOT NULL,
    is_mixed_EQ_NEQ_UNDECIDED BOOLEAN NOT NULL,
    ---
    '#_partitions' INTEGER, -- Can be NULL.
    ---
    '#_partitions_EQ' INTEGER, -- Can be NULL.
    '#_partitions_NEQ' INTEGER, -- Can be NULL.
    "#_partitions_UNDECIDED" INTEGER, -- Can be NULL.
    "#_partitions_MAYBE_EQ" INTEGER, -- Can be NULL.
    "#_partitions_MAYBE_NEQ" INTEGER, -- Can be NULL.
    "#_partitions_UNKNOWN" INTEGER, -- Can be NULL.
    "#_partitions_DEPTH_LIMITED" INTEGER, -- Can be NULL.
    ---
    '%_partitions_EQ' REAL, -- Can be NULL.
    '%_partitions_NEQ' REAL, -- Can be NULL.
    "%_partitions_UNDECIDED" REAL, -- Can be NULL.
    "%_partitions_MAYBE_EQ" REAL, -- Can be NULL.
    "%_partitions_MAYBE_NEQ" REAL, -- Can be NULL.
    "%_partitions_UNKNOWN" REAL, -- Can be NULL.
    "%_partitions_DEPTH_LIMITED" REAL, -- Can be NULL.
    --
    '#_lines_all_partitions' INTEGER, -- Can be NULL.
    ---
    '#_lines_EQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_NEQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_UNDECIDED_partitions' INTEGER, -- Can be NULL.
    '#_lines_MAYBE_EQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_MAYBE_NEQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_UNKNOWN_partitions' INTEGER, -- Can be NULL.
    '#_lines_DEPTH_LIMITED_partitions' INTEGER, -- Can be NULL.
    ---
    "#_lines_per_partition" INTEGER, -- Can be NULL.
    "#_lines_per_EQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_NEQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_UNDECIDED_partition" INTEGER, -- Can be NULL.
    "#_lines_per_MAYBE_EQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_MAYBE_NEQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_UNKNOWN_partition" INTEGER, -- Can be NULL.
    "#_lines_per_DEPTH_LIMITED_partition" INTEGER, -- Can be NULL.
    ---
    "%_line_coverage_per_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_EQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_NEQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_UNDECIDED_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_MAYBE_EQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_MAYBE_NEQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_UNKNOWN_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_DEPTH_LIMITED_partition" REAL, -- Can be NULL.
    ---
    '#_lines' INTEGER, -- Can be NULL.
    ---
    '#_lines_EQ' INTEGER, -- Can be NULL.
    '#_lines_NEQ' INTEGER, -- Can be NULL.
    "#_lines_UNDECIDED" INTEGER, -- Can be NULL.
    '#_lines_non_mixed' INTEGER, -- Can be NULL.
    '#_lines_only_EQ' INTEGER, -- Can be NULL.
    '#_lines_only_NEQ' INTEGER, -- Can be NULL.
    "#_lines_only_UNDECIDED" INTEGER, -- Can be NULL.
    '#_lines_mixed' INTEGER, -- Can be NULL.
    '#_lines_mixed_EQ_NEQ' INTEGER, -- Can be NULL.
    "#_lines_mixed_EQ_UNDECIDED" INTEGER, -- Can be NULL.
    "#_lines_mixed_NEQ_UNDECIDED" INTEGER, -- Can be NULL.
    "#_lines_mixed_EQ_NEQ_UNDECIDED" INTEGER, -- Can be NULL.
    ---
    '%_lines_EQ' REAL, -- Can be NULL.
    '%_lines_NEQ' REAL, -- Can be NULL.
    "%_lines_UNDECIDED" REAL, -- Can be NULL.
    '%_lines_non_mixed' REAL, -- Can be NULL.
    '%_lines_only_EQ' REAL, -- Can be NULL.
    '%_lines_only_NEQ' REAL, -- Can be NULL.
    "%_lines_only_UNDECIDED" REAL, -- Can be NULL.
    '%_lines_mixed' REAL, -- Can be NULL.
    '%_lines_mixed_EQ_NEQ' REAL, -- Can be NULL.
    "%_lines_mixed_EQ_UNDECIDED" REAL, -- Can be NULL.
    "%_lines_mixed_NEQ_UNDECIDED" REAL, -- Can be NULL.
    "%_lines_mixed_EQ_NEQ_UNDECIDED" REAL, -- Can be NULL.
    ---
    PRIMARY KEY (benchmark, tool, iteration),
    FOREIGN KEY (benchmark, tool)
        REFERENCES run(benchmark, tool)
        ON DELETE CASCADE
);

---

CREATE TABLE IF NOT EXISTS mv_run_features
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    ---
    has_timed_out BOOLEAN NOT NULL,
    is_depth_limited BOOLEAN, -- Can be NULL (if result is TIMEOUT or ERROR).
    has_uif BOOLEAN, -- Can be NULL (if result is TIMEOUT or ERROR).
    runtime REAL, -- Can be NULL (if result is BASE_TOOL_MISSING).
    errors TEXT NOT NULL,
    ---
    is_base BOOLEAN NOT NULL,
    is_diff BOLEAN NOT NULL,
    ---
    "#_iterations" INTEGER NOT NULL,
    "result_iteration" INTEGER NOT NULL,
    ---
    expected TEXT NOT NULL,
    result TEXT NOT NULL,
    ---
    is_correct BOOLEAN NOT NULL,
    is_incorrect BOOLEAN NOT NULL,
    is_undecided BOOLEAN NOT NULL,
    ---
    is_fully_analyzed BOOLEAN NOT NULL,
    ---
    is_reducible BOOLEAN NOT NULL,
    are_partitions_reducible BOOLEAN NOT NULL,
    are_lines_reducible BOOLEAN NOT NULL,
    ---
    has_EQ BOOLEAN NOT NULL,
    has_NEQ BOOLEAN NOT NULL,
    has_UNDECIDED BOOLEAN NOT NULL,
    ---
    is_non_mixed BOOLEAN NOT NULL,
    has_only_EQ BOOLEAN NOT NULL,
    has_only_NEQ BOOLEAN NOT NULL,
    has_only_UNDECIDED BOOLEAN NOT NULL,
    ---
    is_mixed BOOLEAN NOT NULL,
    is_mixed_EQ_NEQ BOOLEAN NOT NULL,
    is_mixed_EQ_UNDECIDED BOOLEAN NOT NULL,
    is_mixed_NEQ_UNDECIDED BOOLEAN NOT NULL,
    is_mixed_EQ_NEQ_UNDECIDED BOOLEAN NOT NULL,
    ---
    '#_partitions' INTEGER, -- Can be NULL.
    ---
    '#_partitions_EQ' INTEGER, -- Can be NULL.
    '#_partitions_NEQ' INTEGER, -- Can be NULL.
    "#_partitions_UNDECIDED" INTEGER, -- Can be NULL.
    '#_partitions_MAYBE_EQ' INTEGER, -- Can be NULL.
    '#_partitions_MAYBE_NEQ' INTEGER, -- Can be NULL.
    '#_partitions_UNKNOWN' INTEGER, -- Can be NULL.
    '#_partitions_DEPTH_LIMITED' INTEGER, -- Can be NULL.
    ---
    '%_partitions_EQ' REAL, -- Can be NULL.
    '%_partitions_NEQ' REAL, -- Can be NULL.
    "%_partitions_UNDECIDED" REAL, -- Can be NULL.
    "%_partitions_MAYBE_EQ" REAL, -- Can be NULL.
    "%_partitions_MAYBE_NEQ" REAL, -- Can be NULL.
    "%_partitions_UNKNOWN" REAL, -- Can be NULL.
    "%_partitions_DEPTH_LIMITED" REAL, -- Can be NULL.
    ---
    '#_lines_all_partitions' INTEGER, -- Can be NULL.
    ---
    '#_lines_EQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_NEQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_UNDECIDED_partitions' INTEGER, -- Can be NULL.
    '#_lines_MAYBE_EQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_MAYBE_NEQ_partitions' INTEGER, -- Can be NULL.
    '#_lines_UNKNOWN_partitions' INTEGER, -- Can be NULL.
    '#_lines_DEPTH_LIMITED_partitions' INTEGER, -- Can be NULL.
    ---
    "#_lines_per_partition" INTEGER, -- Can be NULL.
    "#_lines_per_EQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_NEQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_UNDECIDED_partition" INTEGER, -- Can be NULL.
    "#_lines_per_MAYBE_EQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_MAYBE_NEQ_partition" INTEGER, -- Can be NULL.
    "#_lines_per_UNKNOWN_partition" INTEGER, -- Can be NULL.
    "#_lines_per_DEPTH_LIMITED_partition" INTEGER, -- Can be NULL.
    ---
    "%_line_coverage_per_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_EQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_NEQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_UNDECIDED_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_MAYBE_EQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_MAYBE_NEQ_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_UNKNOWN_partition" REAL, -- Can be NULL.
    "%_line_coverage_per_DEPTH_LIMITED_partition" REAL, -- Can be NULL.
    ---
    '#_lines' INTEGER, -- Can be NULL.
    ---
    '#_lines_EQ' INTEGER, -- Can be NULL.
    '#_lines_NEQ' INTEGER, -- Can be NULL.
    "#_lines_UNDECIDED" INTEGER, -- Can be NULL.
    '#_lines_non_mixed' INTEGER, -- Can be NULL.
    '#_lines_only_EQ' INTEGER, -- Can be NULL.
    '#_lines_only_NEQ' INTEGER, -- Can be NULL.
    "#_lines_only_UNDECIDED" INTEGER, -- Can be NULL.
    '#_lines_mixed' INTEGER, -- Can be NULL.
    '#_lines_mixed_EQ_NEQ' INTEGER, -- Can be NULL.
    "#_lines_mixed_EQ_UNDECIDED" INTEGER, -- Can be NULL.
    "#_lines_mixed_NEQ_UNDECIDED" INTEGER, -- Can be NULL.
    "#_lines_mixed_EQ_NEQ_UNDECIDED" INTEGER, -- Can be NULL.
    ---
    '%_lines_EQ' REAL, -- Can be NULL.
    '%_lines_NEQ' REAL, -- Can be NULL.
    "%_lines_UNDECIDED" REAL, -- Can be NULL.
    '%_lines_non_mixed' REAL, -- Can be NULL.
    '%_lines_only_EQ' REAL, -- Can be NULL.
    '%_lines_only_NEQ' REAL, -- Can be NULL.
    "%_lines_only_UNDECIDED" REAL, -- Can be NULL.
    '%_lines_mixed' REAL, -- Can be NULL.
    '%_lines_mixed_EQ_NEQ' REAL, -- Can be NULL.
    "%_lines_mixed_EQ_UNDECIDED" REAL, -- Can be NULL.
    "%_lines_mixed_NEQ_UNDECIDED" REAL, -- Can be NULL.
    "%_lines_mixed_EQ_NEQ_UNDECIDED" REAL, -- Can be NULL.
    ---
    PRIMARY KEY (benchmark, tool),
    FOREIGN KEY (benchmark, tool)
        REFERENCES run(benchmark, tool)
        ON DELETE CASCADE
);

