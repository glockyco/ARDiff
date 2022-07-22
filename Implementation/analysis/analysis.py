import glob
import os
import re
from abc import ABC, abstractmethod
from collections import defaultdict
from enum import Enum
from pathlib import Path
from typing import List, Dict, Optional, Any, Set

import pandas as pd


CURRENT_DIR: Path = Path(os.path.dirname(os.path.realpath(__file__)))
ROOT_DIR: Path = CURRENT_DIR / ".." / ".."
BENCHMARKS_DIR: Path = ROOT_DIR / "benchmarks"
ANALYSIS_DIR: Path = ROOT_DIR / "Implementation" / "analysis"
RESULTS_DIR: Path = ANALYSIS_DIR / "results"


class Classification(Enum):
    MISSING = 1
    ERROR = 2
    TIMEOUT = 3

    UNKNOWN = 4

    # MAYBE_NEQ:
    # Equivalence checking found the two programs to be NEQ, but:
    # (i) there were uninterpreted functions in the solver query AND
    # (ii) at least one input assignment exists for which the programs are EQ.
    # Thus, the base programs without uninterpreted functions might actually
    # be EQ rather than NEQ if the NEQ results only arise due to the
    # introduction of uninterpreted functions.
    MAYBE_NEQ = 5

    # MAYBE_EQ:
    # Equivalence checking found the two programs to be EQ, but the symbolic
    # execution hit the search depth limit. Thus, the programs might be found
    # to be NEQ rather than EQ when using a sufficiently large search depth.
    MAYBE_EQ = 6

    NEQ = 7
    EQ = 8

    def is_success(self):
        return self in [
            Classification.EQ,
            Classification.NEQ,
            Classification.MAYBE_EQ,
            Classification.MAYBE_NEQ,
            Classification.UNKNOWN,
        ]

    def is_failure(self):
        return not self.is_success()


class BenchmarkData(ABC):
    def __init__(self, root_dir: Path, tool_name: str):
        self._root_dir: Path = root_dir
        self._tool_name: str = tool_name
        self._result: Optional[Classification] = None

    def __str__(self) -> str:
        return f"{self.result().name} - {self.full_path()}"

    def full_path(self) -> str:
        benchmark_path: str = os.path.relpath(self._root_dir, BENCHMARKS_DIR)
        tool_path: str = os.path.join(benchmark_path, self._tool_name)
        full_path: str = os.path.join(tool_path, self.name())
        return full_path

    @abstractmethod
    def name(self) -> str:
        pass

    def result(self) -> Classification:
        if self._result is not None:
            return self._result

        if self.is_missing():
            self._result = Classification.MISSING
        elif self.is_error():
            self._result = Classification.ERROR
        elif self.is_timeout():
            self._result = Classification.TIMEOUT
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

        raise Exception(f"Unable to classify {self.full_path()}.")

    def expected_result(self) -> Classification:
        if f"{os.sep}Eq{os.sep}" in self.full_path():
            return Classification.EQ
        elif f"{os.sep}NEq{os.sep}" in self.full_path():
            return Classification.NEQ

        error = f"Unable to infer expected classification for {self.full_path()}."
        raise Exception(error)

    def has_succeeded(self) -> bool:
        return self.result().is_success()

    def has_failed(self) -> bool:
        return self.result().is_failure()

    def is_correct(self):
        should_be_eq: bool = self.expected_result() == Classification.EQ
        should_be_neq: bool = self.expected_result() == Classification.NEQ

        if self.has_failed():
            return False

        if self.is_unknown() or self.is_maybe_neq() or self.is_maybe_eq():
            return False

        if self.is_neq():
            return should_be_neq

        if self.is_eq():
            return should_be_eq

        raise Exception(f"Unable to infer whether {self.full_path()} is correct.")

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


class VersionData(BenchmarkData):
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
        return self._version_name

    # Cannot reliably detect timeout on the VersionData level,
    # so we only distinguish MISSING / ERROR / UNKNOWN.

    def is_missing(self) -> bool:
        return not self._driver_file.exists()

    def is_error(self) -> bool:
        return self._errors != ""

    def is_timeout(self) -> bool:
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


