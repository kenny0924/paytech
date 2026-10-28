import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { builtin } from "./i18n/builtin.ts";
import { enumCodes } from "./enum-label.ts";
import { teamErrorCodes } from "./team.ts";
import { fallbackReasons, homeNoticeCodes } from "./workspace.ts";
import { graceOptions } from "./developer.ts";
import { SCHEMA_NAMESPACE, schemaI18nKey } from "./onboarding/schema.ts";
import { requestErrorCodes } from "./onboarding/draft.ts";
import { legalTextKey, thresholdKeys } from "./onboarding/declarations.ts";

const src = fileURLToPath(new URL(".", import.meta.url));
const seed = JSON.parse(readFileSync(join(src, "../i18n/client-console.zh-CN.seed.json"), "utf8")) as {
  format: string; application: string; locale: string; namespaces: Record<string, Record<string, string>>;
};
const cjk = /[぀-ヿ㐀-䶿一-鿿가-힯豈-﫿＀-￯　-〿]/;

/** 接入范围：src 下全部 ts / tsx，排除运行时目录 src/i18n 与测试文件。 */
function sourceFiles(dir = src): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return relative(src, path) === "i18n" ? [] : sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.ts$/.test(name) ? [path] : [];
  });
}
/** 去掉注释，保留代码、字符串与 JSX 文本；中文注释不属于界面文案。 */
function withoutComments(text: string): string {
  let out = "", i = 0, quote: string | null = null;
  while (i < text.length) {
    const c = text[i], next = text[i + 1];
    if (quote) {
      out += c;
      if (c === "\\") { out += next ?? ""; i += 2; continue; }
      if (c === quote || (c === "\n" && quote !== "`")) quote = null;
      i++;
    } else if (c === "/" && next === "/") {
      while (i < text.length && text[i] !== "\n") i++;
    } else if (c === "/" && next === "*") {
      const end = text.indexOf("*/", i + 2);
      i = end < 0 ? text.length : end + 2;
    } else {
      if (c === "'" || c === '"' || c === "`") quote = c;
      out += c;
      i++;
    }
  }
  return out;
}
const enKeys = (ns: string) => Object.keys(builtin[ns] ?? {});
const hasKey = (full: string) => Object.prototype.hasOwnProperty.call(builtin[full.split(".")[0]] ?? {}, full);

test("no CJK characters remain in UI source outside src/i18n and tests", () => {
  const offenders = sourceFiles().flatMap((path) => withoutComments(readFileSync(path, "utf8")).split("\n")
    .map((line, index) => ({ line, index }))
    .filter(({ line }) => cjk.test(line))
    .map(({ line, index }) => relative(src, path) + ":" + (index + 1) + " " + line.trim()));
  assert.deepEqual(offenders, []);
});

test("the comment stripper keeps strings and JSX text, so the scan cannot be bypassed", () => {
  assert.ok(cjk.test(withoutComments('const a = "中文"; // 注释')));
  assert.ok(cjk.test(withoutComments("<p>中文</p>")));
  assert.ok(!cjk.test(withoutComments("/** 注释 */\nconst url = 'https://x'; // 注释")));
});

test("English base and zh-CN seed have identical namespaces and keys, all prefixed by their namespace", () => {
  assert.equal(seed.format, "code-mor-i18n-translation/1");
  assert.equal(seed.application, "client-console");
  assert.equal(seed.locale, "zh-CN");
  assert.deepEqual(Object.keys(seed.namespaces).sort(), Object.keys(builtin).sort());
  for (const ns of Object.keys(builtin)) {
    assert.deepEqual(Object.keys(seed.namespaces[ns]).sort(), enKeys(ns).sort(), ns);
    for (const key of enKeys(ns)) {
      // 国家包命名空间的键来自 DML 代码（如 onboarding-schema.organizationType.AE.LLC），段内允许大写。
      assert.match(key, new RegExp("^" + ns + (ns === SCHEMA_NAMESPACE ? "(\\.[A-Za-z0-9_]+)+$" : "(\\.[a-z0-9_]+)+$")), key);
      assert.equal(typeof seed.namespaces[ns][key], "string", key);
      assert.ok(seed.namespaces[ns][key].trim(), "empty zh-CN seed for " + key);
    }
  }
});

