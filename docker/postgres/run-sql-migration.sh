#!/bin/sh
# Wait until Postgres accepts connections to the target database, then apply one or more SQL files in order.
# Used by Compose one-shot migration services. Exit 2 from bare psql usually means
# "could not connect" (role missing or init still running); this retries instead.
set -eu

if [ "$#" -lt 1 ]; then
  echo "usage: run-sql-migration.sh /path/to/file.sql [...]" >&2
  exit 1
fi

tries=0
max_tries=60
until psql --dbname="${PGDATABASE:?PGDATABASE is required}" -c 'SELECT 1' >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [ "$tries" -ge "$max_tries" ]; then
    echo "database ${PGDATABASE} did not accept connections after ${max_tries} attempts" >&2
    exit 2
  fi
  sleep 2
done

for file in "$@"; do
  psql -v ON_ERROR_STOP=1 --file="$file"
done
