// Splits the Payment Orchestration PDManer model into one canonical model per owning service
// (requirements/20261017/architecture-V2骨架与模块搬迁/design/03 §2.3, design/09 §4.3 / §5.2).
//
// - channel-connector owns the channel identity, account, MID, capability, routing-policy, interaction and callback tables,
//   since 2026-10-19 the four channel cost configuration tables moved from billing (decision 2026-10-19-billing-fact-engine),
//   and since 2026-10-20 the manual configuration change log t_channel_config_change;
// - card owns the four card tables since step 12 (design/05; mor_card, card-service);
// - payment owns everything else, including the three channel-dispute tables until the dispute service exists;
// - t_compensation_event is a per-database table and appears in every model.
// Entities, field / index / relation / diagram IDs are copied unchanged. Relations whose parent lives in the other service are
// dropped (no cross-database relation, AGENTS.md); diagrams keep only the cells of their own service.

export const CHANNEL_CONNECTOR_TABLES = Object.freeze([
  "t_payment_channel",
  "t_channel_account",
  "t_channel_mid",
  "t_mid_capability",
  "t_mid_capability_detail",
  "t_routing_policy",
  "t_routing_policy_version",
  "t_routing_policy_mid",
  "t_channel_interaction",
  "t_channel_callback",
  "t_channel_callback_item",
  "t_channel_event_exception",
  "t_channel_cost_plan",
  "t_channel_cost_plan_ver",
  "t_channel_cost_scope",
  "t_channel_cost_component",
  "t_channel_config_change",
]);

export const CARD_TABLES = Object.freeze([
  "t_payment_card",
  "t_payment_card_credential",
  "t_payment_card_instrument",
  "t_payment_stored_card",
]);

export const PER_DATABASE_TABLES = Object.freeze(["t_compensation_event"]);

// name / describe follow the per-service convention of commit cf4a429c (service directory name, short Chinese table scope).
export const PAYMENT_SERVICE_MODELS = Object.freeze({
  payment: Object.freeze({
    name: "payment",
    describe: "支付意图、支付尝试与支付动作、路由决策、支付工具 Token、渠道退款与服务商争议通知",
  }),
  "channel-connector": Object.freeze({
    name: "channel-connector",
    describe: "支付服务商、账户与商户号、静态能力、路由策略、服务商交互与回调记录、渠道成本配置",
  }),
  card: Object.freeze({
    name: "card",
    describe: "卡、卡凭证、交易级卡工具、已存卡与一次性读卡票据",
  }),
});

/** Owning service of a physical table. */
export function serviceOfTable(table) {
  if (CHANNEL_CONNECTOR_TABLES.includes(table)) return "channel-connector";
  if (CARD_TABLES.includes(table)) return "card";
  return "payment";
}

/** Whether a table belongs to the given service's database. */
export function tableInService(table, service) {
  return PER_DATABASE_TABLES.includes(table) || serviceOfTable(table) === service;
}

/** Returns a deep copy of the model that contains only the given service's tables, relations and diagram cells. */
export function partitionModel(model, service) {
  const target = PAYMENT_SERVICE_MODELS[service];
  if (!target) throw new Error(`Unknown payment partition: ${service}`);
  const copy = structuredClone(model);
  copy.name = target.name;
  copy.describe = target.describe;
  copy.entities = copy.entities.filter((entity) => tableInService(entity.defKey, service));
  const entityIds = new Set(copy.entities.map((entity) => entity.id));
  for (const entity of copy.entities) {
    entity.correlations = entity.correlations.filter((relation) => entityIds.has(relation.refEntity));
  }
  const relationIds = new Set(copy.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id)));
  copy.diagrams = copy.diagrams.map((diagram) => {
    const cells = diagram.canvasData.cells;
    const tableCells = cells.filter((cell) => cell.shape === "table" && entityIds.has(cell.originKey));
    const cellIds = new Set(tableCells.map((cell) => cell.id));
    const kept = cells.filter((cell) => {
      if (cell.shape === "table") return entityIds.has(cell.originKey);
      if (cell.shape === "erdRelation") {
        return relationIds.has(cell.originKey) && cellIds.has(cell.source?.cell) && cellIds.has(cell.target?.cell);
      }
      return true;
    });
    return { ...diagram, canvasData: { ...diagram.canvasData, cells: kept } };
  }).filter((diagram) => diagram.canvasData.cells.some((cell) => cell.shape === "table"));
  return copy;
}
