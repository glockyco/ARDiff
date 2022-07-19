import glob
import os
import re
from abc import ABC, abstractmethod
from collections import defaultdict
from enum import Enum
from pathlib import Path
from typing import List, Dict, Optional, Any

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
    MAYBE_NEQ = 5
    NEQ = 6
    EQ = 7

    def is_success(self):
        return self in [
            Classification.EQ,
            Classification.NEQ,
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

        if self.is_unknown() or self.is_maybe_neq():
            return False

        if self.is_neq():
            return should_be_neq

        if self.is_eq() or self.is_eq_uif():
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
        self._error_file: Path = self._instrumented_dir / f"{name_prefix}Error.txt"

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

    def is_neq(self) -> bool:
        return False

    def is_eq(self) -> bool:
        return False

    def errors(self) -> str:
        return self._errors


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
    def _classify_output(output: str):
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

    # Base tools don't provide UIF information,
    # so we can only classify EQ / NEQ / UNKNOWN.

    def is_unknown(self) -> bool:
        if self._output_classification is None:
            return False

        return self._output_classification == Classification.UNKNOWN

    def is_maybe_neq(self) -> bool:
        return False

    def is_neq(self) -> bool:
        if self._output_classification is None:
            return False

        return self._output_classification == Classification.NEQ

    def is_eq(self) -> bool:
        if self._output_classification is None:
            return False

        return self._output_classification == Classification.EQ

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

        self._query_file: Path = self._instrumented_dir / f"{name_prefix}-ToSolve.txt"
        self._answer_file: Path = self._instrumented_dir / f"{name_prefix}-Answer.txt"
        self._model_file: Path = self._instrumented_dir / f"{name_prefix}-Model.txt"

        self._answer: Optional[str] = None
        if self._answer_file.exists():
            self._answer = self._answer_file.read_text()

    def name(self) -> str:
        return f"P{self._partition_id}"

    def is_missing(self) -> bool:
        return not self._query_file.exists()

    def is_error(self) -> bool:
        return self._answer is not None and self._answer.startswith("(error")

    def is_timeout(self) -> bool:
        return self._answer is None or self._answer.startswith("timeout")

    def is_unknown(self) -> bool:
        return self._answer is not None and self._answer.startswith("unknown")

    def is_maybe_neq(self) -> bool:
        return self._answer == "sat (has UIF)"

    def is_neq(self) -> bool:
        return self._answer == "sat (has no UIF)" or self._answer == "sat"

    def is_eq(self) -> bool:
        return self._answer is not None and self._answer.startswith("unsat")

    def errors(self) -> str:
        if self.has_succeeded():
            return ""

        if self.result() == Classification.TIMEOUT:
            return f"Timeout while solving partition {self._partition_id}."

        assert self._answer is not None
        assert self.result() == Classification.ERROR

        return f"Error while solving partition {self._partition_id}.\n\n{self._answer}"


class DifferencingData(BenchmarkData):
    def __init__(self, root_dir: Path, tool_name: str):
        super().__init__(root_dir, tool_name)

        self._instrumented_dir: Path = self._root_dir / "instrumented"

        name_prefix: str = f"IDiff{tool_name}"

        self._output_file: Path = self._instrumented_dir / f"{name_prefix}-Output.txt"
        self._error_file: Path = self._instrumented_dir / f"{name_prefix}-Error.txt"

        self._output: Optional[str] = None
        if self._output_file.exists():
            self._output = self._output_file.read_text()

        self._errors: Optional[str] = None
        if self._error_file.exists():
            self._errors = self._error_file.read_text()

        self._partitions: List[PartitionData] = self._init_partitions()

        self._result_counts: Dict[Classification, int] = defaultdict(int)
        for partition in self._partitions:
            classification = partition.result()
            self._result_counts[classification] += 1

    def _init_partitions(self) -> List[PartitionData]:
        partitions: List[PartitionData] = []

        z3_query_pattern = f"IDiff{self._tool_name}-P*-ToSolve.txt"
        for query_file in self._instrumented_dir.glob(z3_query_pattern):
            partition_id_pattern = f"IDiff{self._tool_name}-P(\\d+)-ToSolve.txt"
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

        has_error: bool = False
        has_timeout: bool = False
        if self._errors is not None:
            has_error = "Differencing failed due to error" in self._errors
            has_timeout = "Differencing failed due to timeout" in self._errors

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

        return "\n".join(errors)

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
            "partitions": len(self._partitions),
            "#missing": self._result_counts[Classification.MISSING],
            "#error": self._result_counts[Classification.ERROR],
            "#timeout": self._result_counts[Classification.TIMEOUT],
            "#unknown": self._result_counts[Classification.UNKNOWN],
            "#maybe_neq": self._result_counts[Classification.MAYBE_NEQ],
            "#neq": self._result_counts[Classification.NEQ],
            "#eq": self._result_counts[Classification.EQ],
            "error": self.errors(),
        }


def run_main() -> None:
    base_data: List[Dict[str, Any]] = []
    diff_data: List[Dict[str, Any]] = []

    for directory in glob.glob(os.path.join(BENCHMARKS_DIR, "*", "*", "*")):
        for tool_name in ["ARDiff"]:
            benchmark_path = Path(directory)

            new_base_data = BaseToolData(benchmark_path, tool_name)
            new_diff_data = DifferencingData(benchmark_path, tool_name)

            if new_diff_data.has_failed():
                print()
                print("--------------------------------------------------")
                print()
                print(new_diff_data)
                print()
                print(new_diff_data.errors())

            base_data.append(new_base_data.to_dict())
            diff_data.append(new_diff_data.to_dict())

    base_df = pd.DataFrame(base_data)
    diff_df = pd.DataFrame(diff_data)
    diff_df.insert(4, "actual-base", base_df["actual"])

    pd.set_option("display.max_columns", None)
    pd.set_option("display.max_colwidth", None)
    pd.set_option("display.max_rows", None)
    pd.set_option("display.width", None)

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    base_results_file = RESULTS_DIR / "base_results.md"
    base_results_file.write_text(base_df.to_markdown())

    diff_results_file = RESULTS_DIR / "diff_results.md"
    diff_results_file.write_text(diff_df.to_markdown())


if __name__ == "__main__":
    run_main()
