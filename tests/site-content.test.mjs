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
  for (const code of [
    "CLIENT",
    "COMPLIANCE",
    "CONTRACT",
    "OFFERING",
    "CHECKOUT",
    "ORDER",
    "PAYMENT",
    "CHANNEL",
    "ROUTING",
    "RISK",
    "FINANCE",
    "SETTLEMENT",
    "PLATFORM",
  ]) {
    assert.match(source, new RegExp(`code:\\s*[\"']${code}[\"']`));
  }
});

test("every system exposes use cases", () => {
  for (const prefix of [
    "CLI-",
    "CMP-",
    "CON-",
    "OFF-",
    "CHK-",
    "ORD-",
    "PAY-",
    "CHN-",
    "RTE-",
    "RSK-",
    "FIN-",
    "SET-",
    "PLT-",
  ]) {
    assert.match(source, new RegExp(prefix.replace("-", "\\-")));
  }
});
