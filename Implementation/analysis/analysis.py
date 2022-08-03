import glob
import os
import re
from abc import ABC, abstractmethod
from collections import defaultdict
from enum import Enum
from pathlib import Path
from typing import List, Dict, Optional, Any, Set

import pandas as pd
from sqlalchemy import inspect, text
from sqlalchemy.engine.reflection import Inspector
from sqlalchemy.future.engine import Engine, create_engine, Connection
from sqlalchemy.orm import Session, sessionmaker

from model import create_schema

CURRENT_DIR: Path = Path(os.path.dirname(os.path.realpath(__file__)))
ROOT_DIR: Path = CURRENT_DIR / ".." / ".."
BENCHMARKS_DIR: Path = ROOT_DIR / "benchmarks"
ANALYSIS_DIR: Path = ROOT_DIR / "Implementation" / "analysis"
RESULTS_DIR: Path = ANALYSIS_DIR / "results"


class Classification(Enum):
    MISSING = 1
    ERROR = 2
    TIMEOUT = 3

    UNREACHABLE = 4

    UNKNOWN = 5

    # MAYBE_NEQ:
    # Equivalence checking found the two programs to be NEQ, but:
    # (i) there were uninterpreted functions in the solver query AND
    # (ii) at least one input assignment exists for which the programs are EQ.
    # Thus, the base programs without uninterpreted functions might actually
    # be EQ rather than NEQ if the NEQ results only arise due to the
    # introduction of uninterpreted functions.
    MAYBE_NEQ = 6

    # MAYBE_EQ:
    # Equivalence checking found the two programs to be EQ, but the symbolic
    # execution hit the search depth limit. Thus, the programs might be found
    # to be NEQ rather than EQ when using a sufficiently large search depth.
    MAYBE_EQ = 7

    NEQ = 8
    EQ = 9

    def is_success(self):
        return self in [
            Classification.EQ,
            Classification.NEQ,
            Classification.MAYBE_EQ,
            Classification.MAYBE_NEQ,
            Classification.UNKNOWN,
            Classification.UNREACHABLE,
        ]

    def is_failure(self):
        return not self.is_success()


class Benchmark:
    def __init__(self, root_dir: Path):
        self._root_dir: Path = root_dir
        self._name: str = str(self._root_dir.relative_to(BENCHMARKS_DIR))

    def path(self) -> Path:
        return self._root_dir

    def expected_result(self) -> Classification:
        relpath = os.path.join(self._name, "")

        if f"{os.sep}Eq{os.sep}" in relpath:
            return Classification.EQ
        elif f"{os.sep}NEq{os.sep}" in relpath:
            return Classification.NEQ

        error = f"Unable to infer expected classification for {self._name}."
        raise Exception(error)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": str(self._name),
            "expected": self.expected_result().name,
        }


class BenchmarkResult(ABC):
    def __init__(self, root_dir: Path, tool_name: str):
        self._root_dir: Path = root_dir
        self._tool_name: str = tool_name
        self._result: Optional[Classification] = None

    def __str__(self) -> str:
        return f"{self.result().name} - {self.benchmark()} - {self.name()}"

    def benchmark(self) -> str:
        return os.path.relpath(self._root_dir, BENCHMARKS_DIR)

    @abstractmethod
    def name(self) -> str:
        pass

    def tool_name(self) -> str:
        return self._tool_name

    def result(self) -> Classification:
        if self._result is not None:
            return self._result

        if self.is_missing():
            self._result = Classification.MISSING
        elif self.is_error():
            self._result = Classification.ERROR
        elif self.is_timeout():
            self._result = Classification.TIMEOUT
        elif self.is_unreachable():
            self._result = Classification.UNREACHABLE
        elif self.is_unknown():
            self._result = Classification.UNKNOWN
        elif self.is_maybe_neq():
            self._result = Classification.MAYBE_NEQ
        elif self.is_maybe_eq():
            self._result = Classification.MAYBE_EQ
        elif self.is_neq():
            self._result = Classification.NEQ
        elif self.is_eq():
            self._result = Classification.EQ

        if self._result is not None:
            return self._result

        raise Exception(f"Unable to classify {self.benchmark()} - {self.tool_name()} - {self.name()}.")

    def has_succeeded(self) -> bool:
        return self.result().is_success()

    def has_failed(self) -> bool:
        return self.result().is_failure()

    @abstractmethod
    def is_missing(self) -> bool:
        pass

    @abstractmethod
    def is_error(self) -> bool:
        pass

    @abstractmethod
    def is_timeout(self) -> bool:
        pass

    @abstractmethod
    def is_unreachable(self) -> bool:
        pass

    @abstractmethod
    def is_unknown(self) -> bool:
        pass

    @abstractmethod
    def is_maybe_neq(self) -> bool:
        pass

    @abstractmethod
    def is_maybe_eq(self) -> bool:
        pass

    @abstractmethod
    def is_neq(self) -> bool:
        pass

    @abstractmethod
    def is_eq(self) -> bool:
        pass

    @abstractmethod
    def errors(self) -> str:
        pass


