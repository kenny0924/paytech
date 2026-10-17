import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const root = new URL("../../", import.meta.url);
// CCT compensation handlers live in client-master; commercial keeps an identical
// per-database copy of t_compensation_event.
const models = ["paytech/client-master/docs/database/client-master.pdma.json", "paytech/commercial/docs/database/commercial.pdma.json"]
  .map(file => JSON.parse(readFileSync(new URL(file, root), "utf8")));
const [model] = models;
const event = model.entities.find(e => e.defKey === "t_compensation_event");
const fields = new Map(event.fields.map(f => [f.defKey, f]));

test("CCT compensation storage matches the Foundation consumer contract", () => {
  assert.deepEqual(models[1].entities.find(e => e.defKey === "t_compensation_event"), event, "commercial copy must be identical");
  assert.deepEqual([...fields.keys()], ["id", "event_key", "event_type", "sub_event_type", "payload_json", "execute_status", "execute_count", "next_execute_at", "lock_owner", "lock_until", "last_error_code", "last_error_summary", "lock_version", "create_time", "update_time"]);
  for (const [name, type, len, required, value] of [
    ["id", "BIGINT", "", true, ""],
    ["event_key", "VARCHAR", 128, true, ""],
    ["event_type", "VARCHAR", 64, true, ""],
    ["sub_event_type", "VARCHAR", 64, true, ""],
    ["payload_json", "VARCHAR", 4096, false, ""],
    ["execute_status", "VARCHAR", 16, true, "'INIT'"],
    ["execute_count", "INT", "", true, "0"],
    ["next_execute_at", "DATETIME", 6, true, "CURRENT_TIMESTAMP(6)"],
    ["lock_owner", "VARCHAR", 64, false, ""],
    ["lock_until", "DATETIME", 6, false, ""],
    ["last_error_code", "VARCHAR", 64, false, ""],
    ["last_error_summary", "VARCHAR", 1000, false, ""],
    ["lock_version", "BIGINT", "", true, "1"],
    ["create_time", "DATETIME", 6, true, "CURRENT_TIMESTAMP(6)"],
    ["update_time", "DATETIME", 6, true, "CURRENT_TIMESTAMP(6)"],
  ]) {
    const field = fields.get(name);
    assert.deepEqual([field.type, field.len, field.notNull, field.defaultValue], [type, len, required, value], name);
  }
  for (const name of ["event_key", "event_type", "sub_event_type", "payload_json"]) {
    assert.deepEqual(fields.get(name).extProps, { characterSet: "utf8mb4", collation: "utf8mb4_0900_bin" }, name);
  }
  assert.deepEqual(fields.get("execute_status").extProps, { characterSet: "ascii", collation: "ascii_bin" });
  assert.deepEqual(event.indexes.map(i => [i.unique, i.fields.map(p => event.fields.find(f => f.id === p.fieldDefKey).defKey)]).sort(), [
    [true, ["event_key", "event_type", "sub_event_type"]],
    [false, ["execute_status", "next_execute_at", "id"]],
    [false, ["execute_status", "lock_until", "id"]],
  ].sort());
});
