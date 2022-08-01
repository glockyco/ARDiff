from string import Template

from sqlalchemy import Column, Text, Boolean, Integer
from sqlalchemy.future.engine import Engine
from sqlalchemy.orm import declarative_base, Session
from sqlalchemy.sql.expression import union_all, select

Base = declarative_base()


class Benchmark(Base):
    __tablename__ = "benchmark"

    name = Column(Text, primary_key=True)
    expected = Column(Text)


class BaseToolResult:
    benchmark = Column(Text, primary_key=True)
    tool = Column(Text, primary_key=True)
    tool_variant = Column(Text, primary_key=True)
    result = Column(Text)
    has_succeeded = Column(Boolean)
    is_depth_limited = Column(Boolean)
    iteration_count = Column(Integer)
    runtime = Column(Integer)
    errors = Column(Text)


class ARDiffBaseResult(Base, BaseToolResult):
    __tablename__ = "ardiff_base"


class DSEBaseResult(Base, BaseToolResult):
    __tablename__ = "dse_base"


class SEBaseResult(Base, BaseToolResult):
    __tablename__ = "se_base"


class DifferencingResult:
    benchmark = Column(Text, primary_key=True)
    tool = Column(Text, primary_key=True)
    tool_variant = Column(Text, primary_key=True)
    result = Column(Text)
    has_succeeded = Column(Boolean)
    has_uninterpreted_functions = Column(Boolean)
    is_depth_limited = Column(Boolean)
    runtime = Column(Integer)
    errors = Column(Text)


class ARDiffDiffResult(Base, DifferencingResult):
    __tablename__ = "ardiff_diff"


class DSEDiffResult(Base, DifferencingResult):
    __tablename__ = "dse_diff"


class SEDiffResult(Base, DifferencingResult):
    __tablename__ = "se_diff"


class PartitionResult:
    benchmark = Column(Text, primary_key=True)
    tool = Column(Text, primary_key=True)
    tool_variant = Column(Text, primary_key=True)
    partition = Column(Integer, primary_key=True)
    result = Column(Text)
    has_uninterpreted_functions = Column(Boolean)
    constraint_count = Column(Integer)
    errors = Column(Text)


class ARDiffPartitionResult(Base, PartitionResult):
    __tablename__ = "ardiff_partition"


class DSEPartitionResult(Base, PartitionResult):
    __tablename__ = "dse_partition"


class SEPartitionResult(Base, PartitionResult):
    __tablename__ = "se_partition"