class VersionResult(BenchmarkResult):
    def __init__(self, root_dir: Path, tool_name: str, version_name: str):
        super().__init__(root_dir, tool_name)

        self._version_name: str = version_name

        self._instrumented_dir: Path = self._root_dir / "instrumented"

        name_prefix: str = f"I{version_name}{tool_name}"

        self._driver_file: Path = self._instrumented_dir / f"{name_prefix}.java"
        self._output_file: Path = self._instrumented_dir / f"{name_prefix}JPFOutput.txt"
        self._error_file: Path = self._instrumented_dir / f"{name_prefix}Error.txt"

        self._output: str = ""
        if self._output_file.exists():
            self._output = self._output_file.read_text()

        self._is_depth_limited = "depth limit reached" in self._output

        self._errors: str = ""
        if self._error_file.exists():
            self._errors = self._error_file.read_text()

    def name(self) -> str:
        return f"EQ-Check - Version: {self._version_name}"

    # Cannot reliably detect timeout on the VersionData level,
    # so we only distinguish MISSING / ERROR / UNKNOWN.

    def is_missing(self) -> bool:
        return not self._driver_file.exists()

    def is_error(self) -> bool:
        return self._errors != ""

    def is_timeout(self) -> bool:
        return False

    def is_unreachable(self) -> bool:
        return False

    # Cannot make a specific classification on the VersionData level,
    # so we classify every successful result as UNKNOWN.

    def is_unknown(self) -> bool:
        return self.has_succeeded()

    def is_maybe_neq(self) -> bool:
        return False

    def is_maybe_eq(self) -> bool:
        return False

    def is_neq(self) -> bool:
        return False

    def is_eq(self) -> bool:
        return False

    def errors(self) -> str:
        return self._errors

    def is_depth_limited(self) -> bool:
        return self._is_depth_limited


