# REQ-02 · mor-test 发布 runbook（阶段 7 用，评审稿）

> 状态：**阶段 6 评审稿（2026-10-30，W3）**。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、MID 能力配置、XXL 触发、语言包发布、验收写数据）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权；data-query 登记（第 8 节）另行单独授权。
> 依据：[technique.md](../technique.md) 第 10.3、13、16 节；[baseline.md](./baseline.md)；[W3-report.md](./W3-report.md)。镜像一律钉 sha256（tag 仅供辨认：channel-connector / client-master 为 `req02-20261030a`，commercial / ops-web 为 Q-4 修订后的 `req02-20261030b`）。本需求 0 DDL / 0 DML / 0 注册块 / 0 新 env 键。
> 工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR`；公共变量：
> `REQ=requirements/20261030/channel-connector-渠道身份就绪查询; R=$PWD/$REQ/release; TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-); sqlro() { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }; up() { ./test-environment/compose.sh -f "$R/$1" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$2"; }`
> 任何一步不符合预期就停在该步，按"回滚"列处理。

## 1. 硬性顺序与前置裁决

1. **巡检结论（Q-4 修订口径，2026-10-30 用户确认）**：现网 4 条 ENABLED 中 **2 条预期暂停**——DEMO-CL-001 APPLE_PAY（AE001 无该能力）、DEMO2-CL-101 CARD（只有 AED 价格，AED 无 MID）；DEMO-CL-001 / DEMO2-CL-103 的 CARD 保持 ENABLED 并带 `CHANNEL_IDENTITY_PARTIAL`（AED 缺 MID）提醒。**这 2 条暂停如实反映运行期本就选不到 MID，由用户审批阶段 7 时决定是否接受**；不为此补 MID 能力行（主会话裁决：那等于虚构渠道能力）。
2. 提供方先于消费方：client-master、channel-connector 候选先上（新接口无人调用前不改变行为；候选查询结果不变），commercial 最后（④ 由此开始真实判定）。
3. commercial 上线后，每 5 分钟任务（id 25，`scope=PENDING`）只重查等待 / 门禁暂停的开通项；ENABLED 的在每日 03:00 UTC（id 26）或第 6 步手动触发时按新口径复核。
4. 语言包在 ops-web 之后。约定"谁先进阶段 7 谁先发"，号码按实际发布先后取下一个：现网 #30，**预计 #31，若 REQ-06 先发则 #32**；`req02_bundles.py` 运行时读现网最新号，不写死。若 REQ-06 先发，第 5 步前**重新干跑**，计划应仍只有 commercial 17 / 3 / 2（REQ-06 只动 workflow 命名空间）。REQ-06 若先发 ops-web，主会话会通知重建 ops-web 候选。
5. ops-web 基线是现网 `plops-20261030a`（明细账运维会话发布）：发布前与明细账运维会话确认其不再发 ops-web；若 ops-web 现网 ID 变了，停下重建候选（`source_drift_check.sh` 会报 DRIFT）。
6. `compose.sh up` 不加 `--remove-orphans`；只传本目录覆盖文件，不叠加历史覆盖。

## 2. 步骤

| # | 动作 | 预期 / 停止条件 | 回滚 |
|---|---|---|---|
| 0 | 复核：`$R/scripts/source_drift_check.sh`；`$R/scripts/env_check.sh`；`python3 -I $R/acceptance/req02_acceptance.py plan`；与明细账运维、REQ-06 会话确认无并行发布 | NO DRIFT；8 行 `missing=[] extra=[] valuediff=[]`；4 个镜像均为"发布前" | — |
| 1 | 只读巡检：`sqlro < $R/sql/01-precheck-capabilities-to-suspend.sql > $R/precheck/01-…-<UTC>.out`，结果告知用户（Q-2） | 与阶段 6 读数一致（2 × WILL_SUSPEND、2 × STAYS_ENABLED_WITH_REMINDER（AED）、8 × 等待价格）；变化即记录并重新告知 | — |
| 2 | 替换 **client-master-service**：`up compose-candidate.client-master-service.yaml client-master-service` | healthy；`docker logs` 无 ERROR；Nacos 实例重新注册；中断约 40 s（结账解析、入驻接口 503） | `up compose-rollback.client-master-service.yaml client-master-service` |
| 3 | 替换 **channel-connector-service**：`up compose-candidate.channel-connector-service.yaml channel-connector-service` | healthy；中断约 40 s（payment 选 MID、渠道回调、出站协议 503 / 重试）；`req02_acceptance.py plan` 中就绪查询接口 200 | `up compose-rollback.channel-connector-service.yaml channel-connector-service` |
| 4 | 替换 **commercial-service**：`up compose-candidate.commercial-service.yaml commercial-service` | healthy；日志无 `discovery is unavailable`；中断约 40 s（结账解析、合同页 503）；5 分钟内任务 25 跑过一轮，`check` 中等待 / 暂停项 ④ 为 SKIPPED/AWAITING_PRICE | `up compose-rollback.commercial-service.yaml commercial-service`（④ 恢复 SKIPPED，已暂停项在下一轮 5 分钟任务自动恢复） |
| 5 | 替换 **operations-console-web**：`up compose-candidate.operations-console-web.yaml operations-console-web`；然后语言包 `python3 -I $R/req02_bundles.py --accept-deprecations`（干跑）→ `--accept-deprecations --apply` | 中断约 5 s；干跑 13 项通过、新增 17 / 基准变更 3 / 废弃 2 / zh 导入 20、只在 commercial；发布号 = 下一个空闲号（预计 #31，REQ-06 先发则 #32）READY 且为当前 | ops-web `up compose-rollback.operations-console-web.yaml …`；语言包以 `Code-MoR-plops-rel` 为 `--web-root` 重新发布 |
| 6 | 全量重查：`python3 -I $R/scripts/xxl_recheck.py trigger`（任务 26，`scope=ALL`） | 日志 handle 200；随后 `sqlro < $R/sql/01-…` 与第 1 步预测一致：DEMO2-CL-101 CARD、DEMO-CL-001 APPLE_PAY 变为 `SUSPENDED_BY_PREREQUISITE`（预期暂停），DEMO-CL-001 / DEMO2-CL-103 CARD 仍 ENABLED 带 AED 提醒，其余不变 | 回滚 commercial（第 4 步回滚列） |
| 7 | 验收：`req02_acceptance.py prepare --yes`（无 MID 菜单账号）→ `api --yes`（含 partial 场景）→ `check`（2 条按"预期暂停"判定）；浏览器 `node $R/acceptance/req02_browser.mortest.mjs`（zh-CN / en × 1440 / 390） | 0 FAIL；浏览器无 5xx、无原始键、无溢出 | 按服务回滚；数据按 `cleanup` |
| 8 | data-query 登记（**单独授权**；正文 = 登记材料 `sql/03-golive-mid-ready-template.sql`，与 `db/data-query-templates.sql` 逐字节相同）：`python3 -I $R/acceptance/req02_data_query.py plan` → `apply --yes`；再跑 `req02_acceptance.py api --yes` 的 Q 段（模板已登记时自动执行） | plan 14 / 14（含登记材料与 db 正文逐字节相同、3 处 COALESCE）；apply 试跑 8 组（含不传 currencies、`USD,AED` 任一就绪）与预期一致后才建模板；模板 ACTIVE | 模板置 INACTIVE |
| 9 | 收尾：`req02_acceptance.py cleanup`（生成运营账号停用草稿，主会话审阅后停用）；记录发布 | 本次合成账号停用 | — |

**整体回滚（倒序，无 DDL / DML）**：data-query 模板 INACTIVE → 语言包以现网源码重发（可选）→ ops-web → commercial → channel-connector → client-master，各用 `compose-rollback.*.yaml`。

## 3. 覆盖文件

| 服务 | 候选 | 回滚 |
|---|---|---|
| client-master-service | `compose-candidate.client-master-service.yaml`（`df13cc47…`） | `compose-rollback.client-master-service.yaml`（现网 `2b12c2e2…`） |
| channel-connector-service | `compose-candidate.channel-connector-service.yaml`（`f330c4ae…`） | `compose-rollback.channel-connector-service.yaml`（现网 `23679d45…`） |
| commercial-service | `compose-candidate.commercial-service.yaml`（`2be844e7…`，b） | `compose-rollback.commercial-service.yaml`（现网 `806043a3…`） |
| operations-console-web | `compose-candidate.operations-console-web.yaml`（`bbc03d33…`，b） | `compose-rollback.operations-console-web.yaml`（现网 `5784fa81…`，plops） |

## 4. 验收覆盖

| AC | 方式 |
|---|---|
| AC-01—AC-05 | `api` R 段（就绪查询矩阵，经私网探针直连 channel-connector）；AC-05 对照由 auto 测试 `ChannelIdentityReadinessHttpTest` 覆盖 |
| AC-06 / AC-08 / AC-12 | `api` G 段：每份 AE001 合同"检查前置"，④ 结果 / 原因 / 参数与现场预测一致；AC-08（Q-4 修订后为"任一币种就绪即满足、缺口提醒"）由 DEMO-CL-001 / DEMO2-CL-103 CARD 的 partial 场景覆盖；第 6 步全量重查与巡检一致 |
| AC-07 | mor-test 不演练（不改渠道配置；AE001 两个 MID 各有 CARD × USD 行，停用要两行一起，影响支付）；由 commercial auto 测试覆盖。发布本身即"暂停"半程的真实样本：第 6 步后 2 条预期暂停项变 `SUSPENDED_BY_PREREQUISITE` 并发布事件 |
| AC-09 / AC-10 | mor-test 无未确认 MCC 的合同 Client、不演练服务不可用；由 commercial auto 测试覆盖（`CapabilityPrerequisiteCheckHttpTest`） |
| AC-11 | `req02_data_query.py apply` 试跑 + `api` Q 段 |
| AC-V1 / V2 / V4 | `req02_browser.mortest.mjs`；AC-V3 由 ops-web 单测覆盖 |
