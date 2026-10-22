import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { renderSchema } from "./generate-oe-client-schemas.mjs";
import { generateCardModel } from "./generate-card-model.mjs";
import { serializePaymentModel } from "./generate-payment-model.mjs";
import { CARD_TABLES } from "./payment-service-partition.mjs";

const root = path.resolve(import.meta.dirname, "../..");
const source = "paytech/card/docs/database/card.pdma.json";
const read = (relative) => readFileSync(path.join(root, relative), "utf8");
const createTables = (sql) => sql.split("\n\n").map((block) => block.trim()).filter((block) => block.startsWith("CREATE TABLE"));
// paytech-messaging tables are copied verbatim from their foundation SQL, whose layout differs from the PDManer rendering.
const MESSAGING_TABLES = ["t_consumed_event", "t_consumed_aggregate_version"];
// 2026-10-22 BIN library tables (requirements/20261022/card-BIN库与卡属性 technique §3.6).
const BIN_LIBRARY_TABLES = ["t_card_bin_library_version", "t_card_bin_range", "t_card_bin_import_error",
  "t_card_bin_library_activation"];
const normalize = (block) => block
  .replaceAll("`", "")
  .replace(/ COMMENT ''/gu, "")
  .replace(/\) ENGINE[^;]*COMMENT\s*=\s*('(?:''|[^'])*');$/u, ") COMMENT $1;")
  .split("\n").map((line) => line.trim()).join("\n");

test("Card model is the deterministic card partition plus the step 12 additions", () => {
  assert.equal(read(source), serializePaymentModel(generateCardModel()));
  const model = JSON.parse(read(source));
  assert.deepEqual(model.entities.map((entity) => entity.defKey), [
    ...CARD_TABLES, "t_card_material_ticket", ...BIN_LIBRARY_TABLES, "t_compensation_event", ...MESSAGING_TABLES,
  ]);
  const payment = JSON.parse(read("paytech/payment/docs/database/payment.pdma.json"));
  const shared = payment.entities.map((entity) => entity.defKey).filter((table) => model.entities.some((entity) => entity.defKey === table));
  assert.deepEqual(shared, ["t_compensation_event"]);
});

test("Card canonical schema matches the maintained model", () => {
  const sql = read("paytech/card/docs/database/sql/card-schema.sql");
  const rendered = createTables(renderSchema(JSON.parse(read(source)), source));
  const reviewed = createTables(sql);
  assert.equal(reviewed.length, rendered.length);
  rendered.forEach((block, index) => {
    const messaging = MESSAGING_TABLES.some((table) => block.startsWith(`CREATE TABLE \`${table}\``));
    assert.equal(messaging ? normalize(reviewed[index]) : reviewed[index], messaging ? normalize(block) : block);
  });
  assert.doesNotMatch(sql, /\b(?:FOREIGN KEY|CHECK\s*\()/u);
});
