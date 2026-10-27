# mor-test 发布基线（只读收集，2026-10-27 12:55—13:15Z，rel-dev）

> 收集方式：`docker inspect`（镜像、`Config.Env`、标签）、`docker cp` 从容器**拷出** JAR 与 Web dist、`docker logs`（只读）、mor-test MySQL 只读 `SELECT`、`mysqldump` 备份。未启停或替换任何容器，未写任何 mor-test 库。
> 用途：阶段 6 编写候选 / 回滚覆盖与运行卡。**镜像一律按 sha256 固定**；回滚目标是下表"运行镜像"，不是标签。

## 1. 受影响服务运行现状

| 服务 | 运行镜像（= 回滚目标） | 容器创建（UTC） | 当前覆盖来源（compose.sh 基础三文件之外） |
|---|---|---|---|
| platform-support-service | `sha256:cad6dc14cc7c2c7687cc7aacdaf0a77804e529c6f492adacab56ba1045659ba2`（onboard-20261027a，JAR `478c53de…`） | 2026-10-27 03:50 | 切片 1 `Code-MoR-onboard/requirements/20261027/client-master-全球合规入驻与资格切片1/release/compose-candidate.yaml` |
| client-master-service | `sha256:0af5b79239ef791cf17400fc6f479ec21089a65d9c03af6dd2c1068f915bec33`（onboard-20261027a，JAR `f6bc8f3b…`） | 2026-10-27 06:32 | 同上 |
| client-console-bff | `sha256:83386ff51561cb26311571a7c952e39a762da3a1266c6fd477bd0b30c24323d4`（onboard-20261027a，JAR `dcd2ccad…`） | 2026-10-27 03:57 | 同上 |
| operations-console-bff | `sha256:c5ed58df68333390bc7c99eedf737e73d4e54d9c02bcd6da54d58089cb332f44`（onboard-20261027a，JAR `895edb6a…`） | 2026-10-27 03:58 | 同上 |
| client-console-web | `sha256:34d3d6e303fd7f62fa7da825599391a2f34ba569e4bbf92a8839d5859a133998`（onboard-20261027a，dist 11 个文件） | 2026-10-27 06:29 | 同上 |
| operations-console-web | `sha256:88ab111a7fdabeea6893c307f36a32baec8082b74b218fc2400f12b2cd031872`（onboard-20261027a，dist 235 个文件） | 2026-10-27 06:26 | 同上 |
| 不换：api-gateway | `sha256:270c8ba2…`（card3ds-20261022b） | 2026-10-22 | 块 139 激活后等快照刷新 |
| 不换：vault-service | `sha256:17827ac8…`（:development） | 2026-10-18 | Platform 重启后若登录 503 `No servers available for service: vault-service` 需重启 |
| 不换：ledger / billing / card | `3819ff28…` / `53450948…` / `810b6b72…` | 2026-10-23 | 旧接口消费方（S-01），只做回归观察 |
| mysql | `sha256:b3b90af2…`（mysql:8.4.11）；演练用本机同一镜像 ID | — | — |

六个运行镜像就是切片 1 的最终镜像（与 `client-master-全球合规入驻与资格切片1/verification/rel-build-report.md` 第 2、7 节逐个相同；从容器拷出的 4 个 JAR sha256 与该报告一致）。覆盖来源文件在 `Code-MoR-onboard` worktree 与主工作区各有一份，内容相同；本需求候选文件不依赖它们（env 已抄入本目录）。

## 2. 漂移核对（结论：6 个服务均无漂移，可以发布，不会回退他人线上工作）

