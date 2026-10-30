# 候选树合并记录（W3，2026-10-30 17:55—18:00Z）

候选树 `/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel`：`git worktree add --detach` 于 main `331d8e0e`（开工时 `git log -1 main` 确认）。

| 来源 | 文件 | 方式 |
|---|---|---|
| 明细账运维发布树 `Code-MoR-plops-rel`（只读，差异指纹 `78ccfd1dc42e8c62` 开工时复核一致，计算用临时 `GIT_INDEX_FILE`，未写该树 index） | `operations-console-web/` 下 18 个（11 M + 7 新增） | 原样复制（`cp -p`），清单与哈希 [source-files-plops-opsweb.sha256](./source-files-plops-opsweb.sha256) |
| 研发树 `Code-MoR-req02` | 第一轮 68 个（17:57Z 快照）；第二轮 69 个（18:20Z，Q-4 修订定稿后）：`git status --short`（M + ??）∩ `git diff --name-only 331d8e0e`，两者 M 集合完全相同 | 原样复制；第二轮逐文件 `cmp` 后只复制变动的 9 个（commercial 5 个源文件（含此前未改的已跟踪文件 `CapabilityPrerequisiteDecision.java`，故研发树由 33 M 变为 34 M）+ 1 个测试，ops-web `capabilityGate.ts` / `.test.ts` 与两个 `commercial.json`），清单与哈希 [source-files-req02.sha256](./source-files-req02.sha256) |

- **三方合并：0 个**（两轮相同）。plops 的 18 个 ops-web 文件与 REQ-02 的 10 个 ops-web 文件无交集（plops：finance / nav / App / ConsolePage / menu / pageCapabilities；REQ-02：commercial / channel / ChannelMids / `i18n/zh-CN/commercial.json`）；后端 58 个文件 plops 不涉及（plops 的 Platform / ops-bff / ledger / account 文件不进本候选树，后端三服务以 main 为基线）。
- 合计 87 个文件（第二轮），[source-files.sha256](./source-files.sha256)；漂移检查 `scripts/source_drift_check.sh` NO DRIFT（[source-drift-check-stage6.txt](./source-drift-check-stage6.txt)）。
- 先只放 plops 18 个文件构建 ops-web：dist 291 个文件与现网 `5784fa81` **逐字节相同**（[live](./ops-web-dist-live-5784fa81.sha256) = [rebuild](./ops-web-dist-plopsbase-rebuild.sha256)），再放入 REQ-02 文件构建候选。
- 测试中唯一的环境性失败：ops-web `financeWorkflow.test.ts`「能力清单」读仓库内 Platform `console-resource-capabilities.json`，明细账运维把 `OPERATIONS_FINANCE_POSTING_OPS` 加在 Platform 文件里（现网 Platform 已含），本候选树不带 Platform 文件故 1703 / 1704。临时放入 plops 发布树的该 JSON 复跑 1704 / 1704 后已 `git checkout` 还原；**不影响 ops-web dist**（该 JSON 不在 Web 构建输入内）。提交时该测试随 plops 的 Platform 改动一起成立。
