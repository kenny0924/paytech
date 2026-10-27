#!/usr/bin/env python3
"""从阶段 5 本机浏览器冒烟脚本生成 mor-test 版本（rel-dev 2026-10-27）：只改环境相关行，检查项不变。
  ops：WEB → http://localhost:28080；管理员凭据取 test-environment/private/access.md（不打印）；去掉"root SQL 临时停用 MCC 5411"
       的服务端 422 子步骤（mor-test 不做 root 写，本机已验证）；去掉 DRAFT 币种 XTS 面板检查（mor-test 不建测试币种）。
  client：WEB → https://localhost:28443；账号与申请由 ui_provision_mortest.py 预置（状态文件 ~/Code-MoR-backups/refdata-20261027-ui/ui-client.json，0600）。
生成：ops_refdata.mortest.mjs、client_refdata.mortest.mjs（与本文件同目录）。源脚本变化后重跑。
"""
from pathlib import Path
HERE = Path(__file__).resolve().parent
SRC = HERE.parents[1] / "verification/ui-smoke"
STATE = str(Path.home() / "Code-MoR-backups/refdata-20261027-ui/ui-client.json")


def must(text, old, new):
    if old not in text:
        raise SystemExit("源脚本已变化，找不到：" + old[:80])
    return text.replace(old, new, 1)


def ops():
    s = (SRC / "ops_refdata.mjs").read_text(encoding="utf-8")
    s = s.replace("import.meta.url));\nconst WEB", "import.meta.url));\nconst WEB", 1)
    s = must(s, "const HERE = dirname(fileURLToPath(import.meta.url));", "const HERE = join(dirname(fileURLToPath(import.meta.url)), '../../verification/ui-smoke');")
    s = must(s, "const WEB = 'https://127.0.0.1:18444';", "const WEB = 'http://localhost:28080';  // mor-test（生成自本机版本）")
    start = s.index("const env = Object.fromEntries(")
    end = s.index("\n", s.index("));", start)) + 1
    s = s[:start] + ("const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');\n"
                     "const env = { E2E_ADMIN_EMAIL: access.match(/账号：(\\S+)/)[1], E2E_ADMIN_PASSWORD: access.match(/密码：(\\S+)/)[1] };\n") + s[end:]
    s = must(s, "const sql = statement => execFileSync(MYSQL, ['mor_platform_v2e2e', '-N', '-B'], { input: statement }).toString().trim();",
             "const sql = () => { throw new Error('mor-test 冒烟不执行 SQL'); };")
    start = s.index("  sql(\"UPDATE t_ref_mcc SET status='INACTIVE' WHERE mcc_code='5411'\");")
    end = s.index("  } finally { sql(\"UPDATE t_ref_mcc SET status='ACTIVE' WHERE mcc_code='5411'\"); }\n") + \
        len("  } finally { sql(\"UPDATE t_ref_mcc SET status='ACTIVE' WHERE mcc_code='5411'\"); }\n")
    s = s[:start] + "  console.log('SKIP 服务端 422（临时停用 MCC 5411）：mor-test 不做 root 写，本机已验证');\n" + s[end:]
    start = s.index("  await page.getByText('XTS').first().click();")
    end = s.index("  await page.getByText('USD').first().click();")
    s = s[:start] + "  console.log('SKIP DRAFT 币种 XTS 面板：mor-test 不建测试币种，本机已验证');\n" + s[end:]
    s = s.replace("const tag = `ops-${lang}`", "const tag = `mortest-ops-${lang}`").replace("tag = `ops-${lang}`", "tag = `mortest-ops-${lang}`")
    s = s.replace("`ops-findings-${lang}.jsonl`", "`mortest-ops-findings-${lang}.jsonl`")
    (HERE / "ops_refdata.mortest.mjs").write_text(s, encoding="utf-8")


def client():
    s = (SRC / "client_refdata.mjs").read_text(encoding="utf-8")
    s = must(s, "const HERE = dirname(fileURLToPath(import.meta.url));", "const HERE = join(dirname(fileURLToPath(import.meta.url)), '../../verification/ui-smoke');\nconst REL = dirname(fileURLToPath(import.meta.url));")
    s = must(s, "const WEB = 'https://127.0.0.1:18443';", "const WEB = 'https://localhost:28443';  // mor-test（生成自本机版本）")
    s = must(s, "const STATE = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/8198caa5-69c6-4d42-8400-62a77c6b6af6/scratchpad/refdata-e2e/proc/ui-client.json';",
             f"const STATE = '{STATE}';")
    s = must(s, "execFileSync('python3', ['-I', join(HERE, '../local-e2e/ui_provision.py'), 'reset'], { stdio: 'ignore' });",
             "execFileSync('python3', ['-I', join(REL, 'ui_provision_mortest.py'), 'reset'], { stdio: 'ignore' });")
    s = s.replace("tag = `client-${lang}-${width}`", "tag = `mortest-client-${lang}-${width}`")
    s = s.replace("`client-findings-${lang}-${width}.jsonl`", "`mortest-client-findings-${lang}-${width}.jsonl`")
    (HERE / "client_refdata.mortest.mjs").write_text(s, encoding="utf-8")


ops(); client(); print("generated ops_refdata.mortest.mjs, client_refdata.mortest.mjs")
