import { TABLES } from "./manifest.mjs";

export const CHECK_CONTRACTS = Object.freeze({
  ck_vault_record_mode: "token_mode IN ('REFERENCE','STABLE')",
  ck_vault_record_fingerprint: "(token_mode='STABLE' AND value_fingerprint IS NOT NULL) OR (token_mode='REFERENCE' AND value_fingerprint IS NULL)",
  ck_vault_access_action: "action IN ('PROTECT','REVEAL')",
  ck_vault_access_outcome: "outcome IN ('SUCCESS','DENIED','FAILED')",
});

export const CHECK_BINDINGS = Object.freeze({
  ck_vault_record_mode: Object.freeze(["vault_record"]),
  ck_vault_record_fingerprint: Object.freeze(["vault_record"]),
  ck_vault_access_action: Object.freeze(["vault_access_event"]),
  ck_vault_access_outcome: Object.freeze(["vault_access_event"]),
});

export const DIAGRAM_GROUPS = Object.freeze([
  Object.freeze({
    defKey: "vault_core",
    defName: "Vault 核心",
    primary: Object.freeze([
      "vault_record",
      "vault_protect_request",
      "vault_access_event",
    ]),
  }),
]);

export const ALLOWED_TABLE_KEYS = Object.freeze(TABLES.map((tableSpec) => tableSpec.defKey));

export const ALLOWED_FIELDS_BY_TABLE = Object.freeze(Object.fromEntries(TABLES.map((tableSpec) => [
  tableSpec.defKey,
  Object.freeze(tableSpec.fields.map((fieldSpec) => fieldSpec.defKey)),
])));

export const FORBIDDEN_TABLE_KEYS = Object.freeze([
  "vault_binding",
  "vault_reference_binding",
  "vault_payload_version",
  "vault_metadata_version",
  "vault_blind_index",
  "vault_masked_value",
  "vault_key_version",
  "vault_crypto_job",
  "vault_retention",
  "vault_legal_hold",
  "vault_destruction",
  "compensation_event",
  "platform_audit_event",
]);

export const FORBIDDEN_FIELD_PATTERN = Object.freeze(
  /environment|tenant|binding|resource|plaintext|raw_value|masked|blind_index|key_version|algorithm|status|expiry|shard|bucket|compensation|payload_json/u,
);

export const APPROVED_SENSITIVE_FIELDS = Object.freeze({
  value_fingerprint: "vault_record",
  request_hash: "vault_protect_request",
  ciphertext: "vault_record",
  encryption_nonce: "vault_record",
  wrapped_dek: "vault_record",
  kms_key_ref: "vault_record",
});

export const ASCII_BINARY_FIELDS = Object.freeze([
  "token",
  "data_type",
  "token_mode",
  "caller_service",
  "idempotency_key",
  "action",
  "purpose",
  "outcome",
  "result_code",
  "policy_version",
]);
