#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DB_PATH="${SCRIPT_DIR}/analysis/results/sqlite.db"
DB_CREATE_TABLES_PATH="${SCRIPT_DIR}/analysis/create-tables.sql"
DB_CREATE_VIEWS_PATH="${SCRIPT_DIR}/analysis/create-views.sql"
DB_CREATE_MATERIALIZED_VIEWS_PATH="${SCRIPT_DIR}/analysis/create-materialized-views.sql"
DB_POPULATE_MATERIALIZED_VIEWS_PATH="${SCRIPT_DIR}/analysis/populate-materialized-views.sql"

BASE_JAR_PATH="${SCRIPT_DIR}/build/libs/ARDiff-base-1.0-SNAPSHOT-all.jar"
DIFF_JAR_PATH="${SCRIPT_DIR}/build/libs/ARDiff-diff-1.0-SNAPSHOT-all.jar"

# Configuration settings

dry_run=false

clean_files=false
clean_db=false

build=true

depth_limits=("10")
timeouts=("30" "90" "300")
runs=3

run_base=true
run_diff=true

print_cleaned=true
print_skipped=false
print_commands=true

benchmarks=(
  "../benchmarks/Airy/MAX/Eq"
  "../benchmarks/Airy/MAX/NEq"
  "../benchmarks/Airy/Sign/Eq"
  "../benchmarks/Airy/Sign/NEq"
  "../benchmarks/Bess/SIGN/Eq"
  "../benchmarks/Bess/SIGN/NEq"
  "../benchmarks/Bess/SQR/Eq"
  "../benchmarks/Bess/SQR/NEq"
  "../benchmarks/Bess/bessi/Eq"
  "../benchmarks/Bess/bessi/NEq"
  "../benchmarks/Bess/bessi0/Eq"
  "../benchmarks/Bess/bessi0/NEq"
  "../benchmarks/Bess/bessi1/Eq"
  "../benchmarks/Bess/bessi1/NEq"
  "../benchmarks/Bess/bessj/Eq"
  "../benchmarks/Bess/bessj/NEq"
  "../benchmarks/Bess/bessj0/Eq"
  "../benchmarks/Bess/bessj0/NEq"
  "../benchmarks/Bess/bessj1/Eq"
  "../benchmarks/Bess/bessj1/NEq"
  "../benchmarks/Bess/bessk/Eq"
  "../benchmarks/Bess/bessk/NEq"
  "../benchmarks/Bess/bessk0/Eq"
  "../benchmarks/Bess/bessk0/NEq"
  "../benchmarks/Bess/bessk1/Eq"
  "../benchmarks/Bess/bessk1/NEq"
  "../benchmarks/Bess/bessy/Eq"
  "../benchmarks/Bess/bessy/NEq"
  "../benchmarks/Bess/bessy0/Eq"
  "../benchmarks/Bess/bessy0/NEq"
  "../benchmarks/Bess/bessy1/Eq"
  "../benchmarks/Bess/bessy1/NEq"
  "../benchmarks/Bess/dawson/Eq"
  "../benchmarks/Bess/dawson/NEq"
  "../benchmarks/Bess/probks/Eq"
  "../benchmarks/Bess/probks/NEq"
  "../benchmarks/Bess/pythag/Eq"
  "../benchmarks/Bess/pythag/NEq"
  "../benchmarks/Ell/brent/Eq"
  "../benchmarks/Ell/brent/NEq"
  "../benchmarks/Ell/dbrent/Eq"
  "../benchmarks/Ell/dbrent/NEq"
  "../benchmarks/Ell/ell/Eq"
  "../benchmarks/Ell/ell/NEq"
  "../benchmarks/Ell/ellpi/Eq"
  "../benchmarks/Ell/ellpi/NEq"
  "../benchmarks/Ell/plgndr/Eq"
  "../benchmarks/Ell/plgndr/NEq"
  "../benchmarks/Ell/rc/Eq"
  "../benchmarks/Ell/rc/NEq"
  "../benchmarks/Ell/rd/Eq"
  "../benchmarks/Ell/rd/NEq"
  "../benchmarks/Ell/rf/Eq"
  "../benchmarks/Ell/rf/NEq"
  "../benchmarks/Ell/rj/Eq"
  "../benchmarks/Ell/rj/NEq"
  "../benchmarks/Ell/zbrent/Eq"
  "../benchmarks/Ell/zbrent/NEq"
  "../benchmarks/ModDiff/Eq/Add"
  "../benchmarks/ModDiff/Eq/Comp"
  "../benchmarks/ModDiff/Eq/Const"
  "../benchmarks/ModDiff/Eq/LoopMult10"
  "../benchmarks/ModDiff/Eq/LoopMult15"
  "../benchmarks/ModDiff/Eq/LoopMult2"
  "../benchmarks/ModDiff/Eq/LoopMult20"
  "../benchmarks/ModDiff/Eq/LoopMult5"
  "../benchmarks/ModDiff/Eq/LoopSub"
  "../benchmarks/ModDiff/Eq/LoopUnreach10"
  "../benchmarks/ModDiff/Eq/LoopUnreach15"
  "../benchmarks/ModDiff/Eq/LoopUnreach2"
  "../benchmarks/ModDiff/Eq/LoopUnreach20"
  "../benchmarks/ModDiff/Eq/LoopUnreach5"
  "../benchmarks/ModDiff/Eq/Sub"
  "../benchmarks/ModDiff/Eq/UnchLoop"
  "../benchmarks/ModDiff/NEq/LoopMult10"
  "../benchmarks/ModDiff/NEq/LoopMult15"
  "../benchmarks/ModDiff/NEq/LoopMult2"
  "../benchmarks/ModDiff/NEq/LoopMult20"
  "../benchmarks/ModDiff/NEq/LoopMult5"
  "../benchmarks/ModDiff/NEq/LoopSub"
  "../benchmarks/ModDiff/NEq/LoopUnreach10"
  "../benchmarks/ModDiff/NEq/LoopUnreach15"
  "../benchmarks/ModDiff/NEq/LoopUnreach2"
  "../benchmarks/ModDiff/NEq/LoopUnreach20"
  "../benchmarks/ModDiff/NEq/LoopUnreach5"
  "../benchmarks/ModDiff/NEq/UnchLoop"
  "../benchmarks/Ran/bnldev/Eq"
  "../benchmarks/Ran/bnldev/NEq"
  "../benchmarks/Ran/expdev/Eq"
  "../benchmarks/Ran/expdev/NEq"
  "../benchmarks/Ran/gamdev/Eq"
  "../benchmarks/Ran/gamdev/NEq"
  "../benchmarks/Ran/gammln/Eq"
  "../benchmarks/Ran/gammln/NEq"
  "../benchmarks/Ran/gasdev/Eq"
  "../benchmarks/Ran/gasdev/NEq"
  "../benchmarks/Ran/poidev/Eq"
  "../benchmarks/Ran/poidev/NEq"
  "../benchmarks/Ran/ran/Eq"
  "../benchmarks/Ran/ran/NEq"
  "../benchmarks/Ran/ranzero/Eq"
  "../benchmarks/Ran/ranzero/NEq"
  "../benchmarks/caldat/badluk/Eq"
  "../benchmarks/caldat/badluk/NEq"
  "../benchmarks/caldat/julday/Eq"
  "../benchmarks/caldat/julday/NEq"
  "../benchmarks/dart/test/Eq"
  "../benchmarks/dart/test/NEq"
  "../benchmarks/gam/betacf/Eq"
  "../benchmarks/gam/betacf/NEq"
  "../benchmarks/gam/betai/Eq"
  "../benchmarks/gam/betai/NEq"
  "../benchmarks/gam/ei/Eq"
  "../benchmarks/gam/ei/NEq"
  "../benchmarks/gam/erfcc/Eq"
  "../benchmarks/gam/erfcc/NEq"
  "../benchmarks/gam/expint/Eq"
  "../benchmarks/gam/expint/NEq"
  "../benchmarks/gam/gammp/Eq"
  "../benchmarks/gam/gammp/NEq"
  "../benchmarks/gam/gammq/Eq"
  "../benchmarks/gam/gammq/NEq"
  "../benchmarks/gam/gcf/Eq"
  "../benchmarks/gam/gcf/NEq"
  "../benchmarks/gam/gser/Eq"
  "../benchmarks/gam/gser/NEq"
  "../benchmarks/power/test/Eq"
  "../benchmarks/power/test/NEq"
  "../benchmarks/sine/mysin/Eq"
  "../benchmarks/sine/mysin/NEq"
  "../benchmarks/tcas/NonCrossingBiasedClimb/Eq"
  "../benchmarks/tcas/NonCrossingBiasedClimb/NEq"
  "../benchmarks/tcas/NonCrossingBiasedDescend/Eq"
  "../benchmarks/tcas/NonCrossingBiasedDescend/NEq"
  "../benchmarks/tcas/altseptest/Eq"
  "../benchmarks/tcas/altseptest/NEq"
  "../benchmarks/tsafe/conflict/Eq"
  "../benchmarks/tsafe/conflict/NEq"
  "../benchmarks/tsafe/normAngle/Eq"
  "../benchmarks/tsafe/normAngle/NEq"
  "../benchmarks/tsafe/snippet/Eq"
  "../benchmarks/tsafe/snippet/NEq"
)

