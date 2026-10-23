import { translator } from "./i18n/index.ts";

const te = translator("enum");

/**
 * 枚举 Code 的登记表：展示名在 `enum` 命名空间，key 为 `enum.<group>.<code 小写>`
 * <li>只翻译登记过的 Code；未知 Code 返回 null，由调用方决定原样显示还是显示占位。</li>
 * <li>一致性测试据此核对每个登记 Code 在英文基准中都有展示名。</li>
 */
export const enumCodes = {
  role: ["TENANT_OWNER", "TENANT_ADMIN", "TENANT_DEVELOPER", "TENANT_VIEWER"],
  member_status: ["ACTIVE", "SUSPENDED", "REVOKED"],
  invitation_status: ["PENDING", "EXPIRED", "ACCEPTED", "REVOKED"],
  client_type: ["INDIVIDUAL", "ORGANIZATION"],
  client_status: ["ACTIVE", "PENDING", "SUSPENDED", "TERMINATED"],
  business_model: ["PHYSICAL_GOODS", "DIGITAL_GOODS", "SERVICES", "SUBSCRIPTION", "MARKETPLACE", "OTHER"],
  credential_status: ["ACTIVE", "REVOKED", "EXPIRED"],
  endpoint_status: ["ACTIVE", "DISABLED"],
  delivery_status: ["CREATED", "PROCESSING", "RETRY_WAIT", "SUCCEEDED", "DEAD", "CANCELLED"],
  webhook_failure: ["HTTP_STATUS", "TIMEOUT", "CONNECT_FAILED", "TLS_FAILED", "DNS_FAILED", "BLOCKED_ADDRESS", "INTERRUPTED"],
  application_status: ["DRAFT", "RETURNED", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED"],
  scan_status: ["PENDING_SCAN", "SCANNING", "AVAILABLE", "QUARANTINED", "REJECTED"],
  relation_type: ["OWNERSHIP", "CONTROL", "DIRECTOR", "SIGNATORY", "UBO"],
  material: [
    "PASSPORT", "IDENTITY_DOCUMENT", "IDENTITY", "REGISTRATION_CERTIFICATE", "ADDRESS_PROOF", "PROOF_OF_ADDRESS",
    "OWNERSHIP_STRUCTURE", "AUTHORIZATION_LETTER", "SUPPORTING_DOCUMENT",
  ],
  webhook_event: [
    "order.paid", "order.cancelled", "order.expired", "order.funds_available",
    "refund.created", "refund.succeeded", "refund.failed", "refund.cancelled",
  ],
} as const satisfies Record<string, readonly string[]>;
export type EnumGroup = keyof typeof enumCodes;

export function enumLabel(group: EnumGroup, code: string | null | undefined): string | null {
  if (!code || !(enumCodes[group] as readonly string[]).includes(code)) return null;
  return te(group + "." + code.toLowerCase());
}