class BaseToolResult(BenchmarkResult):
    def __init__(self, root_dir: Path, tool_name: str):
        super().__init__(root_dir, tool_name)

        self._outputs_dir: Path = self._root_dir / "outputs"

        self._output_file: Path = self._outputs_dir / f"{tool_name}.txt"

        self._output: Optional[str] = None
        if self._output_file.exists():
            self._output = self._output_file.read_text()

        self._output_classification: Optional[Classification] = None
        if self._output is not None:
            self._output_classification = self._classify_output(self._output)

        self._old_version: VersionResult = VersionResult(root_dir, tool_name, "oldV")
        self._new_version: VersionResult = VersionResult(root_dir, tool_name, "newV")

    @staticmethod
    def _classify_output(output: str) -> Classification:
        # ARDiff outputs contain results of multiple equivalence checking
        # rounds, so we have to identify the result of the last round,
        # i.e., the result that occurs last in the output file.

        eq_index: int = output.rfind("Output : EQUIVALENT")
        neq_index: int = output.rfind("Output : NOT EQUIVALENT")
        unknown_index: int = output.rfind("Output : UNKNOWN")

        indices: List[int] = [eq_index, neq_index, unknown_index]
        filtered: List[int] = [i for i in indices if i >= 0]
        filtered.sort(reverse=True)

        if filtered[0] == eq_index:
            return Classification.EQ
        elif filtered[0] == neq_index:
            return Classification.NEQ
        elif filtered[0] == unknown_index:
            has_uif_index: int = output.rfind("too much abstraction")
            has_uif: bool = has_uif_index != -1

            if has_uif and has_uif_index > unknown_index:
                return Classification.MAYBE_NEQ

            return Classification.UNKNOWN
        else:
            raise Exception(f"Cannot classify output: {output}")

    def name(self) -> str:
        return "EQ-Check"

    def is_missing(self) -> bool:
        return self._old_version.is_missing() or self._new_version.is_missing()

    def is_error(self) -> bool:
        return self._old_version.is_error() or self._new_version.is_error()

    def is_timeout(self) -> bool:
        return not self.is_error() and not self._output_file.exists()

    def is_unreachable(self) -> bool:
        return False

    def is_unknown(self) -> bool:
        if self._output_classification is None:
            return False

        return self._output_classification == Classification.UNKNOWN

    def is_maybe_neq(self) -> bool:
        if self._output_classification is None:
            return False

        return self._output_classification == Classification.MAYBE_NEQ

    def is_maybe_eq(self) -> bool:
        if self._output_classification is None:
            return False

        if not self._output_classification == Classification.EQ:
            return False

        return self.is_depth_limited()

    def is_neq(self) -> bool:
        if self._output_classification is None:
            return False

        return self._output_classification == Classification.NEQ

    def is_eq(self) -> bool:
        if self._output_classification is None:
            return False

        if not self._output_classification == Classification.EQ:
            return False

        is_old_depth_limited: bool = self._old_version.is_depth_limited()
        is_new_depth_limited: bool = self._new_version.is_depth_limited()

        return (not is_old_depth_limited) and (not is_new_depth_limited)

    def iteration_count(self) -> Optional[int]:
        if self._output is None:
            return None

        iterations = self._output.count("Iteration")
        return iterations if iterations > 0 else None

    def runtime(self) -> Optional[int]:
        if self._output is None:
            return None

        matches: List[str] = re.findall(r"(\d+\.\d+) ms", self._output)
        times: List[float] = [float(match) / 1000 for match in matches]
        runtime = int(sum(times))

        return runtime

    def errors(self) -> str:
        return self._old_version.errors() + self._new_version.errors()

    def is_depth_limited(self) -> bool:
        is_old_depth_limited: bool = self._old_version.is_depth_limited()
        is_new_depth_limited: bool = self._new_version.is_depth_limited()
        return is_old_depth_limited or is_new_depth_limited

    def to_dict(self) -> Dict[str, Any]:
        return {
            "benchmark": self.benchmark(),
            "tool": self.tool_name(),
            "tool_variant": f"{self.tool_name()}-base",
            "result": self.result().name,
            "has_succeeded": self.has_succeeded(),
            "is_depth_limited": self.is_depth_limited(),
            "iteration_count": self.iteration_count(),
            "runtime": self.runtime(),
            "errors": self.errors(),
        }


