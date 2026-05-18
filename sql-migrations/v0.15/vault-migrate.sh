#!/usr/bin/env bash
# =============================================================================
# vault-migrate.sh — Migrate Vault secrets from flat to participant-scoped paths
#
# EDC v0.15 changed vault secret lookups from:
#   secret/data/<key>
# to:
#   secret/data/<participantContextId>/<key>
#
# This script copies all flat secrets under the participant prefix and verifies.
# Run BEFORE flipping edc.vault.hashicorp.allow.fallback=false.
#
# Usage:
#   ./vault-migrate.sh [--vault-addr <addr>] [--participant-id <id>] [--dry-run]
#
# Environment variables:
#   VAULT_ADDR         — Vault address         (default: http://localhost:8200)
#   VAULT_TOKEN        — Vault root token       (required)
#   PARTICIPANT_ID     — Participant context ID  (default: default-participant)
#   DRY_RUN            — Set to "true" to preview without writing (default: false)
# =============================================================================

set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-}"
PARTICIPANT_ID="${PARTICIPANT_ID:-default-participant}"
DRY_RUN="${DRY_RUN:-false}"

GREEN="\033[0;32m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
NC="\033[0m"

log_info()  { echo -e "${YELLOW}→ $*${NC}"; return 0; }
log_ok()    { echo -e "${GREEN}✓ $*${NC}"; return 0; }
log_error() { echo -e "${RED}✗ $*${NC}" >&2; return 0; }
log_dry()   { echo -e "${YELLOW}[DRY-RUN] $*${NC}"; return 0; }

# ─── Parse flags ─────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --vault-addr)     VAULT_ADDR="$2";     shift 2 ;;
        --participant-id) PARTICIPANT_ID="$2"; shift 2 ;;
        --dry-run)        DRY_RUN="true";      shift ;;
        *) log_error "Unknown argument: $1"; exit 1 ;;
    esac
done

# ─── Validate prerequisites ───────────────────────────────────────────────────
if ! command -v vault &>/dev/null; then
    log_error "vault CLI not found. Install hashicorp/vault and retry."
    exit 1
fi

if ! command -v jq &>/dev/null; then
    log_error "jq not found. Install jq and retry."
    exit 1
fi

if [[ -z "${VAULT_TOKEN}" ]]; then
    log_error "VAULT_TOKEN is not set. Export it before running this script."
    exit 1
fi

export VAULT_ADDR VAULT_TOKEN

# ─── Verify vault connectivity ────────────────────────────────────────────────
if ! vault status &>/dev/null; then
    log_error "Cannot connect to Vault at ${VAULT_ADDR}."
    exit 1
fi

log_ok "Connected to Vault at ${VAULT_ADDR}"
log_info "Participant context ID: ${PARTICIPANT_ID}"
[[ "${DRY_RUN}" == "true" ]] && log_dry "DRY-RUN mode — no secrets will be written or deleted"

# ─── Step 1: Inventory flat secrets ──────────────────────────────────────────
FLAT_KEYS_FILE=$(mktemp /tmp/vault-flat-keys.XXXXXX)
trap 'rm -f "${FLAT_KEYS_FILE}"' EXIT

log_info "Listing flat secrets under secret/..."
if ! vault kv list -format=json secret/ 2>/dev/null \
    | jq -r '.[] | select(endswith("/") | not)' \
    > "${FLAT_KEYS_FILE}"; then
    log_error "Failed to list secrets. Check VAULT_TOKEN permissions."
    exit 1
fi

KEY_COUNT=$(wc -l < "${FLAT_KEYS_FILE}")
log_ok "Found ${KEY_COUNT} flat secret(s). Inventory saved to ${FLAT_KEYS_FILE}"

if [[ "${KEY_COUNT}" -eq 0 ]]; then
    log_ok "No flat secrets to migrate. Exiting."
    exit 0
fi

# ─── Step 2: Copy each secret under the participant prefix ───────────────────
log_info "Copying secrets to secret/${PARTICIPANT_ID}/..."

MIGRATED=0
FAILED=0

while IFS= read -r key; do
    key="${key%/}"  # strip trailing slash if any
    src_path="secret/${key}"
    dst_path="secret/${PARTICIPANT_ID}/${key}"

    if [[ "${DRY_RUN}" == "true" ]]; then
        log_dry "Would copy: ${src_path} → ${dst_path}"
        continue
    fi

    # Read secret data
    secret_data=$(vault kv get -format=json "${src_path}" 2>/dev/null | jq -c '.data.data') || {
        log_error "  Failed to read ${src_path} — skipping"
        FAILED=$((FAILED + 1))
        continue
    }

    if [[ "${secret_data}" == "null" ]] || [[ -z "${secret_data}" ]]; then
        log_info "  Skipping ${src_path} (no data)"
        continue
    fi

    # Write to participant-scoped path
    echo "${secret_data}" | vault kv put "${dst_path}" @- &>/dev/null || {
        log_error "  Failed to write ${dst_path} — skipping"
        FAILED=$((FAILED + 1))
        continue
    }

    log_ok "  Migrated: ${src_path} → ${dst_path}"
    MIGRATED=$((MIGRATED + 1))
done < "${FLAT_KEYS_FILE}"

# ─── Step 3: Verify ───────────────────────────────────────────────────────────
if [[ "${DRY_RUN}" != "true" ]]; then
    log_info "Verifying migration — checking a sample key..."
    FIRST_KEY=$(head -1 "${FLAT_KEYS_FILE}")
    if vault kv get "secret/${PARTICIPANT_ID}/${FIRST_KEY}" &>/dev/null; then
        log_ok "Verification passed: secret/${PARTICIPANT_ID}/${FIRST_KEY} exists"
    else
        log_error "Verification FAILED: secret/${PARTICIPANT_ID}/${FIRST_KEY} not found!"
        exit 1
    fi

    log_ok "Migration complete: ${MIGRATED} secret(s) migrated, ${FAILED} failed."
    echo ""
    log_info "Next steps:"
    echo "  1. Redeploy all components with edc.vault.hashicorp.allow.fallback=false"
    echo "  2. Verify zero vault lookup errors in pod logs"
    echo "  3. Then manually delete flat secrets once all components are verified"

    if [[ "${FAILED}" -gt 0 ]]; then
        log_error "Some secrets failed to migrate. Review errors above."
        exit 1
    fi
fi