test("every static t() key in source exists in the English base", () => {
  const missing: string[] = [];
  for (const path of sourceFiles()) {
    const text = readFileSync(path, "utf8");
    const bindings = [...text.matchAll(/\b(\w+)\s*=\s*(?:useT|translator)\(\s*["'](\w+)["']\s*\)/g)];
    for (const [, name, ns] of bindings) {
      for (const call of text.matchAll(new RegExp("\\b" + name + "\\(\\s*([\"'])([^\"'`]+)\\1\\s*[,)]", "g"))) {
        if (!hasKey(ns + "." + call[2])) missing.push(relative(src, path) + ": " + ns + "." + call[2]);
      }
    }
  }
  assert.deepEqual(missing, []);
});

test("every dynamic key family resolves in the English base", () => {
  const expected = [
    ...Object.entries(enumCodes).flatMap(([group, codes]) => codes.map((code) => "enum." + group + "." + code.toLowerCase())),
    ...teamErrorCodes.map((code) => "team.error." + code.toLowerCase()),
    ...["tenant_admin", "tenant_developer", "tenant_viewer"].map((role) => "team.role_description." + role),
    ...["remove", "revoke", "resend"].flatMap((kind) => ["team.confirm." + kind + "_title", "team.confirm." + kind + "_body", "team.action." + kind]),
    ...fallbackReasons.map((reason) => "workspace.fallback." + reason.toLowerCase()),
    ...homeNoticeCodes.map((code) => "workspace.notice." + code.toLowerCase()),
    ...["api_keys", "webhooks"].map((key) => "workspace.nav." + key),
    ...["profile_individual", "profile_organization", "address", "people", "materials_individual", "materials_organization", "declarations", "review"]
      .map((task) => "onboarding.task." + task),
    ...["done", "attention", "active", "todo", "locked"].map((state) => "onboarding.task_state." + state),
    ...["profile_individual", "profile_organization", "address"].map((task) => "onboarding.task_intro." + task),
    ...["field_missing", "group_missing", "too_short", "too_long", "digits", "format", "checksum", "url", "email", "phone"].map((key) => "onboarding.error." + key),
    ...["type", "size", "empty"].map((problem) => "onboarding.upload.problem_" + problem),
    ...["ownership", "control", "senior_managing_official"].map((basis) => "onboarding.people.ubo_basis_" + basis),
    ...["met", "not_met"].map((key) => "onboarding.people." + key),
    ...["yes", "no"].map((key) => "onboarding.form." + key),
    ...[...requestErrorCodes.map((code) => code.toLowerCase()), "gateway_concurrency_limited", "unavailable"].map((key) => "onboarding.request_error." + key),
    ...thresholdKeys.flatMap((key) => ["onboarding." + key, "onboarding." + key + "_gt"]),
    ...graceOptions.map((option) => "developer.grace.m" + option.minutes),
    ...["invalid", "closed", "unavailable", "missing"].map((kind) => "invitation.problem." + kind),
    ...["login", "register", "forgot", "reset", "verify", "sent"].flatMap((kind) => ["auth." + kind + ".title", "auth." + kind + ".intro", "auth.submit." + kind]),
    ...["register", "verify", "reset"].map((kind) => "auth." + kind + ".done_title"),
    ...["verify", "reset"].map((kind) => "auth." + kind + ".done_status"),
  ];
  assert.deepEqual(expected.filter((key) => !hasKey(key)), []);
});

const repo = join(src, "../..");
const readSql = (path: string) => { try { return readFileSync(join(repo, path), "utf8"); } catch { return null; } };

test("Platform namespace codes are lowercase with hyphens", () => {
  for (const ns of Object.keys(builtin)) assert.match(ns, /^[a-z][a-z0-9-]{0,63}$/, ns);
});

const clientMasterSql = () => readSql("paytech/client-master/docs/database/sql/client-master-initial-data.sql");

test("every label and help key of the client-master country packs exists in the onboarding-schema base after mapping", () => {
  const sql = clientMasterSql();
  if (sql === null) return;
  const keys = [...new Set([...sql.matchAll(/'(onboardingSchema\.[A-Za-z0-9_.]+)'/g)].map((match) => match[1]))];
  assert.ok(keys.length > 50, "country pack label keys not found");
  assert.deepEqual(keys.map(schemaI18nKey).filter((key) => !hasKey(key)), []);
});

test("every declaration document version configured in the client-master country packs has a bundled title and body", () => {
  const sql = clientMasterSql();
  if (sql === null) return;
  const versions = [...sql.matchAll(/'DECLARATION', '([A-Z][A-Z0-9_]*)', 'DOCUMENT_VERSION', '([^']+)'/g)];
  assert.ok(versions.length > 0, "declaration document versions not found");
  const missing = versions.flatMap(([, type, version]) => (["title", "body"] as const).map((part) => "onboarding." + legalTextKey(type, version, part)))
    .filter((key) => !hasKey(key));
  assert.deepEqual(missing, []);
});

test("every compliance material requirement and document type has a client label", () => {
  const sql = readSql("paytech/compliance/docs/database/sql/compliance-initial-data.sql");
  if (sql === null) return;
  const requirements = [...sql.matchAll(/t_dd_program_requirement \(.*?VALUES \(\d+, \d+, '([A-Z_]+)'/g)].map((match) => match[1]);
  const types = [...sql.matchAll(/t_dd_program_requirement_doc_type \(.*?VALUES \(\d+, \d+, '([A-Z_]+)'/g)].map((match) => match[1]);
  assert.ok(requirements.length && types.length, "program materials not found");
  const missing = [...new Set([...requirements.map((code) => "onboarding.requirement." + code.toLowerCase()),
    ...types.map((code) => "onboarding.material." + code.toLowerCase())])].filter((key) => !hasKey(key));
  assert.deepEqual(missing, []);
});

test("reference data names are not bundled: refdata is Platform-published data and the old schema catalog keys are gone", () => {
  // 行政区等名称由 Platform 的 refdata 命名空间发布（英文基准 = 参考数据 name），前端不内置，缺失时回退后端 name。
  assert.ok(!Object.prototype.hasOwnProperty.call(builtin, "refdata"));
  assert.ok(!Object.prototype.hasOwnProperty.call(seed.namespaces, "refdata"));
  const retired = /^onboarding-schema\.catalog\.(INDUSTRY|CURRENCY|STATE_OR_PROVINCE)\./;
  assert.deepEqual(enKeys(SCHEMA_NAMESPACE).filter((key) => retired.test(key)), []);
});
