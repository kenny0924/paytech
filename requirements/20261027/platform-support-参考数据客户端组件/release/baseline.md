# mor-test 发布基线（只读收集，2026-10-27 20:10—20:30Z，rel-dev）

> 方式：`docker inspect`（镜像、`Config.Env`、标签、健康检查）、`docker cp` 从容器**拷出** JAR、`docker logs`、只读 HTTP（Nacos 实例查询；经只读 sidecar 访问各服务 management 端口的 `/actuator/health*`）。未停容器、未换镜像、未写库、未改 Gateway / Nacos。
> 重跑：`scripts/build_apps.sh <基线工作树>` → `scripts/baseline_check.sh <基线工作树> <输出目录>`；env：`scripts/env_check.sh`。有效存档：`evidence/baseline-20261027T2032Z/`、`evidence/baseline-20261027T2038Z/`（card-BIN 20:19Z 发布完成之后）。20:12Z 那次采集落在 card-BIN 发布中途，已作废删除。差异分类：`scripts/expected-diffs.tsv` 登记了已知差异（card-BIN 未提交改动、ledger / billing 的公共库更新），20:38Z 复跑结果 6 个服务**未知差异均为 0**。回滚文件由 `scripts/gen_rollback.sh` 按部署前现网生成（不写死）。

## 1. 六个服务现状

| 服务 | 运行镜像（= 回滚目标） | 创建（UTC） | 启动所用覆盖 | 对应代码 |
|---|---|---|---|---|
| platform-support-service | `sha256:186347bcb7b4b4ea11d26804646d77d1f8b9fd6346577adef39ace3deef683a1`（**binlist-20261027a**，JAR `93a3fb44…`） | **2026-10-27 20:11:59**（核对期间由 card-BIN 发布替换；此前为 `aec53586…` refdata） | `requirements/20261027/card-BIN库页面重设计与号段列表/release/compose-candidate.platform-support-service.yaml` | **5e1f16ab + card-BIN 未提交改动**：比 5e1f16ab 多 6 个类（`OperationsAccountLabelController`、`ListOperationsAccountLabelsProcessor` / Request、api `OperationsAccountLabelQueryApi` 与 2 个响应体）、改 6 个（`OperationsPersonnel*` Mapper / Repository / Service 与 XML）——**现网有他人未提交改动** |
| ledger-service | `sha256:3819ff2809c7a7ff2a4e835221871b5b8bcf684a153bb7b30f408ca79de4d210`（billing-b-20261023，JAR `fb1b91d3…`） | 2026-10-23 07:47 | `requirements/20261022/billing-支付事实自动登记/release/compose-candidate.yaml`（只换镜像） | 早于 5e1f16ab 的已提交代码：ledger 自身类与 5e1f16ab **0 差异**；19 处差异全在公共库（foundation http-client、platform-support-api、`catalog/events.yaml`），每个都有 10-23 之后的提交（如 `9f506f0f`）；现网独有条目 0 |
| billing-service | `sha256:53450948e0225e3681930b55417526f2ae51ed35fc464276791cab7105e700b0`（billing-b-20261023，JAR `20869ac0…`） | 2026-10-23 07:45 | 同上（带 4 个 `BILLING_*_SERVICE_URL`） | 同 ledger：自身 0 差异；21 处公共库差异（另含 commercial-api）均有后续提交；现网独有 0 |
| card-service | `sha256:380064febe8fbc3883c81292f7e034b6378f8262c0b7bee0503434f5facf6754`（**binlist-20261027a**，JAR `d41f3ada…`） | **2026-10-27 20:13:37**（20:12 首次核对时还是 `810b6b72…` pubbucket-20261023a，JAR `5cc2fd8d…`：card 自身 0 差异、10 处公共库差异均有后续提交） | `requirements/20261027/card-BIN库页面重设计与号段列表/release/compose-candidate.card-service.yaml`（只换镜像） | **5e1f16ab + card-BIN 未提交改动**：多 `BinRangeResponseBody`、`BinRangeFilter` 等 5 个条目，改 `CardBinLibraryController` / Converter / Api、`CardBinRange*` Mapper / Repository / QueryService 等 9 个 |
| client-master-service | `sha256:80257737ed090d3ba3f70b6b4ce7c4356bf7c0ac7548191d04ccaecd4d609570`（refdata-20261027a，JAR `7e519598…`） | 2026-10-27 15:37 | `requirements/20261027/platform-support-参考数据目录与client-master迁移/release/compose-candidate.yaml` | = 5e1f16ab（1854 / 1854 条目 0 差异） |
| operations-console-bff | `sha256:25f6a869b28091b6f26e14bc1f718d8087ffd83e194b58c2c440712211e446b7`（**binlist-20261027a**，JAR `4dcbfa5d…`） | **2026-10-27 20:14:15**（20:12 时还是 `481132892…` refdata-20261027a，= 5e1f16ab） | card-BIN `compose-candidate.operations-console-bff.yaml`（4 个 BASE_URL 等 7 个键） | **5e1f16ab + card-BIN 未提交改动**：多 15 个条目（`OperationsAccountLabelClient(Impl)`、`Labeled*` 响应、`ListBinLibrary*` / `GetBinLibraryVersion` Processor 与 Request、`OperatorLabelResolver` 等），改 6 个 |

