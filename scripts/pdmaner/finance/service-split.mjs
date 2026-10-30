// Finance 逻辑模型按 V2 服务切分（第 07 步，design/03 §2.1、§4.2，design/09 §5.2）。
// 逻辑模型仍由 manifests 生成 84 张表并做全部校验；每个服务只持有本库表的物理模型：
//   - 按 Manifest 分组：06 → payout；01/03/07 归 ledger，但 ledger 服务库模型已脱离生成器（2026-10-19），不再投影；
//   - 02 其余表原归 tax，2026-10-29 起不再投影（需求 tax-结账算税与事件登记 S-11，决定 2026-10-29-tax-domain-fact-service）：
//     tax 模型脱离生成器直接维护 paytech/tax/docs/database/tax.pdma.json（13 张业务表 + 补偿与消费去重三张标准表）；
//   - 05（渠道与银行对账）及 02 中的渠道成本确认 / 分配两表不再投影（2026-10-20 对账切片 1，决定 2026-10-20-reconciliation-facts-via-billing）：
//     reconciliation 模型脱离生成器直接维护 paytech/reconciliation/docs/database/reconciliation.pdma.json（由 reconciliation-model.test.mjs 校验）；
//   - 02 中原属 billing 的经济事实三表与渠道成本四表不再投影（2026-10-19 计费事实重构，决定 2026-10-19-billing-fact-engine）：
//     billing 模型脱离生成器直接维护 paytech/billing/docs/database/billing.pdma.json（由 billing-model.test.mjs 校验），
//     渠道成本四表归 channel-connector，由 Payment Orchestration 生成器的 channel-connector 分区产出；
//   - 08 的 compensation_event 每库一份，command_idempotency 删除（S10）；
//   - currency_spec 的 Owner 是 platform-support（D41），ledger 与 billing 的只读副本随各自直接维护的模型维护，生成器不再投影；
//   - account（mor_account）、ledger（mor_ledger）、billing（mor_billing）、reconciliation（mor_reconciliation）与 tax（mor_tax）服务库模型
//     已脱离生成器，分别直接维护 paytech/account/docs/database/account.pdma.json、paytech/ledger/docs/database/ledger.pdma.json、
//     paytech/billing/docs/database/billing.pdma.json、paytech/reconciliation/docs/database/reconciliation.pdma.json 与
//     paytech/tax/docs/database/tax.pdma.json，不在此切分。
// 84 张表的逻辑模型不变，仍覆盖全部 Finance 表并做全部校验。
// 跨服务关系（如 accounting_instruction.economic_fact_id → economic_fact）只保留在逻辑模型，服务模型只保留库内关系。
import { TABLES as ACCOUNTING_FOUNDATION_TABLES } from "./manifests/01-accounting-foundation.mjs";
import { TABLES as TAX_ECONOMICS_COST_TABLES } from "./manifests/02-tax-economics-cost.mjs";
import { TABLES as ACCOUNTING_JOURNAL_TABLES } from "./manifests/03-accounting-journal.mjs";
import { TABLES as CHANNEL_CASH_RECON_TABLES } from "./manifests/05-channel-cash-recon.mjs";
import { TABLES as PAYOUT_TABLES } from "./manifests/06-payout.mjs";
import { TABLES as CLOSE_TABLES } from "./manifests/07-close.mjs";
import { TABLES as RELIABILITY_TABLES } from "./manifests/08-reliability.mjs";

export const FINANCE_SERVICES = Object.freeze(["payout"]);

/** 每库一份的基础设施表。 */
export const PER_DATABASE_TABLES = Object.freeze(["compensation_event"]);

/** 只读副本表：Owner 不在 Finance 后继服务内，副本持有方列出（ledger、billing 的 currency_spec 副本随直接维护的模型，不在此列）。 */
export const READ_ONLY_COPIES = Object.freeze({});

/**
 * 服务库内替换逻辑表定义的物理变体：表定义直接给出时替换同一 defKey；{ replaces, table } 形式替换另一个逻辑表并沿用其实体、
 * 同名字段 ID 与库内关系（物理更名）。
 * <li>第 11a 步 billing 使用的现行补偿表变体随 billing 模型脱离生成器（2026-10-19）一并移出；生成器维护的服务库在接入补偿组件前
 * 保持逻辑模型定义。</li>
 */
export const SERVICE_TABLE_VARIANTS = Object.freeze({});

/** 不在逻辑模型中的服务物理表：prepend 为 true 时排在本库逻辑表之前，否则追加在末尾（当前生成器维护的服务库没有）。 */
export const SERVICE_ADDED_TABLES = Object.freeze({});

/** 归属已脱离生成器的服务库（直接维护 JSON）的逻辑表：不投影到任何生成的服务模型。 */
const NOT_PROJECTED = null;

/** 删除且不进入任何服务模型的表。 */
export const DROPPED_TABLES = Object.freeze(["command_idempotency"]);

/** 2026-10-19 起不投影的 02 表：经济事实三表由 billing 计费事实五表取代，渠道成本四表归 channel-connector。 */
const RETIRED_FROM_02 = new Set([
  "economic_fact", "economic_fact_line", "economic_fact_line_link",
  "channel_cost_plan", "channel_cost_plan_ver", "channel_cost_scope", "channel_cost_component",
]);
/** 2026-10-20 起不投影的 02 表：渠道成本确认 / 分配由 reconciliation 直接维护的模型（渠道账单行 actual_fee）取代。 */
const RECONCILIATION_FROM_02 = new Set(["channel_cost_confirm", "channel_cost_alloc"]);

const MANIFEST_DEFAULTS = Object.freeze([
  [ACCOUNTING_FOUNDATION_TABLES, () => NOT_PROJECTED],
  [TAX_ECONOMICS_COST_TABLES, (key) => {
    if (RETIRED_FROM_02.has(key)) return NOT_PROJECTED;
    if (RECONCILIATION_FROM_02.has(key)) return NOT_PROJECTED;
    // 2026-10-29：其余 02 表原归 tax，tax 模型已脱离生成器直接维护。
    return NOT_PROJECTED;
  }],
  [ACCOUNTING_JOURNAL_TABLES, () => NOT_PROJECTED],
  [CHANNEL_CASH_RECON_TABLES, () => NOT_PROJECTED],
  [PAYOUT_TABLES, () => "payout"],
  [CLOSE_TABLES, () => NOT_PROJECTED],
  [RELIABILITY_TABLES, () => NOT_PROJECTED],
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
      if (service === NOT_PROJECTED) continue;
      invariant(byService.has(service), `表未分配服务：${table.defKey}`);
      byService.get(service).push(table.defKey);
    }
  }
  return byService;
}

/** 每库物理表数：design/03 §4.2 的逻辑切分，切分结果必须逐库一致（ledger、account、billing、reconciliation、tax 已脱离生成器，不在此列）。 */
export const EXPECTED_SERVICE_TABLE_COUNTS = Object.freeze({
  payout: 8,
});
