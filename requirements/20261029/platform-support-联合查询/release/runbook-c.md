# 联合查询修订 c（technique 8.7 补充）· mor-test 运行卡

> 2026-10-30 准备（只读核对 + 本机校验）；执行由主会话。**不换 Platform / ops-bff、不执行 SQL、不改注册块**：服务端行为不变，只换 ops-web（页面按对象合并显示）并经运营 API 调整演示数据（3 个子行键类型 + 3 个子行模板新版本）。

## 0. 现网（2026-10-30 08:59Z 只读读取）

| 服务 | 镜像 | 说明 |
|---|---|---|
| platform-support-service | `sha256:a3003ef6499dcf5680154589f501d48db43e9c8041c02dcd5330e5eeade6a11c` | 多语言发布变更记录 `i18nchg-20261030b`，构建在联合查询 b 之上：与 b 候选只差 localization 模块与 `PersistenceConfiguration`，联合查询类逐项相同。本修订不动 |
| operations-console-bff | `sha256:2452aaa79b35a73d707d11934eb42b308d94758b5d4d3269c5178ed6b39ef6d3` | 联合查询 b。本修订不动 |
| operations-console-web | `sha256:d236f1d53e219418515926dbe872d92e3f33278b187ae356c4a62b4d72de2dd4` | 联合查询 b = **回滚目标** |

mor_platform：键类型 26（无 3 个子行键类型）；3 个子行模板 v1、ACTIVE；`JQ_BILLING_FACT_BY_ATTEMPT` ACTIVE（D-b1 未执行）；operations-console 当前语言包 **#26**。

## 1. 内容

| 项 | 内容 |
|---|---|
| ops-web | jq-web 构建 `sha256:6f8161c279e71b24efc79280bdff5d0b3bbe4822e44e442cde409d88f4bd0173`（`jq-20261030c`，dist 273；`compose-candidate-c.operations-console-web.yaml` / `compose-rollback-c.operations-console-web.yaml`，回滚 = `d236f1d5`；`scripts/env_check_c.sh` 渲染与现网 7 / 7 相同）。另：旧 BFF 返回 400 INVALID_REQUEST 时页面不带 loadedSubjects 重发一次。按对象合并显示、去重后无新行的节点不单独绘制、“也可经 … 到达” |
| 键类型 | `ACCOUNT_ENTRY_ID` 账户分录 ID（账户，111）、`JOURNAL_LINE_ID` 凭证分录 ID（总账，121）、`STATEMENT_LINE_ID` 对账单明细 ID（对账，141）；LINK、不作入口；经 K2 新建（Snowflake ID）。仓库初始化数据已同步（块 144 增至 29 行，ID 144127—144129；已初始化环境的 SQL 补丁 `paytech/platform-support/docs/database/sql/changes/2026-10-30-joint-query-child-key-types{,-rollback}.sql`，mor-test 不用） |
| 模板 | 账户分录、凭证分录、渠道对账单明细：输出自身行 ID（`account_entry_id`、`journal_line_id`、`statement_line_id`），行主体改为新键类型（LINK），触发键仍为父键类型；保存为 v2（changeNote `联合查询修订 c：子行以自身键类型为行主体（jq-20261030c）`）。正文已在 mor-test 只读会话与本机 v2e2e / auto 库执行通过（`precheck/demo-templates-local-c.json`、`demo-chains-mortest-c-20261030.json`） |
| 语言包 | #27：只读计划（live #26）新增 1 个 `data-query.joint.detail.alsoVia`（zh-CN “也可经 {path} 到达”），zh-CN 导入 1，其余 0，门禁 12 / 12（`precheck/i18n-plan-c-dryrun.out`） |

## 2. 步骤

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
R=/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel/.jq-notes/release
img() { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
```

| 步 | 内容 | 预期 / 失败即停 |
|---|---|---|
| ① | 通知并行会话：ops-web 数秒、演示模板新版本、operations-console #27 | — |
| ② | `for s in platform-support-service operations-console-bff operations-console-web; do echo "$s $(img $s)"; done` | 与第 0 节相同；ops-web 不是 `d236f1d5` 即停 |
| ③ | `E2E_TARGET=test python3 -I $R/demo/register_jq_demo.py plan` | 动作恰好 6 项：create key type × 3、update template × 3（3 个子行模板）；其余 31 个模板与 7 个数据源无动作 |
| ④ | `E2E_TARGET=test python3 -I $R/demo/register_jq_demo.py apply --yes` | 3 个键类型 ACTIVE；3 个子行模板 v2 ACTIVE 且参与；verify 5 个入口无 excluded |
| ⑤ | `./test-environment/compose.sh -f "$R/compose-candidate-c.operations-console-web.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-web` | `6f8161c2…` healthy、文件数 273 |
| ⑥ | 语言包 #27：`python3 -I $R/jq_bundles_c.py`（计划）→ 加 `--apply` | 当前 **#26**；新增 1（data-query.joint.detail.alsoVia），其余 0；当前号不是 #26 即停 |
| ⑦ | 验收：`E2E_TARGET=test python3 -I $R/acceptance/jq_api_c.py`；浏览器走查（jq-web） | 全部 PASS（C1—C5） |
| ⑧ | 更新 delivery.md、CURRENT_IMPLEMENTATION；通知 ① 的会话 | — |

③ 与 ⑤ 先后不限：旧页面（b）对新模板只是多了子行节点；新页面（c）对旧模板只是子行仍在“另有 N 个视图”里。服务端不变，没有不可用的中间状态。

## 3. 验收预期（mor-test 只读模拟，`precheck/demo-chains-mortest-c-20261030.out`）

| 入口 | 执行数 b → c | 子行节点 |
|---|---|---|
| 交易订单号 `ord_3LOyZii4…` | 16 → **19** | 账户分录 1 行（第 3 跳）、渠道对账单明细 0 行（第 3 跳）、凭证分录 6 行（第 4 跳） |
| 支付意图 / 退款单号 / 渠道交易号 | 13 / 7 / 17（不变） | 无 |
| 会计凭证 ID `241801561934753792` | 21 → 22 | 凭证分录 6 行（入口跳）、账户分录 1 行、渠道对账单明细 0 行 |

“按自身 ID 查”的跳过不变（订单号入口仍跳过订单、会话、计费事实、记账批次、会计凭证的按 ID 模板）；子行模板不再出现在 alreadyLoaded。跨路径重复（如计费事实按订单 / 按支付尝试）在接口层仍各返回一次，由新页面按对象合并显示。

## 4. 回滚（逆序）

| 顺序 | 动作 | 说明 |
|---|---|---|
| R1 | `./test-environment/compose.sh -f "$R/compose-rollback-c.operations-console-web.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-web` | 回 `d236f1d5`（b） |
| R2 | `E2E_TARGET=test python3 -I $R/demo/register_jq_demo.py rollback-c --yes` | 3 个子行模板当前版本若为修订 c，经 restoration 恢复 v1 的定义（生成 v3，restoredFromVersionNo = 1）；然后 3 个子行键类型改 INACTIVE（模板不再引用后才能停用） |
| R3 | 语言包不回退 | 旧页面不引用新 key |

注意：R2 之后 `jq_demo_spec.py` 仍是修订 c 定义；若要让登记脚本与现网一致，改用备份 `demo/jq_demo_spec.b.py.bak`（复制回 `jq_demo_spec.py`）。