结论（20:32Z 复核，`evidence/baseline-20261027T2032Z/`）：ledger、billing、client-master 现网没有他人未提交的线上改动（ledger / billing 只是更早的已提交版本，本次发布会顺带上 10-23 之后已提交的公共库变化）。**Platform、card、ops-bff 在本次核对期间（20:11—20:14Z）被 card-BIN 发布为其未提交版本 binlist-20261027a**：基于 5e1f16ab 的这三个候选镜像会回退它，**不得发布**，须在 card-BIN 提交之上重建（runbook 第 0 节）。"国家包法务修订"会发布 client-master，同样要在其提交之上重建。只有 ledger、billing 与其他需求无交叉，候选可直接使用。

## 2. env 与配置差异（按设计不新增变量）

| 服务 | 运行 env 键数 | 新版本需要 | 核对 |
|---|---|---|---|
| ledger | 39 | 无新增：客户端直连 `LEDGER_PLATFORM_SERVICE_URL`（现网 `http://platform-support-service:18106/platform-support`，主 compose 已有）；删除的 `ledger.currency-snapshot.*` 在现网没有对应 env | 渲染 39 / 39 相同 |
| billing | 43 | 无新增：直连 `BILLING_PLATFORM_SERVICE_URL`（同上）；4 个 `BILLING_*_SERVICE_URL` 来自 20261022 覆盖，候选已带 | 43 / 43 |
| card | 39 | 无新增：不配 `base-url`，经服务发现 `platform-support-service` + `/platform-support`；Nacos 相关 env 现网已有 | 39 / 39 |
| client-master | 40 | 无新增：同 card（服务发现）；26 个显式键抄自参考数据需求覆盖 | 40 / 40 |
| ops-bff | 20 | 无新增（只换镜像；4 个 `OPERATIONS_*_BASE_URL` 已带） | 20 / 20 |
| platform-support | 59 | 无新增（只删接口） | 59 / 59 |

- 候选与回滚覆盖（各 6 个）渲染后与运行容器**逐键逐值相同**（`scripts/env_check.sh`，只打印键名）。
- 覆盖客户端参数时变量名前缀是 `PLATFORMSUPPORT_REFERENCEDATA_CLIENT_*`（如 `PLATFORMSUPPORT_REFERENCEDATA_CLIENT_REFRESHINTERVAL`），不是 `PLATFORM_SUPPORT_…`（阶段 5 遗留 4）。本次发布不覆盖任何参数（刷新 `PT1H`、首次重试 `PT10S` 用缺省）。

## 3. 服务发现、Context Path、内部身份