class PartitionResult(BenchmarkResult):
    def __init__(self, root_dir: Path, tool_name: str, partition_id: int) -> None:
        super().__init__(root_dir, tool_name)

        self._partition_id: int = partition_id

        self._instrumented_dir: Path = self._root_dir / "instrumented"

        name_prefix: str = f"IDiff{tool_name}-P{partition_id}"

        pc_query_file_name: str = f"{name_prefix}-ToSolve-PC.txt"
        pc_answer_file_name: str = f"{name_prefix}-Answer-PC.txt"
        neq_query_file_name: str = f"{name_prefix}-ToSolve-NEQ.txt"
        neq_answer_file_name: str = f"{name_prefix}-Answer-NEQ.txt"
        eq_query_file_name: str = f"{name_prefix}-ToSolve-EQ.txt"
        eq_answer_file_name: str = f"{name_prefix}-Answer-EQ.txt"
        has_uif_file_name: str = f"{name_prefix}-HasUIF.txt"

        self._pc_query_file: Path = self._instrumented_dir / pc_query_file_name
        self._pc_answer_file: Path = self._instrumented_dir / pc_answer_file_name
        self._neq_query_file: Path = self._instrumented_dir / neq_query_file_name
        self._neq_answer_file: Path = self._instrumented_dir / neq_answer_file_name
        self._eq_query_file: Path = self._instrumented_dir / eq_query_file_name
        self._eq_answer_file: Path = self._instrumented_dir / eq_answer_file_name
        self._has_uif_file: Path = self._instrumented_dir / has_uif_file_name

        self._pc_answer: str = ""
        if self._pc_answer_file.exists():
            self._pc_answer = self._pc_answer_file.read_text()

        self._neq_answer: str = ""
        if self._neq_answer_file.exists():
            self._neq_answer = self._neq_answer_file.read_text()

        self._eq_answer: str = ""
        if self._eq_answer_file.exists():
            self._eq_answer = self._eq_answer_file.read_text()

        self._has_uif: bool = False
        if self._has_uif_file.exists():
            self._has_uif = self._has_uif_file.read_text() == "true"

    def name(self) -> str:
        return f"Diff - Partition: {self._partition_id}"

    def is_missing(self) -> bool:
        # has_uif files determine whether a partition is missing because
        # they are the first files that should be created for a partition.
        return not self._has_uif_file.exists()

    def is_error(self) -> bool:
        has_pc_error = self._pc_answer.startswith("(error")
        has_neq_error = self._neq_answer.startswith("(error")
        has_eq_error = self._eq_answer.startswith("(error")

        return has_pc_error or has_neq_error or has_eq_error

    def is_timeout(self) -> bool:
        if not self._has_uif_file.exists():
            return True

        if self._pc_answer == "" or self._pc_answer == "timeout":
            return True

        is_neq_timeout = self._neq_answer == "" or self._neq_answer == "timeout"
        if self._pc_answer == "sat" and is_neq_timeout:
            return True

        if self._has_uif and self._neq_answer == "sat":
            return self._eq_answer == "" or self._eq_answer == "timeout"

        return False

    def is_unreachable(self) -> bool:
        return self._pc_answer == "unsat"

    def is_unknown(self) -> bool:
        return (
            self._pc_answer == "unknown" or
            self._neq_answer == "unknown" or
            self._eq_answer == "unknown"
        )

    def is_maybe_neq(self) -> bool:
        if not self._neq_answer == "sat":
            return False

        if not self._has_uif:
            return False

        return self._eq_answer == "sat"

    def is_maybe_eq(self) -> bool:
        return False

    def is_neq(self) -> bool:
        if not self._neq_answer == "sat":
            return False

        if not self._has_uif:
            return True

        return self._eq_answer == "unsat"

    def is_eq(self) -> bool:
        return self._neq_answer == "unsat"

    def errors(self) -> str:
        if self.has_succeeded():
            return ""

        if self.result() == Classification.TIMEOUT:
            return f"Timeout while solving partition {self._partition_id}."

        assert self.result() == Classification.ERROR

        return f"Error while solving partition {self._partition_id}.\n\n{self._neq_answer}"

    def has_uif(self) -> bool:
        return self._has_uif

    def path_condition(self) -> Optional[str]:
        if not self._pc_query_file.exists():
            return None

        query = self._pc_query_file.read_text()
        for line in query.split("\n"):
            if "(assert" in line:
                return line

        return None

    def constraint_count(self) -> int:
        path_condition: Optional[str] = self.path_condition()
        return 0 if path_condition is None else path_condition.count("(and ")

    def to_dict(self) -> Dict[str, Any]:
        return {
            "benchmark": self.benchmark(),
            "tool": self.tool_name(),
            "tool_variant": f"{self.tool_name()}-diff",
            "partition": self._partition_id,
            "result": self.result().name,
            "has_uninterpreted_functions": self.has_uif(),
            "constraint_count": self.constraint_count(),
            "errors": self.errors(),
        }