class BaseToolData(BenchmarkData):
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

        self._old_version: VersionData = VersionData(root_dir, tool_name, "oldV")
        self._new_version: VersionData = VersionData(root_dir, tool_name, "newV")

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
        return "base"

    def tool_name(self) -> str:
        return f"{self._tool_name}-{self.name()}"

    def is_missing(self) -> bool:
        return self._old_version.is_missing() or self._new_version.is_missing()

    def is_error(self) -> bool:
        return self._old_version.is_error() or self._new_version.is_error()

    def is_timeout(self) -> bool:
        return not self.is_error() and not self._output_file.exists()

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

        is_old_depth_limited: bool = self._old_version.is_depth_limited()
        is_new_depth_limited: bool = self._new_version.is_depth_limited()

        return is_old_depth_limited or is_new_depth_limited

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

    def errors(self) -> str:
        return self._old_version.errors() + self._new_version.errors()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "path": self.full_path(),
            "tool": self.tool_name(),
            "expected": self.expected_result().name,
            "actual": self.result().name,
            "has_succeeded": self.has_succeeded(),
            "is_correct": self.is_correct(),
            "error": self.errors(),
        }


class PartitionData(BenchmarkData):
    def __init__(self, root_dir: Path, tool_name: str, partition_id: int) -> None:
        super().__init__(root_dir, tool_name)

        self._partition_id: int = partition_id

        self._instrumented_dir: Path = self._root_dir / "instrumented"

        name_prefix: str = f"IDiff{tool_name}-P{partition_id}"

        neq_query_file_name: str = f"{name_prefix}-ToSolve-NEQ.txt"
        neq_answer_file_name: str = f"{name_prefix}-Answer-NEQ.txt"
        eq_query_file_name: str = f"{name_prefix}-ToSolve-EQ.txt"
        eq_answer_file_name: str = f"{name_prefix}-Answer-EQ.txt"
        has_uif_file_name: str = f"{name_prefix}-HasUIF.txt"

        self._neq_query_file: Path = self._instrumented_dir / neq_query_file_name
        self._neq_answer_file: Path = self._instrumented_dir / neq_answer_file_name
        self._eq_query_file: Path = self._instrumented_dir / eq_query_file_name
        self._eq_answer_file: Path = self._instrumented_dir / eq_answer_file_name
        self._has_uif_file: Path = self._instrumented_dir / has_uif_file_name

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
        return f"P{self._partition_id}"

    def is_missing(self) -> bool:
        # NEQ query files determine whether a partition is missing because
        # they are the first files that should be created for a partition.
        return not self._neq_query_file.exists()

    def is_error(self) -> bool:
        has_neq_error = self._neq_answer.startswith("(error")
        has_eq_error = self._eq_answer.startswith("(error")

        return has_neq_error or has_eq_error

    def is_timeout(self) -> bool:
        is_uif_missing = not self._has_uif_file.exists()
        is_neq_timeout = self._neq_answer == "" or self._neq_answer == "timeout"

        if is_uif_missing or is_neq_timeout:
            return True

        if self._has_uif and self._neq_answer == "sat":
            return self._eq_answer == "" or self._eq_answer == "timeout"

        return False

    def is_unknown(self) -> bool:
        return self._neq_answer == "unknown" or self._eq_answer == "unknown"

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


