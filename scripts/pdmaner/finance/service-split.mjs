// Finance 逻辑模型按 V2 服务切分（第 07 步，design/03 §2.1、§4.2，design/09 §5.2）。
// 逻辑模型仍由 manifests 生成 84 张表并做全部校验；每个服务只持有本库表的物理模型：
//   - 按 Manifest 分组：01/03/07 → ledger，05 → reconciliation，06 → payout，02 按表拆到 tax / billing / reconciliation；
//   - 08 的 compensation_event 每库一份，command_idempotency 删除（S10）；
//   - currency_spec 的 Owner 是 platform-support（D41），ledger 与 billing 各持一份只读副本；
//   - account 本期只有每库一份的 compensation_event（mor_account 在第 11b 步新建，t_account 本期归 ledger）。
// 跨服务关系（如 accounting_instruction.economic_fact_id → economic_fact）只保留在逻辑模型，服务模型只保留库内关系。
import { TABLES as ACCOUNTING_FOUNDATION_TABLES } from "./manifests/01-accounting-foundation.mjs";
import { TABLES as TAX_ECONOMICS_COST_TABLES } from "./manifests/02-tax-economics-cost.mjs";
import { TABLES as ACCOUNTING_JOURNAL_TABLES } from "./manifests/03-accounting-journal.mjs";
import { TABLES as CHANNEL_CASH_RECON_TABLES } from "./manifests/05-channel-cash-recon.mjs";
import { TABLES as PAYOUT_TABLES } from "./manifests/06-payout.mjs";
import { TABLES as CLOSE_TABLES } from "./manifests/07-close.mjs";
import { TABLES as RELIABILITY_TABLES } from "./manifests/08-reliability.mjs";

export const FINANCE_SERVICES = Object.freeze(["ledger", "account", "billing", "reconciliation", "tax", "payout"]);

/** 每库一份的基础设施表。 */
export const PER_DATABASE_TABLES = Object.freeze(["compensation_event"]);

/** 只读副本表：Owner 不在 Finance 后继服务内，副本持有方列出。 */
export const READ_ONLY_COPIES = Object.freeze({ currency_spec: Object.freeze(["ledger", "billing"]) });

/** 删除且不进入任何服务模型的表。 */
export const DROPPED_TABLES = Object.freeze(["command_idempotency"]);

const BILLING_TABLES = new Set([
  "economic_fact", "economic_fact_line", "economic_fact_line_link",
  "channel_cost_plan", "channel_cost_plan_ver", "channel_cost_scope", "channel_cost_component",
]);
const RECONCILIATION_FROM_02 = new Set(["channel_cost_confirm", "channel_cost_alloc"]);

const MANIFEST_DEFAULTS = Object.freeze([
  [ACCOUNTING_FOUNDATION_TABLES, () => "ledger"],
  [TAX_ECONOMICS_COST_TABLES, (key) => {
    if (BILLING_TABLES.has(key)) return "billing";
    if (RECONCILIATION_FROM_02.has(key)) return "reconciliation";
    return "tax";
  }],
  [ACCOUNTING_JOURNAL_TABLES, () => "ledger"],
  [CHANNEL_CASH_RECON_TABLES, () => "reconciliation"],
  [PAYOUT_TABLES, () => "payout"],
  [CLOSE_TABLES, () => "ledger"],
  [RELIABILITY_TABLES, () => null],
]);

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

/** 逻辑表 → 服务清单（多值表示复制或每库一份）。 */
export function serviceTables() {
  const byService = new Map(FINANCE_SERVICES.map((service) => [service, []]));
  for (const [tables, ownerOf] of MANIFEST_DEFAULTS) {
    for (const table of tables) {
      if (DROPPED_TABLES.includes(table.defKey)) continue;
      if (PER_DATABASE_TABLES.includes(table.defKey)) {
        for (const service of FINANCE_SERVICES) byService.get(service).push(table.defKey);
        continue;
      }
      if (READ_ONLY_COPIES[table.defKey]) {
        for (const service of READ_ONLY_COPIES[table.defKey]) byService.get(service).push(table.defKey);
        continue;
      }
      const service = ownerOf(table.defKey);
      invariant(byService.has(service), `表未分配服务：${table.defKey}`);
      byService.get(service).push(table.defKey);
    }
  }
  return byService;
}

/** design/03 §4.2 的每库物理表数，切分结果必须逐库一致。 */
export const EXPECTED_SERVICE_TABLE_COUNTS = Object.freeze({
  ledger: 29, account: 1, billing: 9, reconciliation: 18, tax: 24, payout: 8,
});