tool_names=(
  "SE"
  "DSE"
#  "Imp"
#  "ARDiffR"
#  "ARDiffH3"
  "ARDiff"
  "PASDA"
)

configurations=(
  "--tool S --s coral"
  "--tool D --s coral"
#  "--tool I --s coral"
#  "--tool A --s coral --H R"
#  "--tool A --s coral --H H3"
  "--tool A --s coral --H H123"
  "--tool P --s coral"
)

# Remove results from previous runs

if [ "$clean_files" = true ] ; then
  for d1 in ../benchmarks/* ; do
    for d2 in "$d1"/* ; do
      for d3 in "$d2"/* ; do
        if [[ ! " ${benchmarks[*]} " =~ " ${d3} " ]]; then
          continue
        fi

        if [ "$print_cleaned" = true ] ; then
          printf "Cleaning %s ...\n" "${d3}"
          echo "rm -rf ${d3}/*/"
        fi

        if [ "$dry_run" = false ] ; then
          rm -rf "${d3:?}"/*/
        fi
      done
    done
  done
fi

# Set up the database

if [ "$clean_db" = true ] ; then
  rm ${DB_PATH}

  touch ${DB_PATH}
  sqlite3 ${DB_PATH} < ${DB_CREATE_TABLES_PATH} > /dev/null
  #sqlite3 ${DB_PATH} < ${DB_CREATE_VIEWS_PATH} > /dev/null
  #sqlite3 ${DB_PATH} < ${DB_CREATE_MATERIALIZED_VIEWS_PATH} > /dev/null
fi

# Build the application JAR files

if [ "$build" = true ] ; then
  if [ "$run_base" = true ] || [ "$run_diff" = true ] ; then
    printf "Building JAR files ..."

    if [ "$run_base" = true ] ; then
      # Build base JAR
      command="./gradlew -PmainClass=Runner.Runner shadowJar"

      if [ "$print_commands" = true ] ; then
        printf "\n%s" "${command}"
      fi

      if [ "$dry_run" = false ] ; then
        eval "${command}"
      fi
    fi

    if [ "$run_diff" = true ] ; then
      # Build diff JAR
      command="./gradlew -PmainClass=differencing.DifferencingRunner shadowJar"

      if [ "$print_commands" = true ] ; then
        printf "\n%s" "${command}"
      fi

      if [ "$dry_run" = false ] ; then
        eval "${command}"
      fi
    fi

    printf "\n"
  fi
fi

# Process the benchmark programs

for depth_limit in "${depth_limits[@]}"; do
  for timeout in "${timeouts[@]}"; do
    for ((run = 1; run <= runs; run++)); do

      for d1 in ../benchmarks/* ; do
        for d2 in "$d1"/* ; do
          for d3 in "$d2"/* ; do
            if [[ ! " ${benchmarks[*]} " =~ " ${d3} " ]]; then
              if [ "$print_skipped" = true ] ; then
                printf "Skipping %s ...\n" "${d3}"
              fi
              continue
            fi

            if [ "$run_base" = true ] || [ "$run_diff" = true ] ; then
              printf "Processing %s ..." "${d3}"

              for i in "${!configurations[@]}" ; do
                # Run base tool(s)
                if [ "$run_base" = true ] ; then
                  oldV="${d3}/oldV.java"
                  newV="${d3}/newV.java"

                  base_command="timeout --verbose --foreground ${timeout}s java -jar '${BASE_JAR_PATH}' --path1 ${oldV} --path2 ${newV} ${configurations[$i]} --b ${depth_limit} --t ${timeout}"

                  if [ "$print_commands" = true ] ; then
                    printf "\n%s" "${base_command}"
                  fi

                  if [ "$dry_run" = false ] ; then
                    printf "\n"
                    mkdir -p "${d3}/instrumented/"
                    eval "${base_command}"
                    # Kill any leftover z3 / RunJPF.jar processes
                    # that were started by the base tool.
                    # This is necessary in case the base tool was
                    # stopped by the timeout and the child processes
                    # were, therefore, not correctly terminated.
                    pkill z3
                    pkill -f RunJPF.jar
                  fi
                fi

                # Run diff tool(s)
                if [ "$run_diff" = true ] ; then
                  diff_command="timeout --verbose --foreground ${timeout}s java -jar '${DIFF_JAR_PATH}' ${d3} ${tool_names[$i]} ${timeout} ${depth_limit}"

                  if [ "$print_commands" = true ] ; then
                    printf "\n%s" "${diff_command}"
                  fi

                  if [ "$dry_run" = false ] ; then
                    printf "\n"
                    mkdir -p "${d3}/instrumented/"
                    eval "${diff_command}"
                  fi
                fi
              done

              printf "\n"
            fi

          done
        done
      done

    done
  done
done

# Populate "materialized views"

printf "Populating materialized views ... "
#sqlite3 ${DB_PATH} < ${DB_POPULATE_MATERIALIZED_VIEWS_PATH} > /dev/null
printf "done!\n"