class DifferencingData(BenchmarkData):
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

        self._partitions: List[PartitionData] = self._init_partitions()

        self._result_counts: Dict[Classification, int] = defaultdict(int)
        for partition in self._partitions:
            classification = partition.result()
            self._result_counts[classification] += 1

    def _init_partitions(self) -> List[PartitionData]:
        partitions: List[PartitionData] = []

        z3_query_pattern = f"IDiff{self._tool_name}-P*-ToSolve-NEQ.txt"
        for query_file in self._instrumented_dir.glob(z3_query_pattern):
            partition_id_pattern = f"IDiff{self._tool_name}-P(\\d+)-ToSolve-NEQ.txt"
            m = re.search(partition_id_pattern, query_file.name)
            if m:
                partition_id = int(m.group(1))
                partition = PartitionData(self._root_dir, self._tool_name, partition_id)

                partitions.append(partition)

        return partitions

    def name(self) -> str:
        return "diff"

    def tool_name(self) -> str:
        return f"{self._tool_name}-{self.name()}"

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

        if self._result is not None:
            return self._result

        raise Exception(f"Unable to classify {self.full_path()}.")

    def is_missing(self) -> bool:
        return self.result() == Classification.MISSING

    def is_error(self) -> bool:
        return self.result() == Classification.ERROR

    def is_timeout(self) -> bool:
        return self.result() == Classification.TIMEOUT

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

        error_msg: str = f"Error while executing benchmark {self.full_path()}.\n\n"

        return error_msg + "\n".join(errors)

    def is_depth_limited(self) -> bool:
        return self._is_depth_limited

    def partitions(self) -> List[PartitionData]:
        return self._partitions

    def succeeded_partitions(self):
        return list(filter(lambda p: p.has_succeeded(), self._partitions))

    def failed_partitions(self):
        return list(filter(lambda p: p.has_failed(), self._partitions))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "path": self.full_path(),
            "tool": self.tool_name(),
            "expected": self.expected_result().name,
            "actual": self.result().name,
            "has_succeeded": self.has_succeeded(),
            "is_correct": self.is_correct(),
            "is_depth_limited": self.is_depth_limited(),
            "partitions": len(self._partitions),
            "has_uif": len(list(filter(lambda p: p.has_uif(), self._partitions))) > 0,
            "#uif": len(list(filter(lambda p: p.has_uif(), self._partitions))),
            "#~uif": len(list(filter(lambda p: not p.has_uif(), self._partitions))),
            "#missing": self._result_counts[Classification.MISSING],
            "#error": self._result_counts[Classification.ERROR],
            "#timeout": self._result_counts[Classification.TIMEOUT],
            "#unknown": self._result_counts[Classification.UNKNOWN],
            "#maybe_neq": self._result_counts[Classification.MAYBE_NEQ],
            "#neq": self._result_counts[Classification.NEQ],
            "#eq": self._result_counts[Classification.EQ],
            "error": self.errors(),
        }


def get_benchmark_paths() -> List[Path]:
    benchmarks: List[Path] = []

    for directory in glob.glob(os.path.join(BENCHMARKS_DIR, "*", "*", "*")):
        benchmark: Path = Path(directory)
        benchmarks.append(benchmark)

    return benchmarks


def create_base_df(tool_name: str) -> pd.DataFrame:
    data: List[Dict[str, Any]] = []

    for benchmark_path in get_benchmark_paths():
        base_tool_data = BaseToolData(benchmark_path, tool_name)
        data.append(base_tool_data.to_dict())

    return pd.DataFrame(data)


def create_diff_df(tool_name: str) -> pd.DataFrame:
    data: List[Dict[str, Any]] = []

    for benchmark_path in get_benchmark_paths():
        diff_data = DifferencingData(benchmark_path, tool_name)
        data.append(diff_data.to_dict())

    return pd.DataFrame(data)


def print_crosstabs(results: Dict[str, pd.DataFrame]):
    column_order = ["EQ", "MAYBE_EQ", "NEQ", "MAYBE_NEQ", "UNKNOWN", "TIMEOUT", "ERROR", "MISSING", "All"]

    print()

    used_classes: Set[str] = set()
    for result_df in results.values():
        used_classes = used_classes | set(result_df["actual"].unique())

    for title, result_df in results.items():
        result_ct = pd.crosstab(result_df["expected"], result_df["actual"], margins=True)

        missing_classes: List[str] = [c for c in used_classes if c not in result_ct.columns]
        for missing_class in missing_classes:
            result_ct[missing_class] = 0

        result_ct: pd.DataFrame = result_ct[[c for c in column_order if c in result_ct.columns]]

        print(f"{title}:")
        print(result_ct.to_markdown())
        print()


