#!/usr/bin/env bash
# Verifies the whole paytech reactor: build, auto tests (including the reactor-level
# arch-rules-reactor-test, which is part of `verify`) and the table-ownership whitelist freshness.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
reactor_dir="$(cd "${script_dir}/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set; export the JDK 25 home first." >&2
  exit 1
fi

cd "${reactor_dir}"
node tools/arch-rules/scripts/generate-table-ownership.mjs --check
./mvnw -q -B verify

echo "== surefire summary =="
total=0
failures=0
for report in $(find . -path '*/target/surefire-reports/TEST-*.xml' | sort); do
  module="${report#./}"
  module="${module%%/target/*}"
  tests="$(sed -n 's/.*<testsuite[^>]* tests="\([0-9]*\)".*/\1/p' "${report}" | head -1)"
  failed="$(sed -n 's/.*<testsuite[^>]* failures="\([0-9]*\)".*/\1/p' "${report}" | head -1)"
  errors="$(sed -n 's/.*<testsuite[^>]* errors="\([0-9]*\)".*/\1/p' "${report}" | head -1)"
  total=$((total + ${tests:-0}))
  failures=$((failures + ${failed:-0} + ${errors:-0}))
  echo "${module}: $(basename "${report}" .xml | sed 's/^TEST-//') tests=${tests:-0}"
done
echo "total tests=${total} failures+errors=${failures}"
if [[ "${failures}" -ne 0 ]]; then
  exit 1
fi
