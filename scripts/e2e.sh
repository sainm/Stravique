#!/usr/bin/env bash
set -uo pipefail

# End-to-end verification against a real HTTP server.
#
# Starts the packaged application, runs a full scan of the shipped fixture,
# and asserts the documented export contract. The server is stopped on exit.
#
# This covers what unit and MockMvc tests cannot: real servlet locale
# resolution, response headers, and the bytes a client actually receives.

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend_dir="${project_root}/backend"
fixture="${project_root}/sample-projects/legacy-web-demo"
port="${STRAVIQUE_E2E_PORT:-18080}"
base_url="http://127.0.0.1:${port}/api/v1"
work_dir="${backend_dir}/build/e2e"
server_log="${work_dir}/server.log"
unknown_project="00000000-0000-0000-0000-000000000000"

pass=0
fail=0
server_pid=""

check() {
  if [[ "$2" == "$3" ]]; then
    echo "  ok   $1"
    pass=$((pass + 1))
  else
    echo "  FAIL $1"
    echo "       expected: $2"
    echo "       actual:   $3"
    fail=$((fail + 1))
  fi
}

cleanup() {
  if [[ -n "${server_pid}" ]] && kill -0 "${server_pid}" 2>/dev/null; then
    kill "${server_pid}" 2>/dev/null
    for _ in $(seq 1 20); do
      kill -0 "${server_pid}" 2>/dev/null || break
      sleep 0.5
    done
    kill -9 "${server_pid}" 2>/dev/null
  fi
  rm -rf "${work_dir}"
}
trap cleanup EXIT

first_line() {
  python3 -c 'import sys;d=open(sys.argv[1],"rb").read();print(d.decode("utf-8-sig").splitlines()[0])' "$1"
}

body_line_count() {
  python3 -c 'import sys;d=open(sys.argv[1],"rb").read();print(len([x for x in d.decode("utf-8-sig").splitlines() if x.strip()]))' "$1"
}

bom_hex() {
  head -c 3 "$1" | od -An -tx1 | tr -d ' \n'
}

json_field() {
  python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get(sys.argv[1],""))' "$1"
}

mkdir -p "${work_dir}"

java_bin="java"
gradle_java_args=()
if [[ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  java_bin=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java
  gradle_java_args+=("-Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
fi

echo "== Building the runnable application =="
cd "${backend_dir}" || exit 1
if [[ -x ./gradlew ]]; then
  gradle_executable=./gradlew
else
  gradle_executable=gradle
fi
if ! "${gradle_executable}" bootJar --no-daemon "${gradle_java_args[@]}" -q; then
  echo "bootJar failed; end-to-end verification cannot run." >&2
  exit 1
fi

jar_path="$(find "${backend_dir}/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -1)"
if [[ -z "${jar_path}" ]]; then
  echo "No runnable jar found under backend/build/libs." >&2
  exit 1
fi

echo "== Starting the application on port ${port} =="
"${java_bin}" -jar "${jar_path}" --server.port="${port}" > "${server_log}" 2>&1 &
server_pid=$!

ready=0
for _ in $(seq 1 120); do
  if curl -sf -m 1 "${base_url}/runtime" > /dev/null 2>&1; then
    ready=1
    break
  fi
  if ! kill -0 "${server_pid}" 2>/dev/null; then
    echo "The application exited before becoming ready:" >&2
    tail -30 "${server_log}" >&2
    exit 1
  fi
  sleep 1
done
if [[ ${ready} -ne 1 ]]; then
  echo "The application did not become ready in time:" >&2
  tail -30 "${server_log}" >&2
  exit 1
fi

fixture_real="$(python3 -c 'import os,sys;print(os.path.realpath(sys.argv[1]))' "${fixture}")"

echo "== Resolving the fixture project =="
project_id="$(
  curl -s "${base_url}/projects" |
    python3 -c 'import sys,json;target=sys.argv[1];print(next((p["id"] for p in json.load(sys.stdin) if p.get("sourcePath")==target),""))' "${fixture_real}"
)"
if [[ -z "${project_id}" ]]; then
  project_id="$(
    curl -s -X POST "${base_url}/projects" \
      -H 'Content-Type: application/json' \
      -d '{"name":"End-to-End Fixture","sourcePath":"'"${fixture_real}"'"}' |
      json_field id
  )"
fi
if [[ -z "${project_id}" ]]; then
  echo "The fixture project could not be registered." >&2
  exit 1
fi
echo "  project ${project_id}"

echo "== Running a scan =="
scan_id="$(curl -s -X POST "${base_url}/projects/${project_id}/scans" | json_field id)"
if [[ -z "${scan_id}" ]]; then
  echo "The scan could not be queued." >&2
  exit 1
fi
scan_status=""
for _ in $(seq 1 180); do
  scan_status="$(curl -s "${base_url}/scans/${scan_id}" | json_field status)"
  case "${scan_status}" in
    COMPLETED | FAILED | CANCELLED) break ;;
  esac
  sleep 1