| 项 | mor-test 现状 | 与客户端缺省是否一致 |
|---|---|---|
| Nacos 注册名 | `DEFAULT_GROUP@@platform-support-service`，namespace `mor-test`，实例 `172.30.73.21:18106`、healthy（服务账号 `mor-test-services` 只读查询） | 一致（`service-id` 缺省 `platform-support-service`） |
| Context Path | Platform `server.servlet.context-path: /platform-support`；ledger / billing 现网直连地址同样以 `/platform-support` 结尾 | 一致（`context-path` 缺省 `/platform-support`） |
| card / client-master 发现配置 | 两者现网都已接 Nacos（`NACOS_*`、`SPRING_CLOUD_NACOS_DISCOVERY_*`，namespace mor-test、组 DEFAULT_GROUP）；card 现已用 `lb://platform-support-service/platform-support` 调文件接口 | 可直接使用 |
| Platform 内部认证 | `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`；客户端始终发 `X-Caller-Service=<spring.application.name>`，DUAL 接受并记 WARN（带调用方名） | 可用；该 WARN 也是"消费方已访问 Platform"的旁证 |

## 4. 就绪组与健康明细

| 服务 | 现网（基线代码）readiness 组 | 新版本 | 健康明细 |
|---|---|---|---|
| ledger | `readinessState,currencySnapshot` | `readinessState,referenceData` | `show-details: never` |
| billing | `readinessState,currencySnapshot` | `readinessState,referenceData` | `never` |
| card | 缺省（只有 readinessState） | `readinessState,referenceData`（新增依赖：首次加载国家前不就绪） | `never`，`show-components: never` |
| client-master | `readinessState` | `readinessState,referenceData`（新增依赖） | `never` |

- 容器健康检查探 `http://127.0.0.1:<业务端口+1000>/actuator/health/readiness`（`LocalHttpHealthProbe`），所以 `compose up --wait` 成功就意味着 `referenceData` 已 UP（已开启的种类全部至少成功加载一次）。
- 只读 sidecar 实测（20:2xZ）：四个服务 `/actuator/health/readiness` → `{"status":"UP"}`，`/actuator/health` → 只有组名与状态；`/actuator/health/<组件>`（如 `ping`、`currencySnapshot`）→ 404。**不改配置看不到 `referenceData` 的条数 / `loadedAt` / 失败次数**；客户端成功加载不打日志、无指标，只有刷新失败时 WARN `Reference data refresh failed`。替代证据见 runbook 3.6。

## 5. 风险与说明

- card、client-master 新增"启动时必须能连上 Platform"：Platform 不可用时它们 `up --wait` 会一直 `OUT_OF_SERVICE` 直到超时（180 秒）。发布这两个时 Platform 必须健康。
- ledger、billing 刷新周期从 60 秒变 1 小时：本次窗口内若有人启用新币种，最长 1 小时后才可用于过账 / 计费。
- 已就绪的消费方在 Platform 重启期间保留快照继续服务（阶段 5 AC-03）。

## 6. 2026-10-28 03:29Z 以 c4ff0e20 为基线重核（b 版）

| 服务 | 现网镜像（= 回滚目标） | 与 c4ff0e20 构建比较 | 结论 |
|---|---|---|---|
| platform-support-service | `186347bc…`（card-BIN binlist-20261027a） | 3102 / 3102，0 差异 | 现网 = c4ff0e20 |
| card-service | `380064fe…`（同上） | 1206 / 1206，0 差异 | 现网 = c4ff0e20 |
| operations-console-bff | `25f6a869…`（同上） | 2806 / 2806，0 差异 | 现网 = c4ff0e20 |
| client-master-service | `80257737…`（refdata-20261027a） | 现网独有 0、变化 0（基线多 3 个更新的公共库类） | 无他人改动 |
| ledger-service | `3819ff28…`（billing-b-20261023） | 19 处公共库差异，全部为 c4ff0e20 历史中的后续修改 | 现网为更早的已提交构建 |
| billing-service | `53450948…`（同上） | 32 处，同上 | 同上 |

证据：`evidence/baseline-c4ff0e20-20261028T0329Z/`（`compare-*.txt`、`images.tsv`、`ledger-billing-trace.tsv`）；`scripts/expected-diffs.tsv` 已改为只登记 ledger / billing 的 51 条，6 个服务**未知差异均为 0**。回滚文件按此刻现网重新生成，env 12 / 12 相同。