方法：干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refdata-base`（`aba76a1a`，无改动）离线构建 4 个 exec JAR（`~/.m3-refdata`）与 2 个 Web dist（`npm ci --offline && npm run build`），与运行容器拷出的 `/app/application.jar`、nginx 目录对比。JAR 按条目 CRC 比较，递归展开 `BOOT-INF/lib/paytech-*.jar`，忽略 MANIFEST / classpath.idx / layers.idx / pom.properties；第三方库按 JAR 条目 CRC 比较。工具：`../verification/rel-build-report.md` 第 2 节。

| 服务 | 现网 vs aba76a1a 构建 | 结论 |
|---|---|---|
| platform-support-service | 3045 / 3045 条目，0 差异 | 无漂移 |
| client-master-service | 1834 / 1834，0 差异 | 无漂移 |
| client-console-bff | 1003 / 1003，0 差异 | 无漂移 |
| operations-console-bff | 2764 / 2764，0 差异 | 无漂移 |
| client-console-web | dist 11 / 11 文件逐字节相同 | 无漂移 |
| operations-console-web | dist 235 / 235 文件逐字节相同 | 无漂移 |

## 3. 运行容器 env（键数；值只在渲染核对中逐值比较，不落盘）

| 服务 | 键数 | 来源 |
|---|---|---|
| platform-support-service | 59 | 主 compose + `env_file`（`private/platform.env`、`private/client-onboarding.env`）；覆盖文件只换镜像 |
| client-master-service | 40 | 主 compose + 切片 1 覆盖（26 个显式键，含 `ONBOARDING_ID_WORKER=26`、`MESSAGING_CONSUMERS_COMPLIANCE_PARTITIONS=4`、XXL 执行器） |
| client-console-bff | 16 | 主 compose + 切片 1 覆盖（`CLIENT_BFF_BIND_ADDRESS`、`CLIENT_ONBOARDING_ENABLED`、`CLIENT_CONSOLE_ORIGIN`） |
| operations-console-bff | 20 | 主 compose + 切片 1 覆盖（含 4 个 `OPERATIONS_*_BASE_URL`，`RECONCILIATION` 漏带会致对账页 503） |
| client-console-web / operations-console-web | 7 | 镜像自带（nginx） |

本需求不新增 env：`git diff aba76a1a` 中 `application*.yml` 无改动；新代码只有既有的 `client.platform-test-base-url`（缺省空，测试用）。候选与回滚文件渲染后与运行容器**逐键、逐值相同**（见报告第 3 节）。

## 4. 数据库只读核对（`precheck/`，2026-10-27 13:0xZ）

### mor_platform（`precheck/mortest-platform-*.out`、`mortest-block139-precheck-*.out`）

| 核对 | 结果 | 影响 |
|---|---|---|
| 参考数据表 | 只有 `t_currency_spec`、`t_country_region`；无 `t_ref_*`、无 `ref_data_assert` 过程 | 变更脚本预检会通过 |
| `t_currency_spec` | 2 行：USD（1001，ACTIVE v1）、**AED（Snowflake `238762073381748736`，ACTIVE v2，运营新建）** | 脚本对 AED **回填**数字码 / 名称 / 符号，不新建、不改状态；SAR、EGP 以固定 ID 1003、1004 新建；回退脚本只删 1002—1004，AED 保留并去掉新列 |
| `t_country_region` | 249 行（ID 2001—2249 齐全） | 预检通过 |
| 块 139 ID 区间、新 code、新路径 | 全 0；依赖 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`（`236085272977162240`）= 1、PLATFORM_ADMIN = 1 | 可注册 |
| 块 139 写死的对象 | ops target `900000000000000001`（operations-console-bff，ACTIVE）、平台配置菜单 `900000000000000562`（ACTIVE；子菜单排序 10—90，卡 BIN 库 90，新菜单 95 在其后） | 存在（块 139 生成的预检未覆盖这两项，已在 `sql/00` 补查） |
| 同形路由守卫 | 现有 Operations GET（卡 BIN、币种列表、Client 管理等）：principal 30/min + 并发 2、IP 30 + 3、shared 300 + 50，header authorization CORS + origin FORWARD，body 0、5000 / 10000 ms、cookie DROP | 块 139 五条路由与之**完全相同** |
| Gateway 路由 | ACTIVE 607、DISABLED 6；最大块路由 ID `900000000000138445`；Gateway 日志每约 5 秒 `Gateway Snapshot 刷新成功: targetCount=4, routeCount=607` | 激活后期望 612 |
| 币种管理路由（块 125） | `operations-currency-spec-{list,create,update,status}` 均 ACTIVE | 币种页可在 mor-test 验收（本机曾缺块 125） |
| 多语言 | client-console 当前 **#5**（9 命名空间，最大排序 90）；operations-console 当前 **#12**（24 命名空间，最大排序 240）；`refdata`、`reference-data` 均不存在 | 期望发布 client #6、ops #13 |
| 文件公开桶 / 扫描策略 | PUBLIC 路由：`*` → PUBLIC_DOCUMENT、client-console-bff → PUBLIC_IMAGE、operations-console-bff → PUBLIC_DOCUMENT、platform-support → I18N_BUNDLE_JSON；PUBLIC_IMAGE 只收 png / jpeg，PUBLIC_DOCUMENT 收 pdf / png / jpeg；**没有策略接受 `image/svg+xml`** | 国旗步骤需转 PNG（runbook 第 8 节） |
| 其他读旧表名的对象 | 视图 0；数据查询模板 0 | 改名不影响 |

### mor_client_master（`precheck/mortest-client-master-*.out`）

| 核对 | 结果 |
|---|---|
| `t_catalog_item` 将删除的行 | CURRENCY 4、INDUSTRY 10、STATE_OR_PROVINCE 34（共 48）；表内共 109 行 |
| Schema 字段 `catalog_code=CURRENCY` | 8（8 个 Schema 版本的 `business.currency`） |
| 地址组件引用目录 | AE_POBOX / EG_STANDARD `stateOrProvince` → STATE_OR_PROVINCE（将改为 REF_COUNTRY_SUBDIVISION）；SA_NATIONAL_ADDRESS `city` → CITY（不变） |
| `t_party_address.state_or_province` | **AE/EG 下划线 12 行（`AE_AZ` 10、`AE_DU` 2）→ 将转为 ISO**；自由文本 38；NULL 2；其他国家下划线 0 |
| 分类 | 38 行全是存量一级行业 `RETAIL` / MCC `5999`（AC-10：存量可读） |

## 5. 备份（本次只读导出）

`/Users/kenny/mor-backups/refdata-20261027/`：

| 文件 | sha256 |
|---|---|
| `mortest-mor_platform-20261027T125606Z.sql.gz` | `1f684acd47b33872effe3fca40d3da6e26bcb961ba153429a83441e4db8b59df` |
| `mortest-mor_client_master-20261027T125606Z.sql.gz` | `3e455cfb4fd810b20b7a24fec1f7f063b4d1256851e652984a8caffba2845f5a` |

`mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers --events --databases <db>`，两份均以 `Dump completed` 结尾。阶段 7 执行前必须**再备份一次**（runbook 第 2 节），回滚整库恢复以那一份为准。