class DifferencingResult(BenchmarkResult):
    def __init__(self, root_dir: Path, tool_name: str):
        super().__init__(root_dir, tool_name)

        self._instrumented_dir: Path = self._root_dir / "instrumented"

        name_prefix: str = f"IDiff{tool_name}"

        self._output_file: Path = self._instrumented_dir / f"{name_prefix}-Output.txt"
        self._error_file: Path = self._instrumented_dir / f"{name_prefix}-Error.txt"

        self._output: str = ""
        if self._output_file.exists():
            self._output = self._output_file.read_text()

        self._errors: str = ""
        if self._error_file.exists():
            self._errors = self._error_file.read_text()

        self._is_depth_limited = "depth limit reached" in self._output

        self._partitions: List[PartitionResult] = self._init_partitions()

        self._result_counts: Dict[Classification, int] = defaultdict(int)
        for partition in self._partitions:
            classification = partition.result()
            self._result_counts[classification] += 1

    def _init_partitions(self) -> List[PartitionResult]:
        partitions: List[PartitionResult] = []

        z3_query_pattern = f"IDiff{self._tool_name}-P*-HasUIF.txt"
        for query_file in self._instrumented_dir.glob(z3_query_pattern):
            partition_id_pattern = f"IDiff{self._tool_name}-P(\\d+)-HasUIF.txt"
            m = re.search(partition_id_pattern, query_file.name)
            if m:
                partition_id = int(m.group(1))
                partition = PartitionResult(self._root_dir, self._tool_name, partition_id)

                partitions.append(partition)

        return partitions

    def name(self) -> str:
        return "Diff"

    # START HERE
    def result(self) -> Classification:
        if self._result is not None:
            return self._result

        has_error: bool = "Differencing failed due to error" in self._errors
        has_timeout: bool = "Differencing failed due to timeout" in self._errors

        if len(self._partitions) < 1:
            self._result = Classification.MISSING
        elif self._result_counts[Classification.ERROR] > 0 or has_error:
            self._result = Classification.ERROR
        elif self._result_counts[Classification.TIMEOUT] > 0 or has_timeout:
            self._result = Classification.TIMEOUT
        elif self._result_counts[Classification.UNKNOWN] > 0:
            self._result = Classification.UNKNOWN
        elif self._result_counts[Classification.MAYBE_NEQ] > 0:
            self._result = Classification.MAYBE_NEQ
        elif self._result_counts[Classification.EQ] > 0 and self._is_depth_limited:
            self._result = Classification.MAYBE_EQ
        elif self._result_counts[Classification.NEQ] > 0:
            self._result = Classification.NEQ
        elif self._result_counts[Classification.EQ] > 0:
            self._result = Classification.EQ
        elif self._result_counts[Classification.UNREACHABLE] > 0 and self.is_depth_limited():
            self._result = Classification.MAYBE_EQ

        if self._result is not None:
            return self._result

        raise Exception(f"Unable to classify {self.benchmark()} - {self.tool_name()} - {self.name()}.")

    def is_missing(self) -> bool:
        return self.result() == Classification.MISSING

    def is_error(self) -> bool:
        return self.result() == Classification.ERROR

    def is_timeout(self) -> bool:
        return self.result() == Classification.TIMEOUT

    def is_unreachable(self) -> bool:
        return False

    def is_unknown(self) -> bool:
        return self.result() == Classification.UNKNOWN

    def is_maybe_neq(self) -> bool:
        return self.result() == Classification.MAYBE_NEQ

    def is_maybe_eq(self) -> bool:
        return self.result() == Classification.MAYBE_EQ

    def is_neq(self) -> bool:
        return self.result() == Classification.NEQ

    def is_eq(self) -> bool:
        return self.result() == Classification.EQ

    def runtime(self) -> Optional[int]:
        if self.has_failed():
            return None

        time_index = self._output.rfind("elapsed time:")
        time_pattern = r"(\d\d:\d\d:\d\d)"
        match = re.search(time_pattern, self._output[time_index:])

        if match:
            h, m, s = match.group(1).split(":")
            return int(h) * 3600 + int(m) * 60 + int(s)

        return None

    def errors(self) -> str:
        errors: List[str] = []

        if self._errors is not None and self._errors != "":
            errors.append(self._errors)

        for partition in self._partitions:
            partition_errors: Optional[str] = partition.errors()
            if partition_errors is not None and partition_errors != "":
                errors.append(partition_errors)

        if len(errors) <= 0:
            return ""

        error_msg: str = f"Error while executing benchmark {self.benchmark()}.\n\n"

        return error_msg + "\n".join(errors)

    def is_depth_limited(self) -> bool:
        return self._is_depth_limited

    def partitions(self) -> List[PartitionResult]:
        return self._partitions

    def to_dict(self) -> Dict[str, Any]:
        return {
            "benchmark": self.benchmark(),
            "tool": self.tool_name(),
            "tool_variant": f"{self.tool_name()}-diff",
            "result": self.result().name,
            "has_succeeded": self.has_succeeded(),
            "has_uninterpreted_functions": len(list(filter(lambda p: p.has_uif(), self._partitions))) > 0,
            "is_depth_limited": self.is_depth_limited(),
            "runtime": self.runtime(),
            "errors": self.errors(),
        }


