-- DROP TABLE IF EXISTS partition_instruction;
-- DROP TABLE IF EXISTS instruction;
-- DROP TABLE IF EXISTS partition;
-- DROP TABLE IF EXISTS iteration;
-- DROP TABLE IF EXISTS runtime;
-- DROP TABLE IF EXISTS run;
-- DROP TABLE IF EXISTS benchmark;

CREATE TABLE IF NOT EXISTS benchmark
(
    benchmark TEXT NOT NULL,

    expected TEXT,

    PRIMARY KEY (benchmark)
);

CREATE TABLE IF NOT EXISTS run
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,

    result TEXT,
    has_timed_out BOOLEAN,
    is_depth_limited BOOLEAN,
    has_uif BOOLEAN,
    iteration_count INTEGER,
    result_iteration INTEGER,
    runtime REAL,
    errors TEXT,

    PRIMARY KEY (benchmark, tool),
    FOREIGN KEY (benchmark) REFERENCES benchmark(benchmark) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS runtime
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    topic TEXT NOT NULL,
    task TEXT NOT NULL,

    runtime REAL NOT NULL,
    step INTEGER NOT NULL,
    is_missing BOOLEAN NOT NULL,

    PRIMARY KEY (benchmark, tool, topic, task),
    FOREIGN KEY (benchmark, tool) REFERENCES run(benchmark, tool) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS iteration
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,

    result TEXT,
    has_timed_out BOOLEAN,
    is_depth_limited BOOLEAN,
    has_uif BOOLEAN,
    partition_count INTEGER,
    runtime REAL,
    errors TEXT,

    PRIMARY KEY (benchmark, tool, iteration),
    FOREIGN KEY (benchmark, tool) REFERENCES run(benchmark, tool) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS partition
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    partition INTEGER NOT NULL,

    result TEXT,
    pc_status INTEGER,
    pc_model TEXT,
    pc_reason_unknown TEXT,
    pc_statistics TEXT,
    neq_status INTEGER,
    neq_model TEXT,
    neq_reason_unknown TEXT,
    neq_statistics TEXT,
    eq_status INTEGER,
    eq_model TEXT,
    eq_reason_unknown TEXT,
    eq_statistics TEXT,
    has_uif BOOLEAN,
    has_uif_pc BOOLEAN,
    has_uif_v1 BOOLEAN,
    has_uif_v2 BOOLEAN,
    constraint_count INTEGER,
    runtime REAL,
    errors TEXT,

    PRIMARY KEY (benchmark, tool, iteration, partition),
    FOREIGN KEY (benchmark, tool, iteration) REFERENCES iteration(benchmark, tool, iteration) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS instruction
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    method TEXT NOT NULL,
    instruction_index INTEGER NOT NULL,

    instruction TEXT,
    position INTEGER,
    source_file TEXT,
    source_line INTEGER,

    PRIMARY KEY (benchmark, tool, iteration, method, instruction_index),
    FOREIGN KEY (benchmark, tool, iteration) REFERENCES iteration(benchmark, tool, iteration) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS partition_instruction
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    partition INTEGER NOT NULL,
    version INTEGER NOT NULL,
    method TEXT NOT NULL,
    instruction_index INTEGER NOT NULL,
    execution_index INTEGER NOT NULL,

    state INTEGER,
    choice INTEGER,

    PRIMARY KEY (benchmark, tool, iteration, partition, version, method, instruction_index, execution_index),
    FOREIGN KEY (benchmark, tool, iteration, partition) REFERENCES partition(benchmark, tool, iteration, partition) ON DELETE CASCADE,
    FOREIGN KEY (benchmark, tool, iteration, method, instruction_index) REFERENCES instruction(benchmark, tool, iteration, method, instruction_index) ON DELETE CASCADE
);
