#!/usr/bin/env bash
# =============================================================================
# apply-migrations.sh — EDC v0.14 → v0.15.1 database migrations
#
# Usage:
#   ./apply-migrations.sh [--host <host>] [--port <port>] [--user <user>] [--pass <pass>]
#
# Environment variables (override via flags or env):
#   DB_HOST   — PostgreSQL host           (default: localhost)
#   DB_PORT   — PostgreSQL port           (default: 57521, the devbox port-forward)
#   DB_USER   — PostgreSQL superuser      (default: postgres)
#   DB_PASS   — PostgreSQL password       (default: password)
#
# Prerequisites:
#   kubectl port-forward postgresql-0 57521:5432 (for devbox)
#   psql available on PATH
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="${SCRIPT_DIR}"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-57521}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-password}"

GREEN="\033[0;32m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
NC="\033[0m"

log_info()  { echo -e "${YELLOW}→ $*${NC}"; return 0; }
log_ok()    { echo -e "${GREEN}✓ $*${NC}"; return 0; }
log_error() { echo -e "${RED}✗ $*${NC}" >&2; return 0; }

run_sql() {
    local db="$1"
    local script="$2"
    PGPASSWORD="${DB_PASS}" psql \
        -h "${DB_HOST}" \
        -p "${DB_PORT}" \
        -U "${DB_USER}" \
        -d "${db}" \
        -v ON_ERROR_STOP=1 \
        -f "${script}"
    return $?
}

# ─── Parse flags ─────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --host) DB_HOST="$2"; shift 2 ;;
        --port) DB_PORT="$2"; shift 2 ;;
        --user) DB_USER="$2"; shift 2 ;;
        --pass) DB_PASS="$2"; shift 2 ;;
        *) log_error "Unknown argument: $1"; exit 1 ;;
    esac
done

# ─── Check psql available ────────────────────────────────────────────────────
if ! command -v psql &>/dev/null; then
    log_error "psql not found. Install postgresql-client and retry."
    exit 1
fi

# ─── Check port-forward reachable ────────────────────────────────────────────
if ! PGPASSWORD="${DB_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "\q" &>/dev/null; then
    log_error "Cannot connect to PostgreSQL at ${DB_HOST}:${DB_PORT}."
    log_error "Run: kubectl port-forward postgresql-0 57521:5432"
    exit 1
fi

log_ok "Connected to PostgreSQL at ${DB_HOST}:${DB_PORT}"

# =============================================================================
# Participant databases (consumerdb, providerdb)
# =============================================================================
PARTICIPANT_SCRIPT="${MIGRATIONS_DIR}/participant-migration.sql"
for db in consumerdb providerdb; do
    log_info "Running participant-migration.sql on ${db}..."
    run_sql "${db}" "${PARTICIPANT_SCRIPT}"
    log_ok "participant-migration.sql applied to ${db}"
done

# =============================================================================
# Authority database (authoritydb)
# =============================================================================
AUTHORITY_SCRIPT="${MIGRATIONS_DIR}/authority-migration.sql"
log_info "Running authority-migration.sql on authoritydb..."
run_sql "authoritydb" "${AUTHORITY_SCRIPT}"
log_ok "authority-migration.sql applied to authoritydb"

log_ok "All database migrations completed successfully."
