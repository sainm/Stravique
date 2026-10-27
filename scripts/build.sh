#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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

cd "${project_root}/frontend"
npm ci
npm run build

"${project_root}/scripts/ensure-postgres.sh"

cd "${project_root}/backend"
"${gradle_executable}" "${gradle_java_args[@]}" clean test bootJar --no-daemon

echo "Built ${project_root}/backend/build/libs/stravique-backend-0.1.0.jar"
