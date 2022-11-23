DROP VIEW IF EXISTS run_result_crosstab_true;
DROP VIEW IF EXISTS run_result_crosstab_lenient;
DROP VIEW IF EXISTS run_result_crosstab_strict;

DROP VIEW IF EXISTS run_runtime_overview;

DROP VIEW IF EXISTS run_reducibility_statistics;

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

CREATE VIEW IF NOT EXISTS run_reducibility_statistics AS
WITH reducibility_overview AS
(
    WITH
        run_reducibility_by_tool_and_expected_and_is_fully_analyzed AS
        (
            SELECT
                tool AS tool,
                expected AS expected,
                is_fully_analyzed AS is_fully_analyzed,
                count(*) AS '#_runs',
                coalesce(sum(CASE WHEN result = 'EQ' THEN 1 END), 0) AS '#_EQ',
                sum(CASE WHEN result != 'EQ' THEN 1 END) AS '#_!EQ',
                sum(CASE WHEN result != 'EQ' AND is_reducible = 0 THEN 1 END) AS '#_!is_reducible',
                sum(is_only_NEQ) AS "#_is_only_NEQ",
                sum(is_only_UNKNOWN) AS "#_is_only_UNKNOWN",
                sum(is_mixed_NEQ_UNKNOWN) AS "#_is_mixed_NEQ_UNKNOWN",
                sum(is_reducible) AS '#_is_reducible',
                sum(are_partitions_reducible) AS '#_are_partitions_reducible',
                sum(CASE WHEN are_partitions_reducible = 1 THEN "#_partitions" END) AS "#_partitions",
                nullif(sum(CASE WHEN are_partitions_reducible = 1 THEN "#_partitions_EQ" ELSE 0 END), 0) AS '#_partitions_reducible',
                nullif(sum(CASE WHEN are_partitions_reducible = 1 THEN "#_partitions" - "#_partitions_EQ" ELSE 0 END), 0) AS '#_partitions_!reducible',
                sum(are_lines_reducible) AS '#_are_lines_reducible',
                sum(CASE WHEN are_lines_reducible = 1 THEN "#_lines" END) AS "#_lines",
                nullif(sum(CASE WHEN are_lines_reducible = 1 THEN "#_lines_only_EQ" ELSE 0 END), 0) AS "#_lines_reducible",
                nullif(sum(CASE WHEN are_lines_reducible = 1 THEN "#_lines" - "#_lines_only_EQ" ELSE 0 END), 0) AS "#_lines_!reducible"
            FROM mv_run_features
            GROUP BY tool, expected, is_fully_analyzed
            ORDER BY tool, expected, is_fully_analyzed DESC
        ),
        run_reducibility_by_tool_and_is_fully_analyzed AS
        (
            SELECT
                tool,
                '' AS expected,
                is_fully_analyzed AS is_fully_analyzed,
                sum("#_runs") AS '#_runs',
                sum("#_EQ") AS '#_EQ',
                sum("#_!EQ") AS '#_!EQ',
                sum("#_!is_reducible") AS '#_!is_reducible',
                sum("#_is_only_NEQ") AS "#_is_only_NEQ",
                sum("#_is_only_UNKNOWN") AS "#_is_only_UNKNOWN",
                sum("#_is_mixed_NEQ_UNKNOWN") AS "#_is_mixed_NEQ_UNKNOWN",
                sum("#_is_reducible") AS '#_is_reducible',
                sum("#_are_partitions_reducible") AS '#_are_partitions_reducible',
                sum("#_partitions") AS '#_partitions',
                sum("#_partitions_reducible") AS '#_partitions_reducible',
                sum("#_partitions_!reducible") AS '#_partitions_!reducible',
                sum("#_are_lines_reducible") AS '#_are_lines_reducible',
                sum("#_lines") AS '#_lines',
                sum("#_lines_reducible") AS '#_lines_reducible',
                sum("#_lines_!reducible") AS '#_lines_!reducible'
            FROM run_reducibility_by_tool_and_expected_and_is_fully_analyzed
            GROUP BY tool, is_fully_analyzed
            ORDER BY tool, is_fully_analyzed DESC
        ),
        run_reducibility_by_tool_and_expected AS
        (
            SELECT
                tool,
                expected AS expected,
                '' AS is_fully_analyzed,
                sum("#_runs") AS '#_runs',
                sum("#_EQ") AS '#_EQ',
                sum("#_!EQ") AS '#_!EQ',
                sum("#_!is_reducible") AS '#_!is_reducible',
                sum("#_is_only_NEQ") AS "#_is_only_NEQ",
                sum("#_is_only_UNKNOWN") AS "#_is_only_UNKNOWN",
                sum("#_is_mixed_NEQ_UNKNOWN") AS "#_is_mixed_NEQ_UNKNOWN",
                sum("#_is_reducible") AS '#_is_reducible',
                sum("#_are_partitions_reducible") AS '#_are_partitions_reducible',
                sum("#_partitions") AS '#_partitions',
                sum("#_partitions_reducible") AS '#_partitions_reducible',
                sum("#_partitions_!reducible") AS '#_partitions_!reducible',
                sum("#_are_lines_reducible") AS '#_are_lines_reducible',
                sum("#_lines") AS '#_lines',
                sum("#_lines_reducible") AS '#_lines_reducible',
                sum("#_lines_!reducible") AS '#_lines_!reducible'
            FROM run_reducibility_by_tool_and_expected_and_is_fully_analyzed
            GROUP BY tool, expected
            ORDER BY tool, expected
        ),
        run_reducibility_by_tool AS
        (
            SELECT
                tool,
                '' AS expected,
                '' AS is_fully_analyzed,
                sum("#_runs") AS '#_runs',
                sum("#_EQ") AS '#_EQ',
                sum("#_!EQ") AS '#_!EQ',
                sum("#_!is_reducible") AS '#_!is_reducible',
                sum("#_is_only_NEQ") AS "#_is_only_NEQ",
                sum("#_is_only_UNKNOWN") AS "#_is_only_UNKNOWN",
                sum("#_is_mixed_NEQ_UNKNOWN") AS "#_is_mixed_NEQ_UNKNOWN",
                sum("#_is_reducible") AS '#_is_reducible',
                sum("#_are_partitions_reducible") AS '#_are_partitions_reducible',
                sum("#_partitions") AS '#_partitions',
                sum("#_partitions_reducible") AS '#_partitions_reducible',
                sum("#_partitions_!reducible") AS '#_partitions_!reducible',
                sum("#_are_lines_reducible") AS '#_are_lines_reducible',
                sum("#_lines") AS '#_lines',
                sum("#_lines_reducible") AS '#_lines_reducible',
                sum("#_lines_!reducible") AS '#_lines_!reducible'
            FROM run_reducibility_by_tool_and_expected_and_is_fully_analyzed
            GROUP BY tool
            ORDER BY tool
        )
    SELECT * FROM run_reducibility_by_tool
    UNION ALL
    SELECT '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', ''
    UNION ALL
    SELECT * FROM run_reducibility_by_tool_and_expected
    UNION ALL
    SELECT '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', ''
    UNION ALL
    SELECT * FROM run_reducibility_by_tool_and_is_fully_analyzed
    UNION ALL
    SELECT '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', ''
    UNION ALL
    SELECT * FROM run_reducibility_by_tool_and_expected_and_is_fully_analyzed
)
SELECT
    tool AS tool,
    expected AS expected,
    is_fully_analyzed AS is_fully_analyzed,
    "#_runs",
    "#_EQ",
    "#_!EQ",
    "#_!is_reducible" AS "#_!is_reducible",
    "#_is_reducible",
    coalesce(round(("#_!is_reducible" * 1.0 / "#_!EQ") * 100, 2), '') AS '%_!i_r',
    coalesce(round(("#_is_reducible" * 1.0 / "#_!EQ") * 100, 2), '') AS '%_i_r',
    '' AS '|',
    tool AS tool,
    expected AS expected,
    is_fully_analyzed AS is_fully_analyzed,
    "#_!is_reducible" AS "#_!is_reducible",
    "#_is_only_NEQ",
    "#_is_only_UNKNOWN",
    "#_is_mixed_NEQ_UNKNOWN",
    coalesce(round(("#_is_only_NEQ" * 1.0 / "#_!is_reducible") * 100, 2), '') AS '%_o_N',
    coalesce(round(("#_is_only_UNKNOWN" * 1.0 / "#_!is_reducible") * 100, 2), '') AS '%_o_U',
    coalesce(round(("#_is_mixed_NEQ_UNKNOWN" * 1.0 / "#_!is_reducible") * 100, 2), '') AS '%_m_N_U',
    '' AS '|',
    tool AS tool,
    expected AS expected,
    is_fully_analyzed AS is_fully_analyzed,
    "#_are_partitions_reducible",
    coalesce(round(("#_are_partitions_reducible" * 1.0 / "#_!EQ") * 100, 2), '') AS '%_a_p_r',
    "#_partitions",
    "#_partitions_reducible",
    "#_partitions_!reducible",
    '' AS '|',
    tool AS tool,
    expected AS expected,
    is_fully_analyzed AS is_fully_analyzed,
    "#_are_lines_reducible",
    coalesce(round(("#_are_lines_reducible" * 1.0 / "#_!EQ") * 100, 2), '') AS '%_a_l_r',
    "#_lines",
    "#_lines_reducible",
    "#_lines_!reducible"
FROM reducibility_overview;
