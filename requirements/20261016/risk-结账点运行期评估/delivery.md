# 结账点运行期评估：交付记录

## 1. 登记（2026-10-16）

- 来源：用户在风控功能候选中选择“选项 2：结账点运行期评估”。
- 适用阶段：默认 8 阶段。

## 2. 阶段 1 结果（2026-10-16，评审稿）

[需求](./requirement.md)：背景与目标、场景、范围与不做项、已确认事实、AC-1～AC-7、待决 Q1～Q7。只写文档。

用户 2026-10-16 确认阶段 1：Q1 Commerce 由本会话实现，Q2～Q7 按建议。

## 3. 阶段 2 结果（2026-10-16，评审稿）

[技术方案](./technique.md)第 1～6 节：三路只读调研（Risk、Commerce、Operations，并行 agent）与 mor-test/本机只读查询；差异、系统影响、待决 D1～D4（D1 建议修订 Q4 为沿用 `CHECKOUT_NOT_ELIGIBLE`）、工作量与风险。未修改代码或数据。

用户 2026-10-16 回复“继续”，按建议确认阶段 2：D1 沿用 `422 CHECKOUT_NOT_ELIGIBLE`（修订 Q4）、D2 新增 v5 并保留 v4 只读、D3 结账运行期不支持附加动作、D4 只送明细行数。

## 4. 阶段 3 结果（2026-10-16，评审稿）

[方案设计](./design.md)、[db/ddl.sql](./db/ddl.sql)、[db/dml.sql](./db/dml.sql)（待执行）。三路并行调研（Risk、Commerce、Operations Web）后汇总；mor-test 与本机只读查询结账点版本号（mor-test 1～33，本机无）。未修改代码或数据。

用户 2026-10-16 回复“可以”确认阶段 3（含 3 个留意点与默认口径：503 用 `DEPENDENCY_UNAVAILABLE`、净额允许 0、`consumer_country_code`），进入阶段 4；同时授权本机 `mor_risk` 执行 db/ddl.sql、db/dml.sql（先备份）。

## 5. 阶段 4 结果（2026-10-16，评审稿）

三路并行实现（Risk、Commerce、Operations Web）+ 独立只读审查（6 条问题已全部修复）。偏离见 [design.md 第 11 节](./design.md#11-阶段-4-实施偏离)。未提交。

| 系统 | 结果 |
|---|---|
| Risk | 全量 auto 2064/2064（新增结账测试 4 类）；220001 hash `7f730e19…4a45` |
| Commerce | 全量 790/790（`CreateCheckoutRiskTest` 等） |
| Operations Web | `npm test` 1052/1052、生产构建通过；BFF 只改 1 行注释，编译通过 |
| 本机 `mor_risk` | 备份 `~/Code-MoR-backups/20261016-risk-checkout-runtime-local/20261016T185413Z/` 后执行 db/ddl.sql、db/dml.sql，核对通过（85 表；12 个 v5 结账变量；91002 DISABLED；220001 FROZEN v5 version_no=100） |
| 文档 | Client API 决定与设计、风控标准目录 RCO-I03 已补；Commerce implementation.md、Risk 物理决策说明已改；CURRENT_IMPLEMENTATION 待阶段 8 |

注意：本机 18104 上的旧 Risk 读不到 v5 结账变量（预期，阶段 5 换新 jar）；在 `mor_risk` 跑 Risk 自动测试会清掉结账执行发布，结账基线须在测试之后激活。

用户 2026-10-16 回复“可以”确认阶段 4，进入阶段 5。

## 6. 阶段 5 结果（2026-10-16，评审稿）

本机验收：[verification/stage5-local-acceptance.md](./verification/stage5-local-acceptance.md)。Commerce → Risk 放行 / 拒绝 / 审核 / Risk 停止 503 / 恢复全部符合；浏览器 9/9；验收数据已清理，生效发布为默认放行基线；Commerce 已停止，Risk 新 jar 在 18104 运行。未提交。

用户 2026-10-17 回复“可以”确认阶段 5，进入阶段 6。

## 7. 阶段 6 结果（2026-10-17，评审稿）

[发布清单](./release/release.md)：mor-test 只读预检（85 表、快照 0 行、v4 变量 10、locale 引用 0、新 ID 无冲突）；候选镜像 `8e26b91b519c`（jar 与阶段 5 一致）；候选/回退 Compose、DML 回滚 SQL；待决 D1～D4。未改 test。

用户 2026-10-17 回复“继续”，按建议确认阶段 6（D1 激活结账默认放行基线、D2 合成评估验收后清理、D3 临时代理、D4 不部署 Web），并授权按清单在 mor-test 执行 SQL 与替换 Risk；进入阶段 7。

## 8. 阶段 7 结果（2026-10-17）

[mor-test 发布与验收记录](./release/test-acceptance.md)：备份 → DDL → DML 空跑核对 → COMMIT 并立即换镜像（26 秒）→ 27 个回归接口结构一致 → 结账基线激活 → 合成评估验收 → 清理。全部符合预期。未提交。

用户 2026-10-17 确认阶段 7 并要求“提交不推送”。

## 9. 阶段 8 提交（2026-10-17，未推送）

| 提交 | 内容 |
|---|---|
| `991e932` feat(risk) | Risk 全部改动（97 个文件，含 risk-api、Schema、PDManer、初始化数据、测试）；与 mor-test 部署的 jar（SHA-256 `baa1a685…`）源码一致 |
| `5ed1437` feat(commerce) | Commerce 风控端口、出站客户端、测试替身与用例（22 个文件） |
| `87fd57f` feat(operations) | Operations Web 12 个文件 + 1 个新测试、BFF 1 行注释 |
| 本提交 docs | 需求目录（含 SQL、验收记录与截图）、requirements 索引本需求行、CURRENT_IMPLEMENTATION 本需求条目、风控标准目录 RCO-I03、Client API 决定与设计补充 |

使用私有 index 提交，共享文件只含本需求的行；其他会话的未提交改动保持原样。
