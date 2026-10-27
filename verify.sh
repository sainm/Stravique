#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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

python3 "${project_root}/scripts/check_comments.py"
"${project_root}/scripts/ensure-postgres.sh"

cd "${project_root}/backend"
"${gradle_executable}" "${gradle_java_args[@]}" test --no-daemon

cd "${project_root}/frontend"
npm test
npm run build

cd "${project_root}"
"${project_root}/scripts/e2e.sh"

echo "Stravique verification passed."
