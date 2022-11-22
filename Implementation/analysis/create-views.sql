DROP VIEW IF EXISTS run_result_crosstab_true;
DROP VIEW IF EXISTS run_result_crosstab_lenient;
DROP VIEW IF EXISTS run_result_crosstab_strict;

DROP VIEW IF EXISTS run_runtime_overview;

CREATE VIEW IF NOT EXISTS run_result_crosstab_true AS
SELECT run.tool, benchmark.expected,
    count(CASE run.result WHEN 'EQ' THEN 1 END) AS 'EQ',
    count(CASE run.result WHEN 'NEQ' THEN 1 END) AS 'NEQ',
    count(CASE run.result WHEN 'MAYBE_EQ' THEN 1 END) AS 'MAYBE_EQ',
    count(CASE run.result WHEN 'MAYBE_NEQ' THEN 1 END) AS 'MAYBE_NEQ',
    count(CASE run.result WHEN 'UNKNOWN' THEN 1 END) AS 'UNKNOWN',
    count(CASE run.result WHEN 'DEPTH_LIMITED' THEN 1 END) AS 'DEPTH_LIMITED',
    count(CASE run.result WHEN 'TIMEOUT' THEN 1 END) AS 'TIMEOUT',
    count(CASE run.result WHEN 'UNREACHABLE' THEN 1 END) AS 'UNREACHABLE',
    count(CASE run.result WHEN 'ERROR' THEN 1 END) AS 'ERROR',
    count(CASE run.result WHEN 'BASE_TOOL_MISSING' THEN 1 END) AS 'BASE_TOOL_MISSING',
    count(CASE run.result WHEN 'MISSING' THEN 1 END) AS 'MISSING'
FROM run
INNER JOIN benchmark on benchmark.benchmark = run.benchmark
GROUP BY run.tool, benchmark.expected ORDER BY benchmark.expected, run.tool;

CREATE VIEW IF NOT EXISTS run_result_crosstab_lenient AS
SELECT run.tool, benchmark.expected,
    count(CASE run.result WHEN 'EQ' THEN 1 WHEN 'MAYBE_EQ' THEN 1 END) AS 'EQ',
    count(CASE run.result WHEN 'NEQ' THEN 1 WHEN 'MAYBE_NEQ' THEN 1 END) AS 'NEQ',
    count(CASE run.result WHEN 'UNKNOWN' THEN 1 END) AS 'UNKNOWN',
    count(CASE run.result WHEN 'DEPTH_LIMITED' THEN 1 END) AS 'DEPTH_LIMITED',
    count(CASE run.result WHEN 'TIMEOUT' THEN 1 END) AS 'TIMEOUT',
    count(CASE run.result WHEN 'UNREACHABLE' THEN 1 END) AS 'UNREACHABLE',
    count(CASE run.result WHEN 'ERROR' THEN 1 END) AS 'ERROR',
    count(CASE run.result WHEN 'BASE_TOOL_MISSING' THEN 1 END) AS 'BASE_TOOL_MISSING',
    count(CASE run.result WHEN 'MISSING' THEN 1 END) AS 'MISSING'
FROM run
INNER JOIN benchmark on benchmark.benchmark = run.benchmark
GROUP BY run.tool, benchmark.expected ORDER BY benchmark.expected, run.tool;

CREATE VIEW IF NOT EXISTS run_result_crosstab_strict AS
SELECT run.tool, benchmark.expected,
    count(CASE run.result WHEN 'EQ' THEN 1 END) AS 'EQ',
    count(CASE run.result WHEN 'NEQ' THEN 1 END) AS 'NEQ',
    count(CASE run.result WHEN 'UNKNOWN' THEN 1 WHEN 'MAYBE_EQ' THEN 1 WHEN 'MAYBE_NEQ' THEN 1 END) AS 'UNKNOWN',
    count(CASE run.result WHEN 'DEPTH_LIMITED' THEN 1 END) AS 'DEPTH_LIMITED',
    count(CASE run.result WHEN 'TIMEOUT' THEN 1 END) AS 'TIMEOUT',
    count(CASE run.result WHEN 'UNREACHABLE' THEN 1 END) AS 'UNREACHABLE',
    count(CASE run.result WHEN 'ERROR' THEN 1 END) AS 'ERROR',
    count(CASE run.result WHEN 'BASE_TOOL_MISSING' THEN 1 END) AS 'BASE_TOOL_MISSING',
    count(CASE run.result WHEN 'MISSING' THEN 1 END) AS 'MISSING'
FROM run
INNER JOIN benchmark on benchmark.benchmark = run.benchmark
GROUP BY run.tool, benchmark.expected ORDER BY benchmark.expected, run.tool;

CREATE VIEW IF NOT EXISTS run_runtime_overview AS
SELECT run.benchmark, benchmark.expected,
    max(CASE WHEN run.tool = 'ARDiff-base' THEN run.runtime END) AS 'ARDiff-base',
    max(CASE WHEN run.tool = 'ARDiff-diff' THEN run.runtime END) AS 'ARDiff-diff',
    max(CASE WHEN run.tool = 'DSE-base' THEN run.runtime END) AS 'DSE-base',
    max(CASE WHEN run.tool = 'DSE-diff' THEN run.runtime END) AS 'DSE-diff',
    max(CASE WHEN run.tool = 'SE-base' THEN run.runtime END) AS 'SE-base',
    max(CASE WHEN run.tool = 'SE-diff' THEN run.runtime END) AS 'SE-diff'
FROM run
INNER JOIN benchmark ON run.benchmark = benchmark.benchmark
GROUP BY run.benchmark;
