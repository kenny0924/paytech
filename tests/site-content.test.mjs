import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(
  new URL("../content/requirements.ts", import.meta.url),
  "utf8",
);

test("requirements catalog contains every stable section", () => {
  for (const id of [
    "overview",
    "business-model",
    "architecture",
    "domain-map",
    "end-to-end",
    "use-cases",
    "systems",
    "payment-model",
    "finance-model",
    "decisions",
    "open-questions",
  ]) {
    assert.match(source, new RegExp(`id:\\s*[\"']${id}[\"']`));
  }
});

test("requirements catalog contains all business systems", () => {
  assert.doesNotMatch(source, /export const DOMAIN_DEFINITIONS/);
  assert.doesNotMatch(source, /Payment Orchestration：支付和退款事实/);
  assert.doesNotMatch(source, /其他五个系统：已发生的业务 MQ 通知/);
  for (const code of [
    "CLIENT_CONTRACT",
    "COMMERCE",
    "PAYMENT",
    "RISK_COMPLIANCE",
    "FINANCE",
    "PLATFORM",
  ]) {
    assert.match(source, new RegExp(`code:\\s*[\"']${code}[\"']`));
  }
});

test("every system exposes use cases", () => {
  for (const prefix of [
    "CCT-",
    "COM-",
    "PAY-",
    "RCO-",
    "FIN-",
    "PLT-",
  ]) {
    assert.match(source, new RegExp(prefix.replace("-", "\\-")));
  }
});

test("Commerce exposes fulfillment evidence required by Risk", () => {
  assert.match(source, /iuc\("COM-I08", "查询履约证据"/);
});
