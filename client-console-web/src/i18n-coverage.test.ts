import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { builtin } from "./i18n/builtin.ts";
import { enumCodes } from "./enum-label.ts";
import { teamErrorCodes } from "./team.ts";
import { fallbackReasons, homeNoticeCodes } from "./workspace.ts";
import { returnSections } from "./onboarding.ts";
import { graceOptions } from "./developer.ts";

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
      assert.match(key, new RegExp("^" + ns + "(\\.[a-z0-9_]+)+$"), key);
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
    ...Object.values(returnSections).map((value) => "onboarding.section." + value.section),
    ...graceOptions.map((option) => "developer.grace.m" + option.minutes),
    ...["invalid", "closed", "unavailable", "missing"].map((kind) => "invitation.problem." + kind),
    ...["login", "register", "forgot", "reset", "verify", "sent"].flatMap((kind) => ["auth." + kind + ".title", "auth." + kind + ".intro", "auth.submit." + kind]),
    ...["register", "verify", "reset"].map((kind) => "auth." + kind + ".done_title"),
  ];
  // OnboardingPage 字段标签由资料路径换算：individual.givenName → onboarding.field.individual.given_name。
  const page = readFileSync(join(src, "OnboardingPage.tsx"), "utf8");
  const fields = [...page.matchAll(/\["((?:individual|organization|address|identifier|business|contact)\.\w+)", "/g)].map((m) => m[1]);
  assert.ok(fields.length >= 30, "field definitions not found");
  expected.push(...fields.map((key) => "onboarding.field." + key.replace(/[A-Z]/g, (letter) => "_" + letter.toLowerCase())));
  assert.deepEqual(expected.filter((key) => !hasKey(key)), []);
});
