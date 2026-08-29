import { createHash } from "node:crypto";

export const LIMIT_VERSION_CANONICAL_SCHEMA = "CODE-MOR:RISK:LIMIT_VERSION:1";

/**
 * Builds the scalar-only RFC 8785/JCS projection for one Limit Version.
 * Keys are emitted in Unicode code-point order, so JSON.stringify produces
 * the canonical bytes for this closed ASCII-key schema.
 */
export function canonicalizeLimitVersionContent(limitVersion) {
  return JSON.stringify({
    amount_limit: limitVersion.amountLimit ?? null,
    count_limit: limitVersion.countLimit ?? null,
    currency_mode: limitVersion.currencyMode,
    fixed_currency: limitVersion.fixedCurrency ?? null,
    limit_code: limitVersion.limitCode,
    metric_type: limitVersion.metricType,
    schema: LIMIT_VERSION_CANONICAL_SCHEMA,
    subject_type: limitVersion.subjectType,
    version_no: limitVersion.versionNo,
    window_size: limitVersion.windowSize,
    window_unit: limitVersion.windowUnit,
  });
}

export function computeLimitVersionContentHash(limitVersion) {
  return createHash("sha256")
    .update(canonicalizeLimitVersionContent(limitVersion), "utf8")
    .digest("hex");
}
