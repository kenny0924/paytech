# 阶段 5：本机联调（e2e-dev，2026-10-27）

范围：本机 e2e 栈；只换 JAR 与重启（用户授权），**未执行 SQL**、未操作 mor-test、未提交、未推送。未改动 18301 / 18311 的 Vite、Gateway、其他服务与数据库结构。时间为 UTC。

## 1. 制品

worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refclient`（HEAD `5e1f16ab` + 本需求未提交改动），JDK 25，`./mvnw -B -o -DskipTests -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ -pl <6 个 app> -am clean package`，私有仓库 `~/.m3-refclient`。不带 tracking 参数时离线构建报 BOM "present, but unavailable"，加上后通过。构建前后 `git status --short` 指纹均为 `9d91dfc2f84ecdba1a0708a6eebacd1b`。

暂存：`local-environment/e2e/staging/refclient-20261027a/`（`SHA256SUMS` 同时复制到 `local-e2e/evidence/`）。

| JAR | sha256 |
|---|---|
| paytech-platform-support-app-exec.jar | `3fa69fb8306945aa721ac1fc41886ac53480a42df1c818b34a871711dede20c3` |
| paytech-ledger-app-exec.jar | `5d2e8695d5df8a2dddfd61c0da43a3a0f2411249bd7d33046f17317490b64ad2` |
| paytech-billing-app-exec.jar | `22e4b02da03b5a49b875a8e5015f6f25a2dddb30eaa99f3f60ae582bdd36f5dd` |
| paytech-card-app-exec.jar | `1331c0ac336cd219a14ff4967652deece7be1636fa03cb574363c92453a0b33c` |
| paytech-client-master-app-exec.jar | `c2c228fdff07593ad7fc107fffee8918399ac1b8dfff2e96335c66755b39d4e8` |
| paytech-operations-console-bff-app-exec.jar | `cac6a6765d0c73e32fb380f76af62fa788ffa2e42eec9c66d1c4c4a10d2de854` |

## 2. 换 JAR 与恢复时间线

脚本 `verification/local-e2e/swap.py`（由上一需求复制：捕获运行进程的 argv / env；先 `cp -p` 备份为 `*.pre-refclient-swap`，新 JAR 写临时文件再 `mv`；按捕获的 argv / env 重启，Platform 的 `SPRING_APPLICATION_JSON` internal-auth DUAL 随捕获环境带上；新增 ledger / billing / card、`stop` 子命令与 `SWAP_NO_WAIT=1`）。完整记录：`local-e2e/evidence/timeline.log`。

| 时间 | 动作 | 结果 |
|---|---|---|
| 19:35 | 基线：财务页币种下拉 4 种查询、旧 / 新接口状态、各服务日志行数 | `evidence/ops-finance-baseline/` |
| 19:35:55—19:36:47 | 依次换 client-master、ledger、billing、card、ops-bff（先换消费方：它们读的 `/reference-data/*` 旧 Platform 已提供） | 均 readiness UP；client-master、card、ops-bff 备份为 `*.pre-refclient-swap`；ledger、billing 磁盘上已无旧 JAR，跳过备份 |
| 19:37:13 | 换 Platform | UP；旧接口 404、新接口 200；运营登录正常 |
| 19:38—19:39 | ledger、billing、card、client-master 以 `MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS=always`（card 另加 `SHOWCOMPONENTS=always`）临时重启 | 观察 `referenceData` 明细用；19:57 已不带覆盖重启恢复原配置 |
| 19:46:45 / 19:50:37 | ledger 临时以 1 分钟刷新周期重启（第一次环境变量名写成 `PLATFORM_SUPPORT_…` 未生效；第二次用 `PLATFORMSUPPORT_REFERENCEDATA_CLIENT_REFRESHINTERVAL=PT1M`） | 供 AC-03 观察已就绪服务的周期刷新失败 |
| 19:47:04—19:50:14 | AC-02：停 Platform → 不等就绪重启 billing、card → 采样 → 启动 Platform | 见第 3 节第 7 项 |
| 19:52:17—19:55:05 | AC-03：停 Platform 约 2.5 分钟 → 启动 | 见第 3 节第 7 项 |
| 19:55:58—19:56:45 | 换回 binlist：Platform、card、ops-bff ← `*.pre-refclient-swap` | 均 UP；运行 JAR 的 sha256 与备份一致（`0f9da075…`、`506976c5…`、`7f9670c2…`） |
| 19:56:56—19:57:18 | ledger、billing、client-master 不带任何覆盖重启（保留新 JAR） | 均 UP |

最终状态（`evidence/final-state.txt`）：Platform / card / ops-bff 为 binlist 版本；ledger、billing、client-master 为本需求新版本；Platform、ledger、billing、card、client-master、ops-bff、client-bff readiness 全部 UP；运营登录正常（Vault 注册未丢失）；财务币种下拉与基线一致。基线后各服务日志无新增 ERROR（ops-bff 只有换 JAR 前就存在的 `/authorization/**` "Cannot replace a committed response"）。

## 3. 逐项结果

| # | 验证项 | 结果 | 证据 | AC |
|---|---|---|---|---|
| 1 | 各服务 readiness UP；`referenceData` 明细条数与 Platform 一致 | **通过**：ledger / billing CURRENCY 4；card COUNTRY 249；client-master CURRENCY 4、COUNTRY 249、SUBDIVISION 47、INDUSTRY 42、MCC 298，与 `mor_platform_v2e2e` 的 `t_ref_*` 行数一致；`consecutiveFailures` 0 | `evidence/health-after-swap.txt` | AC-01、AC-11 预演 |
| 1 | card、client-master 走服务发现 | **通过**：打包配置只有 `kinds`、没有 `base-url`，进程 argv / env 也没有覆盖；Platform 停机（Nacos 摘除实例）时 card 日志 `RoundRobinLoadBalancer: No servers available for service: platform-support-service`，Platform 重新注册后才恢复（比直连的 billing 晚约 6 秒）。ledger、billing 按设计配置 `base-url` 直连 | `evidence/log-excerpts.txt`、`ac02-samples.log` | — |
| 1 | Platform 侧 `X-Caller-Service` | **通过**：Platform `InternalCallerAuthenticator` 记录 DUAL 回退识别的调用方 client-master-service、ledger-service、billing-service、card-service、operations-console-bff | `evidence/log-excerpts.txt` | — |
| 2 | billing 计费与币种精度 | **通过**：USD `1.005` → 400 `FACT_LINE_INVALID`（超精度）；未知币种 JPY → 400 `BUSINESS_CHECK_FAILED`「币种不存在或尚未同步: JPY, 等待 Platform 币种快照下一次刷新后重试」；USD `1.25` → 201 DRAFT（factId 240923121639534592），随后作废为 VOIDED（未审批、未发布）。注意：未知币种是 **400** 而不是 422——与 API 合同注释、迁移前行为一致，rdc-lb 记录中写的 "422" 不准确 | `evidence/finance-checks.txt` | AC-07 |
| 2 | ledger 过账 / 追平 | **部分**：本机 `mor_ledger_v2e2e` 没有任何账簿与分录，真实过账需要建账簿、日历、期间并经 billing 审批发布，成本高，**跳过**；可重投的 500 场景（快照缺币种时消息重投）同样需要待过账事实，跳过（由 `CurrencyRulePostingTest` 覆盖）。实际做了：快照中没有的 JPY 建账簿 → 400 `CURRENCY_NOT_ACTIVE`（读的是 starter 快照，不写入）；追平接口对无账簿的流返回 400 参数错误，未继续 | `evidence/finance-checks.txt` | AC-07（部分） |
| 3 | card 国家核对 | **通过**：经运营链路上传 2 行 CSV（AE 合法、XX 非法）建版本 6 → INVALID，`errorRows=1`，第 3 行 `issuer_country` `COUNTRY_REGION_MISSING`「发卡国 XX 未登记区域」；AE 行通过。校验失败不写号段、未激活 | `evidence/card-bin-check.txt` | AC-08 |
| 4 | client-master 入驻提交与分类保存 | **通过**（76 PASS / 0 FAIL / 4 SKIP）：币种 / 行政区 / 国家不存在、行政区与国家不一致 → `FIELD_VALUE_NOT_IN_CATALOG`；带不存在国家正式提交 → 422 `SUBMISSION_INCOMPLETE` 且仍 DRAFT；旧格式 `AE_DU` 草稿 422；合法 AE 机构提交进入审核；分类只选大类 / 未知行业 / 未知 MCC / 第 2 行大类 → 422 `CLASSIFICATION_INVALID`（字段路径不变），细类 + 建议 MCC 与改选 MCC 保存成功。4 个"停用编码"用例跳过：需要改 Platform 库（本次未授权 SQL），且客户端缓存 1 小时，改库不会立即生效 | `verification/acceptance-local-20261027T194530Z.json`；脚本 `local-e2e/refdata_acceptance.py --only F,A,K --no-flips` | AC-09 |
| 5 | ops-bff 财务页币种下拉 | **通过**：`/api/operations/finance/currencies/v1` 不带 / ACTIVE / INACTIVE / DRAFT 四种查询与换 JAR 前逐字节一致；非法 status 仍 400；换回 binlist 后再次一致 | `evidence/ops-finance-diff.txt`、`ops-finance-baseline/`、`ops-finance-after-swap/` | AC-10 前置 |
| 6 | Platform 旧接口下线 | **通过**：新 Platform 下 `/platform/v1/currency-specs`、`/platform/v1/country-regions` 404；`/reference-data/currencies`、`countries?codes=AE`、`mccs` 200 | `evidence/timeline.log` | AC-10 |
| 7 | AC-02 启动时 Platform 不可用 | **通过**：Platform 停止后重启 billing（直连）、card（服务发现）：readiness 503、`referenceData` `OUT_OF_SERVICE`、`count 0`；失败次数约每 10 秒加 1（billing 日志 19:47:24、34……；采样 2→3→4→5→6）。19:50:01 启动 Platform：billing 19:50:14 装载成功变 UP，card 19:50:20（等 Nacos 注册）变 UP，失败次数清零。停机期间 client-master 分类保存 200（迁移前此处依赖 Platform，会 503）、非法仍 422 | `evidence/ac02-samples.log`、`ac03-serving.txt` | AC-02 |
| 7 | AC-03 已就绪后 Platform 不可用 | **通过**：ledger 以 1 分钟周期运行，停机期间 readiness 一直 UP，`loadedAt` 保留 19:51:45，`consecutiveFailures` 1 → 3，`lastFailure` ServiceUnavailableException；停机期间币种核对照常（JPY → `CURRENCY_NOT_ACTIVE`）；billing、card、client-master 同样保持 UP（默认 1 小时周期，停机期间没有刷新）。Platform 恢复后 ledger 19:55:45 刷新成功，失败次数清零 | `evidence/ac03-samples.log`、`ac03-serving.txt` | AC-03；AC-04（短周期刷新：19:50:44 首次加载、19:51:45 按 1 分钟周期刷新） |
| 8 | 换回 binlist 并复核 | **通过**：见第 2 节 | `evidence/final-state.txt` | — |

## 4. 本机留存数据

- billing：计费事实 240923121639534592（DRAFT → VOIDED，未发布）。
- card：BIN 库版本 6（INVALID，1 条导入错误）及其上传文件 `/files/v1/240923521595740160`；没有删除接口，未写号段、未激活，与本机已有的 INVALID 版本 3、4 同类。如需清理需 SQL（本次未授权）。
- client-master：本次验收新建的 Client 账号与 AE 申请 / 案件（案件 240924025168089088 分类 RETAIL_HOME / 5712，未确认），沿用上一需求的验收数据口径。
- Platform 参考数据未改动（未执行任何 SQL）。

## 5. 回滚

- Platform、card、ops-bff：已在本次结束时换回 `*.pre-refclient-swap`（binlist 版本）。
- client-master：如需回退，`jars/paytech-client-master-app-exec.jar.pre-refclient-swap`（上一需求版本 `910bfc85…`）经 `swap.py install` + `restart` 换回。
- ledger、billing：磁盘上已没有换前的 JAR，**不可直接回滚**。回滚方案：从 `5e1f16ab`（不含本需求改动）构建 `ledger/paytech-ledger-app`、`billing/paytech-billing-app` 的 exec JAR，经 `swap.py install` + `restart` 换入。注意：旧版本读已冻结的 `/currency-specs`，只能与仍保留旧接口的 Platform（当前 binlist 版本保留）配合。

## 6. 遗留与说明

1. ledger 真实过账 / 追平与"查不到币种 → 可重投 500"未在本机演示（本机 ledger 无账簿；构造链路需要建账簿、日历、期间并审批 billing 事实），由 `CurrencyRulePostingTest` 覆盖；建议 mor-test（AC-11）用已有账簿补做一次过账或追平。
2. billing 未知币种的 HTTP 状态是 400 `BUSINESS_CHECK_FAILED`（与迁移前一致），rdc-lb 第 3.3 节写的 "422" 需更正。
3. 健康明细默认不暴露（`show-details` 默认 / card 显式 `never`）。本次用环境覆盖临时打开，生产观察需要另定（例如 management 端口按授权显示或补指标）。
4. 环境变量覆盖 `platform-support.reference-data.client.*` 时变量名是 `PLATFORMSUPPORT_REFERENCEDATA_CLIENT_…`（前缀中的连字符去掉），不是 `PLATFORM_SUPPORT_…`；运行卡如需覆盖周期须注意。
5. 停用编码的 client-master 用例（4 个）未在本机执行，由各服务 auto 测试覆盖。
