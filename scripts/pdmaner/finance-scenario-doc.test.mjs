import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import test from "node:test";

import {
  APPROVED_TABLES,
  EXPECTED_CHECK_BINDINGS,
  EXPECTED_CHECK_CONTRACTS,
} from "./finance-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const scenarioPath = path.join(
  workspaceRoot,
  "agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md",
);
const modelPath = path.join(
  workspaceRoot,
  "finance-service/docs/database/finance.pdma.json",
);

const mechanicalNotNullExemptions = new Set(["create_time", "update_time"]);

function parseScenarioRecordLine(line, lineNumber) {
  const match = line.match(/^\| `(t_[a-z0-9_]+)` \| (.*) \|$/u);
  if (!match) return null;
  const values = new Map();
  for (const fragment of match[2].split("，")) {
    const assignment = fragment.trim().match(/^([a-z][a-z0-9_]*)=([^，]+)$/u);
    assert.ok(assignment, `第 ${lineNumber} 行 ${match[1]} 无法解析片段：${fragment}`);
    const [, field, value] = assignment;
    assert.ok(!values.has(field), `第 ${lineNumber} 行 ${match[1]} 重复字段 ${field}`);
    assert.ok(value.length > 0, `第 ${lineNumber} 行 ${match[1]}.${field} 不能为空字符串`);
    values.set(field, value);
  }
  return { table: match[1], data: match[2], lineNumber, values };
}

