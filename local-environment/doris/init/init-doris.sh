#!/usr/bin/env bash
# One-shot Doris bootstrap for the analytics profile (runs in the doris-init container, Doris FE image).
# Idempotent: sets the root password on first run, registers the single BE, creates mor_data_service and the
# read-only data_service_app account. Both remain only as the queried database and read-only account for the
# Platform data-query Doris dialect manual tests (2026-10-22 decision); the former data-service is removed.
# Passwords come from the environment and are never printed.
set -euo pipefail

readonly FE_HOST=doris-fe
readonly FE_QUERY_PORT=9030
readonly BE_HEARTBEAT=doris-be:9050
readonly DATABASE=mor_data_service
readonly READ_ONLY_USER=data_service_app

validate_password() {
  local variable_name="$1"
  local password="${!variable_name:-}"
  if [[ ! "$password" =~ ^[A-Za-z0-9._@%+=:-]{16,128}$ ]]; then
    echo "Invalid local password format for ${variable_name}" >&2
    exit 1
  fi
}

validate_password MOR_DORIS_ROOT_PASSWORD
validate_password MOR_DATA_SERVICE_DORIS_PASSWORD

doris_root() {
  MYSQL_PWD="$MOR_DORIS_ROOT_PASSWORD" mysql --connect-timeout=3 -h "$FE_HOST" -P "$FE_QUERY_PORT" -uroot -N -B "$@"
}

doris_root_without_password() {
  mysql --connect-timeout=3 -h "$FE_HOST" -P "$FE_QUERY_PORT" -uroot --skip-password -N -B "$@"
}

ensure_root_password() {
  for attempt in $(seq 1 60); do
    if doris_root -e "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    if doris_root_without_password -e "SELECT 1" >/dev/null 2>&1; then
      doris_root_without_password <<<"SET PASSWORD FOR 'root' = PASSWORD('${MOR_DORIS_ROOT_PASSWORD}')"
      echo "Doris root password initialized"
      return 0
    fi
    sleep 2
  done
  echo "Doris FE ${FE_HOST}:${FE_QUERY_PORT} is not reachable with or without the configured root password" >&2
  exit 1
}

ensure_backend() {
  if ! doris_root -e "SHOW BACKENDS" | grep -q "doris-be"; then
    doris_root -e "ALTER SYSTEM ADD BACKEND \"${BE_HEARTBEAT}\""
    echo "Doris backend ${BE_HEARTBEAT} registered"
  fi
  for attempt in $(seq 1 60); do
    if doris_root -e "SHOW BACKENDS" | grep "doris-be" | grep -qw "true"; then
      return 0
    fi
    sleep 2
  done
  echo "Doris backend ${BE_HEARTBEAT} did not become alive" >&2
  exit 1
}

ensure_database_and_reader() {
  doris_root -e "CREATE DATABASE IF NOT EXISTS ${DATABASE}"
  # Statements carrying a password go through stdin so they never appear in a process argument list.
  doris_root <<<"CREATE USER IF NOT EXISTS '${READ_ONLY_USER}'@'%' IDENTIFIED BY '${MOR_DATA_SERVICE_DORIS_PASSWORD}'"
  doris_root <<<"SET PASSWORD FOR '${READ_ONLY_USER}'@'%' = PASSWORD('${MOR_DATA_SERVICE_DORIS_PASSWORD}')"
  doris_root -e "GRANT SELECT_PRIV ON ${DATABASE}.* TO '${READ_ONLY_USER}'@'%'"
}

ensure_root_password
ensure_backend
ensure_database_and_reader
echo "Doris ready: database ${DATABASE}, read-only user ${READ_ONLY_USER}"
