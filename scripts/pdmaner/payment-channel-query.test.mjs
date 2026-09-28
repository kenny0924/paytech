import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { generatePaymentModel } from "./generate-payment-model.mjs";
import { renderSchema } from "./generate-oe-client-schemas.mjs";

test("Channel target has scoped accounts and separate typed capability details", () => {
  const model = generatePaymentModel();
  const tables = new Map(model.entities.map((table) => [table.defKey, table]));
  const fields = (name) => tables.get(name).fields.map((field) => field.defKey);
  assert.deepEqual(fields("t_channel_account"), ["channel_account_id", "channel_id", "entity_no",
    "account_name", "account_vault_token", "account_status", "remark", "create_time", "update_time"]);
  assert.deepEqual(fields("t_mid_capability"), ["mid_capability_id", "mid_id", "payment_method_code",
    "scheme_code", "country_code", "currency", "mcc_code", "capability_status", "create_time", "update_time"]);
  assert.deepEqual(fields("t_mid_capability_detail"), ["mid_capability_detail_id", "mid_capability_id",
    "capability_code", "value_type", "boolean_value", "decimal_value", "code_value", "create_time", "update_time"]);
  assert.ok(!fields("t_payment_channel").includes("adapter_code"));
  assert.ok(!tables.has("t_mid_health_state"));
  assert.match(tables.get("t_mid_capability").fields.find((f) => f.defKey === "country_code").comment, /消费者国家/u);
  const source = "payment-orchestration-service/docs/database/payment-orchestration.pdma.json";
  assert.equal(readFileSync(new URL(`../../${source}`, import.meta.url), "utf8"), `${JSON.stringify(model, null, 2)}\n`);
  assert.equal(readFileSync(new URL("../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql", import.meta.url), "utf8"), renderSchema(model, source));
});