function scenarioRows(content) {
  return content.split(/\n/u).flatMap((line, index) => {
    const record = parseScenarioRecordLine(line, index + 1);
    if (/^\| `t_[^|]+/u.test(line)) {
      assert.ok(record, `第 ${index + 1} 行无法解析表记录：${line}`);
    }
    return record ? [record] : [];
  });
}

function fieldAssignments(data) {
  return data.split("，").map((fragment) => {
    const match = fragment.match(/^([a-z][a-z0-9_]*)=(.+)$/u);
    assert.ok(match, `无法解析字段赋值：${fragment}`);
    return { field: match[1], value: match[2] };
  });
}

function assignmentMap(data) {
  return new Map(fieldAssignments(data).map(({ field, value }) => [field, value]));
}

function parseDeclaredJournalRows(content) {
  return content.split(/\n/u).flatMap((line, index) => {
    if (!line.includes("Journal 平衡：")) return [];
    const match = line.match(/Journal 平衡：entry_key=([^；\n]+)；debit_total=(-?\d+(?:\.\d+)?)；credit_total=(-?\d+(?:\.\d+)?)。$/u);
    assert.ok(match, `第 ${index + 1} 行 Journal 声明无法完整解析`);
    return [{
      entryKey: match[1],
      debitTotal: match[2],
      creditTotal: match[3],
      lineNumber: index + 1,
    }];
  });
}

function allowedValues(field) {
  return field.comment.match(/允许值：([^。]+)。/u)?.[1].split("、") ?? null;
}

function assertValueMatchesType(tableKey, field, value) {
  if (value === "NULL") return;
  const context = `${tableKey}.${field.defKey}=${value}`;
  if (["BIGINT", "INT", "INTEGER", "TINYINT", "SMALLINT", "BOOLEAN"].includes(field.type)) {
    assert.match(value, /^-?\d+$/u, `${context} 不是整数`);
  } else if (field.type === "DECIMAL") {
    assert.match(value, /^-?\d+(?:\.\d+)?$/u, `${context} 不是十进制数`);
    const unsigned = value.replace(/^-/, "");
    const [rawInteger, fraction = ""] = unsigned.split(".");
    const integerDigits = rawInteger.replace(/^0+(?=\d)/u, "").length;
    const precision = Number(field.len);
    const scale = Number(field.scale);
    assert.ok(fraction.length <= scale, `${context} 小数位 ${fraction.length} 超过 ${scale}`);
    assert.ok(integerDigits <= precision - scale,
      `${context} 整数位 ${integerDigits} 超过 ${precision - scale}`);
    assert.ok(integerDigits + fraction.length <= precision,
      `${context} 精度 ${integerDigits + fraction.length} 超过 ${precision}`);
  } else if (field.type === "DATE") {
    assert.match(value, /^\d{4}-\d{2}-\d{2}$/u, `${context} 不是 DATE`);
    const [year, month, day] = value.split("-").map(Number);
    const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    assert.ok(month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth[month - 1],
      `${context} 不是有效 DATE 日历值`);
  } else if (field.type === "DATETIME") {
    const match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.(\d{6})Z$/u);
    assert.ok(match, `${context} 不是六位微秒 UTC DATETIME`);
    const [, year, month, day, hour, minute, second] = match.map(Number);
    const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    assert.ok(month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth[month - 1]
      && hour <= 23 && minute <= 59 && second <= 59,
    `${context} 不是有效 UTC DATETIME 日历/时间值`);
  } else if (field.type === "JSON") {
    assert.doesNotThrow(() => JSON.parse(value), `${context} 不是合法 JSON`);
  } else if (["CHAR", "VARCHAR"].includes(field.type) && Number(field.len) > 0) {
    assert.ok([...value].length <= Number(field.len), `${context} 超过最大长度 ${field.len}`);
  }
}

function valueOf(record, field) {
  return record.values.get(field);
}

function isNull(value) {
  return value === undefined || value === "NULL";
}

function numberOf(record, field) {
  return Number(valueOf(record, field));
}

function identifierKey(value) {
  if (/^[A-Z]{2}[0-9]{3}$/u.test(String(value))) return String(value);
  if (isNull(value)) return null;
  assert.match(String(value), /^-?\d+$/u, `${value} 不是整数 ID`);
  return BigInt(value).toString();
}

function identifierEqual(left, right) {
  return !isNull(left) && !isNull(right) && identifierKey(left) === identifierKey(right);
}

function identifierLookupMap(records, keyOf) {
  const lookup = new Map(records.map((record) => [identifierKey(keyOf(record)), record]));
  const canonicalGet = lookup.get.bind(lookup);
  const canonicalHas = lookup.has.bind(lookup);
  lookup.get = (key) => canonicalGet(identifierKey(key));
  lookup.has = (key) => canonicalHas(identifierKey(key));
  return lookup;
}

function groupByInteger(records, field) {
  const grouped = Map.groupBy(records, (record) => identifierKey(valueOf(record, field)));
  const canonicalGet = grouped.get.bind(grouped);
  const canonicalHas = grouped.has.bind(grouped);
  grouped.get = (key) => canonicalGet(identifierKey(key));
  grouped.has = (key) => canonicalHas(identifierKey(key));
  return grouped;
}

function integerSet(values) {
  return new Set(values.map(identifierKey));
}

let generatedModelCache;

function generatedModel() {
  generatedModelCache ??= JSON.parse(readFileSync(modelPath, "utf8"));
  return generatedModelCache;
}

function decimalField(table, fieldName, model = generatedModel()) {
  const entity = model.entities.find((candidate) => candidate.defKey === table);
  const field = entity?.fields.find((candidate) => candidate.defKey === fieldName);
  assert.equal(field?.type, "DECIMAL", `${table}.${fieldName} 不是 generated DECIMAL field`);
  return field;
}

function parseFixedDecimal(rawValue, scale) {
  assert.match(rawValue, /^-?\d+(?:\.\d+)?$/u, `${rawValue} 不是 fixed decimal`);
  const negative = rawValue.startsWith("-");
  const [integer, fraction = ""] = rawValue.replace(/^-/, "").split(".");
  assert.ok(fraction.length <= scale, `${rawValue} 超过 fixed scale ${scale}`);
  const unscaled = BigInt(`${integer}${fraction.padEnd(scale, "0")}`) * (negative ? -1n : 1n);
  return Object.freeze({ unscaled, scale });
}

function fixedLiteral(rawValue, scale = rawValue.split(".")[1]?.length ?? 0) {
  return parseFixedDecimal(rawValue, scale);
}

function fixedValue(record, fieldName, model = generatedModel()) {
  const rawValue = valueOf(record, fieldName);
  if (isNull(rawValue)) return null;
  const field = decimalField(record.table, fieldName, model);
  return parseFixedDecimal(rawValue, Number(field.scale));
}

function fixedAtScale(value, scale) {
  assert.ok(value.scale <= scale, "fixed decimal 不允许有损降 scale");
  return value.unscaled * (10n ** BigInt(scale - value.scale));
}

function fixedCompare(left, right) {
  assert.ok(left && right, "fixed decimal compare 不接受 NULL");
  const scale = Math.max(left.scale, right.scale);
  const leftValue = fixedAtScale(left, scale);
  const rightValue = fixedAtScale(right, scale);
  return leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0;
}

function fixedAdd(...values) {
  assert.ok(values.length > 0 && values.every(Boolean), "fixed decimal add 不接受 NULL");
  const scale = Math.max(...values.map((value) => value.scale));
  return Object.freeze({
    unscaled: values.reduce((sum, value) => sum + fixedAtScale(value, scale), 0n),
    scale,
  });
}

function fixedSubtract(left, right) {
  return fixedAdd(left, Object.freeze({ unscaled: -right.unscaled, scale: right.scale }));
}

function fixedAbs(value) {
  return Object.freeze({ unscaled: value.unscaled < 0n ? -value.unscaled : value.unscaled, scale: value.scale });
}

function fixedEqual(left, right) {
  return fixedCompare(left, right) === 0;
}

function fixedFormat(value) {
  const negative = value.unscaled < 0n;
  const digits = (negative ? -value.unscaled : value.unscaled).toString().padStart(value.scale + 1, "0");
  const rendered = value.scale === 0
    ? digits
    : `${digits.slice(0, -value.scale)}.${digits.slice(-value.scale)}`;
  return `${negative ? "-" : ""}${rendered}`;
}

function fixedSumRecords(records, fieldName, model = generatedModel()) {
  const values = records.map((record) => fixedValue(record, fieldName, model));
  return values.length === 0 ? fixedLiteral("0", 8) : fixedAdd(...values);
}

function fixedRaw(rawValue, scale = 8) {
  assert.ok(!isNull(rawValue), "fixed raw 不接受 NULL");
  return parseFixedDecimal(rawValue, scale);
}

function fixedRecordEqual(leftRecord, leftField, rightRecord, rightField) {
  return fixedEqual(fixedValue(leftRecord, leftField), fixedValue(rightRecord, rightField));
}

function fixedRecordDifference(leftRecord, leftField, rightRecord, rightField) {
  return fixedAbs(fixedSubtract(fixedValue(leftRecord, leftField), fixedValue(rightRecord, rightField)));
}

function exactRequired(record, entity, fieldName) {
  const value = fixedValue(record, fieldName, { entities: [entity] });
  assert.ok(value, `${record.table}.${fieldName} exact CHECK 不接受 NULL`);
  return value;
}

const EXACT_ZERO = fixedLiteral("0");
const EXACT_ONE = fixedLiteral("1");

function exactNonNegative(record, entity, fields) {
  return fields.every((field) => fixedCompare(exactRequired(record, entity, field), EXACT_ZERO) >= 0);
}

function exactPositive(record, entity, field) {
  return fixedCompare(exactRequired(record, entity, field), EXACT_ZERO) > 0;
}

function exactOptionalRange(record, entity, field, minimum, maximum = null) {
  const value = fixedValue(record, field, { entities: [entity] });
  return value === null || (fixedCompare(value, minimum) >= 0
    && (maximum === null || fixedCompare(value, maximum) <= 0));
}

function allFieldsNull(record, fields) {
  return fields.every((field) => isNull(valueOf(record, field)));
}

function allFieldsPresent(record, fields) {
  return fields.every((field) => !isNull(valueOf(record, field)));
}

function exactPayoutRiskAllowTuple(record, entity, { ownerAware }) {
  const required = ["assessment_id", "evaluation_id", "eligibility_reason_codes", "eligibility_matched_controls",
    "control_set_hash", "eligibility_hash"];
  if (!allFieldsPresent(record, required) || valueOf(record, "eligibility_decision") !== "ALLOW") return false;
  const amount = fixedValue(record, "risk_required_amount", { entities: [entity] });
  const currency = valueOf(record, "risk_required_currency");
  if (!ownerAware) {
    return (amount === null && isNull(currency))
      || (amount !== null && fixedCompare(amount, EXACT_ZERO) >= 0 && !isNull(currency));
  }
  if (valueOf(record, "owner_type") === "CLIENT") {
    return amount !== null && fixedCompare(amount, EXACT_ZERO) >= 0
      && !isNull(currency) && currency === valueOf(record, "currency");
  }
  return valueOf(record, "owner_type") === "LOCAL_PARTNER" && amount === null && isNull(currency);
}

function sqliteValue(field, rawValue) {
  if (isNull(rawValue)) return null;
  if (["BIGINT", "INT", "INTEGER", "TINYINT", "SMALLINT", "BOOLEAN"].includes(field.type)) {
    return BigInt(rawValue);
  }
  if (field.type === "DECIMAL") {
    return rawValue;
  }
  return rawValue;
}

const EXACT_DECIMAL_CHECKS = Object.freeze({
  ck_account_balance_projection: (record, entity) => {
    const debit = exactRequired(record, entity, "debit_total");
    const credit = exactRequired(record, entity, "credit_total");
    const net = exactRequired(record, entity, "net_balance");
    const normalSide = valueOf(record, "normal_side");
    if (!["DEBIT", "CREDIT"].includes(normalSide)) return false;
    const expected = normalSide === "DEBIT" ? fixedSubtract(debit, credit) : fixedSubtract(credit, debit);
    return fixedCompare(debit, EXACT_ZERO) >= 0 && fixedCompare(credit, EXACT_ZERO) >= 0
      && BigInt(valueOf(record, "last_journal_sequence")) >= 0n
      && BigInt(valueOf(record, "balance_version")) >= 0n && fixedEqual(net, expected);
  },
  ck_tax_rate_value: (record, entity) => exactOptionalRange(record, entity, "rate_value", EXACT_ZERO, EXACT_ONE),
  ck_tax_fx_rate: (record, entity) => exactPositive(record, entity, "rate_value")
    && valueOf(record, "base_currency") !== valueOf(record, "quote_currency"),
  ck_tax_snapshot_amounts: (record, entity) => exactNonNegative(record, entity,
    ["total_collected_tax", "total_reverse_charge_tax", "reporting_collected_tax", "reporting_reverse_charge_tax"]),
  ck_fiscal_record_sequence: (record, entity) => identifierEqual(valueOf(record, "source_version"), "1")
    && BigInt(valueOf(record, "generation_key_version")) > 0n
    && BigInt(valueOf(record, "sequence_value")) > 0n
    && fixedCompare(exactRequired(record, entity, "total_amount"), EXACT_ZERO) >= 0,
  ck_fiscal_line_amount: (record, entity) => fixedEqual(
    fixedAdd(exactRequired(record, entity, "net_amount"), exactRequired(record, entity, "collected_tax_amount")),
    exactRequired(record, entity, "gross_amount"),
  ),
  ck_tax_line_liability: (record, entity) => {
    if (!exactNonNegative(record, entity, ["tax_basis", "reporting_tax_basis", "collected_tax_amount",
      "reverse_charge_tax_amount", "reporting_collected_tax", "reporting_reverse_charge_tax"])) return false;
    const rate = exactRequired(record, entity, "tax_rate");
    if (fixedCompare(rate, EXACT_ZERO) < 0 || fixedCompare(rate, EXACT_ONE) > 0) return false;
    if (valueOf(record, "liability_mode") === "SELLER") {
      return valueOf(record, "liable_party_type") === "OPERATING_ENTITY" && !isNull(valueOf(record, "liable_entity_no"))
        && fixedEqual(exactRequired(record, entity, "reverse_charge_tax_amount"), EXACT_ZERO)
        && fixedEqual(exactRequired(record, entity, "reporting_reverse_charge_tax"), EXACT_ZERO);
    }
    if (valueOf(record, "liability_mode") === "BUYER_REVERSE_CHARGE") {
      return valueOf(record, "liable_party_type") === "BUYER" && isNull(valueOf(record, "liable_entity_no"))
        && fixedEqual(exactRequired(record, entity, "collected_tax_amount"), EXACT_ZERO)
        && fixedEqual(exactRequired(record, entity, "reporting_collected_tax"), EXACT_ZERO);
    }
    return false;
  },
  ck_tax_liability_summary: (record, entity) => {
    if (!exactNonNegative(record, entity, ["taxable_amount", "output_tax", "recoverable_tax",
      "reverse_charge_taxable_amount", "reverse_charge_tax_amount"])) return false;
    const net = exactRequired(record, entity, "net_tax_payable");
    if (!fixedEqual(net, fixedSubtract(exactRequired(record, entity, "output_tax"),
      exactRequired(record, entity, "recoverable_tax")))) return false;
    if (valueOf(record, "liability_mode") === "SELLER") {
      return fixedEqual(exactRequired(record, entity, "reverse_charge_taxable_amount"), EXACT_ZERO)
        && fixedEqual(exactRequired(record, entity, "reverse_charge_tax_amount"), EXACT_ZERO);
    }
    if (valueOf(record, "liability_mode") === "BUYER_REVERSE_CHARGE") {
      return ["taxable_amount", "output_tax", "recoverable_tax", "net_tax_payable"]
        .every((field) => fixedEqual(exactRequired(record, entity, field), EXACT_ZERO));
    }
    return false;
  },
  ck_channel_cost_component: (record, entity) => {
    const rate = fixedValue(record, "rate_value", { entities: [entity] });
    const fixed = fixedValue(record, "fixed_amount", { entities: [entity] });
    const minimum = fixedValue(record, "min_amount", { entities: [entity] });
    const maximum = fixedValue(record, "max_amount", { entities: [entity] });
    return (rate !== null || fixed !== null)
      && (rate === null || (fixedCompare(rate, EXACT_ZERO) >= 0 && fixedCompare(rate, EXACT_ONE) <= 0))
      && ((fixed === null && isNull(valueOf(record, "currency")))
        || (fixed !== null && fixedCompare(fixed, EXACT_ZERO) >= 0 && !isNull(valueOf(record, "currency"))))
      && (minimum === null || fixedCompare(minimum, EXACT_ZERO) >= 0)
      && (maximum === null || fixedCompare(maximum, EXACT_ZERO) >= 0)
      && (minimum === null || maximum === null || fixedCompare(minimum, maximum) <= 0);
  },
  ck_channel_cost_confirm: (record, entity) => fixedCompare(exactRequired(record, entity, "actual_cost"), EXACT_ZERO) >= 0,
  ck_channel_cost_alloc_amount: (record, entity) => fixedCompare(exactRequired(record, entity, "allocated_amount"), EXACT_ZERO) >= 0,
  ck_instruction_line_amount: (record, entity) => exactPositive(record, entity, "amount"),
  ck_journal_line_amount: (record, entity) => exactPositive(record, entity, "amount"),
  ck_manual_adjustment_balance: (record, entity) => {
    const debit = exactRequired(record, entity, "total_debit");
    const credit = exactRequired(record, entity, "total_credit");
    return fixedCompare(debit, EXACT_ZERO) > 0 && fixedEqual(debit, credit);
  },
  ck_manual_adjustment_line_amount: (record, entity) => exactPositive(record, entity, "amount"),
  ck_trial_balance_success: (record, entity) => {
    const debit = exactRequired(record, entity, "total_debit");
    const credit = exactRequired(record, entity, "total_credit");
    return fixedCompare(debit, EXACT_ZERO) >= 0 && fixedCompare(credit, EXACT_ZERO) >= 0
      && (valueOf(record, "status") !== "SUCCEEDED" || fixedEqual(debit, credit));
  },
  ck_duplicate_capture_amount: (record, entity) => exactPositive(record, entity, "amount"),
  ck_channel_open_item_amounts: (record, entity) => {
    const gross = exactRequired(record, entity, "gross_receivable");
    const settled = exactRequired(record, entity, "settled_amount");
    const outstanding = exactRequired(record, entity, "outstanding_amount");
    return fixedCompare(gross, EXACT_ZERO) > 0 && fixedCompare(settled, EXACT_ZERO) >= 0
      && fixedCompare(outstanding, EXACT_ZERO) >= 0 && fixedEqual(fixedAdd(settled, outstanding), gross);
  },
  ck_channel_open_item_state: (record, entity) => {
    const gross = exactRequired(record, entity, "gross_receivable");
    const settled = exactRequired(record, entity, "settled_amount");
    const outstanding = exactRequired(record, entity, "outstanding_amount");
    if (valueOf(record, "status") === "OPEN") return fixedEqual(settled, EXACT_ZERO) && fixedEqual(outstanding, gross);
    if (valueOf(record, "status") === "PARTIALLY_SETTLED") {
      return fixedCompare(settled, EXACT_ZERO) > 0 && fixedCompare(outstanding, EXACT_ZERO) > 0;
    }
    if (valueOf(record, "status") === "SETTLED") return fixedEqual(settled, gross) && fixedEqual(outstanding, EXACT_ZERO);
    return valueOf(record, "status") === "EXCEPTION";
  },
  ck_channel_debit_amounts: (record, entity) => {
    const expected = exactRequired(record, entity, "expected_amount");
    const settled = exactRequired(record, entity, "settled_amount");
    const outstanding = exactRequired(record, entity, "outstanding_amount");
    return fixedCompare(expected, EXACT_ZERO) > 0 && fixedCompare(settled, EXACT_ZERO) >= 0
      && fixedCompare(outstanding, EXACT_ZERO) >= 0 && fixedEqual(fixedAdd(settled, outstanding), expected);
  },
  ck_channel_debit_state: (record, entity) => {
    const expected = exactRequired(record, entity, "expected_amount");
    const settled = exactRequired(record, entity, "settled_amount");
    const outstanding = exactRequired(record, entity, "outstanding_amount");
    if (valueOf(record, "status") === "OPEN") return fixedEqual(settled, EXACT_ZERO) && fixedEqual(outstanding, expected);
    if (valueOf(record, "status") === "PARTIALLY_SETTLED") {
      return fixedCompare(settled, EXACT_ZERO) > 0 && fixedCompare(outstanding, EXACT_ZERO) > 0;
    }
    if (valueOf(record, "status") === "SETTLED") return fixedEqual(settled, expected) && fixedEqual(outstanding, EXACT_ZERO);
    return valueOf(record, "status") === "EXCEPTION";
  },
  ck_channel_settle_batch_net: (record, entity) => {
    const gross = exactRequired(record, entity, "gross_amount");
    const fee = exactRequired(record, entity, "fee_amount");
    const debit = exactRequired(record, entity, "debit_amount");
    const net = exactRequired(record, entity, "net_amount");
    if ([gross, fee, debit, net].some((value) => fixedCompare(value, EXACT_ZERO) < 0)) return false;
    const deductions = fixedAdd(fee, debit);
    if (valueOf(record, "cash_direction") === "CREDIT") {
      return fixedCompare(gross, deductions) > 0 && fixedEqual(net, fixedSubtract(gross, deductions));
    }
    if (valueOf(record, "cash_direction") === "DEBIT") {
      return fixedCompare(gross, deductions) < 0 && fixedEqual(net, fixedSubtract(deductions, gross));
    }
    return valueOf(record, "cash_direction") === "NONE"
      && fixedEqual(gross, deductions) && fixedEqual(net, EXACT_ZERO);
  },
  ck_channel_settle_item_direction: (record, entity) => {
    const values = ["gross_amount", "fee_amount", "net_amount"].map((field) => exactRequired(record, entity, field));
    if (!values.every((value) => fixedCompare(value, EXACT_ZERO) >= 0)
      || fixedCompare(fixedAdd(...values), EXACT_ZERO) <= 0) return false;
    const itemType = valueOf(record, "item_type");
    const direction = valueOf(record, "settlement_direction");
    return (["PAYMENT", "DUPLICATE_CAPTURE"].includes(itemType) && direction === "CREDIT")
      || (["REFUND", "DISPUTE", "FEE"].includes(itemType) && direction === "DEBIT")
      || itemType === "ADJUSTMENT";
  },
  ck_channel_transaction_match_amounts: (record, entity) => {
    const values = ["gross_amount", "fee_amount", "net_amount"].map((field) => exactRequired(record, entity, field));
    return values.every((value) => fixedCompare(value, EXACT_ZERO) >= 0)
      && fixedCompare(fixedAdd(...values), EXACT_ZERO) > 0;
  },
  ck_bank_statement_item_amount: (record, entity) => exactPositive(record, entity, "amount"),
  ck_bank_recon_match_amount: (record, entity) => exactPositive(record, entity, "matched_amount"),
  ck_recon_exception_amount: (record, entity) => exactPositive(record, entity, "difference_amount"),
  ck_txn_economics_amount: (record, entity) => fixedCompare(exactRequired(record, entity, "gross_amount"), EXACT_ZERO) >= 0,
  ck_txn_econ_component_amounts: (record, entity) => {
    if (!exactNonNegative(record, entity, ["basis_amount", "raw_amount", "posted_amount"])) return false;
    for (const field of ["rate_value", "source_rate"]) {
      if (!exactOptionalRange(record, entity, field, EXACT_ZERO, EXACT_ONE)) return false;
    }
    const fixed = fixedValue(record, "source_fixed_amount", { entities: [entity] });
    const minimum = fixedValue(record, "source_min_amount", { entities: [entity] });
    const maximum = fixedValue(record, "source_max_amount", { entities: [entity] });
    return ((fixed === null && isNull(valueOf(record, "source_currency")))
      || (fixed !== null && fixedCompare(fixed, EXACT_ZERO) >= 0 && !isNull(valueOf(record, "source_currency"))))
      && (minimum === null || fixedCompare(minimum, EXACT_ZERO) >= 0)
      && (maximum === null || fixedCompare(maximum, EXACT_ZERO) >= 0)
      && (minimum === null || maximum === null || fixedCompare(minimum, maximum) <= 0);
  },
  ck_txn_econ_component_link: (record, entity) => !identifierEqual(
    valueOf(record, "source_component_id"),
    valueOf(record, "target_component_id"),
  )
    && exactPositive(record, entity, "linked_amount"),
  ck_payout_amounts: (record, entity) => {
    const requested = exactRequired(record, entity, "requested_amount");
    const fee = exactRequired(record, entity, "fee_amount");
    const ownerDebit = exactRequired(record, entity, "owner_debit_amount");
    const beneficiary = exactRequired(record, entity, "beneficiary_amount");
    const cash = exactRequired(record, entity, "cash_required_amount");
    if (fixedCompare(requested, EXACT_ZERO) <= 0 || fixedCompare(fee, EXACT_ZERO) < 0
      || fixedCompare(ownerDebit, EXACT_ZERO) <= 0 || fixedCompare(beneficiary, EXACT_ZERO) <= 0
      || !fixedEqual(cash, ownerDebit) || !fixedEqual(fixedAdd(beneficiary, fee), ownerDebit)) return false;
    if (valueOf(record, "fee_mode") === "ADDED_ON_TOP") {
      return fixedEqual(ownerDebit, fixedAdd(requested, fee)) && fixedEqual(beneficiary, requested);
    }
    return valueOf(record, "fee_mode") === "DEDUCTED_FROM_PAYOUT"
      && fixedCompare(requested, fee) > 0 && fixedEqual(ownerDebit, requested)
      && fixedEqual(beneficiary, fixedSubtract(requested, fee));
  },
  ck_payout_risk_snapshot: (record, entity) => {
    const snapshotFields = ["assessment_id", "evaluation_id", "eligibility_decision", "eligibility_reason_codes",
      "eligibility_matched_controls", "risk_required_amount", "risk_required_currency", "control_set_hash", "eligibility_hash"];
    return allFieldsNull(record, snapshotFields) || exactPayoutRiskAllowTuple(record, entity, { ownerAware: true });
  },
  ck_payout_approval_snapshot: (record, entity) => {
    return (isNull(valueOf(record, "approval_hash")) && isNull(valueOf(record, "approved_time")))
      || (!isNull(valueOf(record, "approval_hash")) && !isNull(valueOf(record, "approved_time"))
        && exactPayoutRiskAllowTuple(record, entity, { ownerAware: true }));
  },
  ck_payout_execution_risk_snapshot: (record, entity) => exactPayoutRiskAllowTuple(record, entity, { ownerAware: false }),
  ck_tax_payment_amounts: (record, entity) => {
    const amount = exactRequired(record, entity, "amount");
    const reconciled = exactRequired(record, entity, "reconciled_amount");
    return fixedCompare(amount, EXACT_ZERO) > 0 && fixedCompare(reconciled, EXACT_ZERO) >= 0
      && fixedCompare(reconciled, amount) <= 0;
  },
  ck_tax_payment_reconciled: (record, entity) => {
    const amount = exactRequired(record, entity, "amount");
    const reconciled = exactRequired(record, entity, "reconciled_amount");
    if (valueOf(record, "status") === "RECONCILED") {
      return fixedEqual(reconciled, amount) && !isNull(valueOf(record, "reconciled_time"))
        && !isNull(valueOf(record, "journal_entry_id"));
    }
    if (valueOf(record, "status") === "FAILED") {
      return fixedEqual(reconciled, EXACT_ZERO) && isNull(valueOf(record, "reconciled_time"))
        && isNull(valueOf(record, "journal_entry_id"));
    }
    if (["CREATED", "SUBMITTED", "PROCESSING", "BANK_ACCEPTED", "UNKNOWN"].includes(valueOf(record, "status"))) {
      return fixedCompare(reconciled, amount) < 0 && isNull(valueOf(record, "reconciled_time"))
        && isNull(valueOf(record, "journal_entry_id"));
    }
    return false;
  },
  ck_trial_exception_amount: (record, entity) => exactPositive(record, entity, "difference_amount"),
});

function derivedDecimalCheckNames(model) {
  const entities = new Map(model.entities.map((entity) => [entity.defKey.slice(2), entity]));
  const derived = [];
  for (const [checkName, tables] of Object.entries(EXPECTED_CHECK_BINDINGS)) {
    const expression = EXPECTED_CHECK_CONTRACTS[checkName];
    const referencesDecimal = tables.some((table) => {
      const entity = entities.get(table);
      assert.ok(entity, `DECIMAL CHECK derivation 缺少 t_${table}`);
      return entity.fields.some((field) => field.type === "DECIMAL"
        && new RegExp(`\\b${field.defKey}\\b`, "u").test(expression));
    });
    if (referencesDecimal) derived.push(checkName);
  }
  return derived.toSorted();
}

function validateExactDecimalChecks(records, model, { requireAllDerived = true } = {}) {
  const derived = derivedDecimalCheckNames(model);
  assert.deepEqual(Object.keys(EXACT_DECIMAL_CHECKS).sort(), derived,
    "EXACT_DECIMAL_CHECKS 必须精确覆盖 model/Oracle 动态派生集合");
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const executed = new Map(derived.map((checkName) => [checkName, 0]));
  const violations = [];
  for (const record of records) {
    const entity = entities.get(record.table);
    assert.ok(entity, `Exact DECIMAL 无法解析未知表 ${record.table}`);
    for (const checkName of derived) {
      if (!EXPECTED_CHECK_BINDINGS[checkName].includes(record.table.slice(2))) continue;
      let passed;
      try {
        passed = EXACT_DECIMAL_CHECKS[checkName](record, entity);
      } catch (error) {
        assert.fail(`${record.table} 第 ${record.lineNumber ?? "fixture"} 行执行 exact ${checkName} 失败：${error.message}`);
      }
      executed.set(checkName, executed.get(checkName) + 1);
      if (passed !== true) violations.push(`${record.table} 第 ${record.lineNumber ?? "fixture"} 行违反 exact ${checkName}`);
    }
  }
  if (requireAllDerived) {
    const missing = [...executed].filter(([, count]) => count === 0).map(([checkName]) => checkName);
    assert.deepEqual(missing, [], `以下 exact DECIMAL CHECK 未执行：${missing.join(", ")}`);
  }
  assert.deepEqual(violations, [], `Exact DECIMAL CHECK violations (${violations.length})：\n${violations.join("\n")}`);
  return {
    checkCount: derived.length,
    evaluationCount: [...executed.values()].reduce((sum, count) => sum + count, 0),
    violations: violations.length,
  };
}

function validateAllOracleChecks(records, model, { requireAllContracts = true } = {}) {
  assert.deepEqual(Object.keys(EXPECTED_CHECK_CONTRACTS).sort(), Object.keys(EXPECTED_CHECK_BINDINGS).sort(),
    "Oracle CHECK contracts/bindings 集合不一致");
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const checksByTable = new Map();
  for (const [checkName, logicalTables] of Object.entries(EXPECTED_CHECK_BINDINGS)) {
    for (const logicalTable of logicalTables) {
      const table = `t_${logicalTable}`;
      if (!checksByTable.has(table)) checksByTable.set(table, []);
      checksByTable.get(table).push(checkName);
    }
  }

  const executed = new Map(Object.keys(EXPECTED_CHECK_CONTRACTS).map((checkName) => [checkName, 0]));
  const violations = [];
  const database = new DatabaseSync(":memory:");
  try {
    for (const record of records) {
      const entity = entities.get(record.table);
      assert.ok(entity, `Oracle CHECK 无法解析未知表 ${record.table}`);
      const checkNames = checksByTable.get(record.table) ?? [];
      if (checkNames.length === 0) continue;
      const columns = entity.fields.map((field) => `"${field.defKey}"`).join(", ");
      // DECIMAL values stay strings at the JS boundary. SQLite NUMERIC casts are
      // used only for SQL NULL/branch semantics; the independent BigInt gate
      // below is authoritative for every exact decimal comparison/arithmetic.
      const placeholders = entity.fields
        .map((field) => field.type === "DECIMAL" ? "CAST(? AS NUMERIC)" : "?")
        .join(", ");
      const parameters = entity.fields.map((field) => sqliteValue(field, valueOf(record, field.defKey)));
      for (const checkName of checkNames) {
        const sql = `WITH scenario_row (${columns}) AS (SELECT ${placeholders}) `
          + `SELECT ((${EXPECTED_CHECK_CONTRACTS[checkName]})) IS TRUE AS passed FROM scenario_row`;
        let result;
        try {
          result = database.prepare(sql).get(...parameters);
        } catch (error) {
          assert.fail(`${record.table} 第 ${record.lineNumber ?? "fixture"} 行执行 ${checkName} SQL 失败：${error.message}`);
        }
        executed.set(checkName, executed.get(checkName) + 1);
        if (result.passed !== 1) {
          violations.push(`${record.table} 第 ${record.lineNumber ?? "fixture"} 行违反 ${checkName}`);
        }
      }
    }
  } finally {
    database.close();
  }
  if (requireAllContracts) {
    const missing = [...executed].filter(([, count]) => count === 0).map(([checkName]) => checkName);
    assert.deepEqual(missing, [], `以下 Oracle CHECK 未执行：${missing.join(", ")}`);
  }
  assert.deepEqual(violations, [], `Oracle CHECK violations (${violations.length})：\n${violations.join("\n")}`);
  return {
    checkCount: executed.size,
    evaluationCount: [...executed.values()].reduce((sum, count) => sum + count, 0),
    violations: violations.length,
  };
}

function validateRelationsAndUnique(records, model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  const entitiesByKey = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const recordsByTable = Map.groupBy(records, (record) => record.table);
  const canonicalKeyValue = (field, rawValue) => {
    if (isNull(rawValue)) return null;
    if (["BIGINT", "INT", "INTEGER", "TINYINT", "SMALLINT", "BOOLEAN"].includes(field.type)) {
      return BigInt(rawValue).toString();
    }
    if (field.type === "DECIMAL") {
      return fixedFormat(parseFixedDecimal(rawValue, Number(field.scale)));
    }
    return rawValue;
  };
  for (const record of records) {
    const entity = entitiesByKey.get(record.table);
    assert.ok(entity, `场景使用未知表 ${record.table}`);
    const fieldsById = new Map(entity.fields.map((field) => [field.id, field]));
    for (const relation of entity.correlations) {
      const childField = fieldsById.get(relation.myField);
      const childValue = valueOf(record, childField.defKey);
      if (isNull(childValue)) continue;
      const parent = entitiesById.get(relation.refEntity);
      const parentField = parent.fields.find((field) => field.id === relation.refField);
      const canonicalChild = canonicalKeyValue(childField, childValue);
      const found = (recordsByTable.get(parent.defKey) ?? [])
        .some((candidate) => canonicalKeyValue(parentField, valueOf(candidate, parentField.defKey)) === canonicalChild);
      assert.ok(found, `${record.table}.${childField.defKey}=${childValue} 缺少 ${parent.defKey}.${parentField.defKey} 目标记录`);
    }
  }
  for (const entity of model.entities) {
    const tableRecords = recordsByTable.get(entity.defKey) ?? [];
    const primaryFields = entity.fields.filter((field) => field.primaryKey);
    const fieldsById = new Map(entity.fields.map((field) => [field.id, field]));
    const uniqueTuples = [primaryFields, ...entity.indexes.filter((index) => index.unique)
      .map((index) => index.fields.map(({ fieldDefKey }) => fieldsById.get(fieldDefKey)))]
      .filter((tuple) => tuple.length > 0);
    for (const tuple of uniqueTuples) {
      const seen = new Set();
      for (const record of tableRecords) {
        const values = tuple.map((field) => canonicalKeyValue(field, valueOf(record, field.defKey)));
        if (values.some((value) => value === null)) continue;
        const key = JSON.stringify(values);
        const fieldNames = tuple.map((field) => field.defKey).join(",");
        assert.ok(!seen.has(key), `${entity.defKey} 唯一键 (${fieldNames}) 重复 ${key}`);
        seen.add(key);
      }
    }
  }
}

function validateSchemaRecords(records, model) {
  const entities = new Map(model.entities.map((entity) => [
    entity.defKey,
    new Map(entity.fields.map((field) => [field.defKey, field])),
  ]));
  let assignmentCount = 0;
  for (const record of records) {
    const fields = entities.get(record.table);
    assert.ok(fields, `场景使用未知表 ${record.table}`);
    for (const field of fields.values()) {
      if (field.notNull && !mechanicalNotNullExemptions.has(field.defKey)) {
        assert.ok(record.values.has(field.defKey), `${record.table} 第 ${record.lineNumber} 行缺少 NN 字段 ${field.defKey}`);
        assert.notEqual(valueOf(record, field.defKey), "NULL", `${record.table}.${field.defKey} 不允许 NULL`);
      }
    }
    for (const [fieldKey, value] of record.values) {
      assignmentCount += 1;
      const field = fields.get(fieldKey);
      assert.ok(field, `${record.table} 使用不存在字段 ${fieldKey}`);
      assertValueMatchesType(record.table, field, value);
      const allowed = allowedValues(field);
      if (allowed && value !== "NULL") {
        assert.ok(allowed.includes(value), `${record.table}.${fieldKey}=${value} 不在允许值 ${allowed.join("、")} 中`);
      }
    }
  }
  validateRelationsAndUnique(records, model);
  validateAllOracleChecks(records, model);
  validateExactDecimalChecks(records, model);
  return assignmentCount;
}

function recordsFor(records, table) {
  return records.filter((record) => record.table === table);
}

function recordsById(records, table) {
  return identifierLookupMap(recordsFor(records, table), (record) => valueOf(record, "id"));
}

function requireRecord(records, table, id) {
  const record = recordsById(records, table).get(String(id));
  assert.ok(record, `${table}.id=${id} 记录不存在`);
  return record;
}

function clonedScenarioRows() {
  return scenarioRows(readFileSync(scenarioPath, "utf8"))
    .map((record) => ({ ...record, values: new Map(record.values) }));
}

function appendClonedRecord(records, table, sourceId, targetId) {
  const source = requireRecord(records, table, sourceId);
  const clone = { ...source, lineNumber: 0, values: new Map(source.values) };
  clone.values.set("id", String(targetId));
  records.push(clone);
  return clone;
}

function appendSecondLedgerBook(records) {
  const book = appendClonedRecord(records, "t_ledger_book", "100004", "999004");
  book.values.set("entity_id", "59999");

  const calendar = appendClonedRecord(records, "t_fiscal_calendar", "100006", "999006");
  calendar.values.set("entity_id", "59999");
  calendar.values.set("calendar_code", "CALENDAR_YEAR_SECOND_BOOK");
  const period = appendClonedRecord(records, "t_accounting_period", "100007", "999007");
  period.values.set("calendar_id", valueOf(calendar, "id"));

  const control = appendClonedRecord(records, "t_account", "100005", "999005");
  control.values.set("ledger_book_id", valueOf(book, "id"));
  control.values.set("owner_id", valueOf(book, "entity_id"));
  const suspense = appendClonedRecord(records, "t_account", "20219", "999019");
  suspense.values.set("ledger_book_id", valueOf(book, "id"));
  suspense.values.set("owner_id", valueOf(book, "entity_id"));
  return { book, calendar, period, control, suspense };
}

function appendRetiredRuleBundle(records) {
  const bundle = appendClonedRecord(records, "t_accounting_rule_bundle", "100041", "999041");
  bundle.values.set("bundle_code", "FINANCE_POSTING_HISTORY");
  bundle.values.set("current_ver_id", "999042");
  bundle.values.set("status", "INACTIVE");
  bundle.values.set("version", "1");
  const version = appendClonedRecord(records, "t_accounting_rule_bundle_ver", "100042", "999042");
  version.values.set("bundle_id", valueOf(bundle, "id"));
  version.values.set("effective_from", "2026-07-01T00:00:00.000000Z");
  version.values.set("effective_to", "2026-07-31T23:59:59.999999Z");
  version.values.set("status", "RETIRED");
  const rulesByCurrentId = new Map();
  recordsFor(records, "t_accounting_rule_line")
    .filter((rule) => identifierEqual(valueOf(rule, "bundle_ver_id"), "100042"))
    .forEach((rule, index) => {
      const clone = { ...rule, lineNumber: 0, values: new Map(rule.values) };
      clone.values.set("id", String(990001 + index));
      clone.values.set("bundle_ver_id", valueOf(version, "id"));
      records.push(clone);
      rulesByCurrentId.set(identifierKey(valueOf(rule, "id")), clone);
    });
  return { bundle, version, rulesByCurrentId };
}

function clonedApprovedManualInstructionRows() {
  const rows = clonedScenarioRows();
  const adjustment = requireRecord(rows, "t_manual_adjustment", "100048");
  adjustment.values.set("total_debit", "105");
  adjustment.values.set("total_credit", "105");
  adjustment.values.set("submitted_time", "2026-08-31T00:00:00.000000Z");
  adjustment.values.set("status", "APPROVED");

  const firstAdjustmentLine = requireRecord(rows, "t_manual_adjustment_line", "100049");
  firstAdjustmentLine.values.set("account_id", "100005");
  firstAdjustmentLine.values.set("amount", "105");
  const secondAdjustmentLine = {
    ...firstAdjustmentLine,
    lineNumber: 0,
    values: new Map(firstAdjustmentLine.values),
  };
  secondAdjustmentLine.values.set("id", "100051");
  secondAdjustmentLine.values.set("line_no", "2");
  secondAdjustmentLine.values.set("account_id", "20219");
  secondAdjustmentLine.values.set("entry_side", "CREDIT");
  rows.push(secondAdjustmentLine);

  const makerApproval = requireRecord(rows, "t_adjustment_approval", "100050");
  makerApproval.values.set("approved_content_hash", valueOf(adjustment, "content_hash"));
  const checkerApproval = {
    ...makerApproval,
    lineNumber: 0,
    values: new Map(makerApproval.values),
  };
  checkerApproval.values.set("id", "100051");
  checkerApproval.values.set("approval_role", "CHECKER");
  checkerApproval.values.set("actor_id", "2");
  checkerApproval.values.set("decision_time", "2026-08-31T00:01:00.000000Z");
  rows.push(checkerApproval);

  const instruction = requireRecord(rows, "t_accounting_instruction", "100044");
  instruction.values.set("origin_type", "MANUAL");
  for (const field of ["source_system", "source_event_key", "source_version", "source_payload_hash",
    "transaction_economics_id", "accounting_rule_bundle_ver_id"]) {
    instruction.values.set(field, "NULL");
  }
  instruction.values.set("manual_adjustment_id", valueOf(adjustment, "id"));
  instruction.values.set("input_hash", valueOf(adjustment, "content_hash"));
  instruction.values.set("maker_id", valueOf(makerApproval, "actor_id"));
  instruction.values.set("checker_id", valueOf(checkerApproval, "actor_id"));
  const instructionLines = recordsFor(rows, "t_instruction_line")
    .filter((line) => identifierEqual(valueOf(line, "instruction_id"), valueOf(instruction, "id")))
    .toSorted((left, right) => numberOf(left, "line_no") - numberOf(right, "line_no"));
  instructionLines[0].values.set("accounting_rule_line_id", "NULL");
  instructionLines[0].values.set("manual_adjustment_line_id", valueOf(firstAdjustmentLine, "id"));
  instructionLines[1].values.set("accounting_rule_line_id", "NULL");
  instructionLines[1].values.set("manual_adjustment_line_id", valueOf(secondAdjustmentLine, "id"));
  return rows;
}

function journalSummary(records, journalId) {
  const journal = requireRecord(records, "t_journal_entry", journalId);
  const lines = recordsFor(records, "t_journal_line")
    .filter((line) => identifierEqual(valueOf(line, "journal_entry_id"), journalId));
  assert.ok(lines.length >= 2, `Journal ${journalId} 缺少双边分录`);
  const debitTotal = fixedSumRecords(lines.filter((line) => valueOf(line, "entry_side") === "DEBIT"), "amount");
  const creditTotal = fixedSumRecords(lines.filter((line) => valueOf(line, "entry_side") === "CREDIT"), "amount");
  assert.ok(fixedEqual(debitTotal, creditTotal),
    `Journal ${journalId} 借贷不平衡：${fixedFormat(debitTotal)} != ${fixedFormat(creditTotal)}`);
  return { journal, lines, debitTotal, creditTotal };
}

function validateDeclaredJournalTruth(declarations, records) {
  const declaredKeys = new Set();
  for (const declaration of declarations) {
    assert.ok(!declaredKeys.has(declaration.entryKey), `Journal ${declaration.entryKey} 重复声明`);
    declaredKeys.add(declaration.entryKey);
  }
  const instructionLines = recordsById(records, "t_instruction_line");
  for (const declaration of declarations) {
    const instructions = recordsFor(records, "t_accounting_instruction")
      .filter((record) => valueOf(record, "source_event_key") === declaration.entryKey);
    assert.equal(instructions.length, 1, `Journal ${declaration.entryKey} 无法唯一解析 Accounting Instruction`);
    const journals = recordsFor(records, "t_journal_entry")
      .filter((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(instructions[0], "id")));
    assert.equal(journals.length, 1, `Journal ${declaration.entryKey} 无法唯一解析 Journal Entry`);
    const summary = journalSummary(records, valueOf(journals[0], "id"));
    for (const line of summary.lines) {
      const instructionLine = instructionLines.get(valueOf(line, "instruction_line_id"));
      assert.ok(instructionLine, `Journal ${declaration.entryKey} Line 缺少 Instruction Line`);
      assert.ok(identifierEqual(valueOf(instructionLine, "instruction_id"), valueOf(instructions[0], "id")),
        `Journal ${declaration.entryKey} Line 未绑定同一 Accounting Instruction`);
      assert.ok(identifierEqual(valueOf(instructionLine, "account_id"), valueOf(line, "account_id")));
      assert.equal(valueOf(instructionLine, "entry_side"), valueOf(line, "entry_side"));
      assert.ok(fixedEqual(fixedValue(instructionLine, "amount"), fixedValue(line, "amount")),
        `Journal ${declaration.entryKey} Instruction/Journal Line 金额不一致`);
    }
    const amountScale = Number(decimalField("t_journal_line", "amount").scale);
    assert.ok(fixedEqual(parseFixedDecimal(declaration.debitTotal, amountScale), summary.debitTotal),
      `Journal ${declaration.entryKey} 声明 debit_total 与实际不一致`);
    assert.ok(fixedEqual(parseFixedDecimal(declaration.creditTotal, amountScale), summary.creditTotal),
      `Journal ${declaration.entryKey} 声明 credit_total 与实际不一致`);
  }
  return declarations.length;
}

function validatePostedJournalReplay(records) {
  const instructions = recordsFor(records, "t_accounting_instruction");
  const posted = instructions.filter((instruction) => valueOf(instruction, "status") === "POSTED");
  const ledgerBooks = recordsById(records, "t_ledger_book");
  const accounts = recordsById(records, "t_account");
  const periods = recordsById(records, "t_accounting_period");
  const calendars = recordsById(records, "t_fiscal_calendar");
  const postedIds = integerSet(posted.map((instruction) => valueOf(instruction, "id")));
  const instructionLinesByInstruction = Map.groupBy(
    recordsFor(records, "t_instruction_line"),
    (line) => identifierKey(valueOf(line, "instruction_id")),
  );
  const journalsByInstruction = Map.groupBy(
    recordsFor(records, "t_journal_entry"),
    (journal) => identifierKey(valueOf(journal, "instruction_id")),
  );
  const journalLinesByJournal = Map.groupBy(
    recordsFor(records, "t_journal_line"),
    (line) => identifierKey(valueOf(line, "journal_entry_id")),
  );

  for (const journal of recordsFor(records, "t_journal_entry")) {
    assert.ok(postedIds.has(identifierKey(valueOf(journal, "instruction_id"))),
      `Journal ${valueOf(journal, "id")} 未绑定 POSTED Instruction`);
  }
  for (const instruction of posted) {
    const instructionId = identifierKey(valueOf(instruction, "id"));
    const journals = journalsByInstruction.get(instructionId) ?? [];
    assert.equal(journals.length, 1,
      `POSTED Instruction ${valueOf(instruction, "id")} 必须有唯一 represented Journal`);
    const journal = journals[0];
    assert.ok(identifierEqual(valueOf(instruction, "ledger_book_id"), valueOf(journal, "ledger_book_id")),
      `Instruction ${valueOf(instruction, "id")} 与 Journal ${valueOf(journal, "id")} Ledger Book 不一致`);
    const ledgerBook = ledgerBooks.get(valueOf(instruction, "ledger_book_id"));
    assert.ok(ledgerBook, `Instruction ${valueOf(instruction, "id")} 缺少 Ledger Book`);
    const period = periods.get(valueOf(journal, "accounting_period_id"));
    const calendar = period && calendars.get(valueOf(period, "calendar_id"));
    assert.ok(period && calendar
      && valueOf(calendar, "entity_type") === valueOf(ledgerBook, "entity_type")
      && identifierEqual(valueOf(calendar, "entity_id"), valueOf(ledgerBook, "entity_id")),
    `Journal ${valueOf(journal, "id")} Accounting Period 不属于 Ledger Book ${valueOf(ledgerBook, "id")}`);
    const instructionLines = instructionLinesByInstruction.get(instructionId) ?? [];
    const instructionLinesById = new Map(instructionLines.map((line) => [
      identifierKey(valueOf(line, "id")),
      line,
    ]));
    assert.equal(instructionLinesById.size, instructionLines.length,
      `POSTED Instruction ${valueOf(instruction, "id")} Instruction Line ID 重复`);
    const journalLines = journalLinesByJournal.get(identifierKey(valueOf(journal, "id"))) ?? [];
    assert.equal(journalLines.length, instructionLines.length,
      `Journal ${valueOf(journal, "id")} 与 POSTED Instruction ${valueOf(instruction, "id")} 行数不一致`);
    for (const line of [...instructionLines, ...journalLines]) {
      const account = accounts.get(valueOf(line, "account_id"));
      assert.ok(account && identifierEqual(valueOf(account, "ledger_book_id"), valueOf(ledgerBook, "id")),
        `Journal ${valueOf(journal, "id")} Account ${valueOf(line, "account_id")} 不属于 Ledger Book ${valueOf(ledgerBook, "id")}`);
    }
    const replayed = new Set();
    for (const journalLine of journalLines) {
      const instructionLineId = identifierKey(valueOf(journalLine, "instruction_line_id"));
      const instructionLine = instructionLinesById.get(instructionLineId);
      assert.ok(instructionLine,
        `Journal ${valueOf(journal, "id")} Line ${valueOf(journalLine, "id")} 未绑定同 Instruction 的 Instruction Line`);
      assert.ok(!replayed.has(instructionLineId),
        `Journal ${valueOf(journal, "id")} 重复重放 Instruction Line ${valueOf(instructionLine, "id")}`);
      replayed.add(instructionLineId);
      assert.ok(identifierEqual(valueOf(journalLine, "account_id"), valueOf(instructionLine, "account_id")),
        `Journal ${valueOf(journal, "id")} Instruction Line ${valueOf(instructionLine, "id")} account_id 未重放`);
      assert.equal(valueOf(journalLine, "entry_side"), valueOf(instructionLine, "entry_side"),
        `Journal ${valueOf(journal, "id")} Instruction Line ${valueOf(instructionLine, "id")} entry_side 未重放`);
      assert.ok(fixedRecordEqual(journalLine, "amount", instructionLine, "amount"),
        `Journal ${valueOf(journal, "id")} Instruction Line ${valueOf(instructionLine, "id")} amount 未精确重放`);
    }
    assert.equal(replayed.size, instructionLines.length,
      `Journal ${valueOf(journal, "id")} 缺少 POSTED Instruction Line`);
  }
  return posted.length;
}

function assertInstructionCoordinates(lines, expected, context) {
  assert.equal(lines.length, expected.length, `${context} Instruction Line 数量错误`);
  const unmatched = [...lines];
  for (const coordinate of expected) {
    const index = unmatched.findIndex((line) => identifierEqual(valueOf(line, "account_id"), coordinate.accountId)
      && valueOf(line, "entry_side") === coordinate.side
      && fixedEqual(fixedValue(line, "amount"), coordinate.amount));
    assert.ok(index >= 0,
      `${context} 缺少冻结 Account ${coordinate.accountId}/${coordinate.side}/${fixedFormat(coordinate.amount)}`);
    unmatched.splice(index, 1);
  }
}

function validatePayoutAccountingCoordinates(records, instructionLinesByInstruction) {
  const instructions = recordsFor(records, "t_accounting_instruction");
  const accounts = recordsFor(records, "t_account");
  const chartAccounts = recordsById(records, "t_chart_account");
  const executions = recordsFor(records, "t_payout_execution");
  const matches = recordsFor(records, "t_bank_recon_match")
    .filter((record) => valueOf(record, "match_type") === "PAYOUT_DEBIT"
      && valueOf(record, "status") === "CONFIRMED");
  const exceptions = recordsFor(records, "t_recon_exception")
    .filter((record) => valueOf(record, "source_type") === "PAYOUT_EXECUTION"
      && valueOf(record, "exception_type") === "FEE_DIFFERENCE");
  const resolutions = recordsById(records, "t_recon_resolution");
  const validatedInstructionIds = new Set();

  const bankAccountForExecution = (execution, context) => {
    const candidates = accounts.filter((account) => valueOf(account, "owner_type") === "CASH_ACCOUNT"
      && identifierEqual(valueOf(account, "owner_id"), valueOf(execution, "cash_account_id"))
      && valueOf(account, "balance_bucket") === "NONE"
      && valueOf(chartAccounts.get(valueOf(account, "chart_account_id")), "account_code") === "BANK_CASH");
    assert.equal(candidates.length, 1, `${context} 缺少唯一 BANK_CASH Account`);
    return candidates[0];
  };
  const recoveryAccountForPayout = (payout, context) => {
    const expectedCode = valueOf(payout, "owner_type") === "CLIENT"
      ? "CLIENT_RECOVERY_RECEIVABLE"
      : "PARTNER_RECOVERY_RECEIVABLE";
    const candidates = accounts.filter((account) => valueOf(account, "owner_type") === valueOf(payout, "owner_type")
      && identifierEqual(valueOf(account, "owner_id"), valueOf(payout, "owner_id"))
      && valueOf(account, "balance_bucket") === "NONE"
      && valueOf(chartAccounts.get(valueOf(account, "chart_account_id")), "account_code") === expectedCode);
    assert.equal(candidates.length, 1, `${context} 缺少唯一 Owner Recovery Account`);
    return candidates[0];
  };

  for (const payout of recordsFor(records, "t_payout")) {
    const payoutId = valueOf(payout, "id");
    const canonicalPayoutId = identifierKey(payoutId);
    const amount = fixedValue(payout, "owner_debit_amount");
    const exactInstruction = (type) => instructions.filter((instruction) => valueOf(instruction, "instruction_type") === type
      && valueOf(instruction, "source_event_key") === `${type}:${canonicalPayoutId}`);
    const lock = exactInstruction("PAYOUT_LOCK");
    assert.equal(lock.length, 1, `Payout ${payoutId} 缺少唯一 PAYOUT_LOCK:${payoutId}`);
    validatedInstructionIds.add(identifierKey(valueOf(lock[0], "id")));
    assertInstructionCoordinates(instructionLinesByInstruction.get(valueOf(lock[0], "id")) ?? [], [
      { accountId: valueOf(payout, "available_account_id"), side: "DEBIT", amount },
      { accountId: valueOf(payout, "locked_account_id"), side: "CREDIT", amount },
    ], `Payout ${payoutId} PAYOUT_LOCK 冻结 Account`);

    const release = exactInstruction("PAYOUT_RELEASE");
    if (release.length > 0) {
      assert.equal(release.length, 1, `Payout ${payoutId} PAYOUT_RELEASE 必须唯一`);
      validatedInstructionIds.add(identifierKey(valueOf(release[0], "id")));
      assertInstructionCoordinates(instructionLinesByInstruction.get(valueOf(release[0], "id")) ?? [], [
        { accountId: valueOf(payout, "locked_account_id"), side: "DEBIT", amount },
        { accountId: valueOf(payout, "available_account_id"), side: "CREDIT", amount },
      ], `Payout ${payoutId} PAYOUT_RELEASE 冻结 Account`);
    }

    const paid = exactInstruction("PAYOUT_PAID");
    if (paid.length > 0) {
      assert.equal(paid.length, 1, `Payout ${payoutId} PAYOUT_PAID 必须唯一`);
      validatedInstructionIds.add(identifierKey(valueOf(paid[0], "id")));
      const payoutExecutionIds = integerSet(executions
        .filter((execution) => identifierEqual(valueOf(execution, "payout_id"), payoutId))
        .map((execution) => valueOf(execution, "id")));
      const confirmed = matches.filter((match) => payoutExecutionIds.has(identifierKey(valueOf(match, "payout_execution_id"))));
      assert.ok(confirmed.length > 0, `Payout ${payoutId} PAYOUT_PAID 缺少 CONFIRMED Debit`);
      const actualDebit = fixedSumRecords(confirmed, "matched_amount");
      const clearingAmount = fixedCompare(actualDebit, fixedValue(payout, "cash_required_amount")) < 0
        ? actualDebit
        : fixedValue(payout, "cash_required_amount");
      const execution = requireRecord(records, "t_payout_execution", valueOf(confirmed[0], "payout_execution_id"));
      const bankAccount = bankAccountForExecution(execution, `Payout ${payoutId}`);
      assertInstructionCoordinates(instructionLinesByInstruction.get(valueOf(paid[0], "id")) ?? [], [
        { accountId: valueOf(payout, "locked_account_id"), side: "DEBIT", amount: clearingAmount },
        { accountId: valueOf(bankAccount, "id"), side: "CREDIT", amount: clearingAmount },
      ], `Payout ${payoutId} PAYOUT_PAID 冻结 Account`);
    }
  }

  for (const exception of exceptions) {
    const execution = requireRecord(records, "t_payout_execution", valueOf(exception, "source_id"));
    const payout = requireRecord(records, "t_payout", valueOf(execution, "payout_id"));
    const confirmed = matches.filter((match) => identifierEqual(valueOf(match, "payout_execution_id"), valueOf(execution, "id")));
    const actualDebit = fixedSumRecords(confirmed, "matched_amount");
    const signedDelta = fixedSubtract(actualDebit, fixedValue(payout, "cash_required_amount"));
    assert.notEqual(fixedCompare(signedDelta, EXACT_ZERO), 0,
      `Payout ${valueOf(payout, "id")} FEE_DIFFERENCE 不得为零`);
    const expectedType = fixedCompare(signedDelta, EXACT_ZERO) > 0
      ? "PAYOUT_FEE_INCREASE"
      : "PAYOUT_FEE_DECREASE";
    const resolution = resolutions.get(valueOf(exception, "applied_resolution_id"));
    const instruction = resolution && requireRecord(records, "t_accounting_instruction", valueOf(resolution, "instruction_id"));
    assert.equal(valueOf(instruction, "instruction_type"), expectedType,
      `Payout ${valueOf(payout, "id")} 实际 Debit 方向必须使用 ${expectedType}`);
    validatedInstructionIds.add(identifierKey(valueOf(instruction, "id")));
    assert.equal(valueOf(instruction, "source_event_key"), `PAYOUT_FEE_DIFF:${identifierKey(valueOf(exception, "id"))}`);
    const delta = fixedAbs(signedDelta);
    assert.ok(fixedEqual(fixedValue(exception, "difference_amount"), delta),
      `Payout ${valueOf(payout, "id")} fee difference 金额不精确`);
    const expected = expectedType === "PAYOUT_FEE_INCREASE"
      ? [
        { accountId: valueOf(recoveryAccountForPayout(payout, `Payout ${valueOf(payout, "id")}`), "id"), side: "DEBIT", amount: delta },
        { accountId: valueOf(bankAccountForExecution(execution, `Payout ${valueOf(payout, "id")}`), "id"), side: "CREDIT", amount: delta },
      ]
      : [
        { accountId: valueOf(payout, "locked_account_id"), side: "DEBIT", amount: delta },
        { accountId: valueOf(payout, "available_account_id"), side: "CREDIT", amount: delta },
      ];
    assertInstructionCoordinates(instructionLinesByInstruction.get(valueOf(instruction, "id")) ?? [], expected,
      `Payout ${valueOf(payout, "id")} ${expectedType} 冻结 Account`);
  }
  for (const instruction of instructions.filter((record) => [
    "PAYOUT_LOCK",
    "PAYOUT_RELEASE",
    "PAYOUT_PAID",
    "PAYOUT_FEE_INCREASE",
    "PAYOUT_FEE_DECREASE",
  ].includes(valueOf(record, "instruction_type")))) {
    assert.ok(validatedInstructionIds.has(identifierKey(valueOf(instruction, "id"))),
      `Payout Instruction ${valueOf(instruction, "id")} 未绑定真实 Payout/Exception 冻结坐标`);
  }
}

const approvedRequiredAccountingRuleCoordinates = new Set([
  "DUPLICATE_CAPTURE|DUP_CAPTURE_CONTROL_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|CONTROL_ACCOUNT|NONE|DEBIT",
  "DUPLICATE_CAPTURE|DUP_CAPTURE_SUSPENSE_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|RECON_SUSPENSE_ASSET|NONE|CREDIT",
  "PARTNER_RECOVERY_OPEN|PARTNER_RECOVERY_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|PARTNER_RECOVERY_RECEIVABLE|NONE|DEBIT",
  "PARTNER_RECOVERY_OPEN|PARTNER_RECOVERY_SUSPENSE_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|RECON_SUSPENSE_ASSET|NONE|CREDIT",
  "PAYABLE_BALANCE_MATURED|CLIENT_PENDING_DR|CLIENT_MATURED_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|PENDING|DEBIT",
  "PAYABLE_BALANCE_MATURED|CLIENT_AVAILABLE_CR|CLIENT_MATURED_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|AVAILABLE|CREDIT",
  "PAYABLE_BALANCE_MATURED|PARTNER_PENDING_DR|PARTNER_GROSS_MATURED|FROZEN_ACCOUNT|PARTNER_PAYABLE|PENDING|DEBIT",
  "PAYABLE_BALANCE_MATURED|PARTNER_RECOVERY_NET_CR|RECOVERY_NETTED|FROZEN_ACCOUNT|PARTNER_RECOVERY_RECEIVABLE|NONE|CREDIT",
  "PAYABLE_BALANCE_MATURED|PARTNER_AVAILABLE_CR|PARTNER_NET_AVAILABLE|FROZEN_ACCOUNT|PARTNER_PAYABLE|AVAILABLE|CREDIT",
  "REFUND_SUCCEEDED|REFUND_CLIENT_PENDING_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|PENDING|DEBIT",
  "REFUND_SUCCEEDED|REFUND_CHANNEL_PAYABLE_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|CHANNEL_UNSETTLED_PAYABLE|NONE|CREDIT",
  "PAYOUT_LOCK|OWNER_AVAILABLE_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|OWNER_PAYABLE|AVAILABLE|DEBIT",
  "PAYOUT_LOCK|OWNER_LOCKED_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|OWNER_PAYABLE|LOCKED|CREDIT",
  "PAYOUT_PAID|OWNER_LOCKED_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|OWNER_PAYABLE|LOCKED|DEBIT",
  "PAYOUT_PAID|BANK_CASH_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|BANK_CASH|NONE|CREDIT",
  "PAYOUT_RELEASE|CLIENT_LOCKED_RELEASE_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|LOCKED|DEBIT",
  "PAYOUT_RELEASE|CLIENT_AVAILABLE_RELEASE_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|AVAILABLE|CREDIT",
  "PAYOUT_FEE_INCREASE|FEE_HIGH_RECOVERY_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|CLIENT_RECOVERY_RECEIVABLE|NONE|DEBIT",
  "PAYOUT_FEE_INCREASE|FEE_HIGH_BANK_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|BANK_CASH|NONE|CREDIT",
  "PAYOUT_FEE_DECREASE|FEE_LOW_LOCKED_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|LOCKED|DEBIT",
  "PAYOUT_FEE_DECREASE|FEE_LOW_AVAILABLE_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|CLIENT_PAYABLE|AVAILABLE|CREDIT",
  "LATE_DEBIT|LATE_DEBIT_SUSPENSE_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|RECON_SUSPENSE_ASSET|NONE|DEBIT",
  "LATE_DEBIT|LATE_DEBIT_BANK_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|BANK_CASH|NONE|CREDIT",
  "TAX_PAYMENT_RECONCILED|TAX_PAYABLE_DR|POSTING_AMOUNT|FROZEN_ACCOUNT|TAX_PAYABLE|NONE|DEBIT",
  "TAX_PAYMENT_RECONCILED|TAX_BANK_CR|POSTING_AMOUNT|FROZEN_ACCOUNT|BANK_CASH|NONE|CREDIT",
]);

function accountingRuleCoordinate(rule) {
  return [
    "instruction_type",
    "line_code",
    "amount_source_code",
    "owner_role",
    "account_code",
    "balance_bucket",
    "entry_side",
  ].map((field) => valueOf(rule, field)).join("|");
}

function validateAccountingSourceAuthority(records) {
  const instructions = recordsFor(records, "t_accounting_instruction")
    .filter((instruction) => valueOf(instruction, "origin_type") === "AUTO"
      || valueOf(instruction, "status") === "POSTED");
  const chartAccounts = recordsById(records, "t_chart_account");
  const journals = recordsFor(records, "t_journal_entry");
  const uniqueIntegerRecord = (table, id, context) => {
    const matches = recordsFor(records, table)
      .filter((record) => identifierEqual(valueOf(record, "id"), id));
    assert.equal(matches.length, 1, `${context} authority source 缺少唯一 ${table}.id=${id}`);
    return matches[0];
  };
  const authorityId = (instruction, prefix) => {
    const instructionId = valueOf(instruction, "id");
    const type = valueOf(instruction, "instruction_type");
    const key = valueOf(instruction, "source_event_key");
    const match = key?.match(new RegExp(`^${prefix}:(\\d+)$`, "u"));
    assert.ok(match,
      `Instruction ${instructionId} ${type} authority key 必须为 ${prefix}:<id>`);
    return match[1];
  };
  const journalForInstruction = (instruction, context) => {
    const matches = journals.filter((journal) => identifierEqual(
      valueOf(journal, "instruction_id"), valueOf(instruction, "id"),
    ));
    assert.equal(matches.length, 1, `${context} authority source 必须唯一落到 Journal`);
    return matches[0];
  };
  const requireJournalId = (instruction, journalId, context) => {
    const journal = journalForInstruction(instruction, context);
    assert.ok(identifierEqual(valueOf(journal, "id"), journalId),
      `${context} authority source Journal 坐标不一致`);
  };
  const requireSourceSystem = (instruction, expected) => assert.equal(
    valueOf(instruction, "source_system"), expected,
    `Instruction ${valueOf(instruction, "id")} ${valueOf(instruction, "instruction_type")} source_system 必须为 ${expected}`,
  );
  const requireSourceVersion = (instruction, expected, context) => assert.ok(
    identifierEqual(valueOf(instruction, "source_version"), expected),
    `${context} source_version 必须为决定性 authority version ${expected}`,
  );

  const resolvers = new Map([
    ["DUPLICATE_CAPTURE", (instruction) => {
      const itemId = authorityId(instruction, "DUPLICATE_CAPTURE");
      const context = `Instruction ${valueOf(instruction, "id")} DUPLICATE_CAPTURE`;
      const item = uniqueIntegerRecord("t_duplicate_capture_item", itemId, context);
      const economics = recordsFor(records, "t_transaction_economics")
        .filter((record) => valueOf(record, "economics_type") === "DUPLICATE_CAPTURE"
          && identifierEqual(valueOf(record, "duplicate_capture_item_id"), itemId));
      assert.equal(economics.length, 1, `${context} authority source 缺少唯一 DUPLICATE_CAPTURE Economics`);
      assert.ok(identifierEqual(valueOf(item, "source_version"), valueOf(instruction, "source_version")),
        `${context} source_version 与 Duplicate Capture authority 不一致`);
      requireJournalId(instruction, valueOf(item, "journal_entry_id"), context);
      requireSourceSystem(instruction, "FINANCE");
    }],
    ["PARTNER_RECOVERY_OPEN", (instruction) => {
      const economicsId = authorityId(instruction, "PARTNER_RECOVERY_OPEN");
      const context = `Instruction ${valueOf(instruction, "id")} PARTNER_RECOVERY_OPEN`;
      const economics = uniqueIntegerRecord("t_transaction_economics", economicsId, context);
      assert.ok(identifierEqual(valueOf(instruction, "transaction_economics_id"), economicsId),
        `${context} transaction_economics_id 与 authority key 不一致`);
      assert.equal(valueOf(economics, "economics_type"), "DISPUTE",
        `${context} authority source 必须为负向 DISPUTE Economics`);
      assert.ok(!isNull(valueOf(economics, "channel_dispute_notice_id"))
        && !isNull(valueOf(economics, "dispute_case_id")),
      `${context} authority source 缺少 Dispute 根坐标`);
      assert.ok(identifierEqual(valueOf(economics, "source_version"), valueOf(instruction, "source_version")),
        `${context} source_version 与 Economics 不一致`);
      const partnerId = valueOf(economics, "local_partner_id");
      const recoveryAccounts = recordsFor(records, "t_account").filter((account) => (
        valueOf(account, "owner_type") === "LOCAL_PARTNER"
        && identifierEqual(valueOf(account, "owner_id"), partnerId)
        && valueOf(account, "balance_bucket") === "NONE"
        && valueOf(chartAccounts.get(valueOf(account, "chart_account_id")), "account_code") === "PARTNER_RECOVERY_RECEIVABLE"
      ));
      assert.equal(recoveryAccounts.length, 1,
        `${context} authority source 缺少唯一 Partner Recovery Account`);
      const journal = journalForInstruction(instruction, context);
      assert.ok(valueOf(economics, "commercial_context_time").slice(0, 10) <= valueOf(journal, "posting_date"),
        `${context} Journal 早于负向 Economics authority fact`);
      requireSourceSystem(instruction, valueOf(economics, "source_system"));
    }],
    ["PAYABLE_BALANCE_MATURED", (instruction) => {
      const economicsId = authorityId(instruction, "PAYABLE_BALANCE_MATURED");
      const context = `Instruction ${valueOf(instruction, "id")} PAYABLE_BALANCE_MATURED`;
      const economics = uniqueIntegerRecord("t_transaction_economics", economicsId, context);
      assert.equal(valueOf(economics, "economics_type"), "PAYMENT",
        `${context} authority source 必须为 PAYMENT Economics`);
      assert.ok(identifierEqual(valueOf(instruction, "transaction_economics_id"), economicsId),
        `${context} transaction_economics_id 与 authority key 不一致`);
      const events = recordsFor(records, "t_compensation_event").filter((event) => (
        valueOf(event, "event_type") === "PAYABLE_BALANCE_MATURED"
        && valueOf(event, "event_key") === valueOf(instruction, "source_event_key")
        && valueOf(event, "source_type") === "TRANSACTION_ECONOMICS"
        && identifierEqual(valueOf(event, "source_id"), economicsId)
        && identifierEqual(valueOf(event, "source_version"), valueOf(instruction, "source_version"))
      ));
      assert.equal(events.length, 1, `${context} authority source 缺少唯一 maturity Event`);
      requireSourceSystem(instruction, "FINANCE");
    }],
    ["REFUND_SUCCEEDED", (instruction) => {
      const refundRequestId = authorityId(instruction, "REFUND_SUCCEEDED");
      const context = `Instruction ${valueOf(instruction, "id")} REFUND_SUCCEEDED`;
      const economics = uniqueIntegerRecord(
        "t_transaction_economics", valueOf(instruction, "transaction_economics_id"), context,
      );
      assert.equal(valueOf(economics, "economics_type"), "REFUND",
        `${context} authority source 必须为 REFUND Economics`);
      assert.ok(identifierEqual(valueOf(economics, "refund_request_id"), refundRequestId),
        `${context} refund_request_id 与 authority key 不一致`);
      assert.ok(identifierEqual(valueOf(economics, "source_version"), valueOf(instruction, "source_version")),
        `${context} source_version 与 Economics 不一致`);
      requireSourceSystem(instruction, "PAYMENT");
    }],
    ...["PAYOUT_LOCK", "PAYOUT_RELEASE", "PAYOUT_PAID"].map((instructionType) => [
      instructionType,
      (instruction) => {
        const payoutId = authorityId(instruction, instructionType);
        const context = `Instruction ${valueOf(instruction, "id")} ${instructionType}`;
        const payout = uniqueIntegerRecord("t_payout", payoutId, context);
        const events = recordsFor(records, "t_payout_event")
          .filter((event) => identifierEqual(valueOf(event, "payout_id"), payoutId));
        if (instructionType === "PAYOUT_LOCK") {
          const created = events.filter((event) => numberOf(event, "event_no") === 1);
          assert.equal(created.length, 1, `${context} authority source 缺少 PayoutCreated Event`);
          requireJournalId(instruction, valueOf(created[0], "journal_entry_id"), context);
          requireSourceVersion(instruction, valueOf(created[0], "payout_version_after"), context);
        } else if (instructionType === "PAYOUT_RELEASE") {
          assert.equal(valueOf(payout, "status"), "CANCELLED", `${context} authority source 必须为 CANCELLED Payout`);
          const cancelled = events.filter((event) => valueOf(event, "to_status") === "CANCELLED");
          assert.equal(cancelled.length, 1, `${context} authority source 缺少 CANCELLED Event`);
          requireJournalId(instruction, valueOf(cancelled[0], "journal_entry_id"), context);
          requireSourceVersion(instruction, valueOf(cancelled[0], "payout_version_after"), context);
        } else {
          assert.equal(valueOf(payout, "status"), "PAID", `${context} authority source 必须为 PAID Payout`);
          const paid = events.filter((event) => valueOf(event, "event_scope") === "PAYOUT"
            && valueOf(event, "to_status") === "PAID");
          assert.equal(paid.length, 1, `${context} authority source 缺少唯一 PAID Event`);
          requireJournalId(instruction, valueOf(payout, "journal_entry_id"), context);
          requireJournalId(instruction, valueOf(paid[0], "journal_entry_id"), context);
          requireSourceVersion(instruction, valueOf(paid[0], "payout_version_after"), context);
        }
        requireSourceSystem(instruction, "FINANCE");
      },
    ]),
    ...["PAYOUT_FEE_INCREASE", "PAYOUT_FEE_DECREASE"].map((instructionType) => [
      instructionType,
      (instruction) => {
        const exceptionId = authorityId(instruction, "PAYOUT_FEE_DIFF");
        const context = `Instruction ${valueOf(instruction, "id")} ${instructionType}`;
        const exception = uniqueIntegerRecord("t_recon_exception", exceptionId, context);
        assert.equal(valueOf(exception, "source_type"), "PAYOUT_EXECUTION",
          `${context} authority source 必须属于 PAYOUT_EXECUTION`);
        assert.equal(valueOf(exception, "exception_type"), "FEE_DIFFERENCE",
          `${context} authority source 必须为 FEE_DIFFERENCE Recon Exception`);
        const resolution = uniqueIntegerRecord("t_recon_resolution", valueOf(exception, "applied_resolution_id"), context);
        assert.ok(identifierEqual(valueOf(resolution, "exception_id"), valueOf(exception, "id"))
          && identifierEqual(valueOf(exception, "applied_resolution_id"), valueOf(resolution, "id")),
        `Recon Exception ${valueOf(exception, "id")} 与 Resolution ${valueOf(resolution, "id")} 必须双向一致`);
        assert.ok(identifierEqual(valueOf(resolution, "instruction_id"), valueOf(instruction, "id")),
          `${context} authority source Resolution 未绑定自身 Instruction`);
        requireSourceVersion(instruction, valueOf(exception, "version"), context);
        requireSourceSystem(instruction, "FINANCE");
      },
    ]),
    ["LATE_DEBIT", (instruction) => {
      const bankItemId = authorityId(instruction, "LATE_DEBIT");
      const context = `Instruction ${valueOf(instruction, "id")} LATE_DEBIT`;
      const items = recordsFor(records, "t_bank_statement_item")
        .filter((item) => identifierEqual(valueOf(item, "id"), bankItemId) && valueOf(item, "direction") === "DEBIT");
      const exceptions = recordsFor(records, "t_recon_exception").filter((exception) => (
        valueOf(exception, "exception_type") === "LATE_DEBIT"
        && valueOf(exception, "source_type") === "BANK_STATEMENT_ITEM"
        && identifierEqual(valueOf(exception, "source_id"), bankItemId)
      ));
      assert.ok(items.length === 1 && exceptions.length === 1,
        `${context} authority source 必须同时是 DEBIT Bank Item 与唯一 Recon Exception`);
      requireSourceVersion(instruction, "1", context);
      requireSourceSystem(instruction, "FINANCE");
    }],
    ["TAX_PAYMENT_RECONCILED", (instruction) => {
      const paymentId = authorityId(instruction, "TAX_PAYMENT_RECONCILED");
      const context = `Instruction ${valueOf(instruction, "id")} TAX_PAYMENT_RECONCILED`;
      const payment = uniqueIntegerRecord("t_tax_payment", paymentId, context);
      assert.equal(valueOf(payment, "status"), "RECONCILED",
        `${context} authority source 必须为 RECONCILED Tax Payment`);
      requireSourceVersion(instruction, valueOf(payment, "version"), context);
      requireJournalId(instruction, valueOf(payment, "journal_entry_id"), context);
      const finalEvents = recordsFor(records, "t_tax_payment_event").filter((event) => (
        identifierEqual(valueOf(event, "tax_payment_id"), paymentId)
        && valueOf(event, "event_type") === "RECONCILED"
        && identifierEqual(valueOf(event, "event_no"), valueOf(instruction, "source_version"))
      ));
      assert.equal(finalEvents.length, 1, `${context} source_version 缺少唯一决定性 Tax RECONCILED Event`);
      const matches = recordsFor(records, "t_bank_recon_match").filter((match) => (
        identifierEqual(valueOf(match, "id"), valueOf(finalEvents[0], "source_ref"))
        && valueOf(match, "match_type") === "TAX_PAYMENT_DEBIT"
        && valueOf(match, "status") === "CONFIRMED"
        && identifierEqual(valueOf(match, "tax_payment_id"), paymentId)
      ));
      assert.equal(matches.length, 1, `${context} 决定性 Event 缺少自身 CONFIRMED Tax Match`);
      requireSourceSystem(instruction, "FINANCE");
    }],
    ["CHANNEL_BATCH_SETTLEMENT", (instruction) => {
      const batchId = authorityId(instruction, "CHANNEL_BATCH_SETTLEMENT");
      const context = `Instruction ${valueOf(instruction, "id")} CHANNEL_BATCH_SETTLEMENT`;
      const batch = uniqueIntegerRecord("t_channel_settle_batch", batchId, context);
      assert.equal(valueOf(batch, "status"), "VALIDATED", `${context} authority source 必须为 VALIDATED final Batch`);
    }],
    ["CHANNEL_BANK_MATCH", (instruction) => {
      const matchId = authorityId(instruction, "CHANNEL_BANK_MATCH");
      const context = `Instruction ${valueOf(instruction, "id")} CHANNEL_BANK_MATCH`;
      const match = uniqueIntegerRecord("t_bank_recon_match", matchId, context);
      assert.ok(["CHANNEL_CREDIT", "CHANNEL_DEBIT"].includes(valueOf(match, "match_type"))
        && valueOf(match, "status") === "CONFIRMED",
      `${context} authority source 必须为 CONFIRMED Channel Bank Match`);
    }],
    ["DISPUTE_LOST", (instruction) => {
      const disputeCaseId = authorityId(instruction, "DISPUTE_LOST");
      const context = `Instruction ${valueOf(instruction, "id")} DISPUTE_LOST`;
      const economics = uniqueIntegerRecord(
        "t_transaction_economics", valueOf(instruction, "transaction_economics_id"), context,
      );
      assert.equal(valueOf(economics, "economics_type"), "DISPUTE", `${context} authority source 必须为 DISPUTE Economics`);
      assert.ok(identifierEqual(valueOf(economics, "dispute_case_id"), disputeCaseId),
        `${context} dispute_case_id 与 authority key 不一致`);
    }],
    ...["PLATFORM_SERVICE_CHARGE_PARTNER", "PLATFORM_SERVICE_CHARGE_PLATFORM"].map((instructionType) => [
      instructionType,
      (instruction) => {
        const context = `Instruction ${valueOf(instruction, "id")} ${instructionType}`;
        const economics = uniqueIntegerRecord(
          "t_transaction_economics", valueOf(instruction, "transaction_economics_id"), context,
        );
        assert.equal(valueOf(instruction, "source_event_key"), valueOf(economics, "source_event_key"),
          `${context} authority key 必须复用 Transaction Economics Event Key`);
        assert.ok(recordsFor(records, "t_transaction_economic_component").some((component) => (
          identifierEqual(valueOf(component, "transaction_economics_id"), valueOf(economics, "id"))
          && valueOf(component, "component_type") === "PLATFORM_SERVICE_CHARGE"
        )), `${context} authority source 缺少 PLATFORM_SERVICE_CHARGE Component`);
      },
    ]),
  ]);

  for (const instruction of instructions) {
    if (valueOf(instruction, "origin_type") === "MANUAL") {
      const context = `POSTED MANUAL Instruction ${valueOf(instruction, "id")}`;
      const adjustmentId = valueOf(instruction, "manual_adjustment_id");
      const adjustments = recordsFor(records, "t_manual_adjustment")
        .filter((adjustment) => identifierEqual(valueOf(adjustment, "id"), adjustmentId));
      assert.equal(adjustments.length, 1, `${context} 必须引用唯一 approved manual_adjustment`);
      const adjustment = adjustments[0];
      assert.ok(["APPROVED", "POSTED"].includes(valueOf(adjustment, "status")),
        `${context} 必须引用 approved manual_adjustment`);
      assert.ok(identifierEqual(valueOf(adjustment, "ledger_book_id"), valueOf(instruction, "ledger_book_id")),
        `${context} 与 manual_adjustment ledger_book_id 不一致`);
      assert.equal(valueOf(instruction, "input_hash"), valueOf(adjustment, "content_hash"),
        `${context} 未冻结 approved manual_adjustment content_hash`);
      const manualNullSourceFields = ["source_system", "source_event_key", "source_version", "source_payload_hash",
        "transaction_economics_id", "accounting_rule_bundle_ver_id"];
      assert.ok(manualNullSourceFields.every((field) => isNull(valueOf(instruction, field))),
        `${context} source fields 必须全部为 NULL，不得混用 AUTO authority`);
      assert.ok(!isNull(valueOf(instruction, "maker_id")) && !isNull(valueOf(instruction, "checker_id"))
        && !identifierEqual(valueOf(instruction, "maker_id"), valueOf(instruction, "checker_id")),
      `${context} Maker/Checker 必须非空且不同`);
      const approvals = recordsFor(records, "t_adjustment_approval").filter((approval) => (
        identifierEqual(valueOf(approval, "adjustment_id"), adjustmentId)
        && valueOf(approval, "decision") === "APPROVED"
        && valueOf(approval, "approved_content_hash") === valueOf(adjustment, "content_hash")
      ));
      const makerApprovals = approvals.filter((approval) => valueOf(approval, "approval_role") === "MAKER"
        && identifierEqual(valueOf(approval, "actor_id"), valueOf(adjustment, "maker_id"))
        && identifierEqual(valueOf(approval, "actor_id"), valueOf(instruction, "maker_id")));
      const checkerApprovals = approvals.filter((approval) => valueOf(approval, "approval_role") === "CHECKER"
        && identifierEqual(valueOf(approval, "actor_id"), valueOf(instruction, "checker_id")));
      assert.equal(makerApprovals.length, 1, `${context} 必须有唯一 approved Maker/Checker authority pair`);
      assert.equal(checkerApprovals.length, 1, `${context} 必须有唯一 approved Maker/Checker authority pair`);
      assert.ok(Date.parse(valueOf(adjustment, "submitted_time")) <= Date.parse(valueOf(makerApprovals[0], "decision_time"))
        && Date.parse(valueOf(makerApprovals[0], "decision_time")) <= Date.parse(valueOf(checkerApprovals[0], "decision_time")),
      `${context} submitted/Maker/Checker authority 时间线不单调`);
      const adjustmentLines = recordsFor(records, "t_manual_adjustment_line")
        .filter((line) => identifierEqual(valueOf(line, "adjustment_id"), adjustmentId));
      const adjustmentLinesById = new Map(adjustmentLines.map((line) => [
        identifierKey(valueOf(line, "id")), line,
      ]));
      assert.equal(adjustmentLinesById.size, adjustmentLines.length,
        `${context} manual_adjustment Line ID 重复`);
      const instructionLines = recordsFor(records, "t_instruction_line")
        .filter((line) => identifierEqual(valueOf(line, "instruction_id"), valueOf(instruction, "id")));
      assert.equal(instructionLines.length, adjustmentLines.length,
        `${context} 与 manual_adjustment Line 数量不一致`);
      const replayedAdjustmentLines = new Set();
      for (const line of instructionLines) {
        assert.ok(isNull(valueOf(line, "accounting_rule_line_id")),
          `${context} Line 不得引用 AUTO Rule Line`);
        const adjustmentLineId = valueOf(line, "manual_adjustment_line_id");
        const adjustmentLine = isNull(adjustmentLineId)
          ? undefined
          : adjustmentLinesById.get(identifierKey(adjustmentLineId));
        assert.ok(adjustmentLine, `${context} Line 缺少 manual_adjustment Line authority`);
        const canonicalAdjustmentLineId = identifierKey(adjustmentLineId);
        assert.ok(!replayedAdjustmentLines.has(canonicalAdjustmentLineId),
          `${context} 重复重放 manual_adjustment Line ${adjustmentLineId}`);
        replayedAdjustmentLines.add(canonicalAdjustmentLineId);
        assert.ok(identifierEqual(valueOf(line, "account_id"), valueOf(adjustmentLine, "account_id"))
          && valueOf(line, "entry_side") === valueOf(adjustmentLine, "entry_side")
          && fixedRecordEqual(line, "amount", adjustmentLine, "amount"),
        `${context} Line 未一一重放 manual_adjustment`);
      }
      assert.equal(replayedAdjustmentLines.size, adjustmentLines.length,
        `${context} 缺少 manual_adjustment Line authority`);
      continue;
    }
    assert.equal(valueOf(instruction, "origin_type"), "AUTO",
      `POSTED Instruction ${valueOf(instruction, "id")} origin_type 缺少 authority policy`);
    const resolver = resolvers.get(valueOf(instruction, "instruction_type"));
    assert.ok(resolver,
      `Instruction ${valueOf(instruction, "id")} ${valueOf(instruction, "instruction_type")} 缺少业务 authority resolver`);
    resolver(instruction);
  }
  return instructions.length;
}

function validateAccountingRuleReplay(records) {
  const approvedOwnerRoles = new Set(["FROZEN_ACCOUNT"]);
  const approvedMaturitySources = new Set([
    "CLIENT_MATURED_AMOUNT",
    "PARTNER_GROSS_MATURED",
    "RECOVERY_NETTED",
    "PARTNER_NET_AVAILABLE",
  ]);
  const allRules = recordsFor(records, "t_accounting_rule_line");
  const rules = allRules.filter((record) => valueOf(record, "required_flag") === "1");
  const rulesById = identifierLookupMap(rules, (record) => valueOf(record, "id"));
  const ruleVersions = recordsById(records, "t_accounting_rule_bundle_ver");
  const activeBundles = recordsFor(records, "t_accounting_rule_bundle")
    .filter((bundle) => valueOf(bundle, "status") === "ACTIVE");
  const currentVersionIds = integerSet(activeBundles.map((bundle) => valueOf(bundle, "current_ver_id")));
  const rulesByVersionAndType = Map.groupBy(rules, (record) => [
    identifierKey(valueOf(record, "bundle_ver_id")), valueOf(record, "instruction_type"),
  ].join("|"));
  const instructionLinesByInstruction = groupByInteger(recordsFor(records, "t_instruction_line"), "instruction_id");
  const accounts = recordsById(records, "t_account");
  const chartAccounts = recordsById(records, "t_chart_account");
  const automatic = recordsFor(records, "t_accounting_instruction")
    .filter((record) => valueOf(record, "origin_type") === "AUTO");

  for (const rule of rules) {
    assert.ok(approvedOwnerRoles.has(valueOf(rule, "owner_role")),
      `Accounting Rule ${valueOf(rule, "id")} owner_role=${valueOf(rule, "owner_role")} 不在批准词汇`);
  }
  assert.ok(currentVersionIds.size > 0, "缺少 current Rule Bundle Version");
  for (const bundle of activeBundles) {
    const currentVersion = ruleVersions.get(valueOf(bundle, "current_ver_id"));
    assert.ok(currentVersion
      && identifierEqual(valueOf(currentVersion, "bundle_id"), valueOf(bundle, "id"))
      && valueOf(currentVersion, "status") === "ACTIVE",
    `Accounting Rule Bundle ${valueOf(bundle, "id")} current_ver_id 必须绑定自身 ACTIVE Version`);
  }
  for (const currentVersionId of currentVersionIds) {
    const currentRules = allRules.filter((rule) => identifierEqual(valueOf(rule, "bundle_ver_id"), currentVersionId));
    assert.equal(currentRules.length, approvedRequiredAccountingRuleCoordinates.size,
      `current Rule Bundle Version ${currentVersionId} 必须精确等于 25-line required catalog`);
    for (const rule of currentRules) {
      assert.equal(valueOf(rule, "required_flag"), "1",
        `current Rule Bundle Version ${currentVersionId} 必须精确等于 25-line required catalog`);
      assert.ok(approvedRequiredAccountingRuleCoordinates.has(accountingRuleCoordinate(rule)),
        `Accounting Rule ${valueOf(rule, "id")} instruction_type=${valueOf(rule, "instruction_type")} amount_source_code=${valueOf(rule, "amount_source_code")} 不属于 approved business coordinates`);
    }
    assert.deepEqual(
      currentRules.map(accountingRuleCoordinate).toSorted(),
      [...approvedRequiredAccountingRuleCoordinates].toSorted(),
      `current Rule Bundle Version ${currentVersionId} 必须精确等于 25-line required catalog`,
    );
  }

  validateAccountingSourceAuthority(records);

  for (const instruction of automatic) {
    const instructionId = valueOf(instruction, "id");
    const instructionType = valueOf(instruction, "instruction_type");
    const frozenVersionId = valueOf(instruction, "accounting_rule_bundle_ver_id");
    const frozenVersion = ruleVersions.get(frozenVersionId);
    assert.ok(frozenVersion && ["APPROVED", "ACTIVE", "RETIRED"].includes(valueOf(frozenVersion, "status")),
      `AUTO Instruction ${instructionId} 缺少批准的冻结 Rule Bundle Version ${frozenVersionId}`);
    const requiredRules = rulesByVersionAndType.get(`${identifierKey(frozenVersionId)}|${instructionType}`) ?? [];
    assert.ok(requiredRules.length > 0,
      `AUTO Instruction ${instructionId}/${instructionType} 冻结 Rule Bundle Version 缺少 required Rule Line 定义`);
    const actualLines = instructionLinesByInstruction.get(instructionId) ?? [];
    const actualRuleIds = [];
    const amountsBySource = new Map();
    for (const line of actualLines) {
      const rule = rulesById.get(valueOf(line, "accounting_rule_line_id"));
      assert.ok(rule, `AUTO Instruction ${instructionId} 引用不存在或非 required Rule Line`);
      assert.ok(identifierEqual(valueOf(rule, "bundle_ver_id"), frozenVersionId),
        `AUTO Instruction ${instructionId} Line ${valueOf(line, "id")} 未使用冻结 Rule Bundle Version ${frozenVersionId}`);
      assert.ok(approvedOwnerRoles.has(valueOf(rule, "owner_role")),
        `Accounting Rule ${valueOf(rule, "id")} owner_role=${valueOf(rule, "owner_role")} 不在批准词汇`);
      assert.ok(approvedRequiredAccountingRuleCoordinates.has(accountingRuleCoordinate(rule)),
        `Accounting Rule ${valueOf(rule, "id")} instruction_type=${valueOf(rule, "instruction_type")} amount_source_code=${valueOf(rule, "amount_source_code")} 不属于 approved business coordinates`);
      assert.equal(valueOf(rule, "instruction_type"), instructionType,
        `AUTO Instruction ${instructionId} instruction_type 与 Rule Line ${valueOf(rule, "id")} 不一致`);
      assert.equal(valueOf(rule, "entry_side"), valueOf(line, "entry_side"),
        `AUTO Instruction ${instructionId} Rule Line entry_side 未重放`);
      const account = accounts.get(valueOf(line, "account_id"));
      const chart = account && chartAccounts.get(valueOf(account, "chart_account_id"));
      assert.ok(account && chart, `AUTO Instruction ${instructionId} Line 缺少 Account/Chart Account`);
      assert.equal(valueOf(account, "balance_bucket"), valueOf(rule, "balance_bucket"),
        `AUTO Instruction ${instructionId} Rule Line balance_bucket 未重放`);
      const expectedAccountCode = valueOf(rule, "account_code");
      if (expectedAccountCode === "OWNER_PAYABLE") {
        assert.ok(["CLIENT_PAYABLE", "PARTNER_PAYABLE"].includes(valueOf(chart, "account_code")),
          `AUTO Instruction ${instructionId} OWNER_PAYABLE 未解析为 Owner payable Account`);
      } else {
        assert.equal(valueOf(chart, "account_code"), expectedAccountCode,
          `AUTO Instruction ${instructionId} Rule Line account_code 未重放`);
      }
      actualRuleIds.push(identifierKey(valueOf(rule, "id")));
      const sourceCode = valueOf(rule, "amount_source_code");
      const priorAmount = amountsBySource.get(sourceCode);
      const amount = fixedValue(line, "amount");
      if (priorAmount) {
        assert.ok(fixedEqual(priorAmount, amount),
          `AUTO Instruction ${instructionId} amount_source_code=${sourceCode} 金额不一致`);
      } else {
        amountsBySource.set(sourceCode, amount);
      }
    }
    const expectedRuleIds = requiredRules.map((record) => identifierKey(valueOf(record, "id"))).toSorted();
    assert.deepEqual(actualRuleIds.toSorted(), expectedRuleIds,
      `AUTO Instruction ${instructionId}/${instructionType} 缺少 required Rule Line 或重复发射`);
  }
  const maturitySources = new Set(rules
    .filter((rule) => currentVersionIds.has(identifierKey(valueOf(rule, "bundle_ver_id")))
      && valueOf(rule, "instruction_type") === "PAYABLE_BALANCE_MATURED")
    .map((rule) => valueOf(rule, "amount_source_code")));
  assert.deepEqual(maturitySources, approvedMaturitySources,
    "PAYABLE_BALANCE_MATURED amount_source_code 必须精确等于批准集合");
  validatePayoutAccountingCoordinates(records, instructionLinesByInstruction);
  return automatic.length;
}

function validateTaxAndChannelTuples(records) {
  const ledgerBooks = recordsById(records, "t_ledger_book");
  const journals = recordsById(records, "t_journal_entry");
  const taxRegistration = requireRecord(records, "t_tax_registration", "100011");
  const taxAuthority = requireRecord(records, "t_tax_authority", valueOf(taxRegistration, "authority_id"));
  const taxOperatingEntityId = valueOf(taxRegistration, "entity_id");
  assert.equal(valueOf(taxRegistration, "entity_type"), "OPERATING_ENTITY");
  assert.ok(identifierEqual(taxOperatingEntityId, "AE001"),
    "Tax Registration 必须冻结真实 Operating Entity");
  for (const snapshot of recordsFor(records, "t_tax_snapshot")) {
    assert.ok(identifierEqual(valueOf(snapshot, "entity_no"), taxOperatingEntityId),
      `Tax Snapshot ${valueOf(snapshot, "id")} OE 与 Registration 不一致`);
  }
  for (const fiscalRecord of recordsFor(records, "t_fiscal_record")) {
    assert.ok(identifierEqual(valueOf(fiscalRecord, "entity_no"), taxOperatingEntityId),
      `Fiscal Record ${valueOf(fiscalRecord, "id")} OE 与 Registration 不一致`);
    assert.ok(identifierEqual(valueOf(fiscalRecord, "tax_registration_id"), valueOf(taxRegistration, "id")));
  }
  for (const taxLine of recordsFor(records, "t_tax_line")) {
    assert.ok(identifierEqual(valueOf(taxLine, "registration_id"), valueOf(taxRegistration, "id")));
    if (valueOf(taxLine, "liable_party_type") === "OPERATING_ENTITY") {
      assert.ok(identifierEqual(valueOf(taxLine, "liable_entity_no"), taxOperatingEntityId),
        `Tax Line ${valueOf(taxLine, "id")} liable OE 不一致`);
    }
  }
  for (const taxReturn of recordsFor(records, "t_tax_return")) {
    assert.ok(identifierEqual(valueOf(taxReturn, "tax_registration_id"), valueOf(taxRegistration, "id")));
    assert.ok(identifierEqual(valueOf(taxReturn, "authority_id"), valueOf(taxAuthority, "id")));
  }
  for (const payment of recordsFor(records, "t_tax_payment")) {
    const taxReturn = requireRecord(records, "t_tax_return", valueOf(payment, "tax_return_id"));
    const payableAccount = requireRecord(records, "t_account", valueOf(payment, "tax_payable_account_id"));
    assert.ok(identifierEqual(valueOf(taxReturn, "authority_id"), valueOf(taxAuthority, "id")));
    assert.ok(valueOf(payableAccount, "owner_type") === "TAX_AUTHORITY"
      && identifierEqual(valueOf(payableAccount, "owner_id"), valueOf(taxAuthority, "id")),
    "Tax Payment/Account authority tuple 不一致");
    const paymentJournal = journals.get(valueOf(payment, "journal_entry_id"));
    const paymentBook = paymentJournal && ledgerBooks.get(valueOf(paymentJournal, "ledger_book_id"));
    assert.ok(paymentBook
      && valueOf(paymentBook, "entity_type") === "OPERATING_ENTITY"
      && identifierEqual(valueOf(paymentBook, "entity_id"), taxOperatingEntityId)
      && valueOf(paymentBook, "currency") === valueOf(payment, "currency")
      && valueOf(paymentBook, "currency") === valueOf(taxRegistration, "reporting_currency"),
    `Tax Payment ${valueOf(payment, "id")} Ledger Book OE/currency scope 不一致`);
  }

  const channelAccount = requireRecord(records, "t_channel_account", "100051");
  const channelOperatingEntityId = valueOf(channelAccount, "entity_no");
  assert.ok(identifierEqual(channelOperatingEntityId, "AE001"),
    "Channel Account 必须冻结真实 Operating Entity");
  const channelPlans = recordsById(records, "t_channel_cost_plan");
  const channelBatches = recordsById(records, "t_channel_settle_batch");
  const channelSettleItems = recordsById(records, "t_channel_settle_item");
  const channelOpenItems = recordsById(records, "t_channel_open_item");
  const channelDebitItems = recordsById(records, "t_channel_debit_item");
  const channelAccounts = recordsById(records, "t_channel_account");
  const transactionMatches = recordsFor(records, "t_channel_transaction_match");
  const matchedOpenItemIds = integerSet(transactionMatches
    .filter((match) => valueOf(match, "target_type") === "OPEN_ITEM")
    .map((match) => valueOf(match, "open_item_id")));
  const matchedDebitItemIds = integerSet(transactionMatches
    .filter((match) => valueOf(match, "target_type") === "DEBIT_ITEM")
    .map((match) => valueOf(match, "debit_item_id")));
  const bindingVersions = recordsById(records, "t_cash_account_binding_ver");
  const bindings = recordsById(records, "t_cash_account_binding");
  const cashAccounts = recordsById(records, "t_cash_account");
  const bankItems = recordsById(records, "t_bank_statement_item");
  const bindingVersionsByBinding = groupByInteger(recordsFor(records, "t_cash_account_binding_ver"), "binding_id");
  for (const binding of recordsFor(records, "t_cash_account_binding")) {
    const bindingId = valueOf(binding, "id");
    const versions = bindingVersionsByBinding.get(bindingId) ?? [];
    for (const version of versions) {
      const cashAccount = cashAccounts.get(valueOf(version, "cash_account_id"));
      assert.ok(cashAccount
        && valueOf(cashAccount, "entity_type") === valueOf(binding, "entity_type")
        && identifierEqual(valueOf(cashAccount, "entity_id"), valueOf(binding, "entity_id"))
        && valueOf(cashAccount, "currency") === valueOf(binding, "currency"),
      `Cash Binding ${bindingId} Version ${valueOf(version, "id")} cash account scope 不一致`);
      assert.ok(isNull(valueOf(version, "effective_to"))
        || valueOf(version, "effective_from") < valueOf(version, "effective_to"),
      `Cash Binding ${bindingId} Version ${valueOf(version, "id")} effective window 非法`);
    }
    const effectiveVersions = versions.filter((version) => ["ACTIVE", "EXPIRED"].includes(valueOf(version, "status")));
    for (let leftIndex = 0; leftIndex < effectiveVersions.length; leftIndex += 1) {
      for (let rightIndex = leftIndex + 1; rightIndex < effectiveVersions.length; rightIndex += 1) {
        const left = effectiveVersions[leftIndex];
        const right = effectiveVersions[rightIndex];
        const leftEndsAfterRightStarts = isNull(valueOf(left, "effective_to"))
          || valueOf(right, "effective_from") < valueOf(left, "effective_to");
        const rightEndsAfterLeftStarts = isNull(valueOf(right, "effective_to"))
          || valueOf(left, "effective_from") < valueOf(right, "effective_to");
        assert.ok(!(leftEndsAfterRightStarts && rightEndsAfterLeftStarts),
          `Cash Binding ${bindingId} ACTIVE/effective Version windows overlap`);
      }
    }
    if (valueOf(binding, "status") === "ACTIVE") {
      const currentVersion = bindingVersions.get(valueOf(binding, "current_ver_id"));
      const currentCashAccount = currentVersion && cashAccounts.get(valueOf(currentVersion, "cash_account_id"));
      assert.ok(currentVersion
        && identifierEqual(valueOf(currentVersion, "binding_id"), bindingId)
        && valueOf(currentVersion, "status") === "ACTIVE"
        && currentCashAccount
        && valueOf(currentCashAccount, "status") === "ACTIVE",
      `Cash Binding ${bindingId} current_ver_id 必须指向自身唯一当前 ACTIVE Version/Account`);
    }
  }
  for (const scope of recordsFor(records, "t_channel_cost_scope")) {
    const planVersion = requireRecord(records, "t_channel_cost_plan_ver", valueOf(scope, "cost_ver_id"));
    const plan = channelPlans.get(valueOf(planVersion, "plan_id"));
    assert.ok(identifierEqual(valueOf(plan, "channel_id"), valueOf(channelAccount, "channel_id")),
      `Channel Cost Scope ${valueOf(scope, "id")} Channel 不一致`);
    assert.ok(identifierEqual(valueOf(scope, "mid_id"), valueOf(channelAccount, "mid_id")),
      `Channel Cost Scope ${valueOf(scope, "id")} MID 不匹配 Channel Account`);
    assert.ok(identifierEqual(valueOf(scope, "entity_no"), channelOperatingEntityId),
      `Channel Cost Scope ${valueOf(scope, "id")} OE tuple 不一致`);
    assert.equal(valueOf(scope, "currency"), valueOf(channelAccount, "currency"),
      `Channel Cost Scope ${valueOf(scope, "id")} currency tuple 不一致`);
  }
  for (const channelLedgerAccount of recordsFor(records, "t_account")
    .filter((record) => valueOf(record, "owner_type") === "CHANNEL_ACCOUNT")) {
    assert.ok(identifierEqual(valueOf(channelLedgerAccount, "owner_id"), valueOf(channelAccount, "id")),
      `Channel ledger Account ${valueOf(channelLedgerAccount, "id")} owner_id 不是 Channel Account PK`);
    const channelBook = ledgerBooks.get(valueOf(channelLedgerAccount, "ledger_book_id"));
    assert.ok(channelBook
      && valueOf(channelBook, "entity_type") === "OPERATING_ENTITY"
      && identifierEqual(valueOf(channelBook, "entity_id"), channelOperatingEntityId)
      && valueOf(channelBook, "currency") === valueOf(channelAccount, "currency"),
    `Channel ledger Account ${valueOf(channelLedgerAccount, "id")} Ledger Book OE/currency scope 不一致`);
  }
  const economicsById = recordsById(records, "t_transaction_economics");
  const openItems = recordsFor(records, "t_channel_open_item");
  for (const item of [...recordsFor(records, "t_channel_open_item"), ...recordsFor(records, "t_channel_debit_item")]) {
    const itemChannelAccount = channelAccounts.get(valueOf(item, "channel_account_id"));
    assert.ok(itemChannelAccount, `Channel Item ${valueOf(item, "id")} 缺少 Channel Account`);
    assert.ok(!isNull(valueOf(itemChannelAccount, "channel_id")) && !isNull(valueOf(itemChannelAccount, "mid_id")),
      `Channel Item ${valueOf(item, "id")} Channel/MID identity 不完整`);
    const economics = requireRecord(records, "t_transaction_economics", valueOf(item, "transaction_economics_id"));
    assert.ok(identifierEqual(valueOf(economics, "entity_no"), valueOf(itemChannelAccount, "entity_no")),
      `Channel Item ${valueOf(item, "id")} Economics OE tuple 不一致`);
    const matched = item.table === "t_channel_open_item"
      ? matchedOpenItemIds.has(identifierKey(valueOf(item, "id")))
      : matchedDebitItemIds.has(identifierKey(valueOf(item, "id")));
    if (!matched) {
      assert.equal(valueOf(economics, "currency"), valueOf(itemChannelAccount, "currency"),
        `Channel Item ${valueOf(item, "id")} Economics currency 与 Channel Account 不一致`);
    }
    if (item.table === "t_channel_open_item" && valueOf(item, "item_type") === "PAYMENT") {
      assert.equal(valueOf(economics, "economics_type"), "PAYMENT",
        `Channel Item ${valueOf(item, "id")} PAYMENT 未绑定 PAYMENT Economics`);
      assert.ok(identifierEqual(valueOf(item, "payment_intent_id"), valueOf(economics, "payment_intent_id"))
        && identifierEqual(valueOf(item, "payment_attempt_id"), valueOf(economics, "payment_attempt_id")),
      `Channel Item ${valueOf(item, "id")} Payment root tuple 不一致`);
    }
    if (item.table === "t_channel_debit_item" && valueOf(item, "debit_type") === "REFUND") {
      assert.equal(valueOf(economics, "economics_type"), "REFUND",
        `Channel Item ${valueOf(item, "id")} REFUND 未绑定 REFUND Economics`);
      assert.ok(identifierEqual(valueOf(item, "channel_refund_id"), valueOf(economics, "channel_refund_id")),
        `Channel Item ${valueOf(item, "id")} Refund root tuple 不一致`);
    }
    if (item.table === "t_channel_debit_item") {
      const parentEconomics = isNull(valueOf(economics, "parent_economics_id"))
        ? undefined
        : economicsById.get(valueOf(economics, "parent_economics_id"));
      const parentAccounts = parentEconomics
        ? openItems.filter((openItem) => identifierEqual(
          valueOf(openItem, "transaction_economics_id"), valueOf(parentEconomics, "id"),
        )).map((openItem) => identifierKey(valueOf(openItem, "channel_account_id")))
        : [];
      assert.ok(parentAccounts.includes(identifierKey(valueOf(item, "channel_account_id"))),
        `Channel Debit Item ${valueOf(item, "id")} Channel Account 与来源 Economics/原交易 Channel+MID 不一致`);
    }
  }
  for (const batch of recordsFor(records, "t_channel_settle_batch")) {
    assert.ok(identifierEqual(valueOf(batch, "channel_account_id"), valueOf(channelAccount, "id")));
    assert.equal(valueOf(batch, "currency"), valueOf(channelAccount, "currency"),
      `Channel Batch ${valueOf(batch, "id")} currency tuple 不一致`);
  }
  for (const match of transactionMatches) {
    const settleItem = channelSettleItems.get(valueOf(match, "settle_item_id"));
    const batch = settleItem && channelBatches.get(valueOf(settleItem, "batch_id"));
    assert.ok(settleItem && batch, `Channel Transaction Match ${valueOf(match, "id")} 缺少 Batch/Item`);
    const target = valueOf(match, "target_type") === "OPEN_ITEM"
      ? channelOpenItems.get(valueOf(match, "open_item_id"))
      : channelDebitItems.get(valueOf(match, "debit_item_id"));
    assert.ok(target, `Channel Transaction Match ${valueOf(match, "id")} 缺少目标 Item`);
    assert.ok(identifierEqual(valueOf(target, "channel_account_id"), valueOf(batch, "channel_account_id")),
      `Channel Transaction Match ${valueOf(match, "id")} Channel Account tuple 不一致`);
    const economics = requireRecord(records, "t_transaction_economics", valueOf(target, "transaction_economics_id"));
    assert.ok(identifierEqual(valueOf(economics, "entity_no"), channelOperatingEntityId),
      `Channel Transaction Match ${valueOf(match, "id")} OE tuple 不一致`);
    assert.equal(valueOf(economics, "currency"), valueOf(batch, "currency"),
      `Channel Transaction Match ${valueOf(match, "id")} economics currency 与 Batch 不一致`);
    const expectedTargetKey = `${valueOf(match, "target_type")}:${identifierKey(valueOf(target, "id"))}`;
    assert.equal(valueOf(match, "target_key"), expectedTargetKey,
      `Channel Transaction Match ${valueOf(match, "id")} target_key 不一致`);
    assert.equal(valueOf(settleItem, "reference_type"), valueOf(target, "reference_type"),
      `Channel Transaction Match ${valueOf(match, "id")} reference_type 不一致`);
    assert.equal(valueOf(settleItem, "external_transaction_id"), valueOf(target, "external_transaction_id"),
      `Channel Transaction Match ${valueOf(match, "id")} external_transaction_id 与目标 Item 不一致`);
    assert.equal(valueOf(settleItem, "channel_item_ref"), valueOf(target, "external_transaction_id"),
      `Channel Transaction Match ${valueOf(match, "id")} channel_item_ref 与目标 Item 不一致`);
    if (valueOf(match, "target_type") === "OPEN_ITEM") {
      assert.equal(valueOf(settleItem, "settlement_direction"), "CREDIT",
        `Channel Transaction Match ${valueOf(match, "id")} Open Item direction 不一致`);
      assert.equal(valueOf(settleItem, "item_type"), valueOf(target, "item_type"),
        `Channel Transaction Match ${valueOf(match, "id")} Open Item type 不一致`);
    } else {
      const expectedItemType = new Map([
        ["REFUND", "REFUND"],
        ["DUPLICATE_COMPENSATION", "DUPLICATE_CAPTURE"],
        ["DISPUTE", "DISPUTE"],
      ]).get(valueOf(target, "debit_type"));
      assert.equal(valueOf(settleItem, "settlement_direction"), "DEBIT",
        `Channel Transaction Match ${valueOf(match, "id")} Debit Item direction 不一致`);
      assert.equal(valueOf(settleItem, "item_type"), expectedItemType,
        `Channel Transaction Match ${valueOf(match, "id")} Debit Item type 不一致`);
    }
  }
  const matchesByAllocationTarget = Map.groupBy(transactionMatches, (match) => [
    identifierKey(valueOf(match, "settle_item_id")),
    valueOf(match, "target_type"),
    valueOf(match, "target_key"),
  ].join(":"));
  for (const [targetKey, targetMatches] of matchesByAllocationTarget) {
    const allocationNumbers = targetMatches
      .map((match) => BigInt(identifierKey(valueOf(match, "allocation_no"))))
      .toSorted((left, right) => left < right ? -1 : left > right ? 1 : 0);
    for (let index = 0; index < allocationNumbers.length; index += 1) {
      assert.equal(allocationNumbers[index], BigInt(index + 1),
        `Channel Transaction Match ${targetKey} allocation_no 必须从 1 连续递增`);
    }
  }
  const transactionMatchesByItem = groupByInteger(transactionMatches, "settle_item_id");
  const transactionItemTypes = new Set(["PAYMENT", "DUPLICATE_CAPTURE", "REFUND", "DISPUTE"]);
  for (const item of recordsFor(records, "t_channel_settle_item")
    .filter((record) => transactionItemTypes.has(valueOf(record, "item_type")))) {
    const itemMatches = transactionMatchesByItem.get(valueOf(item, "id")) ?? [];
    assert.ok(itemMatches.length > 0,
      `Channel Settlement Item ${valueOf(item, "id")} is an orphan: transaction Item 缺少 Transaction Match`);
    assert.ok(fixedEqual(fixedSumRecords(itemMatches, "gross_amount"), fixedValue(item, "gross_amount"))
      && fixedEqual(fixedSumRecords(itemMatches, "fee_amount"), fixedValue(item, "fee_amount"))
      && fixedEqual(fixedSumRecords(itemMatches, "net_amount"), fixedValue(item, "net_amount")),
    `Channel Settlement Item ${valueOf(item, "id")} matched gross/fee/net 未精确重放权威 Item 金额`);
  }
  const matchesByOpenTarget = Map.groupBy(
    transactionMatches.filter((match) => valueOf(match, "target_type") === "OPEN_ITEM"),
    (match) => identifierKey(valueOf(match, "open_item_id")),
  );
  const matchesByDebitTarget = Map.groupBy(
    transactionMatches.filter((match) => valueOf(match, "target_type") === "DEBIT_ITEM"),
    (match) => identifierKey(valueOf(match, "debit_item_id")),
  );
  const validateTargetSettlement = (item, itemLabel, totalField, targetMatches) => {
    const settled = fixedValue(item, "settled_amount");
    const total = fixedValue(item, totalField);
    const matchedGross = fixedSumRecords(targetMatches, "gross_amount");
    assert.ok(fixedEqual(matchedGross, settled),
      `Channel ${itemLabel} ${valueOf(item, "id")} Match gross 累计必须精确等于 settled_amount`);
    assert.ok(fixedEqual(fixedSubtract(total, matchedGross), fixedValue(item, "outstanding_amount")),
      `Channel ${itemLabel} ${valueOf(item, "id")} Match gross 累计与 outstanding_amount 不闭合`);
    if (valueOf(item, "status") === "EXCEPTION") return;
    const expectedStatus = fixedEqual(matchedGross, EXACT_ZERO)
      ? "OPEN"
      : fixedEqual(matchedGross, total) ? "SETTLED" : "PARTIALLY_SETTLED";
    assert.equal(valueOf(item, "status"), expectedStatus,
      `Channel ${itemLabel} ${valueOf(item, "id")} Match 累计与 status 不一致`);
  };
  for (const item of channelOpenItems.values()) {
    validateTargetSettlement(item, "Open Item", "gross_receivable",
      matchesByOpenTarget.get(identifierKey(valueOf(item, "id"))) ?? []);
  }
  for (const item of channelDebitItems.values()) {
    validateTargetSettlement(item, "Debit Item", "expected_amount",
      matchesByDebitTarget.get(identifierKey(valueOf(item, "id"))) ?? []);
  }

  const settleItemsByBatch = groupByInteger(recordsFor(records, "t_channel_settle_item"), "batch_id");
  for (const batch of recordsFor(records, "t_channel_settle_batch")) {
    const batchItems = settleItemsByBatch.get(valueOf(batch, "id")) ?? [];
    assert.ok(batchItems.length > 0, `Channel Batch ${valueOf(batch, "id")} 缺少原始 Settlement Item`);
    const itemNumbers = batchItems
      .map((item) => BigInt(identifierKey(valueOf(item, "item_no"))))
      .toSorted((left, right) => left < right ? -1 : left > right ? 1 : 0);
    for (let index = 0; index < itemNumbers.length; index += 1) {
      assert.equal(itemNumbers[index], BigInt(index + 1),
        `Channel Batch ${valueOf(batch, "id")} item_no 必须从 1 连续递增`);
    }
    for (const item of batchItems) {
      const gross = fixedValue(item, "gross_amount");
      const fee = fixedValue(item, "fee_amount");
      const expectedNet = valueOf(item, "settlement_direction") === "CREDIT"
        ? fixedSubtract(gross, fee)
        : fixedAdd(gross, fee);
      assert.ok(fixedCompare(expectedNet, EXACT_ZERO) >= 0 && fixedEqual(expectedNet, fixedValue(item, "net_amount")),
        `Channel Settlement Item ${valueOf(item, "id")} ${valueOf(item, "settlement_direction")} 行金额方程不成立`);
    }
    const creditItems = batchItems.filter((item) => valueOf(item, "settlement_direction") === "CREDIT");
    const debitItems = batchItems.filter((item) => valueOf(item, "settlement_direction") === "DEBIT");
    const gross = fixedSumRecords(creditItems, "gross_amount");
    const fee = fixedSumRecords(batchItems, "fee_amount");
    const debit = fixedSumRecords(debitItems, "gross_amount");
    const signedNet = fixedSubtract(fixedSubtract(gross, fee), debit);
    const expectedDirection = fixedCompare(signedNet, EXACT_ZERO) > 0
      ? "CREDIT"
      : fixedCompare(signedNet, EXACT_ZERO) < 0 ? "DEBIT" : "NONE";
    assert.ok(fixedEqual(gross, fixedValue(batch, "gross_amount"))
      && fixedEqual(fee, fixedValue(batch, "fee_amount"))
      && fixedEqual(debit, fixedValue(batch, "debit_amount"))
      && fixedEqual(fixedAbs(signedNet), fixedValue(batch, "net_amount"))
      && valueOf(batch, "cash_direction") === expectedDirection,
    `Channel Batch ${valueOf(batch, "id")} 全部 Item 未精确回放四个金额与 cash_direction`);
  }
  const channelCostAllocations = recordsFor(records, "t_channel_cost_alloc");
  for (const allocation of channelCostAllocations) {
    const economics = requireRecord(records, "t_transaction_economics", valueOf(allocation, "transaction_economics_id"));
    const paymentTarget = valueOf(economics, "economics_type") === "PAYMENT"
      && identifierEqual(valueOf(allocation, "payment_attempt_id"), valueOf(economics, "payment_attempt_id"))
      && isNull(valueOf(allocation, "channel_refund_id"))
      && isNull(valueOf(allocation, "channel_dispute_notice_id"))
      && isNull(valueOf(allocation, "dispute_case_id"));
    const refundTarget = valueOf(economics, "economics_type") === "REFUND"
      && isNull(valueOf(allocation, "payment_attempt_id"))
      && identifierEqual(valueOf(allocation, "channel_refund_id"), valueOf(economics, "channel_refund_id"))
      && isNull(valueOf(allocation, "channel_dispute_notice_id"))
      && isNull(valueOf(allocation, "dispute_case_id"));
    const disputeTarget = valueOf(economics, "economics_type") === "DISPUTE"
      && isNull(valueOf(allocation, "payment_attempt_id"))
      && isNull(valueOf(allocation, "channel_refund_id"))
      && identifierEqual(valueOf(allocation, "channel_dispute_notice_id"), valueOf(economics, "channel_dispute_notice_id"))
      && identifierEqual(valueOf(allocation, "dispute_case_id"), valueOf(economics, "dispute_case_id"));
    assert.ok(paymentTarget || refundTarget || disputeTarget,
      `Channel Cost Alloc ${valueOf(allocation, "id")} target tuple 与 Economics 类型/Root 不一致`);
  }
  const allocationsByConfirm = groupByInteger(channelCostAllocations, "cost_confirm_id");
  const matchedEconomicsByBatch = new Map();
  for (const match of transactionMatches) {
    const settleItem = channelSettleItems.get(valueOf(match, "settle_item_id"));
    const target = valueOf(match, "target_type") === "OPEN_ITEM"
      ? channelOpenItems.get(valueOf(match, "open_item_id"))
      : channelDebitItems.get(valueOf(match, "debit_item_id"));
    const batchKey = identifierKey(valueOf(settleItem, "batch_id"));
    if (!matchedEconomicsByBatch.has(batchKey)) matchedEconomicsByBatch.set(batchKey, new Set());
    matchedEconomicsByBatch.get(batchKey).add(identifierKey(valueOf(target, "transaction_economics_id")));
  }
  const completeCostConfirmIds = new Set();
  for (const confirmation of recordsFor(records, "t_channel_cost_confirm")) {
    const settleItem = requireRecord(records, "t_channel_settle_item", valueOf(confirmation, "channel_settle_item_id"));
    const batch = requireRecord(records, "t_channel_settle_batch", valueOf(settleItem, "batch_id"));
    const allocations = allocationsByConfirm.get(valueOf(confirmation, "id")) ?? [];
    assert.ok(allocations.length > 0,
      `Channel Cost Confirm ${valueOf(confirmation, "id")} 缺少 allocation`);
    const allocationTotal = fixedAdd(...allocations.map((allocation) => fixedAdd(
      fixedValue(allocation, "allocated_amount"),
      fixedValue(allocation, "rounding_amount"),
    )));
    assert.ok(fixedEqual(allocationTotal, fixedValue(confirmation, "actual_cost")),
      `Channel Cost Confirm ${valueOf(confirmation, "id")} allocation 合计不等于 actual_cost`);
    assert.ok(fixedRecordEqual(confirmation, "actual_cost", settleItem, "fee_amount"),
      `Channel Cost Confirm ${valueOf(confirmation, "id")} 未精确重放 Settlement Item fee_amount`);
    if (["FEE", "ADJUSTMENT"].includes(valueOf(settleItem, "item_type"))) {
      assert.ok(fixedEqual(fixedValue(settleItem, "gross_amount"), EXACT_ZERO)
        && fixedRecordEqual(confirmation, "actual_cost", settleItem, "net_amount"),
      `Channel Cost Confirm ${valueOf(confirmation, "id")} FEE Item 必须以 actual_cost 精确重放 fee/net`);
    }
    const allowedEconomics = matchedEconomicsByBatch.get(identifierKey(valueOf(batch, "id"))) ?? new Set();
    for (const allocation of allocations) {
      assert.ok(allowedEconomics.has(identifierKey(valueOf(allocation, "transaction_economics_id"))),
        `Channel Cost Alloc ${valueOf(allocation, "id")} 必须属于同 Batch Transaction Match 目标 Economics`);
    }
    completeCostConfirmIds.add(identifierKey(valueOf(confirmation, "id")));
  }
  const costConfirmsByItem = groupByInteger(recordsFor(records, "t_channel_cost_confirm"), "channel_settle_item_id");
  const reconResolutions = recordsById(records, "t_recon_resolution");
  const adjustmentExceptionTypes = new Set([
    "UNMATCHED", "SHORT_AMOUNT", "EXCESS_AMOUNT", "FEE_DIFFERENCE", "DATE_DIFFERENCE", "WRONG_ACCOUNT", "WRONG_CURRENCY",
  ]);
  const completeValidatedBatchIds = new Set();
  for (const batch of recordsFor(records, "t_channel_settle_batch")
    .filter((record) => valueOf(record, "status") === "VALIDATED")) {
    const batchItems = recordsFor(records, "t_channel_settle_item")
      .filter((item) => identifierEqual(valueOf(item, "batch_id"), valueOf(batch, "id")));
    assert.ok(batchItems.length > 0, `Channel Batch ${valueOf(batch, "id")} VALIDATED 但无 Settlement Item`);
    for (const item of batchItems) {
      const itemId = valueOf(item, "id");
      const itemMatches = transactionMatchesByItem.get(itemId) ?? [];
      const costConfirms = costConfirmsByItem.get(itemId) ?? [];
      assert.ok(costConfirms.length <= 1,
        `Channel Settlement Item ${itemId} Cost Confirm 必须唯一`);
      const itemExceptions = recordsFor(records, "t_recon_exception").filter((exception) => (
        valueOf(exception, "source_type") === "CHANNEL_SETTLE_ITEM"
        && identifierEqual(valueOf(exception, "source_id"), itemId)
      ));
      const completeExceptions = itemExceptions.filter((exception) => {
        const allowedType = valueOf(item, "item_type") === "FEE"
          ? valueOf(exception, "exception_type") === "FEE_DIFFERENCE"
          : adjustmentExceptionTypes.has(valueOf(exception, "exception_type"));
        assert.ok(allowedType,
          `Channel Settlement Item ${itemId} Exception type 不属于批准的 item-specific 类型`);
        assert.equal(valueOf(exception, "entity_type"), "OPERATING_ENTITY",
          `Channel Settlement Item ${itemId} Exception OE scope 不一致`);
        assert.ok(identifierEqual(valueOf(exception, "entity_id"), valueOf(channelAccount, "entity_no")),
          `Channel Settlement Item ${itemId} Exception OE scope 不一致`);
        assert.equal(valueOf(exception, "currency"), valueOf(batch, "currency"),
          `Channel Settlement Item ${itemId} Exception currency 不一致`);
        assert.ok(fixedRecordEqual(exception, "difference_amount", item, "net_amount"),
          `Channel Settlement Item ${itemId} Exception difference_amount 未完整处置 Item`);
        if (!["RESOLVED", "CLOSED"].includes(valueOf(exception, "status"))) return false;
        const resolution = reconResolutions.get(valueOf(exception, "applied_resolution_id"));
        return resolution
          && identifierEqual(valueOf(resolution, "exception_id"), valueOf(exception, "id"))
          && identifierEqual(valueOf(exception, "applied_resolution_id"), valueOf(resolution, "id"))
          && !identifierEqual(valueOf(resolution, "maker_id"), valueOf(resolution, "checker_id"))
          && valueOf(resolution, "checker_decision") === "APPROVED"
          && valueOf(resolution, "approved_content_hash") === valueOf(resolution, "content_hash")
          && valueOf(resolution, "status") === "APPLIED";
      });
      const blockingExceptions = itemExceptions.filter((exception) => !completeExceptions.includes(exception));
      assert.equal(blockingExceptions.length, 0,
        `Channel Settlement Item ${itemId} unresolved/blocking Exception 阻断 VALIDATED Batch`);
      if (transactionItemTypes.has(valueOf(item, "item_type"))) {
        assert.ok(itemMatches.length > 0,
          `Channel Settlement Item ${itemId} is an orphan: transaction Item 缺少 Transaction Match`);
        assert.ok(fixedEqual(fixedSumRecords(itemMatches, "gross_amount"), fixedValue(item, "gross_amount"))
          && fixedEqual(fixedSumRecords(itemMatches, "fee_amount"), fixedValue(item, "fee_amount"))
          && fixedEqual(fixedSumRecords(itemMatches, "net_amount"), fixedValue(item, "net_amount")),
        `Channel Settlement Item ${itemId} matched gross/fee/net 未精确重放权威 Item 金额`);
        assert.equal(itemExceptions.length, 0,
          `Channel Settlement Item ${itemId} 已匹配交易行不得同时存在任何 Exception`);
        if (fixedCompare(fixedValue(item, "fee_amount"), EXACT_ZERO) > 0) {
          assert.ok(costConfirms.length === 1
            && completeCostConfirmIds.has(identifierKey(valueOf(costConfirms[0], "id"))),
          `Channel Settlement Item ${itemId} 实际 Channel Cost Confirm 不完整`);
        }
        continue;
      }
      assert.equal(itemMatches.length, 0,
        `Channel Settlement Item ${itemId} FEE/ADJUSTMENT 不得伪造 Transaction Match`);
      const costComplete = costConfirms.length === 1
        && completeCostConfirmIds.has(identifierKey(valueOf(costConfirms[0], "id")));
      const exceptionComplete = itemExceptions.length === 1 && completeExceptions.length === 1;
      assert.ok(costComplete !== exceptionComplete,
        `Channel Settlement Item ${itemId} is an orphan or ambiguous: FEE/ADJUSTMENT 必须由 Cost Confirm/Alloc 或 controlled Exception 精确处置`);
    }
    completeValidatedBatchIds.add(identifierKey(valueOf(batch, "id")));
  }
  for (const match of recordsFor(records, "t_bank_recon_match")
    .filter((record) => ["CHANNEL_CREDIT", "CHANNEL_DEBIT"].includes(valueOf(record, "match_type"))
      && valueOf(record, "status") === "CONFIRMED")) {
    const batch = channelBatches.get(valueOf(match, "channel_batch_id"));
    const batchChannelAccount = batch && channelAccounts.get(valueOf(batch, "channel_account_id"));
    const bindingVersion = bindingVersions.get(valueOf(match, "cash_account_binding_ver_id"));
    const binding = bindingVersion && bindings.get(valueOf(bindingVersion, "binding_id"));
    const bankItem = bankItems.get(valueOf(match, "bank_item_id"));
    const cashAccount = bindingVersion && cashAccounts.get(valueOf(bindingVersion, "cash_account_id"));
    assert.ok(batch && batchChannelAccount && bindingVersion && binding && bankItem && cashAccount,
      `Channel Bank Match ${valueOf(match, "id")} tuple 引用不完整`);
    assert.ok(valueOf(batch, "status") === "VALIDATED"
      && completeValidatedBatchIds.has(identifierKey(valueOf(batch, "id"))),
    `Channel Bank Match ${valueOf(match, "id")} 必须引用 complete VALIDATED Batch`);
    const decisionTime = valueOf(match, "decision_time");
    assert.ok(["ACTIVE", "EXPIRED"].includes(valueOf(bindingVersion, "status"))
      && valueOf(bindingVersion, "effective_from") <= decisionTime
      && (isNull(valueOf(bindingVersion, "effective_to")) || decisionTime < valueOf(bindingVersion, "effective_to")),
    `Channel Bank Match ${valueOf(match, "id")} Binding Version 在 decision_time 无效`);
    assert.equal(valueOf(binding, "account_purpose"), "CHANNEL_SETTLEMENT",
      `Channel Bank Match ${valueOf(match, "id")} 必须使用 CHANNEL_SETTLEMENT Binding`);
    assert.equal(valueOf(binding, "entity_type"), "OPERATING_ENTITY",
      `Channel Bank Match ${valueOf(match, "id")} Binding entity_type 不一致`);
    assert.ok(identifierEqual(valueOf(binding, "entity_id"), valueOf(batchChannelAccount, "entity_no")),
      `Channel Bank Match ${valueOf(match, "id")} Binding OE 不一致`);
    assert.equal(valueOf(binding, "currency"), valueOf(batch, "currency"),
      `Channel Bank Match ${valueOf(match, "id")} Binding currency 不一致`);
    assert.equal(valueOf(cashAccount, "entity_type"), "OPERATING_ENTITY",
      `Channel Bank Match ${valueOf(match, "id")} Cash Account entity_type 不一致`);
    assert.ok(identifierEqual(valueOf(cashAccount, "entity_id"), valueOf(batchChannelAccount, "entity_no")),
      `Channel Bank Match ${valueOf(match, "id")} Cash Account OE 不一致`);
    assert.equal(valueOf(cashAccount, "currency"), valueOf(batch, "currency"),
      `Channel Bank Match ${valueOf(match, "id")} Cash Account currency 不一致`);
    assert.ok(identifierEqual(valueOf(bankItem, "cash_account_id"), valueOf(cashAccount, "id")),
      `Channel Bank Match ${valueOf(match, "id")} Bank Item Cash Account 不一致`);
    assert.equal(valueOf(bankItem, "direction"), valueOf(batch, "cash_direction"),
      `Channel Bank Match ${valueOf(match, "id")} direction 与 Batch 不一致`);
    const expectedMatchType = valueOf(batch, "cash_direction") === "CREDIT"
      ? "CHANNEL_CREDIT"
      : "CHANNEL_DEBIT";
    assert.equal(valueOf(match, "match_type"), expectedMatchType,
      `Channel Bank Match ${valueOf(match, "id")} match_type 与 Batch cash_direction 不一致`);
    assert.ok(fixedRecordEqual(match, "matched_amount", batch, "net_amount"),
      `Channel Bank Match ${valueOf(match, "id")} 金额与 Batch net 不一致`);
  }
  let allocatedCost = fixedLiteral("0", Number(decimalField("t_channel_cost_alloc", "allocated_amount").scale));
  for (const confirmation of recordsFor(records, "t_channel_cost_confirm")) {
    const settleItem = requireRecord(records, "t_channel_settle_item", valueOf(confirmation, "channel_settle_item_id"));
    const batch = requireRecord(records, "t_channel_settle_batch", valueOf(settleItem, "batch_id"));
    assert.ok(identifierEqual(valueOf(batch, "channel_account_id"), valueOf(channelAccount, "id")));
    const batchChannelAccount = channelAccounts.get(valueOf(batch, "channel_account_id"));
    const allocations = recordsFor(records, "t_channel_cost_alloc")
      .filter((record) => identifierEqual(valueOf(record, "cost_confirm_id"), valueOf(confirmation, "id")));
    assert.ok(allocations.length > 0, `Channel Cost Confirm ${valueOf(confirmation, "id")} 缺少 allocation`);
    for (const allocation of allocations) {
      const economicsItems = [...channelOpenItems.values(), ...channelDebitItems.values()]
        .filter((item) => identifierEqual(
          valueOf(item, "transaction_economics_id"), valueOf(allocation, "transaction_economics_id"),
        ));
      assert.ok(economicsItems.some((item) => identifierEqual(
        valueOf(item, "channel_account_id"), valueOf(batchChannelAccount, "id"),
      )), `Channel Cost Alloc ${valueOf(allocation, "id")} Economics/Confirm/Batch Channel Account 不一致`);
      const matchingScopes = recordsFor(records, "t_channel_cost_scope").filter((scope) => {
        const version = requireRecord(records, "t_channel_cost_plan_ver", valueOf(scope, "cost_ver_id"));
        const plan = channelPlans.get(valueOf(version, "plan_id"));
        return identifierEqual(valueOf(plan, "channel_id"), valueOf(batchChannelAccount, "channel_id"))
          && identifierEqual(valueOf(scope, "mid_id"), valueOf(batchChannelAccount, "mid_id"))
          && identifierEqual(valueOf(scope, "entity_no"), valueOf(batchChannelAccount, "entity_no"))
          && valueOf(scope, "currency") === valueOf(batchChannelAccount, "currency");
      });
      assert.equal(matchingScopes.length, 1,
        `Channel Cost Alloc ${valueOf(allocation, "id")} 必须唯一解析同 Channel/MID/OE/currency Cost Scope`);
    }
    const allocationTotal = fixedAdd(...allocations.map((allocation) => fixedAdd(
      fixedValue(allocation, "allocated_amount"),
      fixedValue(allocation, "rounding_amount"),
    )));
    assert.ok(fixedEqual(allocationTotal, fixedValue(confirmation, "actual_cost")),
      `Channel Cost allocation ${fixedFormat(allocationTotal)} 不等于 actual_cost ${valueOf(confirmation, "actual_cost")}`);
    allocatedCost = fixedAdd(allocatedCost, allocationTotal);
  }
  return {
    taxOperatingEntityId,
    taxAuthorityId: valueOf(taxAuthority, "id"),
    channelOperatingEntityId,
    channelAccountId: valueOf(channelAccount, "id"),
    allocatedCost: fixedFormat(allocatedCost),
  };
}

function validateApprovalPairs(approvalRecords) {
  const approvedGroups = Map.groupBy(
    approvalRecords.filter((record) => valueOf(record, "decision") === "APPROVED"),
    (record) => [identifierKey(valueOf(record, "payout_id")), valueOf(record, "action_type"),
      identifierKey(valueOf(record, "action_no"))].join(":"),
  );
  const frozenInputs = ["payout_id", "action_type", "action_no", "decision", "requested_from_status",
    "reason_code", "evidence_ref_id", "approved_content_hash"];
  for (const [groupKey, group] of approvedGroups) {
    const maker = group.filter((record) => valueOf(record, "approval_role") === "MAKER");
    const checker = group.filter((record) => valueOf(record, "approval_role") === "CHECKER");
    assert.equal(maker.length, 1, `${groupKey} 必须有唯一 MAKER 与 CHECKER`);
    assert.equal(checker.length, 1, `${groupKey} 必须有唯一 MAKER 与 CHECKER`);
    for (const field of frozenInputs) {
      if (["payout_id", "action_no", "evidence_ref_id"].includes(field) && !isNull(valueOf(maker[0], field))) {
        assert.ok(identifierEqual(valueOf(maker[0], field), valueOf(checker[0], field)), `${groupKey} ${field} 不一致`);
      } else {
        assert.equal(valueOf(maker[0], field), valueOf(checker[0], field), `${groupKey} ${field} 不一致`);
      }
    }
    assert.ok(!identifierEqual(valueOf(maker[0], "actor_id"), valueOf(checker[0], "actor_id")),
      `${groupKey} Maker/Checker actor 必须不同`);
  }
  return approvedGroups.size;
}

function validatePayoutApprovalEvents(records) {
  const approvals = recordsFor(records, "t_payout_approval");
  const payouts = recordsById(records, "t_payout");
  const events = recordsFor(records, "t_payout_event");
  const approvedEvents = events.filter((event) => valueOf(event, "event_scope") === "PAYOUT"
    && valueOf(event, "to_status") === "APPROVED");
  for (const event of approvedEvents) {
    const eventId = valueOf(event, "id");
    const payoutId = valueOf(event, "payout_id");
    const checker = approvals.find((approval) => identifierEqual(
      valueOf(approval, "id"), valueOf(event, "approval_id"),
    ));
    const validChecker = checker
      && identifierEqual(valueOf(checker, "payout_id"), payoutId)
      && valueOf(checker, "action_type") === "PAYOUT_APPROVAL"
      && valueOf(checker, "approval_role") === "CHECKER"
      && valueOf(checker, "decision") === "APPROVED"
      && valueOf(checker, "requested_from_status") === valueOf(event, "from_status");
    assert.ok(validChecker,
      `Payout Event ${eventId} APPROVED 必须绑定 Checker approval 的同 action_no 冻结决定`);
    const pair = approvals.filter((approval) => (
      identifierEqual(valueOf(approval, "payout_id"), payoutId)
      && valueOf(approval, "action_type") === "PAYOUT_APPROVAL"
      && identifierEqual(valueOf(approval, "action_no"), valueOf(checker, "action_no"))
      && valueOf(approval, "decision") === "APPROVED"
    ));
    assert.equal(pair.filter((approval) => valueOf(approval, "approval_role") === "MAKER").length, 1,
      `Payout Event ${eventId} APPROVED Checker 缺少同 action_no Maker`);
    assert.equal(pair.filter((approval) => valueOf(approval, "approval_role") === "CHECKER").length, 1,
      `Payout Event ${eventId} APPROVED Checker action_no 不唯一`);
    assert.equal(valueOf(event, "source_type"), "PAYOUT_APPROVAL",
      `Payout Event ${eventId} APPROVED source_type 必须为 PAYOUT_APPROVAL`);
    assert.ok(identifierEqual(valueOf(event, "source_id"), valueOf(checker, "id")),
      `Payout Event ${eventId} APPROVED source_id 必须指向 Checker`);
    assert.ok(identifierEqual(valueOf(event, "actor_id"), valueOf(checker, "actor_id")),
      `Payout Event ${eventId} APPROVED actor_id 必须等于 Checker actor`);
    assert.equal(valueOf(event, "occurred_time"), valueOf(checker, "decision_time"),
      `Payout Event ${eventId} APPROVED 时间必须等于 Checker decision_time`);
    const payout = payouts.get(payoutId);
    assert.ok(payout, `Payout Event ${eventId} 缺少 Root ${payoutId}`);
    assert.equal(valueOf(payout, "approval_hash"), valueOf(checker, "approved_content_hash"),
      `Payout Event ${eventId} APPROVED hash 与 Root/Checker 不一致`);
    assert.equal(valueOf(payout, "approved_time"), valueOf(event, "occurred_time"),
      `Payout Event ${eventId} APPROVED 时间与 Root 不一致`);
  }
  for (const checker of approvals.filter((approval) => valueOf(approval, "action_type") === "PAYOUT_APPROVAL"
    && valueOf(approval, "approval_role") === "CHECKER" && valueOf(approval, "decision") === "APPROVED")) {
    const bound = approvedEvents.filter((event) => identifierEqual(
      valueOf(event, "approval_id"), valueOf(checker, "id"),
    ));
    assert.equal(bound.length, 1,
      `Payout Approval Checker ${valueOf(checker, "id")} 必须被唯一 APPROVED Event 引用`);
  }
  return approvedEvents.length;
}

function validatePayoutEventAuthority(records) {
  const payouts = recordsById(records, "t_payout");
  const executions = recordsById(records, "t_payout_execution");
  const approvals = recordsById(records, "t_payout_approval");
  const evidenceRefs = recordsById(records, "t_payout_evidence_ref");
  const bankMatches = recordsById(records, "t_bank_recon_match");
  for (const event of recordsFor(records, "t_payout_event")) {
    const eventId = valueOf(event, "id");
    const payout = payouts.get(valueOf(event, "payout_id"));
    assert.ok(payout, `Payout Event ${eventId} authority 缺少 Payout Root`);
    const scopedExecution = isNull(valueOf(event, "execution_id"))
      ? undefined
      : executions.get(valueOf(event, "execution_id"));
    if (!isNull(valueOf(event, "execution_id"))) {
      assert.ok(scopedExecution && identifierEqual(valueOf(scopedExecution, "payout_id"), valueOf(payout, "id")),
        `Payout Event ${eventId} authority execution_id 指向另一 Payout`);
    }

    if (valueOf(event, "source_type") === "PAYOUT_APPROVAL") {
      const approval = approvals.get(valueOf(event, "source_id"));
      assert.ok(approval
        && identifierEqual(valueOf(approval, "payout_id"), valueOf(payout, "id"))
        && identifierEqual(valueOf(event, "approval_id"), valueOf(approval, "id")),
      `Payout Event ${eventId} authority Approval 元组不属于同一 Payout`);
    }
    if (valueOf(event, "source_type") === "PAYOUT_EXECUTION") {
      const sourceExecution = executions.get(valueOf(event, "source_id"));
      assert.ok(sourceExecution && identifierEqual(valueOf(sourceExecution, "payout_id"), valueOf(payout, "id")),
        `Payout Event ${eventId} authority source Execution 指向另一 Payout`);
    }
    if (valueOf(event, "source_type") !== "BANK_RECON_MATCH") continue;

    const match = bankMatches.get(valueOf(event, "source_id"));
    const matchExecution = match && executions.get(valueOf(match, "payout_execution_id"));
    const targetExecution = scopedExecution ?? matchExecution;
    const evidence = isNull(valueOf(event, "evidence_ref_id"))
      ? undefined
      : evidenceRefs.get(valueOf(event, "evidence_ref_id"));
    const validAuthority = match
      && valueOf(match, "status") === "CONFIRMED"
      && valueOf(match, "match_type") === "PAYOUT_DEBIT"
      && identifierEqual(valueOf(event, "bank_recon_match_id"), valueOf(match, "id"))
      && matchExecution
      && identifierEqual(valueOf(matchExecution, "payout_id"), valueOf(payout, "id"))
      && targetExecution
      && identifierEqual(valueOf(targetExecution, "id"), valueOf(matchExecution, "id"))
      && (!evidence || identifierEqual(valueOf(evidence, "payout_execution_id"), valueOf(matchExecution, "id")));
    assert.ok(validAuthority, `Payout Event ${eventId} BANK_RECON_MATCH authority 元组不完整或跨 Payout/Execution`);
    if (valueOf(event, "event_scope") === "PAYOUT" && valueOf(event, "to_status") === "PAID") {
      assert.ok(evidence && identifierEqual(valueOf(event, "journal_entry_id"), valueOf(payout, "journal_entry_id")),
        `Payout Event ${eventId} PAID authority 必须冻结自身 Evidence 与 final Journal`);
    }
  }
}

function validateRetryRequiredEvents(records) {
  const events = recordsFor(records, "t_payout_event");
  const executions = recordsById(records, "t_payout_execution");
  const evidence = recordsById(records, "t_payout_evidence_ref");
  const retryEvents = events.filter((event) => valueOf(event, "event_scope") === "PAYOUT"
    && ["PAYING", "PAYMENT_UNKNOWN"].includes(valueOf(event, "from_status"))
    && valueOf(event, "to_status") === "RETRY_REQUIRED");
  for (const retryEvent of retryEvents) {
    const eventId = valueOf(retryEvent, "id");
    const decisiveCandidates = events.filter((event) => (
      valueOf(event, "event_scope") === "EXECUTION"
      && identifierEqual(valueOf(event, "payout_id"), valueOf(retryEvent, "payout_id"))
      && ["SUBMITTED", "UNKNOWN"].includes(valueOf(event, "from_status"))
      && valueOf(event, "to_status") === "FAILED"
      && numberOf(event, "event_no") < numberOf(retryEvent, "event_no")
    )).toSorted((left, right) => numberOf(left, "event_no") - numberOf(right, "event_no"));
    assert.ok(decisiveCandidates.length > 0,
      `Payout Event ${eventId} RETRY_REQUIRED 缺少 decisive FAILED Event`);
    const decisive = decisiveCandidates.at(-1);
    const executionId = valueOf(decisive, "execution_id");
    const execution = executions.get(executionId);
    const proof = evidence.get(valueOf(decisive, "evidence_ref_id"));
    const completeBinding = execution
      && identifierEqual(valueOf(execution, "payout_id"), valueOf(retryEvent, "payout_id"))
      && valueOf(retryEvent, "source_type") === "PAYOUT_EXECUTION"
      && identifierEqual(valueOf(retryEvent, "source_id"), executionId)
      && valueOf(retryEvent, "reason_code") === "NO_DEBIT_PROVED"
      && identifierEqual(valueOf(retryEvent, "evidence_ref_id"), valueOf(decisive, "evidence_ref_id"))
      && proof
      && identifierEqual(valueOf(proof, "payout_execution_id"), executionId)
      && valueOf(proof, "evidence_type") === "NO_DEBIT_PROOF"
      && Date.parse(valueOf(decisive, "occurred_time")) <= Date.parse(valueOf(retryEvent, "occurred_time"));
    assert.ok(completeBinding,
      `Payout Event ${eventId} 必须绑定 Execution ${executionId} 的 decisive FAILED Event 与同一 NO_DEBIT_PROOF`);
  }
  return retryEvents.length;
}

function validateCancellationRelease(records) {
  const approvals = recordsFor(records, "t_payout_approval");
  const instructions = recordsFor(records, "t_accounting_instruction");
  const journals = recordsFor(records, "t_journal_entry");
  const instructionLines = recordsFor(records, "t_instruction_line");
  const cancellationEvents = recordsFor(records, "t_payout_event")
    .filter((event) => valueOf(event, "event_scope") === "PAYOUT"
      && valueOf(event, "to_status") === "CANCELLED");
  const cancelled = recordsFor(records, "t_payout")
    .filter((payout) => valueOf(payout, "status") === "CANCELLED");

  for (const payout of cancelled) {
    const payoutId = valueOf(payout, "id");
    const events = cancellationEvents.filter((event) => identifierEqual(valueOf(event, "payout_id"), payoutId));
    assert.equal(events.length, 1, `Payout ${payoutId} 必须有唯一 CANCELLED Event`);
    const event = events[0];
    const fromStatus = valueOf(event, "from_status");
    if (["APPROVED", "ON_HOLD", "RETRY_REQUIRED"].includes(fromStatus)) {
      const checker = approvals.find((approval) => identifierEqual(valueOf(approval, "id"), valueOf(event, "approval_id")));
      assert.ok(checker, `Payout ${payoutId} PAYOUT_CANCEL 缺少 MAKER 与 CHECKER`);
      assert.ok(identifierEqual(valueOf(checker, "payout_id"), payoutId)
        && valueOf(checker, "action_type") === "PAYOUT_CANCEL"
        && valueOf(checker, "approval_role") === "CHECKER"
        && valueOf(checker, "decision") === "APPROVED"
        && valueOf(checker, "requested_from_status") === fromStatus,
      `Payout ${payoutId} CANCELLED Event approval_id 必须指向匹配 Checker`);
      const pair = approvals.filter((approval) => identifierEqual(valueOf(approval, "payout_id"), payoutId)
        && valueOf(approval, "action_type") === "PAYOUT_CANCEL"
        && identifierEqual(valueOf(approval, "action_no"), valueOf(checker, "action_no"))
        && valueOf(approval, "decision") === "APPROVED");
      assert.equal(pair.filter((approval) => valueOf(approval, "approval_role") === "MAKER").length, 1,
        `Payout ${payoutId} PAYOUT_CANCEL 缺少 MAKER 与 CHECKER`);
      assert.equal(pair.filter((approval) => valueOf(approval, "approval_role") === "CHECKER").length, 1,
        `Payout ${payoutId} PAYOUT_CANCEL 缺少 MAKER 与 CHECKER`);
      assert.equal(valueOf(event, "source_type"), "PAYOUT_APPROVAL");
      assert.ok(identifierEqual(valueOf(event, "source_id"), valueOf(checker, "id")));
    } else {
      assert.equal(fromStatus, "PENDING_APPROVAL", `Payout ${payoutId} 非法撤回来源状态 ${fromStatus}`);
      assert.ok(isNull(valueOf(event, "approval_id")), `Payout ${payoutId} 申请人撤回不应伪造审批`);
      const executions = recordsFor(records, "t_payout_execution")
        .filter((execution) => identifierEqual(valueOf(execution, "payout_id"), payoutId));
      assert.equal(executions.length, 0, `Payout ${payoutId} PENDING_APPROVAL 撤回必须为零 Execution`);
    }

    const releaseKey = `PAYOUT_RELEASE:${identifierKey(payoutId)}`;
    const releaseInstructions = instructions.filter((instruction) => valueOf(instruction, "source_event_key") === releaseKey);
    assert.equal(releaseInstructions.length, 1, `Payout ${payoutId} 必须有唯一 ${releaseKey}`);
    const instruction = releaseInstructions[0];
    assert.equal(valueOf(instruction, "instruction_type"), "PAYOUT_RELEASE");
    const releaseJournals = journals.filter((journal) => identifierEqual(valueOf(journal, "instruction_id"), valueOf(instruction, "id")));
    assert.equal(releaseJournals.length, 1, `Payout ${payoutId} release Journal 必须唯一`);
    assert.ok(identifierEqual(valueOf(event, "journal_entry_id"), valueOf(releaseJournals[0], "id")),
      `Payout ${payoutId} CANCELLED Event 必须引用同一 release Journal`);
    const amount = fixedValue(payout, "owner_debit_amount");
    const expected = [
      { accountId: valueOf(payout, "locked_account_id"), side: "DEBIT", amount },
      { accountId: valueOf(payout, "available_account_id"), side: "CREDIT", amount },
    ];
    assertInstructionCoordinates(instructionLines.filter((line) => identifierEqual(valueOf(line, "instruction_id"), valueOf(instruction, "id"))),
      expected, `Payout ${payoutId} release 冻结 Account/金额`);
    assertInstructionCoordinates(journalSummary(records, valueOf(releaseJournals[0], "id")).lines,
      expected, `Payout ${payoutId} release Journal 冻结 Account/金额`);
  }

  for (const instruction of instructions.filter((record) => valueOf(record, "instruction_type") === "PAYOUT_RELEASE")) {
    const match = valueOf(instruction, "source_event_key")?.match(/^PAYOUT_RELEASE:(\d+)$/u);
    assert.ok(match, `PAYOUT_RELEASE Instruction ${valueOf(instruction, "id")} key 非法`);
    assert.ok(cancelled.some((payout) => identifierEqual(valueOf(payout, "id"), match[1])),
      `PAYOUT_RELEASE Instruction ${valueOf(instruction, "id")} 指向非 CANCELLED Payout ${match[1]}`);
  }
  return cancelled.length;
}

function validateExecutionResolutionEvidence(records) {
  const evidenceById = recordsById(records, "t_payout_evidence_ref");
  const matchesById = recordsById(records, "t_bank_recon_match");
  const eventsByExecution = groupByInteger(
    recordsFor(records, "t_payout_event").filter((event) => valueOf(event, "event_scope") === "EXECUTION"),
    "execution_id",
  );
  let provenFailedCount = 0;
  for (const execution of recordsFor(records, "t_payout_execution")) {
    const executionId = valueOf(execution, "id");
    const events = eventsByExecution.get(executionId) ?? [];
    const wasSubmitted = events.some((event) => valueOf(event, "to_status") === "SUBMITTED");
    const wasUnknown = events.some((event) => valueOf(event, "to_status") === "UNKNOWN");
    if (valueOf(execution, "status") === "FAILED" && (wasSubmitted || wasUnknown)) {
      const decisive = events.filter((event) => ["SUBMITTED", "UNKNOWN"].includes(valueOf(event, "from_status"))
        && valueOf(event, "to_status") === "FAILED");
      assert.equal(decisive.length, 1, `Execution ${executionId} 缺少唯一 decisive FAILED Event`);
      const proof = evidenceById.get(valueOf(decisive[0], "evidence_ref_id"));
      assert.ok(proof && identifierEqual(valueOf(proof, "payout_execution_id"), executionId)
        && valueOf(proof, "evidence_type") === "NO_DEBIT_PROOF",
      `Execution ${executionId} decisive FAILED Event 必须引用 immutable NO_DEBIT_PROOF`);
      provenFailedCount += 1;
    }
    for (const event of events.filter((candidate) => valueOf(candidate, "from_status") === "UNKNOWN"
      && valueOf(candidate, "to_status") === "RECONCILED")) {
      const match = matchesById.get(valueOf(event, "bank_recon_match_id"));
      assert.ok(match && valueOf(match, "status") === "CONFIRMED"
        && identifierEqual(valueOf(match, "payout_execution_id"), executionId),
      `Execution ${executionId} UNKNOWN->RECONCILED 必须引用自身 CONFIRMED Bank Match`);
    }
  }
  return provenFailedCount;
}

function validateExecutionAttempts(executions) {
  const executionsByPayout = groupByInteger(executions, "payout_id");
  for (const [payoutId, payoutExecutions] of executionsByPayout) {
    const ordered = payoutExecutions.toSorted((left, right) => numberOf(left, "attempt_no") - numberOf(right, "attempt_no"));
    ordered.forEach((execution, index) => {
      assert.equal(numberOf(execution, "attempt_no"), index + 1, `Payout ${payoutId} attempt_no 必须连续`);
      if (index === 0) return;
      const predecessor = ordered[index - 1];
      if (valueOf(predecessor, "status") === "UNKNOWN") {
        assert.fail(`Payout ${payoutId} UNKNOWN 不允许创建重试`);
      }
      assert.ok(["FAILED", "ABORTED"].includes(valueOf(predecessor, "status")),
        `Payout ${payoutId} 仅允许在 FAILED/ABORTED 后创建重试`);
      assert.ok(!identifierEqual(valueOf(predecessor, "assessment_id"), valueOf(execution, "assessment_id")),
        `Payout ${payoutId} 重试必须使用 fresh assessment`);
      assert.ok(!identifierEqual(valueOf(predecessor, "evaluation_id"), valueOf(execution, "evaluation_id")),
        `Payout ${payoutId} 重试必须使用 fresh evaluation`);
    });
  }
  return executionsByPayout.size;
}

function validatePayoutEventGraph(payouts, executions, events) {
  const payoutTransitions = new Set([
    "NULL->PENDING_APPROVAL",
    "PENDING_APPROVAL->APPROVED",
    "PENDING_APPROVAL->REJECTED",
    "PENDING_APPROVAL->CANCELLED",
    "APPROVED->ON_HOLD",
    "APPROVED->PAYING",
    "APPROVED->CANCELLED",
    "ON_HOLD->APPROVED",
    "ON_HOLD->CANCELLED",
    "PAYING->PAID",
    "PAYING->PAYMENT_UNKNOWN",
    "PAYING->RETRY_REQUIRED",
    "PAYMENT_UNKNOWN->RETRY_REQUIRED",
    "PAYMENT_UNKNOWN->PAID",
    "RETRY_REQUIRED->PAYING",
    "RETRY_REQUIRED->CANCELLED",
  ]);
  const executionTransitions = new Set([
    "NULL->CREATED",
    "CREATED->SUBMITTED",
    "CREATED->ABORTED",
    "SUBMITTED->RECONCILED",
    "SUBMITTED->UNKNOWN",
    "SUBMITTED->FAILED",
    "UNKNOWN->FAILED",
    "UNKNOWN->RECONCILED",
  ]);
  const eventsByPayout = groupByInteger(events, "payout_id");
  const executionsByPayout = groupByInteger(executions, "payout_id");
  for (const payout of payouts) {
    const payoutId = valueOf(payout, "id");
    const ordered = (eventsByPayout.get(payoutId) ?? [])
      .toSorted((left, right) => numberOf(left, "event_no") - numberOf(right, "event_no"));
    assert.ok(ordered.length > 0, `Payout ${payoutId} 缺少事件`);
    ordered.forEach((event, index) => {
      assert.equal(numberOf(event, "event_no"), index + 1, `Payout ${payoutId} event_no 必须连续`);
      assert.equal(numberOf(event, "payout_version_after"), index + 1,
        `Payout ${payoutId} payout_version_after 必须随事件单调连续`);
      assert.ok(!Number.isNaN(Date.parse(valueOf(event, "occurred_time"))),
        `Payout ${payoutId} Event ${valueOf(event, "id")} 缺少合法 occurred_time`);
      if (index > 0) {
        assert.ok(Date.parse(valueOf(ordered[index - 1], "occurred_time")) <= Date.parse(valueOf(event, "occurred_time")),
          `Payout ${payoutId} event_no 与 occurred_time 因果顺序不一致`);
      }
    });
    assert.equal(numberOf(ordered.at(-1), "payout_version_after"), numberOf(payout, "version"),
      `Payout ${payoutId} 最后事件 version 必须等于 Root version`);

    const payoutEvents = ordered.filter((event) => valueOf(event, "event_scope") === "PAYOUT");
    assert.ok(payoutEvents.length > 0, `Payout ${payoutId} 缺少 PAYOUT scope 事件`);
    assert.ok(isNull(valueOf(payoutEvents[0], "from_status")), `Payout ${payoutId} 首事件 from_status 必须 NULL`);
    assert.equal(valueOf(payoutEvents[0], "to_status"), "PENDING_APPROVAL",
      `Payout ${payoutId} 首事件必须创建为 PENDING_APPROVAL`);
    assert.equal(numberOf(payoutEvents[0], "event_no"), 1, `Payout ${payoutId} 创建事件必须是 event_no=1`);
    for (let index = 1; index < payoutEvents.length; index += 1) {
      assert.equal(valueOf(payoutEvents[index], "from_status"), valueOf(payoutEvents[index - 1], "to_status"),
        `Payout ${payoutId} PAYOUT 状态链断裂`);
    }
    for (const event of payoutEvents) {
      const from = isNull(valueOf(event, "from_status")) ? "NULL" : valueOf(event, "from_status");
      const transition = `${from}->${valueOf(event, "to_status")}`;
      assert.ok(payoutTransitions.has(transition), `Payout ${payoutId} 非法状态转换 ${transition}`);
      if (valueOf(event, "to_status") === "PAYING") {
        const submitted = ordered.find((candidate) => valueOf(candidate, "event_scope") === "EXECUTION"
          && identifierEqual(valueOf(candidate, "execution_id"), valueOf(event, "source_id"))
          && valueOf(candidate, "to_status") === "SUBMITTED");
        assert.ok(submitted && numberOf(submitted, "event_no") < numberOf(event, "event_no"),
          `Payout ${payoutId} PAYING 必须发生在对应 Execution SUBMITTED 之后`);
        assert.ok(Date.parse(valueOf(submitted, "occurred_time")) <= Date.parse(valueOf(event, "occurred_time")),
          `Payout ${payoutId} PAYING 时间早于 Execution SUBMITTED`);
      }
      if (valueOf(event, "to_status") === "PAID") {
        const reconciled = ordered.find((candidate) => valueOf(candidate, "event_scope") === "EXECUTION"
          && identifierEqual(valueOf(candidate, "bank_recon_match_id"), valueOf(event, "bank_recon_match_id"))
          && valueOf(candidate, "to_status") === "RECONCILED");
        assert.ok(reconciled && numberOf(reconciled, "event_no") < numberOf(event, "event_no"),
          `Payout ${payoutId} PAID 必须发生在对应 Execution RECONCILED 之后`);
      }
    }
    assert.equal(valueOf(payoutEvents.at(-1), "to_status"), valueOf(payout, "status"),
      `Payout ${payoutId} 最后 PAYOUT 事件状态与 Root 不一致`);
    const approvalEvent = payoutEvents.find((event) => valueOf(event, "to_status") === "APPROVED");
    if (payout.values.has("approved_time")) {
      assert.equal(valueOf(approvalEvent, "occurred_time"), valueOf(payout, "approved_time"),
        `Payout ${payoutId} approved_time 与状态事件不一致`);
    }
    if (valueOf(payout, "status") === "PAID" && payout.values.has("reconciled_time")) {
      assert.equal(valueOf(payoutEvents.at(-1), "occurred_time"), valueOf(payout, "reconciled_time"),
        `Payout ${payoutId} reconciled_time 与 PAID 事件不一致`);
    }

    const payoutExecutions = (executionsByPayout.get(payoutId) ?? [])
      .toSorted((left, right) => numberOf(left, "attempt_no") - numberOf(right, "attempt_no"));
    for (const execution of payoutExecutions) {
      const executionEvents = ordered.filter((event) => valueOf(event, "event_scope") === "EXECUTION"
        && identifierEqual(valueOf(event, "execution_id"), valueOf(execution, "id")));
      assert.ok(executionEvents.length > 0, `Execution ${valueOf(execution, "id")} 缺少事件`);
      executionEvents.forEach((event, index) => {
        assert.equal(numberOf(event, "execution_version_after"), index + 1,
          `Execution ${valueOf(execution, "id")} version 必须连续`);
        if (index === 0) assert.ok(isNull(valueOf(event, "from_status")), "Execution 首事件 from_status 必须 NULL");
        if (index > 0) {
          assert.equal(valueOf(event, "from_status"), valueOf(executionEvents[index - 1], "to_status"),
            `Execution ${valueOf(execution, "id")} 状态链断裂`);
        }
        const from = isNull(valueOf(event, "from_status")) ? "NULL" : valueOf(event, "from_status");
        const transition = `${from}->${valueOf(event, "to_status")}`;
        if (transition === "FAILED->FAILED") {
          const validLateDebit = valueOf(event, "reason_code") === "LATE_DEBIT"
            && valueOf(event, "source_type") === "BANK_STATEMENT_ITEM"
            && valueOf(event, "source_event_key") === `LATE_DEBIT:${identifierKey(valueOf(event, "source_id"))}`
            && !isNull(valueOf(event, "evidence_ref_id"))
            && !isNull(valueOf(event, "journal_entry_id"));
          assert.ok(validLateDebit,
            `Execution ${valueOf(execution, "id")} FAILED->FAILED 仅允许完整 LATE_DEBIT 审计路径`);
          return;
        }
        assert.ok(executionTransitions.has(transition),
          `Execution ${valueOf(execution, "id")} 非法状态转换 ${transition}`);
      });
      const approvedEvent = payoutEvents.find((event) => valueOf(event, "to_status") === "APPROVED");
      assert.ok(approvedEvent && numberOf(approvedEvent, "event_no") < numberOf(executionEvents[0], "event_no"),
        `Execution ${valueOf(execution, "id")} 必须在 Payout APPROVED 后创建`);
      assert.equal(valueOf(executionEvents.at(-1), "to_status"), valueOf(execution, "status"));
      assert.equal(numberOf(executionEvents.at(-1), "execution_version_after"), numberOf(execution, "version"),
        `Execution ${valueOf(execution, "id")} 最后事件 version 与 Root 不一致`);
      const submittedEvent = executionEvents.find((event) => valueOf(event, "to_status") === "SUBMITTED");
      if (execution.values.has("submitted_time")) {
        assert.equal(submittedEvent ? valueOf(submittedEvent, "occurred_time") : "NULL",
          valueOf(execution, "submitted_time"),
          `Execution ${valueOf(execution, "id")} submitted_time 与状态事件不一致`);
      }
      if (execution.values.has("resolved_time")) {
        const resolvesAtTerminal = ["ABORTED", "FAILED", "RECONCILED"].includes(valueOf(execution, "status"));
        const terminalEvent = resolvesAtTerminal
          ? executionEvents.find((event) => valueOf(event, "to_status") === valueOf(execution, "status")
            && valueOf(event, "from_status") !== valueOf(event, "to_status"))
          : undefined;
        assert.equal(terminalEvent ? valueOf(terminalEvent, "occurred_time") : "NULL",
          valueOf(execution, "resolved_time"),
          `Execution ${valueOf(execution, "id")} resolved_time 与权威终态事件不一致`);
      }
    }
    for (let index = 1; index < payoutExecutions.length; index += 1) {
      const predecessorEvents = ordered.filter((event) => identifierEqual(valueOf(event, "execution_id"), valueOf(payoutExecutions[index - 1], "id")));
      const retryEvents = ordered.filter((event) => identifierEqual(valueOf(event, "execution_id"), valueOf(payoutExecutions[index], "id")));
      assert.ok(numberOf(predecessorEvents.at(-1), "event_no") < numberOf(retryEvents[0], "event_no"),
        `Payout ${payoutId} 重试必须发生在前一 Execution 终态后`);
    }
  }
  return events.length;
}

function validatePayoutCreationJournals(records) {
  const instructions = recordsById(records, "t_accounting_instruction");
  const journals = recordsById(records, "t_journal_entry");
  for (const payout of recordsFor(records, "t_payout")) {
    const payoutId = valueOf(payout, "id");
    const created = recordsFor(records, "t_payout_event")
      .filter((event) => identifierEqual(valueOf(event, "payout_id"), payoutId) && numberOf(event, "event_no") === 1);
    assert.equal(created.length, 1, `Payout ${payoutId} 必须有唯一 PayoutCreated 首事件`);
    const journal = journals.get(valueOf(created[0], "journal_entry_id"));
    assert.ok(journal, `Payout ${payoutId} PayoutCreated 缺少 PAYOUT_LOCK Journal`);
    const instruction = instructions.get(valueOf(journal, "instruction_id"));
    assert.equal(valueOf(instruction, "instruction_type"), "PAYOUT_LOCK",
      `Payout ${payoutId} PayoutCreated Journal 不是 PAYOUT_LOCK`);
    assert.equal(valueOf(instruction, "source_event_key"), `PAYOUT_LOCK:${identifierKey(payoutId)}`,
      `Payout ${payoutId} PayoutCreated 未绑定自身 PAYOUT_LOCK`);
  }
  return recordsFor(records, "t_payout").length;
}

function validateCancelledPayoutSafety(payouts, executions, bankMatches, evidence = []) {
  const executionsByPayout = groupByInteger(executions, "payout_id");
  for (const payout of payouts.filter((record) => valueOf(record, "status") === "CANCELLED")) {
    const payoutId = valueOf(payout, "id");
    const payoutExecutions = executionsByPayout.get(payoutId) ?? [];
    for (const execution of payoutExecutions) {
      const status = valueOf(execution, "status");
      assert.ok(["ABORTED", "FAILED"].includes(status),
        `CANCELLED Payout ${payoutId} 仍有 ${status} Execution ${valueOf(execution, "id")}`);
      assert.ok(isNull(valueOf(execution, "active_slot")),
        `CANCELLED Payout ${payoutId} Execution ${valueOf(execution, "id")} 仍占 active_slot`);
      if (status === "FAILED") {
        assert.ok(evidence.some((record) => identifierEqual(valueOf(record, "payout_execution_id"), valueOf(execution, "id"))
          && valueOf(record, "evidence_type") === "NO_DEBIT_PROOF"),
          `CANCELLED Payout ${payoutId} FAILED Execution ${valueOf(execution, "id")} 缺少 definitive NO_DEBIT_PROOF`);
      }
      const debitMatches = bankMatches.filter((match) => identifierEqual(valueOf(match, "payout_execution_id"), valueOf(execution, "id")));
      assert.equal(debitMatches.length, 0,
        `CANCELLED Payout ${payoutId} Execution ${valueOf(execution, "id")} 仍有未安全处置的银行 Debit`);
    }
  }
  return payouts.filter((record) => valueOf(record, "status") === "CANCELLED").length;
}

function validateFeeDifferenceTimeline(records) {
  const bankItems = recordsById(records, "t_bank_statement_item");
  const resolutions = recordsById(records, "t_recon_resolution");
  const instructions = recordsById(records, "t_accounting_instruction");
  const journals = recordsFor(records, "t_journal_entry");
  const events = recordsFor(records, "t_payout_event");
  const confirmedMatches = recordsFor(records, "t_bank_recon_match")
    .filter((match) => valueOf(match, "match_type") === "PAYOUT_DEBIT" && valueOf(match, "status") === "CONFIRMED");
  for (const exception of recordsFor(records, "t_recon_exception")
    .filter((record) => valueOf(record, "source_type") === "PAYOUT_EXECUTION"
      && valueOf(record, "exception_type") === "FEE_DIFFERENCE")) {
    const execution = requireRecord(records, "t_payout_execution", valueOf(exception, "source_id"));
    const payout = requireRecord(records, "t_payout", valueOf(execution, "payout_id"));
    const match = confirmedMatches.find((candidate) => identifierEqual(valueOf(candidate, "payout_execution_id"), valueOf(execution, "id")));
    assert.ok(match, `FEE_DIFFERENCE ${valueOf(exception, "id")} 缺少 CONFIRMED Match`);
    const item = bankItems.get(valueOf(match, "bank_item_id"));
    const resolution = resolutions.get(valueOf(exception, "applied_resolution_id"));
    assert.ok(item && resolution, `FEE_DIFFERENCE ${valueOf(exception, "id")} 缺少 Statement/Resolution`);
    const paidEvent = events.find((event) => identifierEqual(valueOf(event, "payout_id"), valueOf(payout, "id"))
      && valueOf(event, "event_scope") === "PAYOUT" && valueOf(event, "to_status") === "PAID");
    assert.ok(paidEvent, `FEE_DIFFERENCE ${valueOf(exception, "id")} 缺少 PAID Event`);
    const matchTime = Date.parse(valueOf(match, "decision_time"));
    const resolutionTime = Date.parse(valueOf(resolution, "decision_time"));
    const paidTime = Date.parse(valueOf(paidEvent, "occurred_time"));
    assert.ok(resolutionTime <= paidTime,
      `FEE_DIFFERENCE ${valueOf(exception, "id")} resolution 必须先于 PAID`);
    assert.ok(valueOf(item, "value_date") <= valueOf(match, "decision_time").slice(0, 10),
      `FEE_DIFFERENCE ${valueOf(exception, "id")} Statement value_date 晚于 Match`);
    assert.ok(matchTime <= resolutionTime,
      `FEE_DIFFERENCE ${valueOf(exception, "id")} Match 必须先于 resolution approval`);
    assert.equal(valueOf(resolution, "checker_decision"), "APPROVED");
    assert.equal(valueOf(resolution, "status"), "APPLIED");
    const diffInstruction = instructions.get(valueOf(resolution, "instruction_id"));
    assert.equal(valueOf(diffInstruction, "source_event_key"), `PAYOUT_FEE_DIFF:${identifierKey(valueOf(exception, "id"))}`);
    const diffJournals = journals.filter((journal) => identifierEqual(valueOf(journal, "instruction_id"), valueOf(diffInstruction, "id")));
    assert.equal(diffJournals.length, 1, `FEE_DIFFERENCE ${valueOf(exception, "id")} diff Journal 必须唯一`);
    assert.ok(valueOf(diffJournals[0], "posting_date") >= valueOf(resolution, "decision_time").slice(0, 10),
      `FEE_DIFFERENCE ${valueOf(exception, "id")} fee Journal 早于 resolution approval`);
    assert.ok(resolutionTime <= Date.parse(valueOf(execution, "resolved_time")),
      `FEE_DIFFERENCE ${valueOf(exception, "id")} Execution completion 早于 approval`);
    assert.ok(Date.parse(valueOf(execution, "resolved_time")) <= paidTime,
      `FEE_DIFFERENCE ${valueOf(exception, "id")} PAID 早于 Execution completion`);
    assert.equal(valueOf(payout, "reconciled_time"), valueOf(paidEvent, "occurred_time"));
    const delta = fixedRecordDifference(match, "matched_amount", payout, "cash_required_amount");
    assert.ok(fixedEqual(fixedValue(exception, "difference_amount"), delta),
      `FEE_DIFFERENCE ${valueOf(exception, "id")} 金额与实际 Debit 差额不一致`);
  }
  return recordsFor(records, "t_recon_exception")
    .filter((record) => valueOf(record, "source_type") === "PAYOUT_EXECUTION"
      && valueOf(record, "exception_type") === "FEE_DIFFERENCE").length;
}

function validateJournalPeriods(records) {
  const periods = recordsById(records, "t_accounting_period");
  for (const journal of recordsFor(records, "t_journal_entry")) {
    const period = periods.get(valueOf(journal, "accounting_period_id"));
    assert.ok(period, `Journal ${valueOf(journal, "id")} 缺少 Accounting Period`);
    const postingDate = valueOf(journal, "posting_date");
    assert.ok(valueOf(period, "start_date") <= postingDate && postingDate <= valueOf(period, "end_date"),
      `Journal ${valueOf(journal, "id")} posting_date 不属于 Accounting Period ${valueOf(period, "id")}`);
  }
  return recordsFor(records, "t_journal_entry").length;
}

function validateLateDebitCausality(records) {
  const lateExceptions = recordsFor(records, "t_recon_exception")
    .filter((record) => valueOf(record, "exception_type") === "LATE_DEBIT");
  assert.ok(lateExceptions.length > 0, "缺少 LATE_DEBIT Recon Exception");
  const journalIds = [];
  for (const lateException of lateExceptions) {
    assert.equal(valueOf(lateException, "source_type"), "BANK_STATEMENT_ITEM");
    const bankItem = requireRecord(records, "t_bank_statement_item", valueOf(lateException, "source_id"));
    assert.equal(valueOf(bankItem, "direction"), "DEBIT",
      `LATE_DEBIT ${valueOf(lateException, "id")} 必须引用 DEBIT Bank Statement Item`);
    const eventKey = `LATE_DEBIT:${identifierKey(valueOf(bankItem, "id"))}`;
    const instructions = recordsFor(records, "t_accounting_instruction")
      .filter((record) => valueOf(record, "instruction_type") === "LATE_DEBIT"
        && valueOf(record, "source_event_key") === eventKey
        && valueOf(record, "status") === "POSTED");
    assert.equal(instructions.length, 1, `${eventKey} 必须唯一解析 POSTED LATE_DEBIT Instruction`);
    const instruction = instructions[0];
    const journals = recordsFor(records, "t_journal_entry")
      .filter((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(instruction, "id")));
    assert.equal(journals.length, 1, `${eventKey} 必须唯一解析 Journal`);
    const journal = journals[0];
    const lateEvents = recordsFor(records, "t_payout_event")
      .filter((record) => valueOf(record, "event_scope") === "EXECUTION"
        && valueOf(record, "from_status") === "FAILED"
        && valueOf(record, "to_status") === "FAILED"
        && valueOf(record, "reason_code") === "LATE_DEBIT"
        && valueOf(record, "source_type") === "BANK_STATEMENT_ITEM"
        && valueOf(record, "source_event_key") === eventKey
        && identifierEqual(valueOf(record, "source_id"), valueOf(bankItem, "id")));
    assert.equal(lateEvents.length, 1, `${eventKey} 必须唯一解析 FAILED->FAILED Payout Event`);
    const lateEvent = lateEvents[0];
    const execution = requireRecord(records, "t_payout_execution", valueOf(lateEvent, "execution_id"));
    const payout = requireRecord(records, "t_payout", valueOf(execution, "payout_id"));
    assert.equal(valueOf(execution, "status"), "FAILED", `${eventKey} 必须保留原 Execution FAILED`);
    assert.ok(identifierEqual(valueOf(lateEvent, "payout_id"), valueOf(payout, "id")),
      `${eventKey} Event/Payout/Execution 坐标不一致`);
    assert.ok(identifierEqual(valueOf(bankItem, "cash_account_id"), valueOf(execution, "cash_account_id")),
      `${eventKey} Bank Item/Execution Cash Account 不一致`);
    assert.equal(valueOf(lateException, "entity_type"), "OPERATING_ENTITY",
      `${eventKey} Recon Exception entity_type 不一致`);
    assert.ok(identifierEqual(valueOf(lateException, "entity_id"), valueOf(payout, "entity_no")),
      `${eventKey} Recon Exception/Payout OE 不一致`);
    assert.equal(valueOf(lateException, "currency"), valueOf(payout, "currency"),
      `${eventKey} Recon Exception/Payout currency 不一致`);
    const evidence = requireRecord(records, "t_payout_evidence_ref", valueOf(lateEvent, "evidence_ref_id"));
    assert.ok(identifierEqual(valueOf(evidence, "payout_execution_id"), valueOf(execution, "id"))
      && valueOf(evidence, "evidence_type") === "RECONCILIATION_SUPPORT",
    `${eventKey} Event Evidence 必须属于同一 Execution 的 RECONCILIATION_SUPPORT`);
    assert.ok(identifierEqual(valueOf(lateEvent, "journal_entry_id"), valueOf(journal, "id")),
      `${eventKey} Event/Journal 坐标不一致`);
    const normalMatches = recordsFor(records, "t_bank_recon_match")
      .filter((match) => identifierEqual(valueOf(match, "bank_item_id"), valueOf(bankItem, "id")));
    assert.equal(normalMatches.length, 0,
      `${eventKey} 迟到 Debit 不得伪装为普通 Bank Match`);
    const decisiveFailed = recordsFor(records, "t_payout_event")
      .filter((event) => valueOf(event, "event_scope") === "EXECUTION"
        && identifierEqual(valueOf(event, "execution_id"), valueOf(execution, "id"))
        && ["SUBMITTED", "UNKNOWN"].includes(valueOf(event, "from_status"))
        && valueOf(event, "to_status") === "FAILED");
    assert.equal(decisiveFailed.length, 1, `${eventKey} 缺少原 decisive FAILED Event`);
    assert.ok(Date.parse(valueOf(decisiveFailed[0], "occurred_time")) < Date.parse(valueOf(lateEvent, "occurred_time")),
      `${eventKey} 必须晚于原 definitive FAILED`);
    const eventDate = valueOf(lateEvent, "occurred_time").slice(0, 10);
    assert.ok(valueOf(bankItem, "value_date") <= valueOf(journal, "posting_date"),
      `${eventKey} value_date 晚于 posting_date`);
    assert.ok(valueOf(bankItem, "value_date") <= eventDate,
      `${eventKey} value_date 晚于 Event occurred_time`);
    assert.ok(valueOf(journal, "posting_date") <= eventDate,
      `${eventKey} Journal posting_date 晚于 Event occurred_time`);
    assert.ok(fixedRecordEqual(bankItem, "amount", lateException, "difference_amount"),
      `${eventKey} Bank amount 与 Recon difference 不一致`);
    journalIds.push(valueOf(journal, "id"));
  }
  const lateInstructions = recordsFor(records, "t_accounting_instruction")
    .filter((instruction) => valueOf(instruction, "instruction_type") === "LATE_DEBIT"
      && valueOf(instruction, "status") === "POSTED");
  const selfLoops = recordsFor(records, "t_payout_event")
    .filter((event) => valueOf(event, "from_status") === "FAILED" && valueOf(event, "to_status") === "FAILED");
  assert.equal(lateInstructions.length, lateExceptions.length,
    "每个 LATE_DEBIT Recon Exception 必须恰有一个 Instruction");
  assert.equal(selfLoops.length, lateExceptions.length,
    "FAILED->FAILED 只能由 LATE_DEBIT 一一产生");
  return journalIds;
}

function validateBookAndAccountSequences(records) {
  const journalsByBook = groupByInteger(recordsFor(records, "t_journal_entry"), "ledger_book_id");
  const sequencesByBook = groupByInteger(recordsFor(records, "t_book_sequence"), "ledger_book_id");
  const journalsById = recordsById(records, "t_journal_entry");
  const touchedMaxByAccount = new Map();
  for (const line of recordsFor(records, "t_journal_line")) {
    const journal = journalsById.get(valueOf(line, "journal_entry_id"));
    assert.ok(journal, `Journal Line ${valueOf(line, "id")} 缺少 Journal`);
    const sequence = BigInt(valueOf(journal, "journal_sequence"));
    const accountId = identifierKey(valueOf(line, "account_id"));
    const prior = touchedMaxByAccount.get(accountId);
    if (!prior || sequence > prior) touchedMaxByAccount.set(accountId, sequence);
  }

  let firstSequence;
  let lastSequence;
  let nextValue;
  for (const [bookId, journals] of journalsByBook) {
    const bookSequences = sequencesByBook.get(bookId) ?? [];
    assert.equal(bookSequences.length, 1, `Ledger Book ${bookId} 必须有唯一 Book Sequence`);
    const ordered = journals.toSorted((left, right) => {
      const leftValue = BigInt(valueOf(left, "journal_sequence"));
      const rightValue = BigInt(valueOf(right, "journal_sequence"));
      return leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0;
    });
    assert.ok(ordered.length > 0, `Ledger Book ${bookId} 缺少 represented Journal`);
    for (let index = 1; index < ordered.length; index += 1) {
      const previous = BigInt(valueOf(ordered[index - 1], "journal_sequence"));
      const current = BigInt(valueOf(ordered[index], "journal_sequence"));
      assert.equal(current, previous + 1n, `Ledger Book ${bookId} represented journal_sequence 不连续`);
      assert.ok(valueOf(ordered[index - 1], "posting_date") <= valueOf(ordered[index], "posting_date"),
        `Ledger Book ${bookId} journal_sequence 与 posting_date 不单调`);
    }
    const first = BigInt(valueOf(ordered[0], "journal_sequence"));
    const last = BigInt(valueOf(ordered.at(-1), "journal_sequence"));
    const next = BigInt(valueOf(bookSequences[0], "next_value"));
    assert.equal(next, last + 1n, `Ledger Book ${bookId} next_value 必须等于 represented max+1`);
    for (const account of recordsFor(records, "t_account")
      .filter((record) => identifierEqual(valueOf(record, "ledger_book_id"), bookId))) {
      const accountLast = BigInt(valueOf(account, "last_journal_sequence"));
      const touchedMax = touchedMaxByAccount.get(identifierKey(valueOf(account, "id")));
      if (touchedMax !== undefined) {
        assert.equal(accountLast, touchedMax,
          `Account ${valueOf(account, "id")} last_journal_sequence 必须等于 represented line max`);
      } else {
        assert.ok(accountLast < next,
          `Untouched Account ${valueOf(account, "id")} last_journal_sequence 必须小于 next_value`);
      }
    }
    firstSequence = first.toString();
    lastSequence = last.toString();
    nextValue = next.toString();
  }
  return { firstSequence, lastSequence, nextValue };
}

function validateTaxPaymentGraph(records) {
  const matchesById = recordsById(records, "t_bank_recon_match");
  const matches = recordsFor(records, "t_bank_recon_match")
    .filter((record) => valueOf(record, "match_type") === "TAX_PAYMENT_DEBIT" && valueOf(record, "status") === "CONFIRMED");
  for (const payment of recordsFor(records, "t_tax_payment")) {
    const confirmed = matches.filter((match) => identifierEqual(valueOf(match, "tax_payment_id"), valueOf(payment, "id")));
    const matchedAmount = fixedSumRecords(confirmed, "matched_amount");
    const paymentAmount = fixedValue(payment, "amount");
    if (valueOf(payment, "status") === "CREATED" && fixedCompare(matchedAmount, paymentAmount) >= 0) {
      assert.fail(`Tax Payment ${valueOf(payment, "id")} CREATED 不得已有 full CONFIRMED Match`);
    }
    if (valueOf(payment, "status") !== "RECONCILED") continue;
    assert.ok(fixedEqual(matchedAmount, paymentAmount), `Tax Payment ${valueOf(payment, "id")} 未完整匹配`);
    assert.ok(fixedRecordEqual(payment, "reconciled_amount", payment, "amount"));
    assert.ok(!isNull(valueOf(payment, "bank_payment_ref")));
    assert.ok(!isNull(valueOf(payment, "reconciled_time")));
    assert.ok(isNull(valueOf(payment, "active_slot")));
    const summary = journalSummary(records, valueOf(payment, "journal_entry_id"));
    assert.ok(summary.lines.some((line) => identifierEqual(valueOf(line, "account_id"), valueOf(payment, "tax_payable_account_id"))
      && valueOf(line, "entry_side") === "DEBIT" && fixedRecordEqual(line, "amount", payment, "amount")));
    assert.ok(summary.lines.some((line) => identifierEqual(valueOf(line, "account_id"), "20207")
      && valueOf(line, "entry_side") === "CREDIT" && fixedRecordEqual(line, "amount", payment, "amount")));
    const paymentEvents = recordsFor(records, "t_tax_payment_event")
      .filter((event) => identifierEqual(valueOf(event, "tax_payment_id"), valueOf(payment, "id")))
      .toSorted((left, right) => numberOf(left, "event_no") - numberOf(right, "event_no"));
    paymentEvents.forEach((event, index) => assert.equal(numberOf(event, "event_no"), index + 1));
    const reconciledEvent = paymentEvents.at(-1);
    assert.equal(valueOf(reconciledEvent, "event_type"), "RECONCILED");
    const eventMatch = matchesById.get(valueOf(reconciledEvent, "source_ref"));
    assert.ok(eventMatch
      && valueOf(eventMatch, "status") === "CONFIRMED"
      && valueOf(eventMatch, "match_type") === "TAX_PAYMENT_DEBIT"
      && identifierEqual(valueOf(eventMatch, "tax_payment_id"), valueOf(payment, "id")),
    `Tax Payment ${valueOf(payment, "id")} RECONCILED Event 必须引用自身 CONFIRMED TAX_PAYMENT_DEBIT Match`);
    assert.equal(paymentEvents.length, numberOf(payment, "version"));
  }
  return matches.length;
}

function validateMaturityGraph(records, economicsId) {
  const eventKey = `PAYABLE_BALANCE_MATURED:${identifierKey(economicsId)}`;
  const maturityEvents = recordsFor(records, "t_compensation_event")
    .filter((record) => valueOf(record, "event_type") === "PAYABLE_BALANCE_MATURED");
  const maturityInstructions = recordsFor(records, "t_accounting_instruction")
    .filter((record) => valueOf(record, "instruction_type") === "PAYABLE_BALANCE_MATURED");
  assert.equal(maturityEvents.length, 1, "到期场景必须只有一个 Compensation Event");
  assert.equal(maturityInstructions.length, 1, "到期场景必须只有一个 Accounting Instruction");
  assert.equal(valueOf(maturityEvents[0], "event_key"), eventKey);
  assert.ok(identifierEqual(valueOf(maturityEvents[0], "source_id"), economicsId));
  assert.equal(valueOf(maturityInstructions[0], "source_event_key"), eventKey);
  assert.ok(identifierEqual(valueOf(maturityInstructions[0], "transaction_economics_id"), economicsId));
  const maturityKeys = new Set([
    ...maturityEvents.map((record) => valueOf(record, "event_key")),
    ...maturityInstructions.map((record) => valueOf(record, "source_event_key")),
  ]);
  assert.deepEqual([...maturityKeys], [eventKey], "同一 economics 只能使用一个精确到期 key");

  const journals = recordsFor(records, "t_journal_entry")
    .filter((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(maturityInstructions[0], "id")));
  assert.equal(journals.length, 1, "到期 Instruction 必须只产生一个 Journal");
  assert.ok(identifierEqual(valueOf(journals[0], "id"), "32242"));
  const summary = journalSummary(records, valueOf(journals[0], "id"));
  assert.equal(summary.lines.length, 5, "共同成熟 Journal 必须恰有 Client 两行和 Partner 三行");

  const line = (accountId, side) => {
    const matches = summary.lines.filter((candidate) => identifierEqual(valueOf(candidate, "account_id"), accountId)
      && valueOf(candidate, "entry_side") === side);
    assert.equal(matches.length, 1, `Journal 32242 缺少 ${accountId}/${side} 唯一行`);
    return matches[0];
  };
  const clientPending = line("20301", "DEBIT");
  const clientAvailable = line("20302", "CREDIT");
  const partnerPending = line("20303", "DEBIT");
  const partnerRecovery = line("20311", "CREDIT");
  const partnerAvailable = line("20304", "CREDIT");
  assert.ok(fixedRecordEqual(clientPending, "amount", clientAvailable, "amount"));
  const partnerGross = fixedValue(partnerPending, "amount");
  const recoveryNetted = fixedValue(partnerRecovery, "amount");
  const partnerNetAvailable = fixedValue(partnerAvailable, "amount");
  assert.ok(fixedEqual(partnerGross, fixedAdd(recoveryNetted, partnerNetAvailable)),
    "partner_gross_matured 必须等于 recovery_netted + partner_net_available");

  const expectedAccounts = new Map([
    ["20301", ["CLIENT", "40001", "PENDING", "CLIENT_PAYABLE"]],
    ["20302", ["CLIENT", "40001", "AVAILABLE", "CLIENT_PAYABLE"]],
    ["20303", ["LOCAL_PARTNER", "41001", "PENDING", "PARTNER_PAYABLE"]],
    ["20304", ["LOCAL_PARTNER", "41001", "AVAILABLE", "PARTNER_PAYABLE"]],
    ["20311", ["LOCAL_PARTNER", "41001", "NONE", "PARTNER_RECOVERY_RECEIVABLE"]],
  ]);
  const chartAccounts = recordsById(records, "t_chart_account");
  for (const [accountId, [ownerType, ownerId, bucket, accountCode]] of expectedAccounts) {
    const account = requireRecord(records, "t_account", accountId);
    assert.equal(valueOf(account, "owner_type"), ownerType, `Account ${accountId} owner_type 错误`);
    assert.ok(identifierEqual(valueOf(account, "owner_id"), ownerId), `Account ${accountId} owner_id 错误`);
    assert.equal(valueOf(account, "balance_bucket"), bucket, `Account ${accountId} balance_bucket 错误`);
    assert.equal(valueOf(chartAccounts.get(valueOf(account, "chart_account_id")), "account_code"), accountCode);
  }

  const recoveryInstruction = recordsFor(records, "t_accounting_instruction")
    .find((record) => valueOf(record, "instruction_type") === "PARTNER_RECOVERY_OPEN"
      && valueOf(record, "source_event_key") === "PARTNER_RECOVERY_OPEN:31030"
      && identifierEqual(valueOf(record, "transaction_economics_id"), "31030"));
  assert.ok(recoveryInstruction, "缺少先前 Partner Recovery 负向 Economics 来源");
  const recoveryEconomics = requireRecord(records, "t_transaction_economics", "31030");
  assert.equal(valueOf(recoveryEconomics, "economics_type"), "DISPUTE");
  assert.ok(identifierEqual(valueOf(recoveryEconomics, "local_partner_id"), "41001"));
  assert.ok(identifierEqual(valueOf(recoveryEconomics, "source_version"), valueOf(recoveryInstruction, "source_version")),
    "Partner Recovery 必须冻结同一负向 Economics/Partner/Version");
  const recoveryJournal = recordsFor(records, "t_journal_entry")
    .find((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(recoveryInstruction, "id")));
  assert.ok(recoveryJournal, "缺少先前 Partner Recovery Journal");
  assert.ok(valueOf(recoveryEconomics, "commercial_context_time").slice(0, 10)
    <= valueOf(recoveryJournal, "posting_date"),
  "Partner Recovery Journal 不得早于负向 Economics");
  assert.ok(valueOf(recoveryJournal, "posting_date") < valueOf(journals[0], "posting_date"),
    "Recovery Debit 必须早于成熟 Credit netting");
  const recoverySummary = journalSummary(records, valueOf(recoveryJournal, "id"));
  assert.ok(recoverySummary.lines.some((candidate) => identifierEqual(valueOf(candidate, "account_id"), "20311")
    && valueOf(candidate, "entry_side") === "DEBIT"
    && fixedEqual(fixedValue(candidate, "amount"), recoveryNetted)));

  return {
    eventKey,
    journalId: valueOf(journals[0], "id"),
    clientAmount: fixedFormat(fixedValue(clientPending, "amount")),
    partnerGross: fixedFormat(partnerGross),
    recoveryNetted: fixedFormat(recoveryNetted),
    partnerNetAvailable: fixedFormat(partnerNetAvailable),
    debitTotal: fixedFormat(summary.debitTotal),
    creditTotal: fixedFormat(summary.creditTotal),
  };
}

function parseRcoCheckpointRows(content) {
  const allowedNames = new Set(["PAYOUT_CREATE", "PAYOUT_CREATE_REPLAY", "FINAL_APPROVAL", "FINAL_APPROVAL_REPLAY",
    "EXECUTION_CREATE", "EXECUTION_CREATE_REPLAY", "EXTERNAL_SUBMIT", "EXTERNAL_SUBMIT_REPLAY"]);
  return content.split(/\n/u).flatMap((line, index) => {
    const match = line.match(/^\| `([A-Z_]+)` \| (.*) \|$/u);
    if (!match || !allowedNames.has(match[1])) return [];
    const values = new Map();
    for (const fragment of match[2].split("，")) {
      const assignment = fragment.match(/^([a-z][a-z0-9_]*)=([^，]+)$/u);
      assert.ok(assignment, `RCO 第 ${index + 1} 行无法解析片段：${fragment}`);
      assert.ok(!values.has(assignment[1]), `RCO 第 ${index + 1} 行重复字段 ${assignment[1]}`);
      values.set(assignment[1], assignment[2]);
    }
    return [{ name: match[1], values, lineNumber: index + 1 }];
  });
}

function validateRcoCheckpoints(checkpoints, records) {
  assert.equal(checkpoints.length, 8, "RCO 矩阵必须含四次首次调用和四次重放");
  const byName = new Map(checkpoints.map((checkpoint) => [checkpoint.name, checkpoint]));
  assert.equal(byName.size, 8, "RCO checkpoint 名称不得重复");
  const replayPairs = [
    ["PAYOUT_CREATE", "PAYOUT_CREATE_REPLAY"],
    ["FINAL_APPROVAL", "FINAL_APPROVAL_REPLAY"],
    ["EXECUTION_CREATE", "EXECUTION_CREATE_REPLAY"],
    ["EXTERNAL_SUBMIT", "EXTERNAL_SUBMIT_REPLAY"],
  ];
  const scopeFields = ["owner_type", "owner_id", "entity_no", "currency", "amount",
    "control_set_hash", "scope_hash", "frozen_target"];
  const firstPairs = new Set();
  for (const [freshName, replayName] of replayPairs) {
    const fresh = byName.get(freshName);
    const replay = byName.get(replayName);
    assert.ok(fresh && replay, `缺少 ${freshName} 或其 replay`);
    for (const checkpoint of [fresh, replay]) {
      for (const field of ["command_key", "assessment_id", "evaluation_id", ...scopeFields]) {
        assert.ok(!isNull(valueOf(checkpoint, field)), `${checkpoint.name} 缺少完整 ${field}`);
      }
    }
    assert.equal(valueOf(fresh, "replay_of"), "NULL");
    assert.equal(valueOf(replay, "replay_of"), freshName);
    for (const field of ["assessment_id", "evaluation_id", "owner_id", "entity_no"]) {
      assert.ok(identifierEqual(valueOf(replay, field), valueOf(fresh, field)),
        `${replayName} 未复用 ${freshName} 的 ${field}`);
    }
    for (const field of ["command_key", "owner_type", "currency", "amount", "control_set_hash", "scope_hash", "frozen_target"]) {
      assert.equal(valueOf(replay, field), valueOf(fresh, field),
        `${replayName} 未复用 ${freshName} 的 ${field}`);
    }
    firstPairs.add(`${identifierKey(valueOf(fresh, "assessment_id"))}:${identifierKey(valueOf(fresh, "evaluation_id"))}`);
  }
  assert.equal(firstPairs.size, 4, "四次首次 RCO 调用必须使用互异 assessment/evaluation pair");

  const payout = requireRecord(records, "t_payout", "92001");
  const execution = requireRecord(records, "t_payout_execution", "93001");
  const finalApproval = byName.get("FINAL_APPROVAL");
  const externalSubmit = byName.get("EXTERNAL_SUBMIT");
  for (const [checkpoint, target, persisted] of [
    [finalApproval, "PAYOUT:92001", payout],
    [externalSubmit, "EXECUTION:93001", execution],
  ]) {
    assert.equal(valueOf(checkpoint, "frozen_target"), target);
    assert.ok(identifierEqual(valueOf(checkpoint, "assessment_id"), valueOf(persisted, "assessment_id")));
    assert.ok(identifierEqual(valueOf(checkpoint, "evaluation_id"), valueOf(persisted, "evaluation_id")));
    assert.equal(valueOf(checkpoint, "control_set_hash"), valueOf(persisted, "control_set_hash"));
  }
  for (const checkpoint of checkpoints) {
    for (const forbidden of ["result_id", "result_version", "result_hash"]) {
      assert.equal(checkpoint.values.has(forbidden), false, `Risk 合同不得新增 ${forbidden}`);
    }
    assert.equal(valueOf(checkpoint, "owner_type"), valueOf(payout, "owner_type"), `${checkpoint.name} owner_type Scope mismatch`);
    assert.ok(identifierEqual(valueOf(checkpoint, "owner_id"), valueOf(payout, "owner_id")), `${checkpoint.name} owner_id Scope mismatch`);
    assert.ok(identifierEqual(valueOf(checkpoint, "entity_no"), valueOf(payout, "entity_no")),
      `${checkpoint.name} entity_no Scope mismatch`);
    assert.equal(valueOf(checkpoint, "currency"), valueOf(payout, "currency"), `${checkpoint.name} currency Scope mismatch`);
    assert.ok(fixedEqual(fixedRaw(valueOf(checkpoint, "amount")), fixedValue(payout, "cash_required_amount")),
      `${checkpoint.name} amount Scope mismatch`);
    assert.equal(valueOf(checkpoint, "scope_hash"), "hash-scope-92001", `${checkpoint.name} scope_hash mismatch`);
  }
  assert.ok(!identifierEqual(valueOf(byName.get("PAYOUT_CREATE"), "assessment_id"), valueOf(payout, "assessment_id")),
    "Finance eligibility_hash 不得跳过最终审批 checkpoint");
  assert.ok(!identifierEqual(valueOf(byName.get("EXECUTION_CREATE"), "assessment_id"), valueOf(execution, "assessment_id")),
    "Finance eligibility_hash 不得跳过外部提交 checkpoint");
  assert.ok(!isNull(valueOf(payout, "eligibility_hash")) && !isNull(valueOf(execution, "eligibility_hash")));
  return firstPairs.size;
}

function parsePartnerPayoutGateRows(content) {
  const names = new Set(["PARTNER_PAYOUT_BLOCKED", "PARTNER_PAYOUT_ALLOWED"]);
  return content.split(/\n/u).flatMap((line, index) => {
    const match = line.match(/^\| `([A-Z_]+)` \| (.*) \|$/u);
    if (!match || !names.has(match[1])) return [];
    const values = new Map();
    for (const fragment of match[2].split("，")) {
      const assignment = fragment.match(/^([a-z][a-z0-9_]*)=([^，]+)$/u);
      assert.ok(assignment, `Partner gate 第 ${index + 1} 行无法解析片段：${fragment}`);
      assert.ok(!values.has(assignment[1]), `Partner gate 第 ${index + 1} 行重复字段 ${assignment[1]}`);
      values.set(assignment[1], assignment[2]);
    }
    return [{ name: match[1], values, lineNumber: index + 1 }];
  });
}

function validatePartnerPayoutGates(gates, records) {
  const byName = new Map(gates.map((gate) => [gate.name, gate]));
  assert.equal(gates.length, 2, "Partner Payout gate 必须包含 blocked 与 allowed 两个快照");
  assert.equal(byName.size, 2, "Partner Payout gate 名称不得重复");
  const blocked = byName.get("PARTNER_PAYOUT_BLOCKED");
  const allowed = byName.get("PARTNER_PAYOUT_ALLOWED");
  const account = requireRecord(records, "t_account", valueOf(blocked, "recovery_account_id"));
  assert.ok(identifierEqual(valueOf(allowed, "recovery_account_id"), valueOf(blocked, "recovery_account_id")));
  assert.equal(valueOf(account, "owner_type"), "LOCAL_PARTNER");
  assert.ok(identifierEqual(valueOf(account, "owner_id"), "41001"));
  assert.equal(valueOf(account, "balance_bucket"), "NONE");
  const chart = requireRecord(records, "t_chart_account", valueOf(account, "chart_account_id"));
  assert.equal(valueOf(chart, "account_code"), "PARTNER_RECOVERY_RECEIVABLE");
  const blockedBalance = fixedRaw(valueOf(blocked, "snapshot_balance"));
  const allowedBalance = fixedRaw(valueOf(allowed, "snapshot_balance"));
  assert.ok(fixedCompare(blockedBalance, EXACT_ZERO) > 0);
  assert.equal(valueOf(blocked, "decision"), "BLOCKED");
  assert.ok(isNull(valueOf(blocked, "payout_id")), "Partner Recovery 非零时不得创建 Payout root");
  const blockedJournal = journalSummary(records, valueOf(blocked, "source_journal_id"));
  assert.ok(blockedJournal.lines.some((line) => identifierEqual(valueOf(line, "account_id"), valueOf(account, "id"))
    && valueOf(line, "entry_side") === "DEBIT"
    && fixedEqual(fixedValue(line, "amount"), blockedBalance)));
  assert.ok(fixedEqual(allowedBalance, EXACT_ZERO));
  assert.equal(valueOf(allowed, "decision"), "ALLOW");
  const payout = requireRecord(records, "t_payout", valueOf(allowed, "payout_id"));
  assert.equal(valueOf(payout, "owner_type"), "LOCAL_PARTNER");
  assert.ok(identifierEqual(valueOf(payout, "owner_id"), "41001"));
  const allowedJournal = journalSummary(records, valueOf(allowed, "source_journal_id"));
  assert.ok(allowedJournal.lines.some((line) => identifierEqual(valueOf(line, "account_id"), valueOf(account, "id"))
    && valueOf(line, "entry_side") === "CREDIT"
    && fixedEqual(fixedValue(line, "amount"), blockedBalance)));
  assert.ok(fixedEqual(fixedValue(account, "net_balance"), EXACT_ZERO));
  assert.ok(Date.parse(valueOf(blocked, "observed_time")) < Date.parse(valueOf(allowed, "observed_time")));
  return { blockedBalance: fixedFormat(blockedBalance), allowedPayoutId: valueOf(payout, "id") };
}

function validateBeneficiaryReplacement(records) {
  const oldAccount = requireRecord(records, "t_beneficiary_account", "91001");
  const replacement = requireRecord(records, "t_beneficiary_account", "91003");
  assert.ok(["SUSPENDED", "CLOSED"].includes(valueOf(oldAccount, "status")), "旧 Beneficiary 必须非 current");
  assert.equal(valueOf(replacement, "status"), "VERIFIED");
  assert.equal(valueOf(oldAccount, "owner_type"), valueOf(replacement, "owner_type"));
  assert.ok(identifierEqual(valueOf(oldAccount, "owner_id"), valueOf(replacement, "owner_id")));
  assert.equal(valueOf(oldAccount, "currency"), valueOf(replacement, "currency"));
  assert.ok(!identifierEqual(valueOf(oldAccount, "id"), valueOf(replacement, "id")),
    "Beneficiary replacement 必须更换 id");
  for (const field of ["account_code", "bank_account_blind_index", "account_last4", "content_hash"]) {
    assert.notEqual(valueOf(oldAccount, field), valueOf(replacement, field), `Beneficiary replacement 必须更换 ${field}`);
  }
  const historical = recordsFor(records, "t_payout")
    .filter((payout) => identifierEqual(valueOf(payout, "beneficiary_account_id"), valueOf(oldAccount, "id")));
  assert.ok(historical.length > 0, "旧 Beneficiary 必须保留历史 Payout 引用");
  for (const payout of historical) {
    assert.equal(valueOf(payout, "beneficiary_content_hash"), valueOf(oldAccount, "content_hash"));
    assert.ok(Date.parse(valueOf(payout, "approved_time")) < Date.parse(valueOf(replacement, "decision_time")),
      `Payout ${valueOf(payout, "id")} 必须早于 Beneficiary replacement`);
  }
  return historical.length;
}

function validateManualPayoutGraph(records) {
  const payouts = recordsFor(records, "t_payout");
  const executions = recordsFor(records, "t_payout_execution");
  const executionsByPayout = groupByInteger(executions, "payout_id");
  const beneficiaries = recordsById(records, "t_beneficiary_account");
  const accounts = recordsById(records, "t_account");
  const ledgerBooks = recordsById(records, "t_ledger_book");
  const bindings = recordsById(records, "t_cash_account_binding_ver");
  const instructions = recordsById(records, "t_accounting_instruction");
  const journals = recordsById(records, "t_journal_entry");
  const bankItems = recordsById(records, "t_bank_statement_item");
  const matches = recordsFor(records, "t_bank_recon_match");
  const evidence = recordsFor(records, "t_payout_evidence_ref");
  const exceptions = recordsFor(records, "t_recon_exception");
  const resolutions = recordsById(records, "t_recon_resolution");
  const statusMatrix = new Map([
    ["APPROVED", "CREATED"],
    ["CANCELLED", "ABORTED"],
    ["PAYMENT_UNKNOWN", "UNKNOWN"],
    ["RETRY_REQUIRED", "FAILED"],
    ["PAID", "RECONCILED"],
  ]);

  validatePostedJournalReplay(records);

  for (const payout of payouts) {
    const beneficiary = beneficiaries.get(valueOf(payout, "beneficiary_account_id"));
    assert.ok(beneficiary, `Payout ${valueOf(payout, "id")} 缺少 Beneficiary`);
    assert.ok(["VERIFIED", "SUSPENDED", "CLOSED"].includes(valueOf(beneficiary, "status")));
    assert.equal(valueOf(beneficiary, "owner_type"), valueOf(payout, "owner_type"));
    assert.ok(identifierEqual(valueOf(beneficiary, "owner_id"), valueOf(payout, "owner_id")));
    assert.equal(valueOf(beneficiary, "currency"), valueOf(payout, "currency"));
    assert.equal(valueOf(beneficiary, "content_hash"), valueOf(payout, "beneficiary_content_hash"));

    const available = accounts.get(valueOf(payout, "available_account_id"));
    const locked = accounts.get(valueOf(payout, "locked_account_id"));
    assert.ok(available && locked, `Payout ${valueOf(payout, "id")} 缺少 Owner Account`);
    assert.ok(valueOf(available, "owner_type") === valueOf(payout, "owner_type")
      && identifierEqual(valueOf(available, "owner_id"), valueOf(payout, "owner_id"))
      && valueOf(available, "balance_bucket") === "AVAILABLE");
    assert.ok(valueOf(locked, "owner_type") === valueOf(payout, "owner_type")
      && identifierEqual(valueOf(locked, "owner_id"), valueOf(payout, "owner_id"))
      && valueOf(locked, "balance_bucket") === "LOCKED");
    assert.ok(identifierEqual(valueOf(available, "chart_account_id"), valueOf(locked, "chart_account_id")));
    const payoutBook = ledgerBooks.get(valueOf(available, "ledger_book_id"));
    assert.ok(payoutBook
      && identifierEqual(valueOf(locked, "ledger_book_id"), valueOf(payoutBook, "id"))
      && valueOf(payoutBook, "entity_type") === "OPERATING_ENTITY"
      && identifierEqual(valueOf(payoutBook, "entity_id"), valueOf(payout, "entity_no"))
      && valueOf(payoutBook, "currency") === valueOf(payout, "currency"),
    `Payout ${valueOf(payout, "id")} Owner Account Ledger Book OE/currency scope 不一致`);

    const payoutExecutions = executionsByPayout.get(valueOf(payout, "id")) ?? [];
    const withdrawalWithoutExecution = valueOf(payout, "status") === "CANCELLED"
      && payoutExecutions.length === 0
      && recordsFor(records, "t_payout_event").some((event) => identifierEqual(valueOf(event, "payout_id"), valueOf(payout, "id"))
        && valueOf(event, "event_scope") === "PAYOUT"
        && valueOf(event, "from_status") === "PENDING_APPROVAL"
        && valueOf(event, "to_status") === "CANCELLED");
    if (!withdrawalWithoutExecution) {
      assert.ok(payoutExecutions.length > 0, `Payout ${valueOf(payout, "id")} 缺少 Execution`);
      assert.ok(payoutExecutions.some((execution) => valueOf(execution, "status") === statusMatrix.get(valueOf(payout, "status"))),
        `Payout ${valueOf(payout, "id")} 缺少与 Root 对应的终态 Execution`);
    }
    const lockInstruction = recordsFor(records, "t_accounting_instruction")
      .find((record) => valueOf(record, "source_event_key") === `PAYOUT_LOCK:${identifierKey(valueOf(payout, "id"))}`);
    assert.ok(lockInstruction, `Payout ${valueOf(payout, "id")} 缺少 PAYOUT_LOCK`);
    const lockJournal = recordsFor(records, "t_journal_entry")
      .find((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(lockInstruction, "id")));
    journalSummary(records, valueOf(lockJournal, "id"));
  }

  validateApprovalPairs(recordsFor(records, "t_payout_approval"));
  validatePayoutApprovalEvents(records);
  validateExecutionAttempts(executions);
  validatePayoutEventGraph(payouts, executions, recordsFor(records, "t_payout_event"));
  validateRetryRequiredEvents(records);
  validatePayoutCreationJournals(records);
  validateCancellationRelease(records);
  validateExecutionResolutionEvidence(records);
  validatePayoutEventAuthority(records);
  validateCancelledPayoutSafety(payouts, executions,
    matches.filter((record) => valueOf(record, "match_type") === "PAYOUT_DEBIT"), evidence);
  validateFeeDifferenceTimeline(records);
  validateJournalPeriods(records);
  validateBeneficiaryReplacement(records);
  for (const cancelChecker of recordsFor(records, "t_payout_approval")
    .filter((record) => valueOf(record, "action_type") === "PAYOUT_CANCEL"
      && valueOf(record, "approval_role") === "CHECKER" && valueOf(record, "decision") === "APPROVED")) {
    const matchingEvents = recordsFor(records, "t_payout_event").filter((event) => identifierEqual(valueOf(event, "approval_id"), valueOf(cancelChecker, "id")));
    assert.equal(matchingEvents.length, 1, `Cancel Checker ${valueOf(cancelChecker, "id")} 必须被唯一 Event 引用`);
    assert.ok(identifierEqual(valueOf(matchingEvents[0], "payout_id"), valueOf(cancelChecker, "payout_id")));
    assert.equal(valueOf(matchingEvents[0], "event_scope"), "PAYOUT");
  }

  for (const execution of executions) {
    const payout = requireRecord(records, "t_payout", valueOf(execution, "payout_id"));
    const binding = bindings.get(valueOf(execution, "cash_account_binding_ver_id"));
    assert.ok(binding, `Execution ${valueOf(execution, "id")} 缺少 Cash Binding Version`);
    assert.ok(identifierEqual(valueOf(binding, "cash_account_id"), valueOf(execution, "cash_account_id")));
    const executionRiskAmount = fixedValue(execution, "risk_required_amount");
    const payoutRiskAmount = fixedValue(payout, "risk_required_amount");
    assert.equal(executionRiskAmount === null, payoutRiskAmount === null);
    if (executionRiskAmount !== null) assert.ok(fixedEqual(executionRiskAmount, payoutRiskAmount));
    assert.equal(valueOf(execution, "risk_required_currency"), valueOf(payout, "risk_required_currency"));
  }

  const payoutMatches = matches.filter((record) => valueOf(record, "match_type") === "PAYOUT_DEBIT"
    && valueOf(record, "status") === "CONFIRMED");
  for (const match of payoutMatches) {
    const execution = requireRecord(records, "t_payout_execution", valueOf(match, "payout_execution_id"));
    const item = bankItems.get(valueOf(match, "bank_item_id"));
    assert.ok(item, `Bank Match ${valueOf(match, "id")} 缺少 Statement Item`);
    assert.equal(valueOf(item, "direction"), "DEBIT");
    assert.ok(fixedRecordEqual(item, "amount", match, "matched_amount"));
    assert.ok(identifierEqual(valueOf(match, "cash_account_binding_ver_id"), valueOf(execution, "cash_account_binding_ver_id")));
  }

  const paid = payouts.filter((payout) => valueOf(payout, "status") === "PAID");
  for (const payout of paid) {
    const execution = executionsByPayout.get(valueOf(payout, "id"))
      .find((candidate) => valueOf(candidate, "status") === "RECONCILED");
    const confirmed = payoutMatches.filter((match) => identifierEqual(valueOf(match, "payout_execution_id"), valueOf(execution, "id")));
    assert.ok(confirmed.length > 0, `PAID Payout ${valueOf(payout, "id")} 缺少 CONFIRMED Bank Match`);
    if (confirmed.length === 1) {
      assert.equal(valueOf(confirmed[0], "payout_component_type"), "COMBINED");
    } else {
      assert.deepEqual(new Set(confirmed.map((match) => valueOf(match, "payout_component_type"))),
        new Set(["BENEFICIARY_TRANSFER", "BANK_FEE"]));
      const beneficiaryMatch = confirmed
        .find((match) => valueOf(match, "payout_component_type") === "BENEFICIARY_TRANSFER");
      const feeMatch = confirmed.find((match) => valueOf(match, "payout_component_type") === "BANK_FEE");
      assert.ok(fixedRecordEqual(beneficiaryMatch, "matched_amount", payout, "beneficiary_amount"));
      assert.ok(fixedRecordEqual(feeMatch, "matched_amount", payout, "fee_amount"));
    }
    const journal = journals.get(valueOf(payout, "journal_entry_id"));
    assert.ok(journal, `PAID Payout ${valueOf(payout, "id")} 的 final Journal 不存在`);
    const instruction = instructions.get(valueOf(journal, "instruction_id"));
    assert.equal(valueOf(instruction, "instruction_type"), "PAYOUT_PAID");
    assert.equal(valueOf(instruction, "source_event_key"), `PAYOUT_PAID:${identifierKey(valueOf(payout, "id"))}`);
    const finalSummary = journalSummary(records, valueOf(journal, "id"));
    assert.ok(finalSummary.lines.some((line) => identifierEqual(valueOf(line, "account_id"), "20207")
      && valueOf(line, "entry_side") === "CREDIT"));

    const actualDebit = fixedSumRecords(confirmed, "matched_amount");
    const delta = fixedAbs(fixedSubtract(actualDebit, fixedValue(payout, "cash_required_amount")));
    const feeException = exceptions.find((record) => valueOf(record, "source_type") === "PAYOUT_EXECUTION"
      && identifierEqual(valueOf(record, "source_id"), valueOf(execution, "id"))
      && valueOf(record, "exception_type") === "FEE_DIFFERENCE");
    if (fixedEqual(delta, EXACT_ZERO)) {
      assert.equal(feeException, undefined, `Payout ${valueOf(payout, "id")} 无差额却有 FEE_DIFFERENCE`);
    } else {
      assert.ok(feeException, `Payout ${valueOf(payout, "id")} 差额缺少 Recon Exception`);
      assert.ok(fixedEqual(fixedValue(feeException, "difference_amount"), delta));
      const resolution = resolutions.get(valueOf(feeException, "applied_resolution_id"));
      assert.equal(valueOf(resolution, "status"), "APPLIED");
      const diffInstruction = instructions.get(valueOf(resolution, "instruction_id"));
      assert.ok(["PAYOUT_FEE_INCREASE", "PAYOUT_FEE_DECREASE"].includes(valueOf(diffInstruction, "instruction_type")),
        `Payout ${valueOf(payout, "id")} fee diff Instruction 未按方向拆分`);
      const diffJournal = recordsFor(records, "t_journal_entry")
        .find((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(diffInstruction, "id")));
      journalSummary(records, valueOf(diffJournal, "id"));
    }
  }

  const payoutBinding = recordsFor(records, "t_cash_account_binding")
    .find((record) => valueOf(record, "account_purpose") === "PAYOUT");
  assert.ok(payoutBinding, "缺少 account_purpose=PAYOUT 的 Cash Binding");
  assert.equal(recordsFor(records, "t_account").some((record) => identifierEqual(valueOf(record, "id"), "20307")), false);
  assert.equal(valueOf(requireRecord(records, "t_account", "20207"), "owner_type"), "CASH_ACCOUNT");

  for (const journal of recordsFor(records, "t_journal_entry")) {
    journalSummary(records, valueOf(journal, "id"));
  }
  validateTaxPaymentGraph(records);
  validateAccountingRuleReplay(records);
  validateTaxAndChannelTuples(records);
  validateLateDebitCausality(records);
  validateBookAndAccountSequences(records);
  return {
    payoutCount: payouts.length,
    executionCount: executions.length,
    paidCount: paid.length,
    confirmedPayoutMatchCount: payoutMatches.length,
    balancedJournalCount: recordsFor(records, "t_journal_entry").length,
  };
}

test("scenario record parser rejects duplicate fields and unparsed fragments", () => {
  assert.throws(
    () => parseScenarioRecordLine("| `t_payout` | id=1，id=2 |", 1),
    /重复字段 id/u,
  );
  assert.throws(
    () => parseScenarioRecordLine("| `t_payout` | id=1，not-an-assignment |", 2),
    /无法解析/u,
  );
  assert.throws(
    () => scenarioRows("| `t_payout` | id=1"),
    /无法解析表记录/u,
  );
});

test("Manual Payout CHECK validator rejects invalid execution and event matrices", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const rejects = (record, pattern) => assert.throws(
    () => validateAllOracleChecks([record], model, { requireAllContracts: false }),
    pattern,
  );
  const validRisk = {
    assessment_id: "990001",
    evaluation_id: "990002",
    eligibility_decision: "ALLOW",
    eligibility_reason_codes: "[]",
    eligibility_matched_controls: "[]",
    risk_required_amount: "5",
    risk_required_currency: "USD",
    control_set_hash: "hash-controls",
    eligibility_hash: "hash-eligibility",
  };
  rejects({
      table: "t_payout_execution",
      values: new Map(Object.entries({
        ...validRisk,
        status: "ABORTED",
        active_slot: "NULL",
        submitted_time: "NULL",
        resolved_time: "2026-08-31T11:10:00.000000Z",
        failure_code: "ABORTED_BEFORE_SUBMIT",
      })),
    }, /ck_payout_execution_state/u);
  rejects({
      table: "t_payout_execution",
      values: new Map(Object.entries({
        ...validRisk,
        status: "UNKNOWN",
        active_slot: "1",
        submitted_time: "2026-08-31T11:20:00.000000Z",
        resolved_time: "NULL",
        failure_code: "BANK_RESULT_UNKNOWN",
      })),
    }, /ck_payout_execution_state/u);
  rejects({
      table: "t_payout_event",
      values: new Map(Object.entries({
        event_scope: "PAYOUT",
        execution_id: "93001",
        execution_version_after: "2",
      })),
    }, /ck_payout_event_scope/u);
  rejects({
      table: "t_payout_approval",
      values: new Map(Object.entries({
        action_type: "PAYOUT_APPROVAL",
        action_no: "1",
        decision: "REJECTED",
        requested_from_status: "PENDING_APPROVAL",
        reason_code: "NULL",
        evidence_ref_id: "NULL",
      })),
    }, /ck_payout_approval_action/u);
});

test("DECIMAL validator enforces generated precision and scale", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const payout = model.entities.find((entity) => entity.defKey === "t_payout");
  const amount = payout.fields.find((field) => field.defKey === "requested_amount");
  assert.equal(amount.len, 24);
  assert.equal(amount.scale, 8);
  assert.throws(() => assertValueMatchesType("t_payout", amount, "12345678901234567.00000000"), /整数位|精度/u);
  assert.throws(() => assertValueMatchesType("t_payout", amount, "12345678901234567"), /整数位/u);
  assert.throws(() => assertValueMatchesType("t_payout", amount, "1.000000000"), /小数位/u);
});

test("exact DECIMAL CHECKs preserve cents above 2^53", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const valid = {
    table: "t_payout",
    lineNumber: 1,
    values: new Map(Object.entries({
      requested_amount: "9007199254740992.00",
      fee_amount: "0.01",
      owner_debit_amount: "9007199254740992.01",
      beneficiary_amount: "9007199254740992.00",
      cash_required_amount: "9007199254740992.01",
      fee_mode: "ADDED_ON_TOP",
    })),
  };
  assert.doesNotThrow(() => validateExactDecimalChecks([valid], model, { requireAllDerived: false }));
  const invalid = { ...valid, values: new Map(valid.values) };
  invalid.values.set("owner_debit_amount", "9007199254740992.00");
  invalid.values.set("cash_required_amount", "9007199254740992.00");
  assert.throws(
    () => validateExactDecimalChecks([invalid], model, { requireAllDerived: false }),
    /ck_payout_amounts/u,
  );
});

test("exact money validators reject journal, fee and allocation rounding aliases", () => {
  const journalRows = [
    { table: "t_journal_entry", values: new Map(Object.entries({ id: "1" })) },
    { table: "t_journal_line", values: new Map(Object.entries({ journal_entry_id: "1", entry_side: "DEBIT", amount: "9007199254740992.01" })) },
    { table: "t_journal_line", values: new Map(Object.entries({ journal_entry_id: "1", entry_side: "CREDIT", amount: "9007199254740992.00" })) },
  ];
  assert.throws(() => journalSummary(journalRows, "1"), /借贷不平衡/u);

  const feeRows = scenarioRows(readFileSync(scenarioPath, "utf8"))
    .map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(feeRows, "t_payout", "92007").values.set("cash_required_amount", "9007199254740992.00");
  requireRecord(feeRows, "t_bank_recon_match", "40509").values.set("matched_amount", "9007199254740992.01");
  requireRecord(feeRows, "t_recon_exception", "40532").values.set("difference_amount", "0");
  assert.throws(() => validateFeeDifferenceTimeline(feeRows), /金额.*差额/u);

  const allocationRows = scenarioRows(readFileSync(scenarioPath, "utf8"))
    .map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(allocationRows, "t_channel_cost_confirm", "100039").values.set("actual_cost", "9007199254740992.01");
  requireRecord(allocationRows, "t_channel_cost_alloc", "100040").values.set("allocated_amount", "9007199254740992.00");
  assert.throws(() => validateTaxAndChannelTuples(allocationRows), /allocation.*actual_cost/u);
});

test("POSTED replay rejects Instruction and Journal headers from different Ledger Books", () => {
  const rows = clonedScenarioRows();
  const { book } = appendSecondLedgerBook(rows);
  requireRecord(rows, "t_accounting_instruction", "100044")
    .values.set("ledger_book_id", valueOf(book, "id"));
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validatePostedJournalReplay(rows),
    /Instruction 100044.*Journal 100046.*Ledger Book/u,
  );
});

test("POSTED replay rejects synchronized Lines whose Accounts belong to another Book", () => {
  const rows = clonedScenarioRows();
  const { control, suspense } = appendSecondLedgerBook(rows);
  for (const line of recordsFor(rows, "t_instruction_line")
    .filter((record) => identifierEqual(valueOf(record, "instruction_id"), "100044"))) {
    line.values.set("account_id", numberOf(line, "line_no") === 1 ? valueOf(control, "id") : valueOf(suspense, "id"));
  }
  for (const line of recordsFor(rows, "t_journal_line")
    .filter((record) => identifierEqual(valueOf(record, "journal_entry_id"), "100046"))) {
    line.values.set("account_id", numberOf(line, "line_no") === 1 ? valueOf(control, "id") : valueOf(suspense, "id"));
  }
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validatePostedJournalReplay(rows),
    /Journal 100046.*Account.*Ledger Book/u,
  );
});

test("POSTED replay rejects an Accounting Period owned by another Book entity", () => {
  const rows = clonedScenarioRows();
  const { period } = appendSecondLedgerBook(rows);
  requireRecord(rows, "t_journal_entry", "100046")
    .values.set("accounting_period_id", valueOf(period, "id"));
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validatePostedJournalReplay(rows),
    /Journal 100046.*Accounting Period.*Ledger Book/u,
  );
});

test("a legal retired Rule Bundle Version does not alter the current 25-line catalog", () => {
  const rows = clonedScenarioRows();
  appendRetiredRuleBundle(rows);
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.doesNotThrow(() => validateAccountingRuleReplay(rows));
});

test("AUTO Instruction Lines reject a Rule from a different frozen Bundle Version", () => {
  const rows = clonedScenarioRows();
  const history = appendRetiredRuleBundle(rows);
  const historicalRule = history.rulesByCurrentId.get("109003");
  requireRecord(rows, "t_instruction_line", "321411")
    .values.set("accounting_rule_line_id", valueOf(historicalRule, "id"));
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /AUTO Instruction 32141.*冻结 Rule Bundle Version/u,
  );
});

test("current Rule Bundle Version rejects a missing approved catalog coordinate", () => {
  const removedIds = integerSet(["109004", "321412", "322412"]);
  const rows = clonedScenarioRows().filter((record) => !removedIds.has(identifierKey(valueOf(record, "id"))));
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /current Rule Bundle Version.*25.*catalog/u,
  );
});

test("AUTO source_version binds the decisive authority version for every posting family", () => {
  for (const instructionId of ["33001", "32154", "32151", "32158", "32161", "32170"]) {
    const rows = clonedScenarioRows();
    requireRecord(rows, "t_accounting_instruction", instructionId).values.set("source_version", "999");
    assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
    assert.throws(
      () => validateAccountingSourceAuthority(rows),
      new RegExp(`Instruction ${instructionId}.*source_version`, "u"),
    );
  }
});

test("PAID Payout Event rejects cross-fact source, evidence and Journal coordinates", () => {
  for (const [field, wrongValue] of [
    ["source_id", "40508"],
    ["evidence_ref_id", "93503"],
    ["journal_entry_id", "32402"],
  ]) {
    const rows = clonedScenarioRows();
    requireRecord(rows, "t_payout_event", "94106").values.set(field, wrongValue);
    assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
    assert.throws(
      () => validateManualPayoutGraph(rows),
      /Payout Event 94106.*authority/u,
    );
  }
});

test("Fee Resolution and Exception require a bidirectional one-to-one authority tuple", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_recon_resolution", "40531").values.set("exception_id", "40532");
  requireRecord(rows, "t_recon_resolution", "40533").values.set("exception_id", "40530");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Recon (?:Exception|Resolution).*双向/u,
  );
});

test("Tax RECONCILED Event source_ref rejects a Channel Bank Match", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_tax_payment_event", "100133").values.set("source_ref", "40500");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateTaxPaymentGraph(rows),
    /Tax Payment 100030.*RECONCILED.*Match/u,
  );
});

test("unmatched Channel Debit Item rejects another Channel and MID identity", () => {
  const rows = clonedScenarioRows();
  const otherChannel = appendClonedRecord(rows, "t_channel_account", "100051", "999051");
  otherChannel.values.set("channel_id", "2");
  otherChannel.values.set("mid_id", "2");
  requireRecord(rows, "t_channel_debit_item", "100053")
    .values.set("channel_account_id", valueOf(otherChannel, "id"));
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateTaxAndChannelTuples(rows),
    /Channel Debit Item 100053.*Channel Account/u,
  );
});

test("Channel Cost Allocation rejects wrong Attempt and wrong Economics target shapes", () => {
  for (const [field, wrongValue] of [
    ["payment_attempt_id", "101003"],
    ["transaction_economics_id", "31020"],
  ]) {
    const rows = clonedScenarioRows();
    requireRecord(rows, "t_channel_cost_alloc", "100040").values.set(field, wrongValue);
    assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
    assert.throws(
      () => validateTaxAndChannelTuples(rows),
      /Channel Cost Alloc 100040.*target tuple/u,
    );
  }
});

test("Channel CREDIT Batch rejects a CHANNEL_DEBIT Bank Match label", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_bank_recon_match", "40500").values.set("match_type", "CHANNEL_DEBIT");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateTaxAndChannelTuples(rows),
    /Channel Bank Match 40500.*match_type/u,
  );
});

test("Channel Transaction Match rejects a Settlement external transaction mismatch", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_channel_settle_item", "100056")
    .values.set("external_transaction_id", "NI-SALE-WRONG");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateTaxAndChannelTuples(rows),
    /Channel Transaction Match 100057.*external_transaction_id/u,
  );
});

test("full semantic graph accepts leading-zero aliases for every integer FK", () => {
  for (const [table, id, field, alias] of [
    ["t_payout_execution", "93001", "payout_id", "092001"],
    ["t_instruction_line", "321411", "instruction_id", "032141"],
    ["t_channel_debit_item", "100053", "channel_account_id", "0100051"],
  ]) {
    const rows = clonedScenarioRows();
    requireRecord(rows, table, id).values.set(field, alias);
    assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
    assert.doesNotThrow(() => validateManualPayoutGraph(rows));
  }
});

test("Maker and Checker reject the same numeric actor identity with different leading zeroes", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_payout_approval", "92510").values.set("actor_id", "090001");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /92004:PAYOUT_CANCEL:2 Maker\/Checker actor 必须不同/u,
  );
});

test("current Accounting Rule Bundle rejects an optional undeclared 26th Rule Line", () => {
  const rows = clonedScenarioRows();
  const optional = appendClonedRecord(rows, "t_accounting_rule_line", "109028", "199998");
  optional.values.set("line_no", "99");
  optional.values.set("line_code", "OPTIONAL_UNUSED");
  optional.values.set("required_flag", "0");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /current Rule Bundle Version.*25-line required catalog/u,
  );
});

test("Channel Transaction Match amounts must exactly replay the authoritative Settlement Item", () => {
  const rows = clonedScenarioRows();
  const match = requireRecord(rows, "t_channel_transaction_match", "100057");
  match.values.set("gross_amount", "104");
  match.values.set("fee_amount", "2");
  match.values.set("net_amount", "102");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateTaxAndChannelTuples(rows),
    /Channel Settlement Item 100056.*matched gross\/fee\/net/u,
  );
});

test("VALIDATED Channel Batch rejects an orphan Settlement Item", () => {
  const rows = clonedScenarioRows();
  const orphan = appendClonedRecord(rows, "t_channel_settle_item", "100056", "999056");
  orphan.values.set("item_no", "2");
  orphan.values.set("channel_item_ref", "NI-SALE-ORPHAN");
  orphan.values.set("external_transaction_id", "NI-SALE-ORPHAN");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateTaxAndChannelTuples(rows),
    /Channel Settlement Item 999056.*orphan/u,
  );
});

test("retry Execution rejects a numerically reused Risk pair with leading zeroes", () => {
  const rows = clonedScenarioRows();
  const retry = requireRecord(rows, "t_payout_execution", "93010");
  retry.values.set("assessment_id", "01085006");
  retry.values.set("evaluation_id", "01086006");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Payout 92006 重试必须使用 fresh (?:assessment|evaluation)/u,
  );
});

test("full semantic graph rejects a numeric alias for the exact Tax Operating Entity code", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_tax_registration", "100011").values.set("entity_id", "050001");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(() => validateManualPayoutGraph(rows));
});

test("Channel Batch rejects synchronized Item and Match drift from Batch and target settlement", () => {
  const rows = clonedScenarioRows();
  for (const table of ["t_channel_settle_item", "t_channel_transaction_match"]) {
    const record = requireRecord(rows, table, table === "t_channel_settle_item" ? "100056" : "100057");
    record.values.set("gross_amount", "104");
    record.values.set("fee_amount", "2");
    record.values.set("net_amount", "102");
  }
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel (?:Batch 100055.*四个金额|Open Item 100052.*settled)/u,
  );
});

test("Channel target settlement rejects zero-match SETTLED and PARTIALLY_SETTLED totals", () => {
  for (const [status, settled, outstanding] of [
    ["SETTLED", "20", "0"],
    ["PARTIALLY_SETTLED", "10", "10"],
  ]) {
    const rows = clonedScenarioRows();
    const debitItem = requireRecord(rows, "t_channel_debit_item", "100053");
    debitItem.values.set("status", status);
    debitItem.values.set("settled_amount", settled);
    debitItem.values.set("outstanding_amount", outstanding);
    assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
    assert.throws(
      () => validateManualPayoutGraph(rows),
      /Channel Debit Item 100053.*Match.*settled/u,
    );
  }
});

test("Channel Cost Allocation rejects Economics outside its matched Batch target set", () => {
  const rows = clonedScenarioRows();
  const allocation = requireRecord(rows, "t_channel_cost_alloc", "100040");
  allocation.values.set("payment_attempt_id", "NULL");
  allocation.values.set("channel_refund_id", "105001");
  allocation.values.set("transaction_economics_id", "31020");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel Cost Alloc 100040.*同 Batch.*Economics/u,
  );
});

test("Channel FEE Item cannot fake Cost completeness or escape Batch aggregation", () => {
  const rows = clonedScenarioRows();
  const feeItem = appendClonedRecord(rows, "t_channel_settle_item", "100056", "199956");
  feeItem.values.set("item_no", "2");
  feeItem.values.set("channel_item_ref", "NI-FEE-ORPHAN");
  feeItem.values.set("item_type", "FEE");
  feeItem.values.set("settlement_direction", "DEBIT");
  feeItem.values.set("reference_type", "CHANNEL_FEE");
  feeItem.values.set("external_transaction_id", "NI-FEE-ORPHAN");
  feeItem.values.set("gross_amount", "0");
  feeItem.values.set("fee_amount", "7");
  feeItem.values.set("net_amount", "7");
  requireRecord(rows, "t_channel_cost_confirm", "100039")
    .values.set("channel_settle_item_id", "199956");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel (?:Cost Confirm 100039.*FEE Item|Batch 100055.*四个金额)/u,
  );
});

test("Channel FEE controlled Exception is not misclassified as a Payout fee difference", () => {
  const rows = clonedScenarioRows();
  const feeItem = appendClonedRecord(rows, "t_channel_settle_item", "100056", "199956");
  feeItem.values.set("item_no", "2");
  feeItem.values.set("channel_item_ref", "NI-FEE-CONTROLLED");
  feeItem.values.set("item_type", "FEE");
  feeItem.values.set("settlement_direction", "DEBIT");
  feeItem.values.set("reference_type", "CHANNEL_FEE");
  feeItem.values.set("external_transaction_id", "NI-FEE-CONTROLLED");
  feeItem.values.set("gross_amount", "0");
  feeItem.values.set("fee_amount", "7");
  feeItem.values.set("net_amount", "7");
  const batch = requireRecord(rows, "t_channel_settle_batch", "100055");
  batch.values.set("fee_amount", "9");
  batch.values.set("net_amount", "96");
  requireRecord(rows, "t_bank_statement_item", "40401").values.set("amount", "96");
  requireRecord(rows, "t_bank_recon_match", "40500").values.set("matched_amount", "96");

  const exception = appendClonedRecord(rows, "t_recon_exception", "40530", "49934");
  exception.values.set("source_type", "CHANNEL_SETTLE_ITEM");
  exception.values.set("source_id", "199956");
  exception.values.set("difference_amount", "7");
  exception.values.set("applied_resolution_id", "49935");
  const resolution = appendClonedRecord(rows, "t_recon_resolution", "40531", "49935");
  resolution.values.set("exception_id", "49934");
  resolution.values.set("resolution_type", "ACCEPT_TIMING_DIFFERENCE");
  resolution.values.set("instruction_id", "NULL");
  resolution.values.set("content_hash", "hash-channel-fee-controlled");
  resolution.values.set("approved_content_hash", "hash-channel-fee-controlled");

  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.doesNotThrow(() => validateTaxAndChannelTuples(rows));
  assert.doesNotThrow(() => validateManualPayoutGraph(rows));
});

test("Channel ADJUSTMENT controlled Exception must close item scope and exact amount", () => {
  const rows = clonedScenarioRows();
  const adjustment = appendClonedRecord(rows, "t_channel_settle_item", "100056", "199957");
  adjustment.values.set("item_no", "2");
  adjustment.values.set("channel_item_ref", "NI-ADJUSTMENT-FAKE");
  adjustment.values.set("item_type", "ADJUSTMENT");
  adjustment.values.set("settlement_direction", "DEBIT");
  adjustment.values.set("reference_type", "ADJUSTMENT");
  adjustment.values.set("external_transaction_id", "NI-ADJUSTMENT-FAKE");
  adjustment.values.set("gross_amount", "1");
  adjustment.values.set("fee_amount", "0");
  adjustment.values.set("net_amount", "1");
  const batch = requireRecord(rows, "t_channel_settle_batch", "100055");
  batch.values.set("debit_amount", "1");
  batch.values.set("net_amount", "102");
  requireRecord(rows, "t_bank_recon_match", "40500").values.set("matched_amount", "102");

  const exception = appendClonedRecord(rows, "t_recon_exception", "40530", "49934");
  exception.values.set("exception_type", "DATE_DIFFERENCE");
  exception.values.set("source_type", "CHANNEL_SETTLE_ITEM");
  exception.values.set("source_id", "199957");
  exception.values.set("entity_type", "FINANCE_ENTITY");
  exception.values.set("entity_id", "100003");
  exception.values.set("currency", "EUR");
  exception.values.set("difference_amount", "0.01");
  exception.values.set("applied_resolution_id", "49935");
  const resolution = appendClonedRecord(rows, "t_recon_resolution", "40531", "49935");
  resolution.values.set("exception_id", "49934");
  resolution.values.set("resolution_type", "ACCEPT_TIMING_DIFFERENCE");
  resolution.values.set("instruction_id", "NULL");
  resolution.values.set("content_hash", "hash-channel-adjustment-resolution");
  resolution.values.set("approved_content_hash", "hash-channel-adjustment-resolution");

  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel Settlement Item 199957.*Exception.*(?:OE|currency|difference)/u,
  );
});

test("Channel Cash Binding validates current scope while preserving historical Match versions", () => {
  const rows = clonedScenarioRows();
  const cashAccount = appendClonedRecord(rows, "t_cash_account", "40200", "49900");
  cashAccount.values.set("entity_type", "FINANCE_ENTITY");
  cashAccount.values.set("entity_id", "100003");
  cashAccount.values.set("account_vault_token", "vlt-cash-finance-entity");
  cashAccount.values.set("account_blind_index", "bidx-cash-finance-entity");
  cashAccount.values.set("account_last4", "9900");
  cashAccount.values.set("vault_metadata_hash", "hash-cash-finance-entity");
  const version = appendClonedRecord(rows, "t_cash_account_binding_ver", "40202", "49902");
  version.values.set("binding_ver", "2");
  version.values.set("cash_account_id", "49900");
  version.values.set("effective_from", "2026-08-01T00:00:00.000000Z");
  requireRecord(rows, "t_cash_account_binding", "40201").values.set("current_ver_id", "49902");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Cash Binding 40201.*(?:current|scope|overlap)/u,
  );

  const historical = clonedScenarioRows();
  const oldVersion = requireRecord(historical, "t_cash_account_binding_ver", "40202");
  oldVersion.values.set("effective_to", "2026-09-01T00:00:00.000000Z");
  oldVersion.values.set("status", "EXPIRED");
  const currentVersion = appendClonedRecord(historical, "t_cash_account_binding_ver", "40202", "49902");
  currentVersion.values.set("binding_ver", "2");
  currentVersion.values.set("effective_from", "2026-09-01T00:00:00.000000Z");
  currentVersion.values.set("effective_to", "NULL");
  currentVersion.values.set("status", "ACTIVE");
  requireRecord(historical, "t_cash_account_binding", "40201").values.set("current_ver_id", "49902");
  assert.doesNotThrow(() => validateSchemaRecords(historical, generatedModel()));
  assert.doesNotThrow(() => validateManualPayoutGraph(historical),
    "Channel Match 应允许引用 decision_time 当时有效的历史 Binding Version");
});

test("VALIDATED Channel Batch rejects an unresolved Item Exception after matching", () => {
  const withOpenException = () => {
    const rows = clonedScenarioRows();
    const exception = appendClonedRecord(rows, "t_recon_exception", "40534", "49934");
    exception.values.set("exception_type", "WRONG_ACCOUNT");
    exception.values.set("source_type", "CHANNEL_SETTLE_ITEM");
    exception.values.set("source_id", "100056");
    exception.values.set("difference_amount", "103");
    exception.values.set("applied_resolution_id", "NULL");
    exception.values.set("status", "OPEN");
    return rows;
  };

  const open = withOpenException();
  assert.doesNotThrow(() => validateSchemaRecords(open, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(open),
    /Channel Settlement Item 100056.*unresolved.*Exception/u,
  );

  const mixed = withOpenException();
  const resolved = appendClonedRecord(mixed, "t_recon_exception", "40530", "49935");
  resolved.values.set("exception_type", "DATE_DIFFERENCE");
  resolved.values.set("source_type", "CHANNEL_SETTLE_ITEM");
  resolved.values.set("source_id", "100056");
  resolved.values.set("difference_amount", "103");
  resolved.values.set("applied_resolution_id", "49936");
  const resolution = appendClonedRecord(mixed, "t_recon_resolution", "40531", "49936");
  resolution.values.set("exception_id", "49935");
  resolution.values.set("resolution_type", "ACCEPT_TIMING_DIFFERENCE");
  resolution.values.set("instruction_id", "NULL");
  resolution.values.set("content_hash", "hash-channel-item-timing");
  resolution.values.set("approved_content_hash", "hash-channel-item-timing");
  assert.doesNotThrow(() => validateSchemaRecords(mixed, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(mixed),
    /Channel Settlement Item 100056.*unresolved.*Exception/u,
  );
});

test("CONFIRMED Channel Bank Match rejects a REJECTED Batch", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_channel_settle_batch", "100055").values.set("status", "REJECTED");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel Bank Match 40500.*complete VALIDATED Batch/u,
  );
});

test("Channel Transaction Match allocation_no is continuous within each target", () => {
  const rows = clonedScenarioRows();
  const first = requireRecord(rows, "t_channel_transaction_match", "100057");
  first.values.set("gross_amount", "50");
  first.values.set("fee_amount", "1");
  first.values.set("net_amount", "49");
  const third = appendClonedRecord(rows, "t_channel_transaction_match", "100057", "199957");
  third.values.set("allocation_no", "3");
  third.values.set("gross_amount", "55");
  third.values.set("fee_amount", "1");
  third.values.set("net_amount", "54");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel Transaction Match.*allocation_no.*1.*连续/u,
  );
});

test("Channel Settlement Item item_no is continuous within each Batch", () => {
  const rows = clonedScenarioRows();
  const refundItem = appendClonedRecord(rows, "t_channel_settle_item", "100056", "199956");
  refundItem.values.set("item_no", "3");
  refundItem.values.set("channel_item_ref", "NI-REFUND-90001");
  refundItem.values.set("item_type", "REFUND");
  refundItem.values.set("settlement_direction", "DEBIT");
  refundItem.values.set("reference_type", "REFUND");
  refundItem.values.set("external_transaction_id", "NI-REFUND-90001");
  refundItem.values.set("gross_amount", "20");
  refundItem.values.set("fee_amount", "0");
  refundItem.values.set("net_amount", "20");
  const refundMatch = appendClonedRecord(rows, "t_channel_transaction_match", "100057", "199957");
  refundMatch.values.set("settle_item_id", "199956");
  refundMatch.values.set("target_type", "DEBIT_ITEM");
  refundMatch.values.set("target_key", "DEBIT_ITEM:100053");
  refundMatch.values.set("open_item_id", "NULL");
  refundMatch.values.set("debit_item_id", "100053");
  refundMatch.values.set("gross_amount", "20");
  refundMatch.values.set("fee_amount", "0");
  refundMatch.values.set("net_amount", "20");
  const refundTarget = requireRecord(rows, "t_channel_debit_item", "100053");
  refundTarget.values.set("settled_amount", "20");
  refundTarget.values.set("outstanding_amount", "0");
  refundTarget.values.set("status", "SETTLED");
  refundTarget.values.set("version", "2");
  const batch = requireRecord(rows, "t_channel_settle_batch", "100055");
  batch.values.set("debit_amount", "20");
  batch.values.set("net_amount", "83");
  requireRecord(rows, "t_bank_recon_match", "40500").values.set("matched_amount", "83");
  requireRecord(rows, "t_bank_statement_item", "40401").values.set("amount", "83");
  assert.doesNotThrow(() => validateSchemaRecords(rows, generatedModel()));
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Channel Batch 100055.*item_no.*1.*连续/u,
  );
});

test("every POSTED Journal replays its Instruction Lines without account redirection", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_journal_line", "323931").values.set("account_id", "20304");
  requireRecord(rows, "t_journal_line", "323932").values.set("account_id", "20305");
  const journals = recordsById(rows, "t_journal_entry");
  const maxSequenceByAccount = new Map();
  for (const line of recordsFor(rows, "t_journal_line")) {
    const sequence = BigInt(valueOf(journals.get(valueOf(line, "journal_entry_id")), "journal_sequence"));
    const accountId = identifierKey(valueOf(line, "account_id"));
    const prior = maxSequenceByAccount.get(accountId);
    if (prior === undefined || sequence > prior) maxSequenceByAccount.set(accountId, sequence);
  }
  for (const account of recordsFor(rows, "t_account")) {
    const maximum = maxSequenceByAccount.get(identifierKey(valueOf(account, "id")));
    if (maximum !== undefined) account.values.set("last_journal_sequence", maximum.toString());
  }
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Journal 32393.*Instruction Line.*account_id/u,
  );
});

test("every POSTED Instruction has exactly one represented Journal", () => {
  const missing = clonedScenarioRows().filter((record) => !(
    (record.table === "t_journal_entry" && identifierEqual(valueOf(record, "id"), "32241"))
    || (record.table === "t_journal_line" && identifierEqual(valueOf(record, "journal_entry_id"), "32241"))
  ));
  assert.throws(
    () => validateManualPayoutGraph(missing),
    /POSTED Instruction 32141.*唯一.*Journal/u,
  );

  const extra = clonedScenarioRows();
  const journal = requireRecord(extra, "t_journal_entry", "32241");
  const duplicate = { ...journal, lineNumber: 0, values: new Map(journal.values) };
  duplicate.values.set("id", "39998");
  extra.push(duplicate);
  assert.throws(
    () => validateManualPayoutGraph(extra),
    /POSTED Instruction 32141.*唯一.*Journal/u,
  );
});

test("AUTO or POSTED Instruction rejects an unbound authority key", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_accounting_instruction", "100044")
    .values.set("source_event_key", "UNBOUND_DUPLICATE:999999");
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /Instruction 100044.*DUPLICATE_CAPTURE.*authority key/u,
  );
});

test("PARTNER_RECOVERY_OPEN binds a unique negative Economics fact instead of a Partner owner id", () => {
  const rows = clonedScenarioRows();
  const instruction = requireRecord(rows, "t_accounting_instruction", "32141");
  assert.equal(valueOf(instruction, "source_system"), "RISK");
  assert.equal(valueOf(instruction, "source_event_key"), "PARTNER_RECOVERY_OPEN:31030");
  assert.ok(identifierEqual(valueOf(instruction, "transaction_economics_id"), "31030"));
  const economics = requireRecord(rows, "t_transaction_economics", "31030");
  assert.equal(valueOf(economics, "economics_type"), "DISPUTE");
  assert.ok(identifierEqual(valueOf(economics, "local_partner_id"), "41001"));
  assert.ok(identifierEqual(valueOf(economics, "source_version"), valueOf(instruction, "source_version")));
  const journal = requireRecord(rows, "t_journal_entry", "32241");
  assert.ok(valueOf(economics, "commercial_context_time").slice(0, 10) <= valueOf(journal, "posting_date"));
});

test("a POSTED MANUAL Instruction requires an approved manual_adjustment root", () => {
  const rows = clonedScenarioRows();
  const instruction = requireRecord(rows, "t_accounting_instruction", "100044");
  instruction.values.set("origin_type", "MANUAL");
  instruction.values.set("manual_adjustment_id", "999999");
  instruction.values.set("accounting_rule_bundle_ver_id", "NULL");
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /POSTED MANUAL Instruction 100044.*approved manual_adjustment/u,
  );
});

test("a POSTED MANUAL Instruction follows the model-null source and exact Maker/Checker authority", () => {
  const valid = clonedApprovedManualInstructionRows();
  assert.doesNotThrow(() => validateAccountingSourceAuthority(valid));

  const noChecker = clonedApprovedManualInstructionRows()
    .filter((record) => record.table !== "t_adjustment_approval"
      || valueOf(record, "approval_role") !== "CHECKER");
  assert.throws(
    () => validateAccountingSourceAuthority(noChecker),
    /POSTED MANUAL Instruction 100044.*Maker.*Checker/u,
  );

  const nonNullAutoSource = clonedApprovedManualInstructionRows();
  requireRecord(nonNullAutoSource, "t_accounting_instruction", "100044")
    .values.set("source_event_key", "MANUAL_ADJUSTMENT:100048");
  assert.throws(
    () => validateAccountingSourceAuthority(nonNullAutoSource),
    /POSTED MANUAL Instruction 100044.*source.*NULL/u,
  );
});

test("LATE_DEBIT source rejects an existing bank item of the wrong direction and kind", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_accounting_instruction", "32161")
    .values.set("source_event_key", "LATE_DEBIT:40401");
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /Instruction 32161.*LATE_DEBIT.*DEBIT.*Recon Exception/u,
  );
});

test("an extra AUTO LATE_DEBIT Instruction cannot invent an unrepresented source", () => {
  const rows = clonedScenarioRows();
  const instruction = requireRecord(rows, "t_accounting_instruction", "32161");
  const extra = { ...instruction, lineNumber: 0, values: new Map(instruction.values) };
  extra.values.set("id", "39999");
  extra.values.set("source_event_key", "LATE_DEBIT:49999");
  extra.values.set("source_payload_hash", "hash-unbound-late-debit");
  extra.values.set("input_hash", "hash-input-unbound-late-debit");
  rows.push(extra);
  for (const [templateId, id] of [["321611", "399991"], ["321612", "399992"]]) {
    const template = requireRecord(rows, "t_instruction_line", templateId);
    const line = { ...template, lineNumber: 0, values: new Map(template.values) };
    line.values.set("id", id);
    line.values.set("instruction_id", "39999");
    rows.push(line);
  }
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /Instruction 39999.*LATE_DEBIT.*authority source/u,
  );
});

test("maturity sources bind their exact approved Rule Line business coordinates", () => {
  const rows = clonedScenarioRows();
  const recovery = requireRecord(rows, "t_accounting_rule_line", "109008");
  const available = requireRecord(rows, "t_accounting_rule_line", "109009");
  const recoverySource = valueOf(recovery, "amount_source_code");
  recovery.values.set("amount_source_code", valueOf(available, "amount_source_code"));
  available.values.set("amount_source_code", recoverySource);
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /Accounting Rule 109008.*approved business coordinates/u,
  );
});

test("unused required Accounting Rule Lines still obey the approved catalog", () => {
  const rows = clonedScenarioRows();
  const template = requireRecord(rows, "t_accounting_rule_line", "109028");
  const extra = { ...template, lineNumber: 0, values: new Map(template.values) };
  extra.values.set("id", "199999");
  extra.values.set("instruction_type", "UNUSED_AUTHORITY_TEST");
  extra.values.set("line_no", "999");
  extra.values.set("line_code", "UNUSED_AUTHORITY_TEST_DR");
  extra.values.set("owner_role", "UNRELATED_OWNER");
  rows.push(extra);
  assert.throws(
    () => validateAccountingRuleReplay(rows),
    /Accounting Rule 199999.*owner_role=UNRELATED_OWNER.*批准词汇/u,
  );
});

test("every APPROVED Payout Event is driven by its exact Checker approval", () => {
  const makerDriven = clonedScenarioRows();
  const event = requireRecord(makerDriven, "t_payout_event", "94101");
  event.values.set("approval_id", "920011");
  event.values.set("source_id", "920011");
  event.values.set("actor_id", "90001");
  assert.throws(
    () => validateManualPayoutGraph(makerDriven),
    /Payout Event 94101.*Checker.*action_no/u,
  );

  const otherPayout = clonedScenarioRows();
  const crossEvent = requireRecord(otherPayout, "t_payout_event", "94101");
  crossEvent.values.set("approval_id", "920022");
  crossEvent.values.set("source_id", "920022");
  assert.throws(
    () => validateManualPayoutGraph(otherPayout),
    /Payout Event 94101.*Checker.*action_no/u,
  );
});

test("RETRY_REQUIRED binds the same failed Execution and its NO_DEBIT_PROOF", () => {
  const rows = clonedScenarioRows();
  const event = requireRecord(rows, "t_payout_event", "94606");
  event.values.set("source_id", "93002");
  event.values.set("evidence_ref_id", "93503");
  assert.throws(
    () => validateManualPayoutGraph(rows),
    /Payout Event 94606.*Execution 93006.*NO_DEBIT_PROOF/u,
  );
});

test("FAILED to FAILED is reserved for the complete LATE_DEBIT audit path", () => {
  const rows = clonedScenarioRows();
  const event = requireRecord(rows, "t_payout_event", "94907");
  event.values.set("reason_code", "UNBOUND_POST_FAILURE_NOTE");
  event.values.set("source_type", "FINANCE_NOTE");
  event.values.set("source_id", "note-93009");
  event.values.set("source_event_key", "UNBOUND_POST_FAILURE_NOTE:93009");
  event.values.set("evidence_ref_id", "NULL");
  event.values.set("journal_entry_id", "NULL");
  assert.throws(
    () => validatePayoutEventGraph(
      recordsFor(rows, "t_payout"),
      recordsFor(rows, "t_payout_execution"),
      recordsFor(rows, "t_payout_event"),
    ),
    /Execution 93009.*FAILED->FAILED.*LATE_DEBIT/u,
  );
});

test("every Channel open or debit Item validates its Economics currency tuple", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_transaction_economics", "31020").values.set("currency", "EUR");
  assert.throws(
    () => validateTaxAndChannelTuples(rows),
    /Channel Item 100053.*Economics currency/u,
  );
});

test("relation keys canonicalize integer primary-key aliases", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const records = [
    { table: "t_finance_entity", values: new Map(Object.entries({ id: "100003", entity_code: "ENTITY-A", legal_party_id: "700001" })) },
    { table: "t_finance_entity", values: new Map(Object.entries({ id: "0100003", entity_code: "ENTITY-B", legal_party_id: "700002" })) },
  ];
  assert.throws(
    () => validateRelationsAndUnique(records, model),
    /t_finance_entity.*唯一键 \(id\).*100003/u,
  );
});

test("relation keys accept a valid integer FK written with leading zeroes", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const records = [
    { table: "t_payout", values: new Map(Object.entries({ id: "92001" })) },
    { table: "t_payout_execution", values: new Map(Object.entries({ id: "93001", payout_id: "092001" })) },
  ];
  assert.doesNotThrow(() => validateRelationsAndUnique(records, model));
});

test("relation keys canonicalize every integer member of a composite UK", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const records = [
    { table: "t_payout", values: new Map(Object.entries({ id: "92001" })) },
    { table: "t_payout_execution", values: new Map(Object.entries({ id: "93001", payout_id: "92001", attempt_no: "1" })) },
    { table: "t_payout_execution", values: new Map(Object.entries({ id: "93002", payout_id: "092001", attempt_no: "01" })) },
  ];
  assert.throws(
    () => validateRelationsAndUnique(records, model),
    /t_payout_execution.*唯一键 \(payout_id,attempt_no\).*92001.*1/u,
  );
});

test("relation keys canonicalize fixed DECIMAL aliases when a key uses DECIMAL", () => {
  const model = {
    entities: [{
      id: "decimal-key-entity",
      defKey: "t_decimal_key_fixture",
      fields: [
        { id: "decimal-key-id", defKey: "id", type: "BIGINT", primaryKey: true },
        { id: "decimal-key-value", defKey: "amount_key", type: "DECIMAL", len: "24", scale: "8" },
      ],
      correlations: [],
      indexes: [{ unique: true, fields: [{ fieldDefKey: "decimal-key-value" }] }],
    }],
  };
  const records = [
    { table: "t_decimal_key_fixture", values: new Map(Object.entries({ id: "1", amount_key: "1.0" })) },
    { table: "t_decimal_key_fixture", values: new Map(Object.entries({ id: "2", amount_key: "1.00000000" })) },
  ];
  assert.throws(
    () => validateRelationsAndUnique(records, model),
    /t_decimal_key_fixture.*唯一键 \(amount_key\).*1\.00000000/u,
  );
});

test("Accounting Rule replay rejects an unapproved owner_role", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_accounting_rule_line", "109012").values.set("owner_role", "ARBITRARY_OWNER");
  assert.throws(() => validateAccountingRuleReplay(rows), /owner_role.*批准词汇/u);
});

test("Payout Accounting replay rejects cross-owner frozen accounts", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_instruction_line", "330011").values.set("account_id", "20304");
  requireRecord(rows, "t_instruction_line", "330012").values.set("account_id", "20305");
  assert.throws(() => validateAccountingRuleReplay(rows), /Payout 92001.*冻结.*Account/u);
});

test("maturity Accounting Rule rejects an unapproved amount source", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_accounting_rule_line", "109005").values.set("amount_source_code", "ARBITRARY_MATURITY");
  assert.throws(() => validateAccountingRuleReplay(rows), /PAYABLE_BALANCE_MATURED.*amount_source_code/u);
});

test("fee-difference Accounting replay rejects the opposite direction", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_accounting_instruction", "32158").values.set("instruction_type", "PAYOUT_FEE_DECREASE");
  const debit = requireRecord(rows, "t_instruction_line", "321581");
  debit.values.set("accounting_rule_line_id", "109023");
  debit.values.set("account_id", "20306");
  const credit = requireRecord(rows, "t_instruction_line", "321582");
  credit.values.set("accounting_rule_line_id", "109024");
  credit.values.set("account_id", "20302");
  assert.throws(() => validateAccountingRuleReplay(rows), /Payout 92007.*PAYOUT_FEE_INCREASE/u);
});

test("approved cancellation rejects a missing Maker/Checker decision", () => {
  const removedApprovalIds = integerSet(["92509", "92510"]);
  const rows = clonedScenarioRows().filter((record) => !(record.table === "t_payout_approval"
    && removedApprovalIds.has(identifierKey(valueOf(record, "id")))));
  assert.throws(() => validateManualPayoutGraph(rows), /Payout 92004.*PAYOUT_CANCEL.*MAKER.*CHECKER/u);
});

test("every cancellation rejects a missing PAYOUT_RELEASE graph", () => {
  const removedIds = integerSet(["32154", "321541", "321542", "32404", "324041", "324042"]);
  const rows = clonedScenarioRows().filter((record) => !removedIds.has(identifierKey(valueOf(record, "id"))));
  assert.throws(() => validateManualPayoutGraph(rows), /Payout 92004.*PAYOUT_RELEASE/u);
});

test("cancellation release rejects the wrong payout key and event journal id", () => {
  const wrongKey = clonedScenarioRows();
  requireRecord(wrongKey, "t_accounting_instruction", "32154").values.set("source_event_key", "PAYOUT_RELEASE:92003");
  assert.throws(() => validateManualPayoutGraph(wrongKey), /Payout 92004.*PAYOUT_RELEASE:92004/u);

  const wrongPayoutId = clonedScenarioRows();
  requireRecord(wrongPayoutId, "t_payout_event", "94404").values.set("payout_id", "92003");
  assert.throws(() => validateCancellationRelease(wrongPayoutId), /Payout 92004.*CANCELLED Event/u);

  const wrongEventJournal = clonedScenarioRows();
  requireRecord(wrongEventJournal, "t_payout_event", "94404").values.set("journal_entry_id", "32401");
  assert.throws(() => validateManualPayoutGraph(wrongEventJournal), /Payout 92004.*release Journal/u);
});

test("cancellation release rejects non-frozen accounts", () => {
  const rows = clonedScenarioRows();
  const availableTemplate = requireRecord(rows, "t_account", "20302");
  const lockedTemplate = requireRecord(rows, "t_account", "20306");
  rows.push({ ...availableTemplate, lineNumber: 0, values: new Map(availableTemplate.values) });
  rows.at(-1).values.set("id", "29998");
  rows.at(-1).values.set("owner_id", "49999");
  rows.push({ ...lockedTemplate, lineNumber: 0, values: new Map(lockedTemplate.values) });
  rows.at(-1).values.set("id", "29999");
  rows.at(-1).values.set("owner_id", "49999");
  requireRecord(rows, "t_instruction_line", "321541").values.set("account_id", "29999");
  requireRecord(rows, "t_instruction_line", "321542").values.set("account_id", "29998");
  requireRecord(rows, "t_journal_line", "324041").values.set("account_id", "29999");
  requireRecord(rows, "t_journal_line", "324042").values.set("account_id", "29998");
  assert.throws(() => validateManualPayoutGraph(rows), /Payout 92004.*冻结.*Account/u);
});

test("cancellation release rejects an amount different from the Payout", () => {
  const rows = clonedScenarioRows();
  for (const id of ["321541", "321542", "324041", "324042"]) {
    const table = id.startsWith("324") ? "t_journal_line" : "t_instruction_line";
    requireRecord(rows, table, id).values.set("amount", "29");
  }
  assert.throws(() => validateManualPayoutGraph(rows), /Payout 92004.*release.*金额/u);
});

test("submitted FAILED execution rejects missing immutable NO_DEBIT_PROOF", () => {
  const rows = clonedScenarioRows().filter((record) => !(record.table === "t_payout_evidence_ref"
    && identifierEqual(valueOf(record, "id"), "93506")));
  assert.throws(() => validateManualPayoutGraph(rows), /Execution 93009.*NO_DEBIT_PROOF/u);
});

test("PAYING cannot transition back to ON_HOLD", () => {
  const payout = { table: "t_payout", values: new Map(Object.entries({ id: "1", status: "ON_HOLD", version: "6" })) };
  const execution = { table: "t_payout_execution", values: new Map(Object.entries({
    id: "11", payout_id: "1", attempt_no: "1", status: "SUBMITTED", version: "2", submitted_time: "2026-08-31T09:04:00.000000Z",
  })) };
  const event = (eventNo, scope, fromStatus, toStatus, executionVersion = "NULL") => ({
    table: "t_payout_event",
    values: new Map(Object.entries({
      id: `10${eventNo}`, payout_id: "1", execution_id: scope === "EXECUTION" ? "11" : "NULL",
      event_scope: scope, event_no: String(eventNo), from_status: fromStatus, to_status: toStatus,
      source_id: toStatus === "PAYING" ? "11" : `source-${eventNo}`,
      bank_recon_match_id: "NULL", payout_version_after: String(eventNo),
      execution_version_after: executionVersion, occurred_time: `2026-08-31T09:0${eventNo}:00.000000Z`,
    })),
  });
  const events = [
    event(1, "PAYOUT", "NULL", "PENDING_APPROVAL"),
    event(2, "PAYOUT", "PENDING_APPROVAL", "APPROVED"),
    event(3, "EXECUTION", "NULL", "CREATED", "1"),
    event(4, "EXECUTION", "CREATED", "SUBMITTED", "2"),
    event(5, "PAYOUT", "APPROVED", "PAYING"),
    event(6, "PAYOUT", "PAYING", "ON_HOLD"),
  ];
  assert.throws(() => validatePayoutEventGraph([payout], [execution], events), /PAYING->ON_HOLD/u);
});

test("Channel tuple rejects a Cost Scope MID outside its Channel Account identity", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_channel_cost_scope", "100037").values.set("mid_id", "999999");
  assert.throws(() => validateTaxAndChannelTuples(rows), /Channel Cost Scope 100037.*MID/u);
});

test("Channel confirmed Bank Match rejects a non-settlement binding purpose", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_cash_account_binding", "40201").values.set("account_purpose", "PAYOUT");
  assert.throws(() => validateTaxAndChannelTuples(rows), /Channel Bank Match 40500.*CHANNEL_SETTLEMENT/u);
});

test("Channel tuple rejects economics currency outside its Batch", () => {
  const rows = clonedScenarioRows();
  requireRecord(rows, "t_transaction_economics", "31001").values.set("currency", "EUR");
  assert.throws(() => validateTaxAndChannelTuples(rows), /Channel Transaction Match 100057.*currency/u);
});

test("maturity prose uses one Agreement-OE T+N and keeps Channel cash timing independent", () => {
  const content = readFileSync(scenarioPath, "utf8");
  assert.match(content, /Client 与 Partner 均为 T\+1 工作日/u);
  assert.match(content, /Channel T\+2[^。\n]*仅表示 Channel 资金时间[^。\n]*独立于 Owner 成熟周期/u);
  assert.doesNotMatch(content, /Client T\+(\d+)[^。\n]*Partner T\+(?!\1)\d+/u);
});

test("exact integer predicates normalize leading zeroes with BigInt", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const fiscal = requireRecord(clonedScenarioRows(), "t_fiscal_record", "100024");
  fiscal.values.set("source_version", "01");
  assert.doesNotThrow(() => validateExactDecimalChecks([fiscal], model, { requireAllDerived: false }));
});

test("exact integer ID predicates reject leading-zero aliases", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const link = requireRecord(clonedScenarioRows(), "t_transaction_economic_comp_link", "100035");
  link.values.set("source_component_id", "0100033");
  link.values.set("target_component_id", "100033");
  assert.throws(() => validateExactDecimalChecks([link], model, { requireAllDerived: false }), /ck_txn_econ_component_link/u);
});

test("AUTO Accounting Instructions deterministically replay every required Rule Line", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assert.equal(validateAccountingRuleReplay(rows), 23);

  const missing = rows.filter((record) => !(record.table === "t_instruction_line"
    && identifierEqual(valueOf(record, "id"), "330012")));
  assert.throws(() => validateAccountingRuleReplay(missing), /缺少 required Rule Line/u);

  const wrongType = rows.map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(wrongType, "t_accounting_rule_line", "109012").values.set("instruction_type", "WRONG_TYPE");
  assert.throws(() => validateAccountingRuleReplay(wrongType), /instruction_type/u);

  const unequal = rows.map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(unequal, "t_instruction_line", "330012").values.set("amount", "101.99");
  assert.throws(() => validateAccountingRuleReplay(unequal), /amount_source_code.*金额/u);
});

test("Tax and Channel authority tuples close on one OE and exact cost allocation", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assert.deepEqual(validateTaxAndChannelTuples(rows), {
    taxOperatingEntityId: "AE001",
    taxAuthorityId: "100010",
    channelOperatingEntityId: "AE001",
    channelAccountId: "100051",
    allocatedCost: "2.00000000",
  });
});

test("authority transition set accepts withdrawal and both UNKNOWN resolutions", () => {
  const event = (id, payoutId, eventNo, scope, fromStatus, toStatus, executionId = "NULL", executionVersion = "NULL") => ({
    table: "t_payout_event",
    values: new Map(Object.entries({
      id: String(id), payout_id: String(payoutId), execution_id: String(executionId), event_scope: scope,
      event_no: String(eventNo), from_status: fromStatus, to_status: toStatus,
      source_id: executionId === "NULL" ? `source-${id}` : String(executionId),
      bank_recon_match_id: "NULL", payout_version_after: String(eventNo),
      execution_version_after: String(executionVersion), occurred_time: `2026-08-31T09:${String(eventNo).padStart(2, "0")}:00.000000Z`,
    })),
  });
  const withdrawal = { table: "t_payout", values: new Map(Object.entries({ id: "1", status: "CANCELLED", version: "2" })) };
  const withdrawalEvents = [
    event(1, 1, 1, "PAYOUT", "NULL", "PENDING_APPROVAL"),
    event(2, 1, 2, "PAYOUT", "PENDING_APPROVAL", "CANCELLED"),
  ];
  assert.doesNotThrow(() => validatePayoutEventGraph([withdrawal], [], withdrawalEvents));
  assert.equal(validateCancelledPayoutSafety([withdrawal], [], []), 1);
  assert.equal(validateApprovalPairs([]), 0, "申请人在 PENDING_APPROVAL 撤回不需要 Maker/Checker");

  const resolvedFlow = (payoutId, executionTerminal, payoutTerminal) => {
    const payout = { table: "t_payout", values: new Map(Object.entries({ id: String(payoutId), status: payoutTerminal, version: "9" })) };
    const execution = { table: "t_payout_execution", values: new Map(Object.entries({
      id: String(payoutId + 100), payout_id: String(payoutId), attempt_no: "1", status: executionTerminal, version: "4",
    })) };
    const executionId = String(payoutId + 100);
    const events = [
      event(payoutId * 100 + 1, payoutId, 1, "PAYOUT", "NULL", "PENDING_APPROVAL"),
      event(payoutId * 100 + 2, payoutId, 2, "PAYOUT", "PENDING_APPROVAL", "APPROVED"),
      event(payoutId * 100 + 3, payoutId, 3, "EXECUTION", "NULL", "CREATED", executionId, "1"),
      event(payoutId * 100 + 4, payoutId, 4, "EXECUTION", "CREATED", "SUBMITTED", executionId, "2"),
      event(payoutId * 100 + 5, payoutId, 5, "PAYOUT", "APPROVED", "PAYING", "NULL", "NULL"),
      event(payoutId * 100 + 6, payoutId, 6, "EXECUTION", "SUBMITTED", "UNKNOWN", executionId, "3"),
      event(payoutId * 100 + 7, payoutId, 7, "PAYOUT", "PAYING", "PAYMENT_UNKNOWN"),
      event(payoutId * 100 + 8, payoutId, 8, "EXECUTION", "UNKNOWN", executionTerminal, executionId, "4"),
      event(payoutId * 100 + 9, payoutId, 9, "PAYOUT", "PAYMENT_UNKNOWN", payoutTerminal),
    ];
    events[4].values.set("source_id", executionId);
    if (executionTerminal === "RECONCILED") {
      events[7].values.set("bank_recon_match_id", "77");
      events[8].values.set("bank_recon_match_id", "77");
    }
    return { payout, execution, events };
  };
  for (const flow of [resolvedFlow(2, "FAILED", "RETRY_REQUIRED"), resolvedFlow(3, "RECONCILED", "PAID")]) {
    assert.doesNotThrow(() => validatePayoutEventGraph([flow.payout], [flow.execution], flow.events));
  }
});

test("PENDING_APPROVAL withdrawal needs no approval but posts one exact release", () => {
  const records = [
    { table: "t_payout", values: new Map(Object.entries({
      id: "1", status: "CANCELLED", owner_debit_amount: "10", available_account_id: "20", locked_account_id: "21",
    })) },
    { table: "t_payout_event", values: new Map(Object.entries({
      id: "2", payout_id: "1", event_scope: "PAYOUT", from_status: "PENDING_APPROVAL", to_status: "CANCELLED",
      approval_id: "NULL", journal_entry_id: "40",
    })) },
    { table: "t_accounting_instruction", values: new Map(Object.entries({
      id: "30", instruction_type: "PAYOUT_RELEASE", source_event_key: "PAYOUT_RELEASE:1",
    })) },
    { table: "t_instruction_line", values: new Map(Object.entries({
      id: "31", instruction_id: "30", account_id: "21", entry_side: "DEBIT", amount: "10",
    })) },
    { table: "t_instruction_line", values: new Map(Object.entries({
      id: "32", instruction_id: "30", account_id: "20", entry_side: "CREDIT", amount: "10",
    })) },
    { table: "t_journal_entry", values: new Map(Object.entries({ id: "40", instruction_id: "30" })) },
    { table: "t_journal_line", values: new Map(Object.entries({
      id: "41", journal_entry_id: "40", account_id: "21", entry_side: "DEBIT", amount: "10",
    })) },
    { table: "t_journal_line", values: new Map(Object.entries({
      id: "42", journal_entry_id: "40", account_id: "20", entry_side: "CREDIT", amount: "10",
    })) },
  ];
  assert.equal(validateApprovalPairs([]), 0);
  assert.equal(validateCancellationRelease(records), 1);
});

test("UNKNOWN resolves to FAILED only through referenced NO_DEBIT_PROOF", () => {
  const records = [
    { table: "t_payout_execution", values: new Map(Object.entries({ id: "11", payout_id: "1", status: "FAILED" })) },
    { table: "t_payout_event", values: new Map(Object.entries({
      id: "21", payout_id: "1", execution_id: "11", event_scope: "EXECUTION", from_status: "CREATED", to_status: "SUBMITTED",
    })) },
    { table: "t_payout_event", values: new Map(Object.entries({
      id: "22", payout_id: "1", execution_id: "11", event_scope: "EXECUTION", from_status: "SUBMITTED", to_status: "UNKNOWN",
    })) },
    { table: "t_payout_event", values: new Map(Object.entries({
      id: "23", payout_id: "1", execution_id: "11", event_scope: "EXECUTION", from_status: "UNKNOWN", to_status: "FAILED",
      evidence_ref_id: "31",
    })) },
    { table: "t_payout_evidence_ref", values: new Map(Object.entries({
      id: "31", payout_execution_id: "11", evidence_type: "NO_DEBIT_PROOF",
    })) },
  ];
  assert.equal(validateExecutionResolutionEvidence(records), 1);
  assert.throws(() => validateExecutionResolutionEvidence(records.filter((record) => record.table !== "t_payout_evidence_ref")),
    /Execution 11.*NO_DEBIT_PROOF/u);
});

test("UNKNOWN resolves to RECONCILED only through its own CONFIRMED Bank Match", () => {
  const records = [
    { table: "t_payout_execution", values: new Map(Object.entries({ id: "11", payout_id: "1", status: "RECONCILED" })) },
    { table: "t_payout_event", values: new Map(Object.entries({
      id: "21", payout_id: "1", execution_id: "11", event_scope: "EXECUTION", from_status: "UNKNOWN", to_status: "RECONCILED",
      bank_recon_match_id: "31",
    })) },
    { table: "t_bank_recon_match", values: new Map(Object.entries({
      id: "31", payout_execution_id: "11", status: "CONFIRMED",
    })) },
  ];
  assert.equal(validateExecutionResolutionEvidence(records), 0);
  records.at(-1).values.set("status", "PROPOSED");
  assert.throws(() => validateExecutionResolutionEvidence(records), /UNKNOWN->RECONCILED.*CONFIRMED/u);
});

test("DATE and DATETIME validators reject normalized or non-UTC values", () => {
  const dateField = { defKey: "posting_date", type: "DATE" };
  const datetimeField = { defKey: "occurred_time", type: "DATETIME" };
  assert.throws(() => assertValueMatchesType("t_journal_entry", dateField, "2026-02-31"), /DATE/u);
  assert.throws(() => assertValueMatchesType("t_payout_event", datetimeField, "2026-08-31"), /DATETIME|UTC/u);
  assert.throws(() => assertValueMatchesType("t_payout_event", datetimeField, "2026-08-31T12:00:00.000000"), /DATETIME|UTC/u);
  assert.throws(() => assertValueMatchesType("t_payout_event", datetimeField, "2026-02-30T12:00:00.000000Z"), /DATETIME|UTC/u);
});

test("late Debit and Book/Account sequence causality reject backdating and stale watermarks", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assert.doesNotThrow(() => validateLateDebitCausality(rows));
  assert.deepEqual(validateBookAndAccountSequences(rows), { firstSequence: "9007", lastSequence: "9029", nextValue: "9030" });

  const backdated = rows.map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(backdated, "t_journal_entry", "32411").values.set("posting_date", "2026-08-31");
  assert.throws(() => validateLateDebitCausality(backdated), /value_date.*posting_date/u);

  const badNext = rows.map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(badNext, "t_book_sequence", "100008").values.set("next_value", "9999");
  assert.throws(() => validateBookAndAccountSequences(badNext), /next_value/u);

  const badAccount = rows.map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(badAccount, "t_account", "20207").values.set("last_journal_sequence", "1");
  assert.throws(() => validateBookAndAccountSequences(badAccount), /last_journal_sequence/u);
});

test("all 104 Oracle CHECK contracts and all 37 derived exact-decimal predicates execute", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  const result = validateAllOracleChecks(rows, model);
  const exactResult = validateExactDecimalChecks(rows, model);
  const expectedEvaluationCount = rows.reduce((count, row) => count
    + Object.values(EXPECTED_CHECK_BINDINGS).filter((tables) => tables.includes(row.table.slice(2))).length, 0);
  assert.equal(result.checkCount, 104);
  assert.equal(result.violations, 0);
  assert.equal(result.evaluationCount, expectedEvaluationCount,
    "每个绑定了 Oracle CHECK 的结构化场景记录都必须逐条执行");
  assert.deepEqual({ checkCount: exactResult.checkCount, violations: exactResult.violations },
    { checkCount: 37, violations: 0 });
});

test("full Oracle gate rejects a formerly unvalidated Accounting Instruction", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const invalid = {
    table: "t_accounting_instruction",
    lineNumber: 1,
    values: new Map(Object.entries({
      origin_type: "AUTO",
      source_system: "FINANCE",
      source_event_key: "BAD:1",
      source_version: "1",
      source_payload_hash: "hash",
      transaction_economics_id: "NULL",
      manual_adjustment_id: "NULL",
      accounting_rule_bundle_ver_id: "NULL",
      input_hash: "hash-input",
      maker_id: "NULL",
      checker_id: "NULL",
    })),
  };
  assert.throws(
    () => validateAllOracleChecks([invalid], model, { requireAllContracts: false }),
    /ck_instruction_origin/u,
  );
});

test("approved Payout cannot use the all-null Risk snapshot branch", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const values = {
    owner_type: "CLIENT",
    currency: "USD",
    requested_amount: "100",
    fee_mode: "ADDED_ON_TOP",
    fee_amount: "2",
    owner_debit_amount: "102",
    beneficiary_amount: "100",
    cash_required_amount: "102",
    approval_hash: "approved-hash",
    approved_time: "2026-08-31T09:20:00.000000Z",
  };
  for (const field of ["assessment_id", "evaluation_id", "eligibility_decision", "eligibility_reason_codes",
    "eligibility_matched_controls", "risk_required_amount", "risk_required_currency", "control_set_hash", "eligibility_hash"]) {
    values[field] = "NULL";
  }
  const invalid = { table: "t_payout", lineNumber: 1, values: new Map(Object.entries(values)) };
  assert.throws(
    () => validateAllOracleChecks([invalid], model, { requireAllContracts: false }),
    /ck_payout_approval_snapshot/u,
  );
});

test("declared Journal truth rejects wrong keys, totals and duplicates", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  const declarations = parseDeclaredJournalRows(content);
  const wrongKey = declarations.map((declaration) => ({ ...declaration }));
  wrongKey[0].entryKey = "PAYOUT_PAID:DOES_NOT_EXIST";
  assert.throws(() => validateDeclaredJournalTruth(wrongKey, rows), /无法唯一解析/u);
  const wrongTotal = declarations.map((declaration) => ({ ...declaration }));
  wrongTotal[0].debitTotal = "999999";
  assert.throws(() => validateDeclaredJournalTruth(wrongTotal, rows), /声明.*实际/u);
  assert.throws(
    () => validateDeclaredJournalTruth([...declarations, { ...declarations[0] }], rows),
    /重复声明/u,
  );
  assert.throws(
    () => parseDeclaredJournalRows("Journal 平衡：entry_key=PAYOUT_PAID:1；debit_total=1；bad=1。"),
    /无法完整解析/u,
  );
});

test("approved Cancel requires an exact independent Maker and Checker pair", () => {
  const checker = {
    table: "t_payout_approval",
    values: new Map(Object.entries({
      id: "92510",
      payout_id: "92004",
      action_type: "PAYOUT_CANCEL",
      action_no: "2",
      approval_role: "CHECKER",
      actor_id: "90002",
      decision: "APPROVED",
      requested_from_status: "APPROVED",
      reason_code: "ABORTED_BEFORE_SUBMIT",
      evidence_ref_id: "NULL",
      approved_content_hash: "hash-cancel-92004",
    })),
  };
  assert.throws(() => validateApprovalPairs([checker]), /MAKER.*CHECKER/u);
});

test("Payout event graph rejects missing events, version gaps and unsafe retries", () => {
  const payout = (version = "1") => ({
    table: "t_payout",
    values: new Map(Object.entries({ id: "1", status: "APPROVED", version })),
  });
  const event = (eventNo, payoutVersion) => ({
    table: "t_payout_event",
    values: new Map(Object.entries({
      id: `9${eventNo}`,
      payout_id: "1",
      execution_id: "NULL",
      event_scope: "PAYOUT",
      event_no: String(eventNo),
      from_status: eventNo === 1 ? "NULL" : "PENDING_APPROVAL",
      to_status: eventNo === 1 ? "PENDING_APPROVAL" : "APPROVED",
      payout_version_after: String(payoutVersion),
      execution_version_after: "NULL",
      occurred_time: `2026-08-31T09:0${eventNo}:00.000000Z`,
    })),
  });
  assert.throws(() => validatePayoutEventGraph([payout()], [], []), /缺少事件/u);
  assert.throws(() => validatePayoutEventGraph([payout("3")], [], [event(1, 1), event(3, 3)]), /event_no/u);
  assert.throws(() => validatePayoutEventGraph([payout("2")], [], [event(1, 1)]), /最后.*version/u);
  const payingPayout = payout("3");
  payingPayout.values.set("status", "PAYING");
  const payingWithoutSubmit = event(3, 3);
  payingWithoutSubmit.values.set("from_status", "APPROVED");
  payingWithoutSubmit.values.set("to_status", "PAYING");
  payingWithoutSubmit.values.set("source_id", "101");
  assert.throws(
    () => validatePayoutEventGraph([payingPayout], [], [event(1, 1), event(2, 2), payingWithoutSubmit]),
    /PAYING.*SUBMITTED/u,
  );

  const execution = (attemptNo, status, assessmentId) => ({
    table: "t_payout_execution",
    values: new Map(Object.entries({
      id: String(100 + attemptNo),
      payout_id: "1",
      attempt_no: String(attemptNo),
      status,
      assessment_id: String(assessmentId),
      evaluation_id: String(Number(assessmentId) + 100),
    })),
  });
  assert.throws(
    () => validateExecutionAttempts([execution(1, "FAILED", 1), execution(3, "CREATED", 3)]),
    /attempt_no/u,
  );
  assert.throws(
    () => validateExecutionAttempts([execution(1, "UNKNOWN", 1), execution(2, "CREATED", 2)]),
    /UNKNOWN.*重试/u,
  );
});

test("CANCELLED payout rejects a second unresolved UNKNOWN execution", () => {
  const payout = {
    table: "t_payout",
    values: new Map(Object.entries({ id: "1", status: "CANCELLED" })),
  };
  const execution = (id, attemptNo, status) => ({
    table: "t_payout_execution",
    values: new Map(Object.entries({ id, payout_id: "1", attempt_no: String(attemptNo), status })),
  });
  assert.throws(
    () => validateCancelledPayoutSafety([payout], [execution("11", 1, "ABORTED"), execution("12", 2, "UNKNOWN")], []),
    /CANCELLED.*UNKNOWN/u,
  );
  assert.throws(
    () => validateCancelledPayoutSafety([payout], [execution("11", 1, "ABORTED")], [{
      table: "t_bank_recon_match",
      values: new Map(Object.entries({ payout_execution_id: "11", status: "CONFIRMED" })),
    }]),
    /银行 Debit/u,
  );
});

test("fee-difference timeline rejects approval after PAID", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  const mutated = rows.map((record) => ({ ...record, values: new Map(record.values) }));
  requireRecord(mutated, "t_recon_resolution", "40533").values.set("decision_time", "2027-01-01T00:00:00.000000Z");
  assert.throws(() => validateFeeDifferenceTimeline(mutated), /resolution.*PAID/u);
});

test("Journal posting date must belong to its Accounting Period", () => {
  const records = [
    {
      table: "t_accounting_period",
      values: new Map(Object.entries({ id: "1", start_date: "2026-08-01", end_date: "2026-08-31" })),
    },
    {
      table: "t_journal_entry",
      values: new Map(Object.entries({ id: "2", accounting_period_id: "1", posting_date: "2026-09-01" })),
    },
  ];
  assert.throws(() => validateJournalPeriods(records), /posting_date.*Accounting Period/u);
});

test("Tax payment graph rejects CREATED roots with a full CONFIRMED bank match", () => {
  const records = [
    {
      table: "t_tax_payment",
      values: new Map(Object.entries({
        id: "100030",
        status: "CREATED",
        amount: "5",
        reconciled_amount: "0",
        bank_payment_ref: "NULL",
        journal_entry_id: "NULL",
        reconciled_time: "NULL",
        active_slot: "1",
      })),
    },
    {
      table: "t_bank_recon_match",
      values: new Map(Object.entries({
        id: "40506",
        match_type: "TAX_PAYMENT_DEBIT",
        tax_payment_id: "100030",
        matched_amount: "5",
        status: "CONFIRMED",
      })),
    },
  ];
  assert.throws(() => validateTaxPaymentGraph(records), /CREATED.*CONFIRMED/u);
});

test("scenario relation validator rejects a missing local FK target", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  assert.throws(
    () => validateRelationsAndUnique([
      {
        table: "t_payout_execution",
        lineNumber: 1,
        values: new Map([["id", "93001"], ["payout_id", "99999"]]),
      },
    ], model),
    /t_payout_execution\.payout_id=99999.*t_payout/u,
  );
});

test("scenario schema validator rejects missing NN fields and duplicate non-null UK tuples", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  assert.throws(
    () => validateSchemaRecords([{
      table: "t_currency_spec",
      lineNumber: 1,
      values: new Map([["id", "1"]]),
    }], model),
    /缺少 NN 字段 currency/u,
  );
  const currency = (id) => ({
    table: "t_currency_spec",
    lineNumber: Number(id),
    values: new Map(Object.entries({
      id,
      currency: "USD",
      minor_unit: "2",
      rounding_mode: "HALF_UP",
      status: "ACTIVE",
    })),
  });
  assert.throws(
    () => validateSchemaRecords([currency("1"), currency("2")], model),
    /唯一键 .*重复/u,
  );
});

test("structured maturity graph has one key, one journal and gross/netted/net closure", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  const result = validateMaturityGraph(rows, "31001");
  assert.deepEqual(result, {
    eventKey: "PAYABLE_BALANCE_MATURED:31001",
    journalId: "32242",
    clientAmount: "96.80000000",
    partnerGross: "0.80000000",
    recoveryNetted: "0.30000000",
    partnerNetAvailable: "0.50000000",
    debitTotal: "97.60000000",
    creditTotal: "97.60000000",
  });
});

test("structured RCO checkpoints preserve replay identity and bind final persisted pairs", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  const checkpoints = parseRcoCheckpointRows(content);
  assert.equal(validateRcoCheckpoints(checkpoints, rows), 4);
  const mismatch = checkpoints.map((checkpoint) => ({
    ...checkpoint,
    values: new Map(checkpoint.values),
  }));
  mismatch.find((checkpoint) => checkpoint.name === "EXTERNAL_SUBMIT").values.set("owner_id", "40002");
  assert.throws(() => validateRcoCheckpoints(mismatch, rows), /owner_id/u);
});

test("structured Manual Payout graph closes roots, evidence, bank matches and journals", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  assert.deepEqual(validatePartnerPayoutGates(parsePartnerPayoutGateRows(content), rows), {
    blockedBalance: "0.30000000",
    allowedPayoutId: "92002",
  });
  assert.deepEqual(validateManualPayoutGraph(rows), {
    payoutCount: 9,
    executionCount: 10,
    paidCount: 5,
    confirmedPayoutMatchCount: 6,
    balancedJournalCount: 23,
  });
});

test("Finance reality scenarios cover every approved table", () => {
  assert.ok(existsSync(scenarioPath), `Finance 场景文档尚未生成：${scenarioPath}`);
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  assert.deepEqual([...new Set(rows.map((row) => row.table))].sort(), [...APPROVED_TABLES].sort());
  for (const requiredScene of [
    "支付成功与入账",
    "退款成功",
    "重复扣款",
    "拒付与追偿",
    "Channel 清算与银行到账",
    "Client 与 Local Partner 手工出款",
    "税务申报与付款",
    "期间关账与重开",
  ]) {
    assert.match(content, new RegExp(requiredScene, "u"), `缺少场景：${requiredScene}`);
  }
});

test("Finance scenario assignments use real fields, primitive types and closed enum values", () => {
  assert.ok(existsSync(modelPath), `Finance 模型尚未生成：${modelPath}`);
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  const assignmentCount = validateSchemaRecords(rows, model);
  assert.ok(assignmentCount >= 700, `场景字段赋值不足，当前仅 ${assignmentCount} 个`);
});

test("Finance scenarios demonstrate the unified account and bucket model", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  const accounts = recordsFor(rows, "t_account");
  assert.deepEqual(
    new Set(accounts.map((record) => valueOf(record, "owner_type"))),
    new Set(["OPERATING_ENTITY", "CLIENT", "LOCAL_PARTNER", "CHANNEL_ACCOUNT", "CASH_ACCOUNT", "TAX_AUTHORITY"]),
  );
  assert.ok(accounts.some((record) => valueOf(record, "owner_type") === "CLIENT"
    && valueOf(record, "balance_bucket") === "PENDING"));
  assert.ok(accounts.some((record) => valueOf(record, "owner_type") === "LOCAL_PARTNER"
    && valueOf(record, "balance_bucket") === "LOCKED"));
  const chartCodes = new Set(recordsFor(rows, "t_chart_account").map((record) => valueOf(record, "account_code")));
  for (const code of ["CLIENT_PAYABLE", "PARTNER_PAYABLE", "RECON_ADJUSTMENT_EXPENSE",
    "CLIENT_SECURITY_DEPOSIT", "CLIENT_RECOVERY_RECEIVABLE", "PARTNER_RECOVERY_RECEIVABLE"]) {
    assert.ok(chartCodes.has(code), `缺少统一 Chart Account ${code}`);
  }
  const bookSequence = recordsFor(rows, "t_book_sequence");
  assert.equal(bookSequence.length, 1);
  assert.equal(bookSequence[0].values.has("version"), false, "Book Sequence 不使用额外 Version");
  assert.doesNotMatch(content, /source_type=ACCOUNT/u);
  assert.match(content, /CHANNEL_COST_INCREASE/u);
  assert.match(content, /CHANNEL_COST_DECREASE/u);
  assert.match(content, /预计 Channel Cost.*借记.*CHANNEL_FEE_EXPENSE.*贷记.*CHANNEL_FEE_ACCRUAL/u);
  assert.match(content, /实际成本 5.*预计成本 2.*借记.*CHANNEL_FEE_EXPENSE.*贷记.*CHANNEL_FEE_ACCRUAL/u);
  assert.match(content, /实际成本 1.*预计成本 2.*借记.*CHANNEL_FEE_ACCRUAL.*贷记.*CHANNEL_FEE_EXPENSE/u);
  assert.match(content, /实际成本等于预计成本.*不创建 COST_ADJUSTMENT/u);
  assert.doesNotMatch(content, /normal_side=BOTH|balance_direction=/u);
  assert.doesNotMatch(content, /RECON_ROUNDING/u);
  assert.doesNotMatch(content, /`t_ledger_account`|`t_subledger_account`|`t_subledger_balance`/u);
  assert.doesNotMatch(
    content,
    /CLIENT_PAYABLE_(?:PENDING|AVAILABLE|RESERVED|LOCKED)|PARTNER_PAYABLE_(?:PENDING|AVAILABLE|LOCKED)/u,
  );
});

test("Finance scenarios demonstrate Channel positive and negative three-stage accounting", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  for (const accountCode of [
    "CHANNEL_UNSETTLED_RECEIVABLE",
    "CHANNEL_SETTLEMENT_RECEIVABLE",
    "CHANNEL_UNSETTLED_PAYABLE",
    "CHANNEL_SETTLEMENT_PAYABLE",
    "BANK_CASH",
  ]) {
    assert.match(content, new RegExp(accountCode, "u"));
  }
  assert.doesNotMatch(
    content,
    /\bCHANNEL_RECEIVABLE\b|\bCASH_IN_TRANSIT\b|\bCASH_OUT_TRANSIT\b|CASH_IN_TRANSIT_MISMATCH/u,
  );

  const openItem = recordsFor(rows, "t_channel_open_item")[0];
  const debitItem = recordsFor(rows, "t_channel_debit_item")[0];
  const batch = recordsFor(rows, "t_channel_settle_batch")[0];
  const settleItem = recordsFor(rows, "t_channel_settle_item")[0];
  const transactionMatch = recordsFor(rows, "t_channel_transaction_match")[0];
  const bankMatch = recordsFor(rows, "t_bank_recon_match")
    .find((record) => valueOf(record, "match_type") === "CHANNEL_CREDIT");
  assert.ok(identifierEqual(valueOf(transactionMatch, "open_item_id"), valueOf(openItem, "id")));
  assert.ok(identifierEqual(valueOf(transactionMatch, "settle_item_id"), valueOf(settleItem, "id")));
  assert.ok(identifierEqual(valueOf(settleItem, "batch_id"), valueOf(batch, "id")));
  assert.ok(fixedRecordEqual(transactionMatch, "gross_amount", openItem, "gross_receivable"));
  assert.ok(fixedRecordEqual(transactionMatch, "net_amount", batch, "net_amount"));
  assert.ok(identifierEqual(valueOf(bankMatch, "channel_batch_id"), valueOf(batch, "id")));
  assert.ok(fixedRecordEqual(bankMatch, "matched_amount", batch, "net_amount"));
  assert.equal(valueOf(bankMatch, "status"), "CONFIRMED");
  assert.equal(valueOf(debitItem, "status"), "OPEN");
  assert.ok(fixedEqual(fixedValue(debitItem, "settled_amount"), EXACT_ZERO));
  assert.ok(fixedEqual(fixedValue(debitItem, "outstanding_amount"), fixedLiteral("20")));
  assert.match(content, /交易成功.*CHANNEL_UNSETTLED_RECEIVABLE/su);
  assert.match(content, /CHANNEL_UNSETTLED_PAYABLE.*CHANNEL_SETTLEMENT_PAYABLE/su);
  assert.match(content, /VALIDATED.*单独.*不能触发.*重分类/u);
  assert.match(content, /多条.*Transaction Match.*一个.*批次.*Journal/u);
  assert.match(content, /预计 Channel Cost.*不得.*重复/u);
  assert.ok(fixedEqual(fixedValue(batch, "gross_amount"), fixedLiteral("105")));
  assert.ok(fixedEqual(fixedValue(batch, "fee_amount"), fixedLiteral("2")));
  assert.ok(fixedEqual(fixedValue(batch, "net_amount"), fixedLiteral("103")));
  assert.match(content, /Preliminary Transaction Report.*不能.*最终批次/u);
  assert.match(content, /PROPOSED\/REJECTED.*不过账/u);
  assert.match(content, /NONE.*禁止 Channel Bank Match/u);
  assert.match(content, /CHANNEL_SETTLEMENT_MISMATCH/u);
});

test("Finance scenarios mature Client and Partner payable balances at one contractual time", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  const rowsFor = (tableKey) => rows
    .filter((row) => row.table === tableKey)
    .map((row) => row.data)
    .join("；");

  for (const removedTable of [
    "t_balance_lot",
    "t_balance_lot_bucket",
    "t_balance_lot_move",
    "t_availability_policy",
    "t_availability_policy_ver",
    "t_availability_policy_scope",
    "t_availability_condition",
    "t_availability_evaluation",
    "t_availability_eval_reason",
    "t_settlement_line",
    "t_refund_funding_reservation",
    "t_beneficiary_account_ver",
    "t_settlement_profile",
    "t_settlement_profile_ver",
    "t_profile_review",
    "t_payout_request",
    "t_settlement_run",
    "t_settlement_candidate",
    "t_candidate_requirement_snapshot",
    "t_settlement",
    "t_settlement_req_snapshot",
    "t_settlement_approval",
    "t_settlement_cancel",
    "t_settlement_cancel_approval",
    "t_settlement_state_change",
    "t_settlement_payment",
    "t_settlement_payment_attempt",
    "t_settlement_payment_event",
    "t_settlement_evidence_ref",
  ]) {
    assert.ok(!rows.some((row) => row.table === removedTable), `场景仍使用已删除表 ${removedTable}`);
  }

  assert.match(rowsFor("t_transaction_economics"), /payment_succeeded_time=/u);
  assert.match(rowsFor("t_transaction_economics"), /balance_available_time=/u);
  assert.doesNotMatch(rowsFor("t_transaction_economics"), /client_available_time=/u);
  assert.match(rowsFor("t_account"), /chart_account_id=20102.*balance_bucket=PENDING/u);
  assert.match(rowsFor("t_account"), /chart_account_id=20114.*balance_bucket=PENDING/u);
  assert.match(rowsFor("t_account"), /chart_account_id=20128.*balance_bucket=NONE/u);

  for (const expected of [
    "T+1 工作日",
    "Channel T+2",
    "平台承担流动性缺口",
    "FOLLOWING",
    "PAYABLE_BALANCE_MATURED:<economics_id>",
    "同一个 balance_available_time",
    "Client 与 Partner 都先进入 PENDING",
    "Client 与 Partner 在同一事务共同成熟",
    "partner_gross_matured",
    "recovery_netted",
    "partner_net_available",
    "partner_net_available=partner_gross_matured-recovery_netted",
    "PARTNER_RECOVERY_RECEIVABLE/NONE",
    "PENDING -> AVAILABLE/NONE",
    "AVAILABLE -> LOCKED",
    "LOCKED -> AVAILABLE",
    "Channel 实际结算不阻断",
    "未决退款不阻断",
    "balance_available_time >= payment_succeeded_time",
    "Partner Recovery 非零时禁止出款",
  ]) {
    assert.ok(content.includes(expected), `合同到期场景缺少关键语义：${expected}`);
  }

  assert.match(content, /未决退款[^。\n]*不阻断[^。\n]*(?:balance_available_time|PENDING[^。\n]*AVAILABLE)/u);
  assert.doesNotMatch(content, /balance_lot_id=|locked_bucket_id=|active_lock_flag=/u);
  assert.doesNotMatch(content, /BALANCE_LOT_BUCKET|LOT_AVAILABILITY/u);
  assert.doesNotMatch(content, /balance_bucket=RESERVED/u);
  assert.doesNotMatch(
    content,
    /refund_funding_reservation|REFUND_CAP|SOURCE_COVER|CLIENT_BALANCE_MATURED:<economics_id>:REFUND_RELEASED:/u,
  );
});

test("Finance scenarios cover the complete Manual Payout lifecycle and high-risk branches", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  const rowsFor = (tableKey) => rows.filter((row) => row.table === tableKey);
  const payoutRows = rowsFor("t_payout");
  const clientPayout = assignmentMap(
    payoutRows.find((row) => row.data.includes("payout_no=PO-CLIENT-001"))?.data ?? "",
  );
  const partnerPayout = assignmentMap(
    payoutRows.find((row) => row.data.includes("payout_no=PO-PARTNER-001"))?.data ?? "",
  );

  assert.deepEqual(
    Object.fromEntries([
      "fee_mode",
      "requested_amount",
      "fee_amount",
      "owner_debit_amount",
      "beneficiary_amount",
      "cash_required_amount",
    ].map((field) => [field, clientPayout.get(field)])),
    {
      fee_mode: "ADDED_ON_TOP",
      requested_amount: "100",
      fee_amount: "2",
      owner_debit_amount: "102",
      beneficiary_amount: "100",
      cash_required_amount: "102",
    },
  );
  assert.deepEqual(
    Object.fromEntries([
      "fee_mode",
      "requested_amount",
      "fee_amount",
      "owner_debit_amount",
      "beneficiary_amount",
      "cash_required_amount",
    ].map((field) => [field, partnerPayout.get(field)])),
    {
      fee_mode: "DEDUCTED_FROM_PAYOUT",
      requested_amount: "100",
      fee_amount: "2",
      owner_debit_amount: "100",
      beneficiary_amount: "98",
      cash_required_amount: "100",
    },
  );

  const beneficiaryRows = rowsFor("t_beneficiary_account").map((row) => row.data).join("；");
  assert.match(beneficiaryRows, /status=VERIFIED/u);
  assert.match(content, /单表 Beneficiary.*审核后冻结.*银行账号变化.*新 ID/u);

  const executionRows = rowsFor("t_payout_execution").map((row) => row.data).join("；");
  for (const status of ["CREATED", "ABORTED", "UNKNOWN", "FAILED", "RECONCILED"]) {
    assert.match(executionRows, new RegExp(`status=${status}`, "u"));
  }
  assert.match(content, /CREATED.*外部银行提交前/u);
  assert.match(content, /ABORTED.*仅限.*提交前/u);
  assert.match(content, /UNKNOWN.*阻断.*重试.*取消/u);
  assert.match(content, /FAILED.*释放.*Bank Cash.*Owner LOCKED/u);

  const bankMatchRows = rowsFor("t_bank_recon_match").map((row) => row.data).join("；");
  assert.match(bankMatchRows, /match_type=PAYOUT_DEBIT.*payout_component_type=BENEFICIARY_TRANSFER.*status=CONFIRMED/u);
  assert.match(bankMatchRows, /match_type=PAYOUT_DEBIT.*payout_component_type=BANK_FEE.*status=CONFIRMED/u);
  assert.match(bankMatchRows, /match_type=PAYOUT_DEBIT.*payout_component_type=COMBINED.*status=CONFIRMED/u);
  assert.match(content, /只有 CONFIRMED Bank Match.*权威/u);
  assert.match(content, /split.*BENEFICIARY_TRANSFER.*BANK_FEE/u);
  assert.match(content, /COMBINED.*同一 Bank Payment Ref.*权威证据/u);

  assert.match(rowsFor("t_payout_evidence_ref").map((row) => row.data).join("；"), /evidence_type=PAYMENT_PROOF/u);
  assert.match(content, /Payment proof.*不等于 PAID/u);
  for (const checkpoint of ["Payout 创建", "最终审批", "Execution 创建", "外部银行提交"]) {
    assert.match(content, new RegExp(`${checkpoint}.*RCO-I09.*assessment_id=.*evaluation_id=`, "u"));
  }
  assert.match(content, /assessment_id.*evaluation_id.*Scope mismatch.*Fail-Closed/u);

  for (const entryKey of ["PAYOUT_LOCK:", "PAYOUT_RELEASE:", "PAYOUT_PAID:", "PAYOUT_FEE_DIFF:", "LATE_DEBIT:"]) {
    assert.match(content, new RegExp(entryKey, "u"));
  }
  assert.match(content, /实际费用低于.*PAYOUT_FEE_DIFF/u);
  assert.match(content, /实际费用高于.*PAYOUT_FEE_DIFF/u);
  assert.match(content, /迟到 Debit.*LATE_DEBIT/u);
  assert.match(content, /每个 `status=POSTED` Instruction 恰有一条 Journal/u);
  assert.match(content, /Journal Line[^。\n]*instruction_line_id[^。\n]*Account[^。\n]*Side[^。\n]*exact Amount/u);
  assert.match(content, /origin_type=MANUAL[^。\n]*source fields[^。\n]*NULL[^。\n]*manual_adjustment_id/u);
  assert.match(content, /进入 APPROVED[^。\n]*同 `action_no`[^。\n]*Checker approval/u);
  assert.match(content, /PAYING\/PAYMENT_UNKNOWN -> RETRY_REQUIRED[^。\n]*decisive FAILED Event[^。\n]*NO_DEBIT_PROOF/u);
  assert.match(content, /普通状态白名单不包含 `FAILED -> FAILED`/u);

  const balanced = parseDeclaredJournalRows(content);
  assert.ok(balanced.length >= 8, `Manual Payout 平衡 Journal 示例不足，当前 ${balanced.length} 条`);
  assert.equal(validateDeclaredJournalTruth(balanced, rows), balanced.length);
});

test("Finance scenarios post RefundSucceeded directly without a refund Funding reservation", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  const refund = recordsFor(rows, "t_transaction_economics")
    .find((record) => valueOf(record, "economics_type") === "REFUND");
  assert.ok(refund);
  assert.ok(identifierEqual(valueOf(refund, "parent_economics_id"), "31001"));
  const refundComponent = recordsFor(rows, "t_transaction_economic_component")
    .find((record) => identifierEqual(valueOf(record, "transaction_economics_id"), valueOf(refund, "id")));
  const reversal = recordsFor(rows, "t_transaction_economic_comp_link")
    .find((record) => identifierEqual(valueOf(record, "target_component_id"), valueOf(refundComponent, "id")));
  assert.equal(valueOf(reversal, "link_type"), "REVERSAL");
  const instruction = recordsFor(rows, "t_accounting_instruction")
    .find((record) => identifierEqual(valueOf(record, "transaction_economics_id"), valueOf(refund, "id")));
  assert.equal(valueOf(instruction, "instruction_type"), "REFUND_SUCCEEDED");
  const journal = recordsFor(rows, "t_journal_entry")
    .find((record) => identifierEqual(valueOf(record, "instruction_id"), valueOf(instruction, "id")));
  const summary = journalSummary(rows, valueOf(journal, "id"));
  assert.equal(fixedFormat(summary.debitTotal), "20.00000000");
  assert.equal(fixedFormat(summary.creditTotal), "20.00000000");
  assert.match(
    content,
    /RefundSucceeded[^。\n]*(?:直接|幂等)[^。\n]*REFUND Transaction Economics[^。\n]*Component Link[^。\n]*Journal/u,
  );
  assert.match(
    content,
    /退款成功[^。\n]*(?:优先|依次)[^。\n]*PENDING[^。\n]*AVAILABLE[^。\n]*(?:Recovery Receivable|CLIENT_RECOVERY_RECEIVABLE)/u,
  );
  assert.doesNotMatch(content, /CREATE_REFUND_FUNDING|REFUND_FUNDING_RESERVATION/u);
});

test("Finance scenarios use a prepaid security-deposit Account and Journal-only recovery", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);

  for (const removedTable of [
    "t_reserve_case",
    "t_restricted_fund",
    "t_restricted_fund_move",
    "t_recovery_case",
    "t_recovery_application",
  ]) {
    assert.ok(!rows.some((row) => row.table === removedTable), `场景仍使用已删除表 ${removedTable}`);
  }

  for (const expected of [
    "CLIENT_SECURITY_DEPOSIT",
    "CLIENT_RECOVERY_RECEIVABLE",
    "Dr BANK_CASH / Cr CLIENT_SECURITY_DEPOSIT",
    "Payout 创建、最终审批、Execution 创建、外部银行提交",
    "Recovery Receivable 为 0",
    "保证金余额",
    "未来净额抵扣",
    "人工回款",
  ]) {
    assert.ok(content.includes(expected), `保证金/追偿场景缺少关键语义：${expected}`);
  }

  assert.match(content, /RefundSucceeded[^。\n]*不[^。\n]*(?:消耗|使用)[^。\n]*CLIENT_SECURITY_DEPOSIT/u);
  assert.match(content, /DisputeLost[^。\n]*明确允许[^。\n]*(?:消耗|使用)[^。\n]*CLIENT_SECURITY_DEPOSIT/u);
  assert.doesNotMatch(content, /CLIENT_RESTRICTED_FUNDS|reserve_case_id=|ReserveHeld|ReserveReleased|RecoveryApplied/u);
});

test("Finance scenarios explain two-layer Channel reconciliation without Payment online dependency", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);

  for (const removedTable of [
    "t_channel_settle_alloc",
    "t_channel_cash_item",
    "t_recon_match",
  ]) {
    assert.ok(!rows.some((row) => row.table === removedTable), `场景仍使用旧对账表 ${removedTable}`);
  }
  for (const expectedTable of ["t_channel_transaction_match", "t_bank_recon_match"]) {
    assert.ok(rows.some((row) => row.table === expectedTable), `场景缺少 ${expectedTable}`);
  }
  for (const expected of [
    "第一层：Channel 交易对账",
    "第二层：银行现金对账",
    "Finance 本地冻结引用",
    "无需在线查询 Payment",
    "未知外部流水",
    "VALIDATED 只表示",
    "交易对账完整",
    "FEE/ADJUSTMENT",
    "Cost Confirm",
    "Channel Batch 本身就是银行对账目标",
  ]) {
    assert.ok(content.includes(expected), `Channel 对账场景缺少关键语义：${expected}`);
  }
  assert.doesNotMatch(content, /Channel Cash Item|channel_cash_item_id=/u);
  assert.match(content, /account_purpose=CHANNEL_SETTLEMENT/u);
  assert.doesNotMatch(content, /account_purpose=REFUND/u);
});

test("Finance scenarios derive Bank Cash capacity without generic Funding rows", () => {
  const text = readFileSync(scenarioPath, "utf8");

  assert.doesNotMatch(text, /\| `t_funding_(?:position|commitment|allocation|move)` \|/u);
  assert.match(text, /Bank Cash Account\.net_balance/u);
  assert.match(text, /活动 Payout Execution/u);
  assert.match(text, /活动 Tax Payment/u);
  assert.match(text, /UNKNOWN.*不释放/u);
  assert.match(text, /CREATED\/SUBMITTED\/UNKNOWN.*占用.*Bank Cash/u);
  assert.match(text, /ABORTED\/FAILED\/RECONCILED.*不占用.*Bank Cash/u);
  assert.match(text, /Execution 创建.*同一事务.*占用银行现金容量/u);
  assert.match(text, /FAILED.*释放银行现金容量.*Owner LOCKED/u);
  assert.match(text, /同一 `tax_payable_account_id`.*全部活动 Tax Payment/u);
  assert.match(text, /两个 Tax Return.*同一 Tax Payable Account/u);
  assert.match(text, /LATE_DEBIT.*RECON_SUSPENSE_ASSET.*BANK_CASH/u);
  assert.match(text, /旧 FAILED.*不得.*普通成功.*新 Execution.*占用/u);
});

test("Finance scenarios prove Partner OE and platform Finance Entity double entry", () => {
  const content = readFileSync(scenarioPath, "utf8");
  for (const expected of [
    "t_finance_entity",
    "PLATFORM_SERVICE_CHARGE",
    "PLATFORM_SERVICE_CHARGE_PARTNER",
    "PLATFORM_SERVICE_CHARGE_PLATFORM",
    "PLATFORM_SERVICE_FEE_EXPENSE",
    "PLATFORM_SERVICE_PAYABLE",
    "PLATFORM_SERVICE_RECEIVABLE",
    "PLATFORM_SERVICE_REVENUE",
    "USD 100.00",
    "USD 0.40",
    "ledger_book.id ASC",
    "account.id ASC",
    "commit once",
    "任一失败全部回滚",
    "非 Partner 来源不生成平台服务费",
    "Finance Entity 不存在、重复或 CLOSED",
    "Platform Service Party 不一致",
    "相同 Event Key + 相同 Payload",
    "相同 Key + 不同金额或主体",
    "Refund 沿 parent_economics_id + component link",
    "两本账分别执行 Trial、Close 和 Profit Carry Forward",
    "Accounting Rule 只读取已计算的 PLATFORM_SERVICE_CHARGE Component Amount",
  ]) {
    assert.ok(content.includes(expected), `双账场景缺少关键语义：${expected}`);
  }
  assert.match(content, /Dr PLATFORM_SERVICE_FEE_EXPENSE.*Cr PLATFORM_SERVICE_PAYABLE/su);
  assert.match(content, /Dr PLATFORM_SERVICE_RECEIVABLE.*Cr PLATFORM_SERVICE_REVENUE/su);
  assert.match(content, /两条 Journal.*各自平衡.*同一 Finance 本地事务/su);
  assert.doesNotMatch(content, /`t_platform_service_charge`/u);
});

test("Finance scenarios prove Trial watermark, rebuild and final-close gates", () => {
  const content = readFileSync(scenarioPath, "utf8");
  for (const expected of [
    "期末余额按实际借贷方向拆分",
    "CREATED/RUNNING 时 content_hash=NULL",
    "PROJECTION_REBUILD",
    "不追加 Journal",
    "修复后必须新建 Trial Run",
    "as_of_sequence=9015",
    "旧水位不得覆盖",
    "PLATFORM_SERVICE_PAYABLE 与 PLATFORM_SERVICE_RECEIVABLE",
    "同一 Book、Period、as_of_sequence",
    "无 OPEN/UNDER_REVIEW 异常",
    "七个 Close Step 全部 SUCCEEDED",
    "同一事务",
  ]) {
    assert.ok(content.includes(expected), `Trial/Close 场景缺少关键语义：${expected}`);
  }
});

test("Finance scenarios use the 23-table Tax and Fiscal Rule and Return model", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);

  for (const removedTable of [
    "t_tax_rule_version",
    "t_tax_rule_approval",
    "t_tax_return_ver",
    "t_tax_return_approval",
  ]) {
    assert.ok(!rows.some((row) => row.table === removedTable), `场景仍使用已合并表 ${removedTable}`);
  }

  const taxRule = recordsFor(rows, "t_tax_rule")[0];
  assert.ok(identifierEqual(valueOf(taxRule, "rule_ver"), "1"));
  assert.equal(valueOf(taxRule, "content_hash"), "hash-rule-v1");
  assert.ok(identifierEqual(valueOf(taxRule, "maker_id"), "90001"));
  assert.ok(identifierEqual(valueOf(taxRule, "checker_id"), "90002"));
  assert.equal(valueOf(taxRule, "checker_decision"), "APPROVED");
  assert.equal(valueOf(taxRule, "status"), "ACTIVE");
  assert.equal(taxRule.values.has("current_ver_id"), false);
  const taxRate = recordsFor(rows, "t_tax_rate")[0];
  assert.ok(identifierEqual(valueOf(taxRate, "tax_rule_id"), valueOf(taxRule, "id")));
  assert.equal(taxRate.values.has("tax_rule_ver_id"), false);

  const taxReturn = recordsFor(rows, "t_tax_return")[0];
  assert.ok(identifierEqual(valueOf(taxReturn, "return_ver"), "1"));
  assert.ok(isNull(valueOf(taxReturn, "amends_return_id")));
  assert.equal(valueOf(taxReturn, "content_hash"), "hash-return-v1");
  assert.equal(valueOf(taxReturn, "checker_decision"), "APPROVED");
  assert.equal(valueOf(taxReturn, "status"), "CLOSED");
  assert.equal(taxReturn.values.has("current_ver_id"), false);
  for (const childTable of ["t_tax_return_line", "t_tax_payment"]) {
    const child = recordsFor(rows, childTable)[0];
    assert.ok(identifierEqual(valueOf(child, "tax_return_id"), valueOf(taxReturn, "id")));
    assert.equal(child.values.has("tax_return_ver_id"), false);
  }
  assert.match(content, /离开 DRAFT 后.*冻结.*新.*rule_ver/su);
  assert.match(content, /return_ver=1.*amends_return_id=NULL/su);
  assert.match(content, /return_ver>1.*被修订 Return/su);
});