class Repository:
    def __init__(self, tool: str, engine: Engine, use_cache: bool = True):
        self._tool: str = tool
        self._engine: Engine = engine
        self._inspector: Inspector = inspect(engine)
        self._use_cache: bool = use_cache

        self._benchmark_table: str = "benchmark"
        self._base_table: str = f"{tool}_base".lower()
        self._diff_table: str = f"{tool}_diff".lower()
        self._partition_table: str = f"{tool}_partition".lower()

        self._benchmark_data: Optional[List[Benchmark]] = None
        self._base_data: Optional[List[BaseToolResult]] = None
        self._diff_data: Optional[List[DifferencingResult]] = None
        self._partition_data: Optional[List[PartitionResult]] = None

        self._benchmark_df: Optional[pd.DataFrame] = None
        self._base_df: Optional[pd.DataFrame] = None
        self._diff_df: Optional[pd.DataFrame] = None
        self._partition_df: Optional[pd.DataFrame] = None

    def read_benchmark_data(self):
        if self._use_cache and self._benchmark_data is not None:
            return self._benchmark_data
        return self._read_benchmark_data_from_disk()

    def _read_benchmark_data_from_disk(self) -> List[Benchmark]:
        print(f"Reading benchmark_data from disk ...")
        self._benchmark_data = []
        for directory in glob.glob(os.path.join(BENCHMARKS_DIR, "*", "*", "*")):
            benchmark: Benchmark = Benchmark(Path(directory))
            self._benchmark_data.append(benchmark)
        return self._benchmark_data

    def read_base_data(self) -> List[BaseToolResult]:
        if self._use_cache and self._base_data is not None:
            return self._base_data
        return self._read_base_data_from_disk()

    def _read_base_data_from_disk(self) -> List[BaseToolResult]:
        print(f"Reading {self._tool} base_data from disk ...")
        self._base_data = []
        for benchmark in self.read_benchmark_data():
            base_tool_data = BaseToolResult(benchmark.path(), self._tool)
            self._base_data.append(base_tool_data)
        return self._base_data

    def read_diff_data(self) -> List[DifferencingResult]:
        if self._use_cache and self._diff_data is not None:
            return self._diff_data
        return self._read_diff_data_from_disk()

    def _read_diff_data_from_disk(self) -> List[DifferencingResult]:
        print(f"Reading {self._tool} diff_data from disk ...")
        self._diff_data = []
        for benchmark in self.read_benchmark_data():
            diff_data = DifferencingResult(benchmark.path(), self._tool)
            self._diff_data.append(diff_data)
        return self._diff_data

    def read_partition_data(self) -> List[PartitionResult]:
        if self._use_cache and self._partition_data is not None:
            return self._partition_data
        return self._read_partition_data_from_disk()

    def _read_partition_data_from_disk(self) -> List[PartitionResult]:
        print(f"Reading {self._tool} partition_data from disk ...")
        self._partition_data = []
        for diff_data in self.read_diff_data():
            for partition in diff_data.partitions():
                self._partition_data.append(partition)
        return self._partition_data

    def read_benchmark_df(self) -> pd.DataFrame:
        if self._use_cache and self._benchmark_df is not None:
            return self._benchmark_df
        if self._use_cache and not self._is_table_empty(self._benchmark_table):
            return self._read_benchmark_df_from_sql()
        return self._read_benchmark_df_from_disk()

    def _read_benchmark_df_from_sql(self) -> pd.DataFrame:
        with self._engine.begin() as conn:
            index_col: List[str] = ["name"]
            self._benchmark_df = pd.read_sql_table(self._benchmark_table, conn, index_col=index_col)
            return self._benchmark_df

    def _read_benchmark_df_from_disk(self) -> pd.DataFrame:
        data: List[Benchmark] = self.read_benchmark_data()
        keys: List[str] = ["name"]
        self._benchmark_df = self._read_df_from_disk(data, keys, self._benchmark_table)
        return self._benchmark_df

    def read_base_df(self) -> pd.DataFrame:
        if self._use_cache and self._base_df is not None:
            return self._base_df
        if self._use_cache and not self._is_table_empty(self._base_table):
            return self._read_base_df_from_sql()
        return self._read_base_df_from_disk()

    def _read_base_df_from_sql(self) -> pd.DataFrame:
        with self._engine.begin() as conn:
            index_col: List[str] = ["benchmark", "tool"]
            self._base_df = pd.read_sql_table(self._base_table, conn, index_col=index_col)
            return self._base_df

    def _read_base_df_from_disk(self) -> pd.DataFrame:
        data: List[BaseToolResult] = self.read_base_data()
        keys: List[str] = ["benchmark", "tool"]
        self._base_df = self._read_df_from_disk(data, keys, self._base_table)
        return self._base_df

    def read_diff_df(self) -> pd.DataFrame:
        if self._use_cache and self._diff_df is not None:
            return self._diff_df
        if self._use_cache and not self._is_table_empty(self._diff_table):
            return self._read_diff_df_from_sql()
        return self._read_diff_df_from_disk()

    def _read_diff_df_from_sql(self) -> pd.DataFrame:
        with self._engine.begin() as conn:
            index_col: List[str] = ["benchmark", "tool"]
            self._diff_df = pd.read_sql_table(self._diff_table, conn, index_col=index_col)
            return self._diff_df

    def _read_diff_df_from_disk(self) -> pd.DataFrame:
        data: List[DifferencingResult] = self.read_diff_data()
        keys: List[str] = ["benchmark", "tool"]
        self._diff_df = self._read_df_from_disk(data, keys, self._diff_table)
        return self._diff_df

    def read_partition_df(self) -> pd.DataFrame:
        if self._use_cache and self._partition_df is not None:
            return self._partition_df
        if self._use_cache and not self._is_table_empty(self._partition_table):
            return self._read_partition_df_from_sql()
        return self._read_partition_df_from_disk()

    def _read_partition_df_from_sql(self) -> pd.DataFrame:
        with self._engine.begin() as conn:
            index_col: List[str] = ["benchmark", "tool", "partition"]
            self._partition_df = pd.read_sql_table(self._partition_table, conn, index_col=index_col)
            return self._partition_df

    def _read_partition_df_from_disk(self) -> pd.DataFrame:
        data: List[PartitionResult] = self.read_partition_data()
        keys: List[str] = ["benchmark", "tool", "partition"]
        self._partition_df = self._read_df_from_disk(data, keys, self._partition_table)
        return self._partition_df

    def _read_df_from_disk(self, data, keys: List[str], table: str) -> pd.DataFrame:
        df: pd.DataFrame = pd.DataFrame([d.to_dict() for d in data])
        df.set_index(keys, inplace=True)
        with self._engine.begin() as conn:
            conn.execute(text(f"DELETE FROM {table};"))
        df.to_sql(table, self._engine, if_exists="append")
        return df

    def _is_table_empty(self, table: str):
        conn: Connection
        with self._engine.begin() as conn:
            return conn.execute(text(f"SELECT TRUE from {table} LIMIT 1")).fetchone() is None