def run_main(use_cache: bool = True) -> None:
    tool_names = ["SE", "DSE", "ARDiff"]

    true_base_results: Dict[str, pd.DataFrame] = {}
    true_diff_results: Dict[str, pd.DataFrame] = {}
    true_results: Dict[str, pd.DataFrame] = {}

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    for tool_name in tool_names:
        base_df: Optional[pd.DataFrame]
        diff_df: Optional[pd.DataFrame]

        base_df_file = RESULTS_DIR / f"{tool_name}_base_df.csv"
        diff_df_file = RESULTS_DIR / f"{tool_name}_diff_df.csv"

        if use_cache and base_df_file.exists():
            base_df = pd.read_csv(base_df_file)
        else:
            base_df = create_base_df(tool_name)
            base_df.to_csv(base_df_file)

        if use_cache and diff_df_file.exists():
            diff_df = pd.read_csv(diff_df_file)
        else:
            diff_df = create_diff_df(tool_name)
            diff_df.insert(4, "actual-base", base_df["actual"])
            diff_df.to_csv(diff_df_file)

        true_base_results[f"{tool_name}-base"] = base_df
        true_diff_results[f"{tool_name}-diff"] = diff_df

        true_results[f"{tool_name}-base"] = base_df
        true_results[f"{tool_name}-diff"] = diff_df

    # --------------------------------------------------------------------------

    overall_base_df: pd.DataFrame = pd.concat(true_base_results.values())
    overall_base_df.to_csv(RESULTS_DIR / "overall_base_df.csv")

    overall_diff_df: pd.DataFrame = pd.concat(true_diff_results.values())
    overall_diff_df.to_csv(RESULTS_DIR / "overall_diff_df.csv")

    # --------------------------------------------------------------------------

    strict_results: Dict[str, pd.DataFrame] = {}
    lenient_results: Dict[str, pd.DataFrame] = {}

    for tool_name, df in true_results.items():
        strict_df: pd.DataFrame = df.copy()
        strict_df.loc[df["actual"] == "MAYBE_EQ", "actual"] = "UNKNOWN"
        strict_df.loc[df["actual"] == "MAYBE_NEQ", "actual"] = "UNKNOWN"

        lenient_df: pd.DataFrame = df.copy()
        lenient_df.loc[df["actual"] == "MAYBE_EQ", "actual"] = "EQ"
        lenient_df.loc[df["actual"] == "MAYBE_NEQ", "actual"] = "NEQ"

        if "actual-base" in df.columns:
            strict_df.loc[df["actual-base"] == "MAYBE_EQ", "actual"] = "UNKNOWN"
            strict_df.loc[df["actual-base"] == "MAYBE_NEQ", "actual"] = "UNKNOWN"

            lenient_df.loc[df["actual-base"] == "MAYBE_EQ", "actual"] = "EQ"
            lenient_df.loc[df["actual-base"] == "MAYBE_NEQ", "actual"] = "NEQ"

        strict_results[tool_name] = strict_df
        lenient_results[tool_name] = lenient_df

    # --------------------------------------------------------------------------

    pd.set_option("display.max_columns", None)
    pd.set_option("display.max_colwidth", None)
    pd.set_option("display.max_rows", None)
    pd.set_option("display.width", None)

    # --------------------------------------------------------------------------

    columns = ["actual", "path", "error"]
    base_error_mask = overall_base_df["actual"] == "ERROR"
    diff_error_mask = overall_diff_df["actual"] == "ERROR"
    base_errors_df: pd.DataFrame = overall_base_df.loc[base_error_mask, columns]
    diff_errors_df: pd.DataFrame = overall_diff_df.loc[diff_error_mask, columns]

    errors_df = pd.concat([base_errors_df, diff_errors_df])

    with open(RESULTS_DIR / "errors.txt", "w") as f:
        for index, row in errors_df.iterrows():
            f.write("--------------------------------------------------\n\n")
            f.write(f"{row['actual']} - {row['path']}\n\n")
            f.write(f"{row['error']}\n\n")

    # --------------------------------------------------------------------------

    print("\n---------- LENIENT RESULTS ----------")

    print_crosstabs(lenient_results)

    print("\n---------- STRICT RESULTS ----------")

    print_crosstabs(strict_results)

    print("\n---------- TRUE RESULTS ----------")

    print_crosstabs(true_results)


if __name__ == "__main__":
    run_main(use_cache=True)