done
check "scan reaches a terminal state" "COMPLETED" "${scan_status}"
if [[ "${scan_status}" != "COMPLETED" ]]; then
  echo "Scan did not complete; skipping export assertions." >&2
  exit 1
fi

echo "== Exporting reports =="
reports="${work_dir}/reports"
mkdir -p "${reports}"
curl -s -D "${reports}/files.headers" "${base_url}/projects/${project_id}/reports/FILES.csv" -o "${reports}/files.csv"
curl -s "${base_url}/projects/${project_id}/reports/FILES.csv?lang=zh-CN" -o "${reports}/files.zh.csv"
curl -s -H 'Accept-Language: ja-JP' "${base_url}/projects/${project_id}/reports/FILES.csv" -o "${reports}/files.ja.csv"
curl -s "${base_url}/projects/${project_id}/reports/FILES.csv?lang=de-DE" -o "${reports}/files.unknown.csv"
curl -s "${base_url}/projects/${project_id}/reports/FILES.markdown?lang=zh-CN" -o "${reports}/files.zh.md"
curl -s -H 'Accept-Language: ja-JP' "${base_url}/projects/${project_id}/reports/FILES.markdown" -o "${reports}/files.ja.md"

expected_header='"id","path","type","moduleId","sizeBytes","encoding","lastModifiedAt","scanStatus","parseStatus","parseError","frameworkApiCount","generated","changeType","structureRole","sha256"'

echo "== Asserting the export contract =="
check "CSV starts with a UTF-8 byte order mark" "efbbbf" "$(bom_hex "${reports}/files.csv")"
check "CSV header uses stable field names" "${expected_header}" "$(first_line "${reports}/files.csv")"
check "CSV header is unaffected by the lang parameter" "${expected_header}" "$(first_line "${reports}/files.zh.csv")"
check "CSV header is unaffected by Accept-Language" "${expected_header}" "$(first_line "${reports}/files.ja.csv")"
check "CSV header falls back to the default for an unsupported tag" "${expected_header}" "$(first_line "${reports}/files.unknown.csv")"
if grep -q 'legacy-broken.xml' "${reports}/files.csv"; then
  fixture_row=1
else
  fixture_row=0
fi
check "CSV contains persisted fixture rows" "1" "${fixture_row}"
check "Markdown title follows the lang parameter" "# 文件清单" "$(first_line "${reports}/files.zh.md")"
check "Markdown title follows Accept-Language" "# ファイル棚卸" "$(first_line "${reports}/files.ja.md")"
if grep -q '| 路径 |' "${reports}/files.zh.md"; then
  localized_column=1
else
  localized_column=0
fi
check "Markdown column labels are localized" "1" "${localized_column}"
if grep -qi 'content-type: text/csv;charset=UTF-8' "${reports}/files.headers"; then
  csv_charset=1
else
  csv_charset=0
fi
check "CSV response declares its charset" "1" "${csv_charset}"
if grep -qi 'content-disposition: attachment' "${reports}/files.headers"; then
  attachment=1
else
  attachment=0
fi
check "CSV response is served as an attachment" "1" "${attachment}"

echo "== Asserting localized errors =="
error_zh="$(curl -s -H 'Accept-Language: zh-CN' "${base_url}/projects/${unknown_project}" | json_field message)"
error_ja="$(curl -s -H 'Accept-Language: ja-JP' "${base_url}/projects/${unknown_project}" | json_field message)"
check "errors localize for zh-CN" "未找到指定的分析项目。" "${error_zh}"
check "errors localize for ja-JP" "指定された分析プロジェクトが見つかりません。" "${error_ja}"

echo "== Asserting empty report output =="
empty_source="${work_dir}/empty-project"
mkdir -p "${empty_source}/src/main/java/demo"
printf 'package demo;\n\nclass Empty {\n}\n' > "${empty_source}/src/main/java/demo/Empty.java"
empty_real="$(python3 -c 'import os,sys;print(os.path.realpath(sys.argv[1]))' "${empty_source}")"
empty_project_id="$(
  curl -s "${base_url}/projects" |
    python3 -c 'import sys,json;target=sys.argv[1];print(next((p["id"] for p in json.load(sys.stdin) if p.get("sourcePath")==target),""))' "${empty_real}"
)"
if [[ -z "${empty_project_id}" ]]; then
  empty_project_id="$(
    curl -s -X POST "${base_url}/projects" \
      -H 'Content-Type: application/json' \
      -d '{"name":"End-to-End Empty Report","sourcePath":"'"${empty_real}"'"}' |
      json_field id
  )"
fi
if [[ -n "${empty_project_id}" ]]; then
  curl -s "${base_url}/projects/${empty_project_id}/reports/FILES.csv" -o "${reports}/empty.csv"
  check "an unscanned report still emits exactly one header row" "1" "$(body_line_count "${reports}/empty.csv")"
  check "the header row of an empty report stays complete" "${expected_header}" "$(first_line "${reports}/empty.csv")"
else
  echo "  FAIL the empty report project could not be registered"
  fail=$((fail + 1))
fi

echo ""
echo "End-to-end checks: ${pass} passed, ${fail} failed."
[[ ${fail} -eq 0 ]]
