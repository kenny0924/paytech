# rel-dev 报告：阶段 6 发布准备（2026-10-27 12:50—13:20Z）

> 范围：基线与漂移核对、6 个镜像构建与核对、候选 / 回滚覆盖、发布 SQL 整理与离线演练（含 Gateway 编译）、兼容性与顺序分析、语言包计划、验收脚本、国旗步骤评估、运行卡。
> 对 mor-test 只做了：`docker inspect`、从容器**拷出** JAR / dist、`docker logs`（只读）、`mor_platform` / `mor_client_master` 只读 `SELECT`、两库 `mysqldump`。没有启停或替换容器、没有写任何 mor-test 库、没有推送镜像、没有 XXL 改动、没有提交。没有修改代码。
> 输出在 `release/`：[baseline.md](../release/baseline.md)、[runbook.md](../release/runbook.md)、`compose-{candidate,rollback}[.<service>].yaml`、`sql/` + [sync_sql.sh](../release/sync_sql.sh)、`precheck/`、`harness/`、`refdata_bundles.py`、`i18n-bundles/<应用>.release.*`、`acceptance/`、`evidence/`。

## 1. 构建源

- 基线：干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refdata-base`（`git worktree add --detach … aba76a1a`，无改动）。
- 发布源：`/Users/kenny/Desktop/Workspace/Code-MoR-refdata-rel` = `aba76a1a` + `Code-MoR-refdata` 全部未提交改动（按 `git status --porcelain -z --untracked-files=all` 复制 273 个文件、删除 14 个）。不在 `Code-MoR-refdata` 里构建，避免清掉 dev agent 的 `target/`。
- 指纹（全部非忽略文件 sha1 清单再 sha1）：12:55Z 两边都是 **`eff2848560062d60`**；13:14Z 复核 worktree 仍是 `eff2848560062d60`，逐文件清单与发布源相同。阶段 7 前用同一命令再核对：

```bash
fp(){ (cd "$1"; git ls-files --cached --others --exclude-standard -z | xargs -0 shasum 2>/dev/null | sort -k2 | shasum | cut -c1-16); }
fp /Users/kenny/Desktop/Workspace/Code-MoR-refdata; fp /Users/kenny/Desktop/Workspace/Code-MoR-refdata-rel
```

- 构建命令：`JAVA_HOME=jdk-25 ./mvnw -B -o -DskipTests -pl platform-support/paytech-platform-support-app,client-master/paytech-client-master-app,client-console-bff/paytech-client-console-bff-app,operations-console-bff/paytech-operations-console-bff-app -am clean package`（基线加 `-Dmaven.repo.local=~/.m3-refdata/repository`，发布源由 `paytech/.mvn/maven.config` 指定同一仓库），两次都 BUILD SUCCESS；Web：`npm ci --offline --no-audit --no-fund && npm run build`（`package-lock.json` 与基线相同）。
- 结论：final 代码（含 client-fe P1 F-1、ops-fe F-3—F-7 与 refdata 根预加载、cm-dev `addressComponents`）就是本次镜像的内容；e2e 暂存 JAR 未复用（JAR 字节不可复现，直接从发布源重建）。

## 2. 漂移核对（现网 vs aba76a1a）

工具：`release/harness/tools/jarcmp.py`（按条目 CRC；递归展开 `BOOT-INF/lib/paytech-*.jar`；忽略 MANIFEST、classpath.idx、layers.idx、pom.properties）。Web 用逐文件 sha256。

| 服务 | 现网 vs 基线 | 发布 vs 基线（只为确认范围） |
|---|---|---|
| platform-support | 3045 / 3045，0 差异 | +63 −12 ~43：参考数据 5 个 Controller / Processor / Repository / Mapper、api `referencedata`（15 个新类）、file-document `describeAll`、audit-security 新字段、access-control 能力清单（`OPERATIONS_REFERENCE_DATA`）、币种维护改动；删除旧 `CountryRegionQueryService` 等 12 个 |
| client-master | 1834 / 1834，0 差异 | +22 −2 ~40：`ReferenceDataClient(Impl)`、`ReferenceCatalogCodes`、`ReferenceCodeCheck`、`AdminJurisdictionResponseBody$AddressComponentItem`；删除 `CatalogOptionResponseBody`、`ClassificationOptionsResponseBody` |
| client-console-bff | 1003 / 1003，0 差异 | +24 −2 ~26：`GetClientFormSchemaProcessor`、`ClientFormSchemaResponseBody`、`PlatformReferenceDataClient(Impl)` |
| operations-console-bff | 2764 / 2764，0 差异 | +29 −2 ~43：`ReferenceDataAdminController`、`PlatformReferenceDataClient(Impl)`、`ReferenceDataModels`、分类 / 币种模型 |
| client-console-web | 11 / 11 逐字节相同 | 入口 JS / CSS 与 index.html 变化 |
| operations-console-web | 235 / 235 逐字节相同 | 245 个文件（新增参考数据页等） |

现网六个镜像都是切片 1 最终镜像，无他人线上改动；本次发布不会回退任何线上工作。

## 3. 镜像（tag `refdata-20261027a`，本机构建，未推送）

用 mor-test 同一套 Dockerfile 与参数：Java 为 `test-environment/java/Dockerfile` + `--build-arg JAR_FILE=<service>.jar`，构建上下文含 `health-probe/`（`javac --release 25` 编译 `LocalHttpHealthProbe.java`、`LocalSnapshotEvidenceFetch.java`，5 个 class 大小与现网镜像相同：5013 / 425 / 4822 / 535 / 567）；Web 为各自 `Dockerfile`（`.dockerignore` 只放 nginx.conf 与 dist）。没有运行 `build.sh`，`:development` 未改动。核对：`docker create` + `docker cp` 取出镜像内文件与构建产物比较。

| 服务 | 镜像 ID | 制品 | 镜像内核对 |
|---|---|---|---|
| platform-support-service | `sha256:aec535863836669db6063a139c448ee817d00f64d6a4adea008448a413e4d5b5` | JAR `f4d03bef50208c3e442a20a6077d96601c745c41828679b1e48f5207adb5667e` | 相同；User 10001:10001 |
| client-master-service | `sha256:80257737ed090d3ba3f70b6b4ce7c4356bf7c0ac7548191d04ccaecd4d609570` | JAR `7e519598184788a0fd026a9f1162baf6386fb2b385d52ced9e2c25b4f1508c28` | 相同 |
| client-console-bff | `sha256:be79550ff3ed649758b164574e2c2a13fd389466b19cec27330746674a9e6910` | JAR `ca2888638d7a6938f3f254a382e7bd7f31ccc6ec19a0e8b76bd350e710f02d7c` | 相同 |
| operations-console-bff | `sha256:481132892c9388687261370f50d5da21229b83b214deab699872907239bf57b3` | JAR `bd03e82df00f5c8d40df97d9fb4d604db8dfaaebc1b4e8beb27009f4ce7397e6` | 相同 |
| client-console-web | `sha256:c09af02a9c4d84faa2aaf78b5d95b6b4e36e0a28c56050d1ae202c5e000e495e` | dist 11 个文件 | 11 / 11 相同；User nginx、Entrypoint 与现网相同 |
| operations-console-web | `sha256:b1309a45933504f30db0e9d47fdb26d1152d0d4c87c99b7fad30c36850e8168a` | dist 245 个文件 | 245 / 245 相同 |

## 4. 候选 / 回滚覆盖与 env

- 新代码不引入 env：`application*.yml` 无改动；新增 `@Value` 只有既有 `client.platform-test-base-url`（缺省空）。
- env 来源：运行容器 `docker inspect --format '{{json .Config.Env}}'`（只在脚本内逐值比较，不输出值）、主 compose、切片 1 合并候选文件（现网容器就是用它启动的，`com.docker.compose.project.config_files` 标签核对）。非敏感值显式写出，敏感值只写主 compose 同一 `${...}` 引用。
- 渲染核对：`./test-environment/compose.sh -f <文件> config --format json`（只读）后与运行容器逐键逐值比较（`harness/tools/envcmp.py`，只打印键名）：

| 文件 | Platform | client-master | client-bff | ops-bff | client-web | ops-web |
|---|---|---|---|---|---|---|
| 候选（分服务 6 个 + 合并） | 59 / 59 | 40 / 40 | 16 / 16 | 20 / 20（含 4 个 BASE_URL） | 7 / 7 | 7 / 7 |
| 回滚（分服务 6 个 + 合并） | 59 / 59 | 40 / 40 | 16 / 16 | 20 / 20 | 7 / 7 | 7 / 7 |

全部 missing 0、extra 0、值差异 0；镜像分别为候选 / 现网 sha256。

## 5. mor-test 只读核对与备份

详见 baseline 第 4、5 节。要点：
- `t_currency_spec` 现有 USD（1001）与**运营新建的 AED（Snowflake ID，ACTIVE v2）**：脚本回填 AED、新建 SAR / EGP（1003 / 1004）；回退只删 1002—1004，AED 保留。
- client-master 将删 48 行目录、改 8 个 Schema 字段与 2 个地址组件、转换 **12 行**地址（`AE_AZ` 10、`AE_DU` 2）。
- 块 139：ID / code / 路径全空，依赖齐全；写死的 ops target `…001` 与菜单 `…562` 存在（生成的预检未覆盖，已补进 `sql/00`）；五条路由的守卫 / header / 超时 / cookie 与现有 Operations GET 路由完全相同。
- 备份：`/Users/kenny/mor-backups/refdata-20261027/mortest-mor_platform-20261027T125606Z.sql.gz`（`1f684acd…`）、`mortest-mor_client_master-20261027T125606Z.sql.gz`（`3e455cfb…`）。

## 6. SQL 演练（断网一次性 MySQL，镜像 `sha256:b3b90af2…` = mor-test 同一 ID）

`release/harness/rehearse.sh`：导入两份 mor-test 备份 → 规范化导出（SHOW CREATE + 例程 + 按主键逐行数据）→ 按运行卡顺序执行全部正向脚本 → 逆序执行全部回滚脚本 → 再导出比对。Platform 每步后用**发布 Platform JAR 内的生产 `GatewayConfigurationCompiler`** 编译（`harness/BlockSnapshotCheck.java`，复制自切片 1，逻辑未改）。日志与每步输出：`release/evidence/rehearsal-20261027T1303Z/`。

| 步 | 结果 | Gateway 编译（loaded / effective / 块 139 loaded / ACTIVE / effective） |
|---|---|---|
| baseline | — | OK 613 / 607 / 0 / 0 / 0 |
| 00、01 预检 | 与 mor-test 直接查询一致 | — |
| 10 参考数据变更 | 成功，0.50 秒 | OK 613 / 607 / 0 / 0 / 0（改名不影响网关表） |
| 11 核对 | 5 张 `t_ref_*`；币种 AED（原行）784 / EGP 1004 818 / SAR 1003 682 / USD 1001 840 全 ACTIVE；国家 249 全齐、区号 242、法定货币 248；行政区 AE 7 / EG 27 / SA 13，本地名无空；行业 10 + 32；建议 MCC 未启用 0；MCC 298 / 高风险 14；旧 `/currency-specs` 约 583 字节 | — |
| 12 行政区本地名补丁（可选） | 执行前后规范化导出 **0 行差异**：mor-test 不需要，运行卡标为跳过 | — |
| 60 块 139 休眠 | 成功 | OK 618 / 607 / 5 / 0 / 0 |
| 61 激活 | routes 5、resources 1、grants 1、role 1 | OK 618 / **612** / 5 / 5 / 5 |
| 20 client-master DML | before 48 / 8 / 2 / 12 / 0 → after 0 / 8 / 2 / 0 / 12 | — |
| 21 注释 DDL | 成功 | — |
| 20 重跑 | 空操作（after 不变） | — |
| 81、80 回退 | 恢复 48 行、目录码、12 行下划线地址、原注释 | — |
| 62 停用 | 成功 | OK 618 / 607 / 5 / 0 / 0 |
| 69 移除 | 成功 | OK 613 / 607 / 0 / 0 / 0 |
| 89 Platform 回退 | 成功，0.64 秒 | OK 613 / 607 / 0 / 0 / 0 |
| 逆序后比对 | **两库结构（含注释、索引、例程）与备份逐字相同**；数据差异只有：DML 触及的 22 行 `update_time`（地址 12、Schema 字段 8、组件 2）与 PLATFORM_ADMIN 角色 `version` 51 → 54 / `update_time`（块 139 启用、停用、移除各 +1） | — |

发布专用 SQL（`release/release-owned-sql/`，由 sync_sql.sh 复制进 `sql/`）：`platform-reference-data-verify.sql`（`11`，只读核对与期望值）、`client-master-comment-rollback.sql`（`81`，从备份 SHOW CREATE 取原注释；cm-dev 的注释 DDL 原本"不需要回退"，补上后回退可做到结构逐字一致）；预检 `00`、`02` 来自 `release/precheck/`。

## 7. 兼容性与顺序（详见 runbook 第 4 节）

- **brief 第 6 条的预判不成立**：ops-bff 调 client-master 的两个客户端用 `HttpServiceClientOptions.strict()` 但没有 `withStrictJsonResponses`，因此不启用 Foundation 严格转换器，走 `RestClient.builder()` 默认 Jackson 转换器。实测（`harness/tools/DefaultConverters.java`、`CompatCheck.java`，分别在现网与发布 ops-bff JAR 的类路径上运行）：默认转换器 `FAIL_ON_UNKNOWN_PROPERTIES=false`、`FAIL_ON_MISSING_CREATOR_PROPERTIES=false`；`AdminJurisdictionResponseBody` 是 setter 型 POJO。四种组合（旧 / 新 ops-bff × 旧 / 新 JSON）都 `READ OK`。`addressComponents` 这条边不约束顺序。
- 约束顺序的是：Platform 表名与镜像同窗口；新接口的三个消费方（client-master、client-bff、ops-bff）在 Platform 之后；client-master 镜像与 DML 同窗口（停 → DML → 起）；client-bff 先于 client-master（新 BFF 兼容旧 client-master，反之币种 / 行政区下拉为空）；Web 在各自 BFF 之后。
- 已知短窗口（不改数据、失败关闭）：Platform 之后到 ops-bff 之前，运营新建 / 修改币种被拒（新请求体字段必填）；client-master 之后到 ops-web 之前，分类编辑不可用（旧 ops-bff 行业 UNAVAILABLE、旧目录拒 2 级行业）。
- 旧接口消费方：仓库中 `CurrencySpecQueryApi` / `CountryRegionQueryApi` 只有 ledger、billing、card、ops-bff 使用；mor-test 无视图、无数据查询模板引用旧表名。

## 8. 语言包

`release/refdata_bundles.py`：复制切片 1 `onboarding_bundles.py`（mor-test 已用过），改动：合并 `i18n-bundles/<应用>.refdata.{en,zh-CN}.json`（Web 源码不得内置 `refdata`）；refdata 的 zh-CN 与线上不同时以语言包为准；新增"确认基准已变更译文"一步，**只处理本次触及的 key**（基准变更 key 与 refdata）。只读计划已对 mor-test 运行（只读 SQL，未登录）：

| 应用 | 当前 | 新命名空间 | 新增 | 基准变更 | 废弃 | 缺 zh | 期望 |
|---|---|---|---|---|---|---|---|
| client-console | #5 | refdata（排序 100） | 436 | 0 | 48 | 0 | #6，20 个语言包 |
| operations-console | #12 | reference-data（250）、refdata（260） | 645 | 1 | 59 | 0 | #13，52 个语言包 |

- 废弃 107 个 key 逐个核对：都在 `aba76a1a` 的 Web 源码中、都已被本需求删除（client 48 = `onboarding-schema.catalog.{STATE_OR_PROVINCE 34, INDUSTRY 10, CURRENCY 4}`；ops 59 = 同样 48 + 分类 / 币种页旧文案 11），外来 0。
- 发现：client-console 线上已有 2 条过期译文（`onboarding.start.applications_title`、`onboarding.upload.choose`，2026-10-23 写入，`based_on_source_revision` 1 < 2），#5 带着它们发布过，说明不阻塞发布。它们不属于本需求，脚本不触碰，只在计划中列出（`outdatedUntouched`）。本机阶段 5 需要的"确认"在 mor-test 预计 0 条（refdata key 在 mor-test 是新增，导入时即以当前基准写入）；步骤保留作保护。
- 导入文件：`i18n-bundles/{client-console,operations-console}.release.{en.json,zh-CN.delta.json,plan.json}`（plan 中有废弃清单与文件 sha256）。

## 9. 验收脚本（准备，未运行）

- `release/acceptance/refdata_mortest.py`：读入本机验收脚本源码后改写（不复制场景）：目标 mor-test（私网经一次性探针容器，镜像钉本次 Platform 镜像 ID）；去掉 4 处 root SQL 临时停用（改记 SKIP）与 XTS 草稿币种（Platform 无删除接口）；不跑 U；root SQL 只允许 SELECT；新增 L（ledger / billing / card 自 `--since` 起的 ERROR、`Currency snapshot refresh failed` 与健康状态）。改写已离线核对：转换后语法正确、停用 0 处、XTS 0 处、SKIP 4 处。
- `acceptance/make_ui_smoke.py` 生成 `ops_refdata.mortest.mjs`、`client_refdata.mortest.mjs`（`node --check` 通过）；`ui_provision_mortest.py` 预置验收 Client（无效币种用 `XYZ` 代替本机的 DRAFT `XTS`），凭据写 `~/Code-MoR-backups/refdata-20261027-ui/`（0600）。
- 写入范围：注册 3 + 1 个验收 Client、建申请并提交、保存 1 个案件分类——与切片 1 mor-test 验收同类。

## 10. 国旗（可选后续，未运行）

见 runbook 第 8 节：mor-test 没有接受 SVG 的公开扫描策略，也不建议加；建议转 PNG 后经运营控制台公开上传（`operations-console-bff` → PUBLIC_DOCUMENT 收 png，无需新注册；开始 / 完成路由每主体 10 次 / 分钟，约 25 分钟），再 root 回填 `t_ref_country.logo_file_address`。上传脚本需补全 PNG 转换与运营上传链路；两项写操作需单独授权。

## 11. 发现与待决

| # | 事项 | 建议 |
|---|---|---|
| D-1 | brief 预判"旧 ops-bff 会因 FAIL_ON_UNKNOWN_PROPERTIES 拒绝新 `addressComponents`"与实测不符（第 7 节） | 按实测：该边不约束顺序；顺序由其他边决定（runbook 第 4 节） |
| D-2 | 块 139 生成的预检没有检查写死的 ops target 与父菜单 | 已在 `sql/00` 补查（mor-test 均存在）；`gen_block139.py` 是否补上由 ops-fe / 主会话决定 |
| D-3 | client-master 注释 DDL 没有回退脚本 | 发布侧补了 `81`（可选），演练证明回退后结构逐字一致 |
| D-4 | 行政区本地名补丁（`12`）在 mor-test 是空操作 | 运行卡跳过；只用于执行过旧主脚本的环境（本机） |
| D-5 | AC-02 的"重启后首次加载"证据 | mor-test 本次不重启 ledger / billing；靠其 60 秒定时刷新（同一严格解析路径）与 L 场景日志判断。若要更强证据，可在发布后另行重启其中一个（需授权） |
| D-6 | 阶段 7 前 dev agent 若再改代码 | 指纹变化即停；重新同步 `Code-MoR-refdata-rel` 并重建受影响镜像，回填 runbook 与覆盖文件 |

本次未发现阻塞发布的代码缺陷。

## 12. 保留的本机产物

- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-refdata-base`（基线构建）、`/Users/kenny/Desktop/Workspace/Code-MoR-refdata-rel`（发布源与 `target/`、`dist/`，`refdata_bundles.py` 缺省 `--web-root`）。发布完成后由主会话决定是否删除。
- 本机镜像 tag `mor-test/<service>:refdata-20261027a`（6 个）。
