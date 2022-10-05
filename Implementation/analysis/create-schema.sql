-- DROP TABLE IF EXISTS partition_instruction;
-- DROP TABLE IF EXISTS instruction;
-- DROP TABLE IF EXISTS partition;
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
    has_succeeded BOOLEAN,
    is_depth_limited BOOLEAN,
    has_uif BOOLEAN,
    iteration_count INTEGER,
    runtime REAL,
    errors TEXT,

    PRIMARY KEY (benchmark, tool),
    FOREIGN KEY (benchmark) REFERENCES benchmark(benchmark) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS partition
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    partition INTEGER NOT NULL,

    result TEXT,
    has_succeeded BOOLEAN,
    is_depth_limited BOOLEAN,
    has_uif BOOLEAN,
    has_uif_pc BOOLEAN,
    has_uif_v1 BOOLEAN,
    has_uif_v2 BOOLEAN,
    constraint_count INTEGER,
    errors TEXT,

    PRIMARY KEY (benchmark, tool, partition),
    FOREIGN KEY (benchmark, tool) REFERENCES run(benchmark, tool) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS instruction
(
    benchmark TEXT NOT NULL,
    method TEXT NOT NULL,
    instruction_index INTEGER NOT NULL,

    instruction TEXT,
    position INTEGER,
    source_file TEXT,
    source_line INTEGER,

    PRIMARY KEY (benchmark, method, instruction_index),
    FOREIGN KEY (benchmark) REFERENCES benchmark(benchmark) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS partition_instruction
(
    benchmark TEXT NOT NULL,
    tool TEXT NOT NULL,
    partition INTEGER NOT NULL,
    version INTEGER NOT NULL,
    method TEXT NOT NULL,
    instruction_index INTEGER NOT NULL,
    execution_index INTEGER NOT NULL,

    state INTEGER,
    choice INTEGER,

    PRIMARY KEY (benchmark, tool, partition, version, method, instruction_index, execution_index),
    FOREIGN KEY (benchmark, tool, partition) REFERENCES partition(benchmark, tool, partition) ON DELETE CASCADE,
    FOREIGN KEY (benchmark, method, instruction_index) REFERENCES instruction(benchmark, method, instruction_index) ON DELETE CASCADE
);