def run_main(use_cache: bool = True) -> None:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    sqlite_db_file: Path = RESULTS_DIR / "sqlite.db"
    engine: Engine = create_engine(f"sqlite:///{sqlite_db_file}")
    session: Session = sessionmaker(bind=engine)()

    create_schema(engine, session)

    true_base_results: Dict[str, pd.DataFrame] = {}
    true_diff_results: Dict[str, pd.DataFrame] = {}
    true_partition_results: Dict[str, pd.DataFrame] = {}
    true_results: Dict[str, pd.DataFrame] = {}

    for tool_name in ["ARDiff", "DSE", "SE"]:
        print(f"Collecting {tool_name} data ...")

        repository: Repository = Repository(tool_name, engine, use_cache)

        # Reading benchmark_df to ensure it is saved in the database.
        repository.read_benchmark_df()

        base_df: pd.DataFrame = repository.read_base_df()
        diff_df: pd.DataFrame = repository.read_diff_df()
        partition_df: pd.DataFrame = repository.read_partition_df()

        true_base_results[f"{tool_name}-base"] = base_df
        true_diff_results[f"{tool_name}-diff"] = diff_df
        true_partition_results[f"{tool_name}-diff"] = partition_df

        true_results[f"{tool_name}-base"] = base_df
        true_results[f"{tool_name}-diff"] = diff_df

    print("Data collection done!")


if __name__ == "__main__":
    run_main(use_cache=True)
