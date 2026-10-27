#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend_pid=""
frontend_pid=""

cleanup() {
  if [[ -n "${frontend_pid}" ]]; then
    kill "${frontend_pid}" 2>/dev/null || true
  fi
  if [[ -n "${backend_pid}" ]]; then
    kill "${backend_pid}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

gradle_java_args=()
if [[ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  gradle_java_args+=("-Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
fi
if [[ -x "${project_root}/backend/gradlew" ]]; then
  gradle_executable="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  gradle_executable="gradle"
else
  echo "Neither the Gradle wrapper nor a system Gradle installation is available." >&2
  exit 1
fi

if [[ ! -d "${project_root}/frontend/node_modules" ]]; then
  (cd "${project_root}/frontend" && npm ci)
fi

"${project_root}/scripts/ensure-postgres.sh"

(
  cd "${project_root}/backend"
  "${gradle_executable}" "${gradle_java_args[@]}" bootRun --no-daemon
) &
backend_pid=$!

backend_ready=false
for _ in {1..60}; do
  if curl --silent --fail http://127.0.0.1:8080/api/v1/runtime >/dev/null; then
    backend_ready=true
    break
  fi
  if ! kill -0 "${backend_pid}" 2>/dev/null; then
    echo "Backend stopped before becoming ready." >&2
    exit 1
  fi
  sleep 1
done

if [[ "${backend_ready}" != "true" ]]; then
  echo "Backend did not become ready." >&2
  exit 1
fi

(
  cd "${project_root}/frontend"
  npm run dev -- --host 127.0.0.1
) &
frontend_pid=$!

echo "Stravique is running at http://127.0.0.1:5173"
wait