def create_schema(engine: Engine, session: Session):
    Base.metadata.create_all(engine)

    overall_base_query = union_all(
        select(ARDiffBaseResult),
        select(DSEBaseResult),
        select(SEBaseResult),
    )

    session.execute(f"CREATE VIEW IF NOT EXISTS overall_base AS {overall_base_query}")

    overall_diff_query = union_all(
        select(ARDiffDiffResult),
        select(DSEDiffResult),
        select(SEDiffResult),
    )

    session.execute(f"CREATE VIEW IF NOT EXISTS overall_diff AS {overall_diff_query}")

    overall_partition_query = union_all(
        select(ARDiffPartitionResult),
        select(DSEPartitionResult),
        select(SEPartitionResult),
    )

    session.execute(f"CREATE VIEW IF NOT EXISTS overall_partition AS {overall_partition_query}")

    session.execute("DROP VIEW IF EXISTS overall_results_true")
    session.execute("""
        CREATE VIEW IF NOT EXISTS overall_results_true AS
        SELECT tool_variant, expected, result
            FROM overall_base
            INNER JOIN benchmark ON benchmark = benchmark.name
        UNION ALL
        SELECT tool_variant, expected, result
            FROM overall_diff
            INNER JOIN benchmark ON benchmark = benchmark.name
    """)

    overall_results_view: Template = Template("""
        CREATE VIEW IF NOT EXISTS $view AS
        SELECT tool_variant, expected,
            CASE result
                WHEN 'MAYBE_EQ' THEN '$maybe_eq'
                WHEN 'MAYBE_NEQ' THEN '$maybe_neq'
                ELSE result
            END AS result
        FROM overall_results_true
    """)

    session.execute("DROP VIEW IF EXISTS overall_results_strict")
    session.execute(overall_results_view.substitute(
        view="overall_results_strict",
        maybe_eq="UNKNOWN",
        maybe_neq="UNKNOWN"
    ))

    session.execute("DROP VIEW IF EXISTS overall_results_lenient")
    session.execute(overall_results_view.substitute(
        view="overall_results_lenient",
        maybe_eq="EQ",
        maybe_neq="NEQ"
    ))

    session.execute("DROP VIEW IF EXISTS crosstab_results_true")
    session.execute("""
        CREATE VIEW IF NOT EXISTS crosstab_results_true AS
        SELECT tool_variant, expected,
            COUNT(CASE result WHEN 'EQ' THEN 1 END) AS 'EQ',
            COUNT(CASE result WHEN 'MAYBE_EQ' THEN 1 END) AS "MAYBE_EQ",
            COUNT(CASE result WHEN 'NEQ' THEN 1 END) AS "NEQ",
            COUNT(CASE result WHEN 'MAYBE_NEQ' THEN 1 END) AS "MAYBE_NEQ",
            COUNT(CASE result WHEN 'UNKNOWN' THEN 1 END) AS "UNKNOWN",
            COUNT(CASE result WHEN 'TIMEOUT' THEN 1 END) AS "TIMEOUT",
            COUNT(CASE result WHEN 'ERROR' THEN 1 END) AS "ERROR",
            COUNT(CASE result WHEN 'MISSING' THEN 1 END) AS "MISSING"
        FROM overall_results_true
        GROUP BY tool_variant, expected ORDER BY expected, tool_variant
    """)

    session.execute("DROP VIEW IF EXISTS crosstab_results_strict")
    session.execute("""
        CREATE VIEW IF NOT EXISTS crosstab_results_strict AS
        SELECT 
            tool_variant, expected, EQ, NEQ,
            MAYBE_EQ + MAYBE_NEQ + "UNKNOWN" AS 'UNKNOWN',
            "TIMEOUT", ERROR, MISSING
        FROM crosstab_results_true
    """)

    session.execute("DROP VIEW IF EXISTS crosstab_results_lenient")
    session.execute("""
        CREATE VIEW IF NOT EXISTS crosstab_results_lenient AS
        SELECT 
            tool_variant, expected, 
            EQ + MAYBE_EQ AS EQ,
            NEQ + MAYBE_NEQ as NEQ,
            "UNKNOWN", "TIMEOUT", ERROR, MISSING
        FROM crosstab_results_true
    """)

    session.execute("DROP VIEW IF EXISTS errors")
    session.execute("""
        CREATE VIEW IF NOT EXISTS errors AS
        SELECT benchmark, tool_variant, result, errors FROM overall_base WHERE errors != ''
        UNION ALL
        SELECT benchmark, tool_variant, result, errors FROM overall_diff WHERE errors != ''
        ORDER BY benchmark, tool_variant
    """)

    session.execute("DROP VIEW IF EXISTS overall_diff_metrics")
    session.execute("""
        CREATE VIEW IF NOT EXISTS overall_diff_metrics AS
        SELECT
            diff.benchmark,
            diff.tool_variant,
            bench.expected,
            diff.result,
            diff.runtime,
            metrics.partitions,
            metrics.constraints,
            metrics.avg_constraints,
            metrics.min_constraints,
            metrics.max_constraints,
            metrics.has_uif,
            metrics.has_no_uif,
            metrics.eq,
            metrics.maybe_eq,
            metrics.neq,
            metrics.maybe_neq,
            metrics.unknown,
            metrics.timeout,
            metrics.error,
            metrics.missing
        FROM overall_diff diff
        INNER JOIN benchmark bench ON bench.name = diff.benchmark
        INNER JOIN (
            SELECT
                benchmark, tool_variant,
                COUNT(*) AS partitions,
                SUM(constraint_count) AS constraints,
                ROUND(AVG(constraint_count), 2) AS avg_constraints,
                MIN(constraint_count) AS min_constraints,
                MAX(constraint_count) AS max_constraints,
                SUM(has_uninterpreted_functions) AS has_uif,
                COUNT(*) - SUM(has_uninterpreted_functions) AS has_no_uif,
                COUNT(CASE result WHEN 'EQ' THEN 1 END) AS eq,
                COUNT(CASE result WHEN 'MAYBE_EQ' THEN 1 END) AS maybe_eq,
                COUNT(CASE result WHEN 'NEQ' THEN 1 END) AS neq,
                COUNT(CASE result WHEN 'MAYBE_NEQ' THEN 1 END) AS maybe_neq,
                COUNT(CASE result WHEN 'UNKNOWN' THEN 1 END) AS unknown,
                COUNT(CASE result WHEN 'TIMEOUT' THEN 1 END) AS timeout,
                COUNT(CASE result WHEN 'ERROR' THEN 1 END) AS error,
                COUNT(CASE result WHEN 'MISSING' THEN 1 END) AS missing
            FROM overall_partition GROUP BY tool_variant, benchmark
            ORDER BY tool_variant, benchmark
        ) metrics ON diff.benchmark = metrics.benchmark AND diff.tool_variant = metrics.tool_variant;
    """)

    session.execute("DROP VIEW IF EXISTS overall_runtime_metrics")
    session.execute("""
        CREATE VIEW IF NOT EXISTS overall_runtime_metrics AS
        SELECT
            metrics.benchmark,
            bench.expected,
            MAX(CASE WHEN metrics.tool_variant = 'ARDiff-base' THEN metrics.runtime END) AS 'ARDiff-base',
            MAX(CASE WHEN metrics.tool_variant = 'ARDiff-diff' THEN metrics.runtime END) AS 'ARDiff-diff',
            MAX(CASE WHEN metrics.tool_variant = 'DSE-base' THEN metrics.runtime END) AS 'DSE-base',
            MAX(CASE WHEN metrics.tool_variant = 'DSE-diff' THEN metrics.runtime END) AS 'DSE-diff',
            MAX(CASE WHEN metrics.tool_variant = 'SE-base' THEN metrics.runtime END) AS 'SE-base',
            MAX(CASE WHEN metrics.tool_variant = 'SE-diff' THEN metrics.runtime END) AS 'SE-diff'
        FROM benchmark bench
        INNER JOIN (
            SELECT benchmark, tool, tool_variant, runtime FROM overall_base
            UNION ALL
            SELECT benchmark, tool, tool_variant, runtime FROM overall_diff
        ) metrics ON bench.name = metrics.benchmark
        GROUP BY metrics.benchmark
    """)
