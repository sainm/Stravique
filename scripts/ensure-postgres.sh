#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${STRAVIQUE_DB_URL:-}" || -n "${STRAVIQUE_TEST_DB_URL:-}" ]]; then
  echo "External PostgreSQL configuration detected; assuming databases are provisioned."
  exit 0
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required. Install PostgreSQL before starting Stravique." >&2
  exit 1
fi

admin_url="${STRAVIQUE_ADMIN_DB_URL:-postgresql:///postgres}"
database_user="${STRAVIQUE_DB_USER:-$(id -un)}"
runtime_database="${STRAVIQUE_DB_NAME:-stravique}"
test_database="${STRAVIQUE_TEST_DB_NAME:-stravique_test}"

if [[ -n "${STRAVIQUE_DB_PASSWORD:-}" ]]; then
  export PGPASSWORD="${STRAVIQUE_DB_PASSWORD}"
fi

for identifier in "${database_user}" "${runtime_database}" "${test_database}"; do
  if [[ ! "${identifier}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "PostgreSQL identifiers may contain only letters, digits, and underscores." >&2
    exit 1
  fi
done

if ! psql "${admin_url}" -Atqc "SELECT 1" >/dev/null 2>&1; then
  echo "PostgreSQL is not reachable through ${admin_url}." >&2
  exit 1
fi

if [[ "$(psql "${admin_url}" -Atqc "SELECT 1 FROM pg_roles WHERE rolname = '${database_user}'")" != "1" ]]; then
  echo "PostgreSQL role ${database_user} does not exist." >&2
  exit 1
fi

for database in "${runtime_database}" "${test_database}"; do
  if [[ "$(psql "${admin_url}" -Atqc "SELECT 1 FROM pg_database WHERE datname = '${database}'")" != "1" ]]; then
    psql "${admin_url}" -v ON_ERROR_STOP=1 \
      -c "CREATE DATABASE \"${database}\" OWNER \"${database_user}\""
  fi
done

psql "postgresql:///${runtime_database}" -Atqc "SELECT current_database()" >/dev/null
psql "postgresql:///${test_database}" -Atqc "SELECT current_database()" >/dev/null
echo "PostgreSQL databases are ready: ${runtime_database}, ${test_database}"
