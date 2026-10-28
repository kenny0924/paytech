# mor-test 发布基线（阶段 6 只读收集，2026-10-27 19:20—19:40Z）

> 收集方式：`docker inspect`（镜像、标签、启动覆盖文件标签、`Config.Env` 只比较不落值）、`docker exec printenv`（只取键名）、`docker cp` 从容器**拷出** JAR 与 Web dist、`docker logs`（只读）、mor-test MySQL `SET SESSION TRANSACTION READ ONLY` 会话内的 `SELECT`、`mysqldump --single-transaction` 到本机。没有启停或替换任何容器、没有向容器拷入文件、没有写任何 mor-test 库、没有发布语言包、没有改动或重打已有镜像标签、没有提交。
> 镜像一律按 sha256 固定；回滚目标是下表"运行镜像"，不是标签。

## 1. 受影响服务运行现状

| 服务 | 运行镜像（= 回滚目标） | 标签 | 容器启动（UTC） | 启动时使用的覆盖文件（compose.sh 基础三文件之外） |
|---|---|---|---|---|
| platform-support-service | `sha256:aec535863836669db6063a139c448ee817d00f64d6a4adea008448a413e4d5b5`（JAR `f4d03bef50208c3e`） | refdata-20261027a | 2026-10-27 15:34 | `requirements/20261027/platform-support-参考数据目录与client-master迁移/release/compose-candidate.yaml`（只换镜像） |
| card-service | `sha256:810b6b72b6ea019c60b0afff4750b0d858aaec5c2ee0acd5102a7d488459a81f`（JAR `5cc2fd8dad707c11`） | pubbucket-20261023a | 2026-10-23 05:23 | `requirements/20261022/platform-support-文件公开桶/release/compose-candidate.yaml`（只换镜像） |
| operations-console-bff | `sha256:481132892c9388687261370f50d5da21229b83b214deab699872907239bf57b3`（JAR `bd03e82df00f5c8d`） | refdata-20261027a | 2026-10-27 15:39 | 参考数据发布 `compose-candidate.yaml`（7 个显式 env：BIND_ADDRESS、CONSOLE_ORIGIN、TEST_HTTP_ENABLED、LEDGER / ACCOUNT / BILLING / RECONCILIATION 四个 BASE_URL） |
| operations-console-web | `sha256:2e179d6f1bc3635a427f50246aa86fea338e740995d703868799cdf54efb9a4c`（dist 245 个文件） | fileupload-ux-20261027d | 2026-10-27 18:38 | `requirements/20261027/operations-console-文件上传页面整理/release/compose-candidate.operations-console-web.industry-expand.yaml`（只换镜像） |
| 不换：api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4` | card3ds-20261022b | 2026-10-22 18:04 | `requirements/20261022/payment-3DS全链路/release/compose-candidate.yaml`；代码本次不变，只消费块 140 快照 |
| mysql | `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`（mysql:8.4.11）；离线演练用本机同一镜像 ID | — | — | — |

Gateway 日志（19:31Z）：每约 5 秒 `Gateway Snapshot 刷新成功: targetCount=4, routeCount=612`。`mor_platform` 路由 ACTIVE 612、DISABLED 6（与快照一致）。

## 2. 现网内容 vs 研发基线 5e1f16ab

方法：干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-binlist-base`（`git worktree add --detach … 5e1f16ab`，无改动）离线构建 3 个 exec JAR（JDK 25，`./mvnw -o -Dmaven.repo.local=~/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ -pl <3 个 app> -am clean package -DskipTests`）与 ops-web dist（`npm run build`，node_modules 链接主工作区，`package-lock.json` 相同）。JAR 用 `harness/tools/jarcmp.py`（条目 CRC，递归展开 `paytech-*.jar`，忽略 MANIFEST / classpath.idx / layers.idx / pom.properties）；dist 用逐文件 sha256。

> 构建参数说明：`~/.m3/repository` 里的 `_remote.repositories` 标记来自 Nexus 仓库 ID（maven-central / maven-public），wrapper Maven 离线时会判为"present, but unavailable"；`-Daether.enhancedLocalRepository.trackingFilename=_no_tracking_` 让解析器不读这些标记、直接使用本地已有制品（不联网、不改仓库内容）。

| 服务 | 现网 vs 5e1f16ab 构建 | 结论 |
|---|---|---|
| platform-support-service | 3096 / 3096 条目，0 差异 | **相同** |
| operations-console-bff | 2791 / 2791，0 差异 | **相同** |
| card-service | card 自身类（`BOOT-INF/classes` 与 `paytech-card-*`）**0 差异**；只有共享库不同：`paytech-platform-support-api` +66 类（多语言 `localization/*`、参考数据 `referencedata/*`、`FileUploadConstraintsResponseBody`）与 6 个类变化（`InternalFileApi`、币种请求体、工作流 3 个类）、`paytech-http-client-support` 2 个类（422 远端字段错误 `remoteFieldErrors`，f707f2da）、`paytech-messaging` `catalog/events.yaml`（98210e0b / 9f506f0f 新事件） | **现网早于基线**：现网镜像构建于 2026-10-23（文件公开桶），之后 card 代码只改了测试与文档；差异全部是 5e1f16ab 之前**已提交**、已在其他服务上线的共享库。不是他人未提交 / 未上线的内容。随本次 card 候选一起上线（决定 D-4） |
| operations-console-web | 现网 245 个文件，5e1f16ab 构建 243 个，内容哈希全部不同 | **现网含基线之外的内容（已停止按 5e1f16ab 出 ops-web 计划）**：见下 |

### 2.1 operations-console-web：现网 = c4b99e2d，不是 5e1f16ab

- 现网镜像 `2e179d6f`（fileupload-ux-20261027d）来自"operations-console 文件上传页面整理"四轮发布，源码已提交为 main **c4b99e2d**（在本需求基线 5e1f16ab 之后）。
- 证明：干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-binlist-c4b`（c4b99e2d，无改动）`npm run build` 的 dist 245 个文件与从容器拷出的 `/usr/share/nginx/operations-console/` **逐字节相同**（sha256 清单 diff 为空）；`deploy/nginx.conf` 与现网相同。
- 5e1f16ab → c4b99e2d 的 ops-web 差异（15 个文件，全部不在本需求范围）：`i18n/zh-CN/{file,reference-data}.json`、`src/i18n/en/{file,reference-data}.json`、`src/fileRecords/{FileRecordDetailView.tsx,FileRecordParts.tsx,fileRecords.css}`、`src/fileUpload/{RecentUploads.tsx,UploadPanel.tsx,fileUpload.css,fileUpload.ts}`、`src/pages/{CurrencySpecs.tsx,FileUpload.tsx,ReferenceData.tsx}`、`src/referenceData/referenceData.css`。
- 若按 5e1f16ab + 本需求构建 ops-web：会**回退**文件上传卡、文件记录详情页签、参考数据页签按需加载与 MCC 后端分页、行业展开 / 收起；语言包按该源码导入会**废弃** 15 个线上 key（`file` 13、`reference-data` 2，已用工具核实）。
- 与本需求 11 个 ops-web 文件（`src/card/*` 8 个、`src/pages/CardBinLibrary.tsx`、`src/i18n/en/card.json`、`i18n/zh-CN/card.json`）**0 重叠**；本需求代码只引用 `src/referenceData/referenceData.ts`（c4b99e2d 未改）。
- team-lead 2026-10-27 同意以 c4b99e2d 为 ops-web 基础（"ok c4b99e2d"；主仓库核对 5e1f16ab..c4b99e2d 不改 card / platform-support / operations-console-bff，Java 三个服务仍按 5e1f16ab 等价基线）；用户在阶段 7 确认点一并确认。已准备：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-binlist-webrel` = c4b99e2d + 本需求 11 个文件（与 Code-MoR-binlist 逐字节相同）；`npm test` 1571 / 1571 通过，`npm run build` 通过。

## 3. 候选镜像（tag `binlist-20261027a`，本机构建，未推送，`:development` 未改动）

构建源（冻结）：

| 源 | 内容 | 指纹（全部非忽略文件 sha1 清单再 sha1，不含 node_modules） |
|---|---|---|
| `/Users/kenny/Desktop/Workspace/Code-MoR-binlist`（研发） | 5e1f16ab + 57 个改动路径（32 M、25 新增，清单 `harness/release-files.txt`） | `33406c1159595015` |
| `/Users/kenny/Desktop/Workspace/Code-MoR-binlist-rel`（Java 构建） | 5e1f16ab + 同 57 个文件的复制 | `33406c1159595015`（相同） |
| `/Users/kenny/Desktop/Workspace/Code-MoR-binlist-webrel`（ops-web 构建，D-1） | c4b99e2d + 11 个 ops-web 文件 | `0b8765bdfc415d43`；11 个文件子集与研发树相同 `8a566b23d0ca47b4` |

不在研发树里构建，避免清掉 dev agent 的 `target/`。阶段 7 前重算，见 runbook P-2。

| 服务 | 镜像 ID | 制品 | 候选 vs 基线（只为确认范围） | 镜像核对 |
|---|---|---|---|---|
| platform-support-service | `sha256:186347bcb7b4b4ea11d26804646d77d1f8b9fd6346577adef39ace3deef683a1` | JAR `93a3fb441d773f39…` | +6 ~6：`OperationsAccountLabelController`、`ListOperationsAccountLabelsProcessor` + Request、api `OperationsAccountLabelQueryApi` + 2 个响应体；`OperationsPersonnel{Mapper,Repository(Impl),Service(Impl)}` 与 Mapper XML | 镜像内 JAR = 构建产物；User 10001:10001、Entrypoint 与现网镜像相同（compose 另以 `entrypoint` 覆盖内存参数）；health-probe 5 个 class 大小与现网相同 |
| card-service | `sha256:380064febe8fbc3883c81292f7e034b6378f8262c0b7bee0503434f5facf6754` | JAR `d41f3ada4ec33f4c…` | +5 ~9：`BinRangeResponseBody`、`BinRangeFilter`、`CardBinLibraryController` / Converter / Api、`CardBinRangeMapper`(+XML)、`CardBinRangeRepository(Impl)`、`CardBinLibraryQueryService(Impl)`；另 platform-support-api 3 个新类型（随依赖） | 同上 |
| operations-console-bff | `sha256:25f6a869b28091b6f26e14bc1f718d8087ffd83e194b58c2c440712211e446b7` | JAR `4dcbfa5dfc1f2dd8…` | +15 ~6：`OperationsAccountLabelClient(Impl)`、`OperatorLabelResolver`、3 个 Processor + 3 个 Request、2 个 Labeled 响应体、`PlatformClientConfiguration`、`CardBinLibraryController` / Client(Impl)；card-api / platform-support-api 新类型 | 同上 |
| operations-console-web（D-1） | `sha256:aec066e9d896fd69fe4340ff7cd90eee54f95a780cf335bde7bed4382dbae3e1` | dist 250 个文件，入口 `assets/index-D0P_eShl.js`（现网 `index-BNsrd1bW.js`） | 源码 = c4b99e2d + 11 个文件。dist 去哈希比较（`harness/tools/distcmp.py`）：`CardBinLibrary` js / css 变化，新增 5 个图标分片（ClockCircle / Download / FileText / UnorderedList / Upload），另有 12 个分片因打包器重新分块（共享图标拆出、`binLibrary.ts` 与 en `card` 文案所在分片变化）而内容移动——源码层面只有 11 个文件 | 镜像内 dist 与构建产物 250 / 250 相同；`nginx.conf` 与现网相同；User nginx、Entrypoint `nginx`、Cmd、7 个 env 与现网镜像相同 |

## 4. 覆盖文件与 env

- 本需求**不新增 env**：`application*.yml` 无改动；新代码只用既有 `operations.platform-test-base-url`（缺省空，测试用）。Platform 新接口的调用方准入在 Processor（只认 `operations-console-bff`），不需要配置项。
- 分服务覆盖：`compose-{candidate,rollback}.{platform-support-service,card-service,operations-console-bff,operations-console-web}.yaml`。Platform / card / ops-web 只换镜像（与现网启动覆盖相同形状）；ops-bff 带现网 7 个显式键。
- 渲染核对：`./test-environment/compose.sh -f <文件> config --format json`（只读）与运行容器逐键逐值比较（`harness/tools/envcmp.py`，只打印键名）：

| 文件 | Platform | card | ops-bff | ops-web |
|---|---|---|---|---|
| 候选 | 59 / 59 | 39 / 39 | 20 / 20 | 7 / 7 |
| 回滚 | 59 / 59 | 39 / 39 | 20 / 20 | 7 / 7 |

全部 missing 0、extra 0、值差异 0。另核对三个 Java 服务渲染后的挂载点、`entrypoint`、网络与固定 IP（172.30.73.21 / .37 / .22）与运行容器相同。

现网 env 键名（值不落盘）：card 39 个（含 `CARD_FINGERPRINT_KEY`、RabbitMQ 5、XXL 8、Snowflake 2）；Platform 59 个（含 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE`、FILES、RabbitMQ 5、`DATAQUERY_HTTP_CALLERS_0`、`SPRING_APPLICATION_JSON`）；ops-bff 20 个；ops-web 7 个（nginx 镜像自带）。

## 5. 数据库只读核对（`precheck/mortest-block140-precheck-*.out`，19:3xZ）

| 核对 | 结果 |
|---|---|
| 块 140 区间 900000000000140001—140999 | 74 张 BIGINT 主键表合计 **0 行** |
| route code `operations-card-bin-range-list`、(GET, `/api/operations/card-admin/v1/bin-library/versions/{versionNo}/ranges`)、相同下游路径 | 均 0 |
| 权限 900000000000132101 | `CARD_BIN_LIBRARY_READ` **ACTIVE** |
| target 900000000000000001 | `operations-console-bff` **ACTIVE** |
| 同形路由 900000000000132405 `operations-card-bin-lookup` | ACTIVE；GET、OPERATIONS_JWT、HUMAN_PERMISSION 132101、cookie DROP / NONE、幂等 NONE、body 0、5000 / 10000 ms；header authorization CORS_REQUEST_ALLOWED + origin FORWARD_REQUEST；guard SHARED 300/min + 50、IP 30 + 3、principal 30 + 2——与块 140 逐项相同 |
| 现有卡 BIN 路由 | 132401—132409 共 9 条全部 ACTIVE；新路径 `/versions/{versionNo}/ranges` 与 `/versions/{versionNo}`、`/errors` 段数不同，无冲突 |
| 路由计数 | ACTIVE 612、DISABLED 6 → 激活后期望 **613** |
| mor_card BIN 数据（验收依据） | v1 ACTIVE 17 个号段（系统导入，createdBy 空）；v2 VALID 8、v3 INVALID（9 行错误）、v4 INVALID（表头）、v5 RETIRED 8、v6 VALID 8（导入人 `…267008`）；激活记录 3 条（系统 1、该账号 2）。发卡国 US 11 / AE 2 / GB / JP / SA / CN 各 1 |
| 显示名依据 | 账号 `…267008`：OPERATIONS Realm，**无 login_name**，有 email_masked → 页面显示脱敏邮箱 |
| 国旗 | `t_ref_country` 249 / 249 有 `logo_file_address`；BIN 用到的 6 个国家全部有 |

## 6. Gateway 快照离线演练（`evidence/rehearsal-20261027T1932Z/rehearse.log`）

断网一次性 MySQL（镜像 = mor-test 同一 ID）导入 19:32Z 只读备份，按 runbook 顺序执行；每步后用**候选 Platform JAR（93a3fb44）内的生产 `GatewayConfigurationCompiler`** 编译（`harness/BlockSnapshotCheck.java`，复制自参考数据发布，逻辑未改）。

| 步 | 结果 | 编译（loaded / effective / 块 140 loaded / ACTIVE / effective） |
|---|---|---|
| baseline | — | OK 618 / 612 / 0 / 0 / 0 |
| 00 预检 | 与 mor-test 直接查询一致 | — |
| 10 注册（休眠） | 成功 0.30 秒 | OK 619 / 612 / 1 / 0 / 0 |
| 13 核对 | route DISABLED v1；header 2、query 6、guard 6、与 132405 守卫差异 0 | — |
| 10 重复执行（应失败） | ERROR 1062 主键重复，整体不写入 | — |
| 11 激活 | routes 1 | OK 619 / **613** / 1 / 1 / 1 |
| 19 在 ACTIVE 时回退（应失败） | ERROR 1242 拒绝，未删任何行 | OK 619 / 613 / 1 / 1 / 1 |
| 12 停用 | routes 1 | OK 619 / 612 / 1 / 0 / 0 |
| 19 回退 | guard 6、query 6、header 2、route 1 | OK 618 / 612 / 0 / 0 / 0 |
| 结构 + 数据 前后比对 | **0 行差异** | — |

## 7. 语言包（operations-console，只读计划，19:35Z）

现网：应用 `operations-console` 当前发布 **#17**（READY），26 个命名空间，ACTIVE key 10104；`card` 命名空间 141 个 key。计划由 `binlist_bundles.py`（复用参考数据发布的 `refdata_bundles.py`，只读会话）对 `Code-MoR-binlist-webrel` 计算：

| 项 | 数 | 说明 |
|---|---|---|
| 新增 | **121** | 全部 `card.*`（本需求新页面文案） |
| 基准变更（en 文本改了） | **7** | 全部 `card.*`；其中 1 个 zh-CN 未变，发布时由"确认基准已变更译文"一步确认（`rebaseExpected` 1） |
| 废弃 | **68** | 全部 `card.*`，与"c4b99e2d 有、本需求删除"的 key 集合**完全相等**（逐个核对） |
| zh-CN 导入 | 128 | = 新增 121 + 基准变更 7，缺 0 |
| 其他命名空间 | 0 变化 | 新命名空间 0；现网 zh-CN 保留 10097 条 |
| 已知例外 | 3 | `nav.sidebar.{product,hide,show}`：现网 zh-CN 是用户 2026-10-24 批准的中文（#7，R2 例外），仓库种子仍英文；工具保持现网不导入 |
| 期望发布 | **#18** | 26 个命名空间 × 2 语言 = 52 个语言包 |

导入文件与计划：`i18n-bundles/operations-console.release.{en.json,zh-CN.delta.json,plan.json}`。对照：若用 5e1f16ab 基础的 `Code-MoR-binlist` 作源，计划会多废弃 `file` 13 + `reference-data` 2，工具的"废弃只在 card"门禁会拦下。

## 8. 并行会话与同服务待上线改动（只读 `git status`，19:3xZ）

| worktree | 基线 | 与本次服务重叠 | 风险 |
|---|---|---|---|
| `Code-MoR-refclient`（platform-support-参考数据客户端组件） | 5e1f16ab | **card 17、platform-support 58、ops-bff 7 个文件**（含与本需求同一文件 `operations-console-bff/.../PlatformClientConfiguration.java`），另 billing / ledger / client-master | 两边各自从 5e1f16ab 出镜像：**后上线者会回退先上线者**。需定先后（D-2） |
| `Code-MoR-legal`（client-master-国家包法务修订） | c4b99e2d | ops-web 11 个文件（clientReview / onboardingConfig / 两个 i18n）、ops-bff 1 个 | ops-web 同一镜像：后上线者需以先上线者的镜像源码为基础（D-3） |
| `Code-MoR-fileupload-ux` | 5e1f16ab | ops-web（已提交为 c4b99e2d 并已上线） | 已纳入本次 ops-web 基础 |
| `Code-MoR-onboard` | ea0272f3 | ops-web 等（较旧） | 不在本次范围 |

## 8.1 提交基线

阶段 8 的提交**必须基于 c4b99e2d（或之后的 main）**，不能以 5e1f16ab 为父提交重放整棵树：否则会回退 c4b99e2d 的 15 个 ops-web 文件。本需求 57 个路径与 5e1f16ab..c4b99e2d 的改动 0 重叠，按 `harness/release-files.txt` 逐文件放到 main 之上即可；提交后用 `git diff --name-only c4b99e2d <新提交>` 核对只含这 57 个路径。

## 9. 备份

`~/Code-MoR-backups/20261027-binlist-mortest-prep/mortest-mor_platform-20261027T193210Z.sql.gz`（sha256 `870ab8e41a98f5120b17f72ccd08b88f20461f7a70fc51bf3609c834d82056c7`，以 `Dump completed` 结尾）；演练的整库规范化导出、导出表与解开的 JAR 在同目录 `rehearsal-20261027T1932Z-artifacts/`（不入库）。阶段 7 执行前须**再备份一次**（runbook 第 2 节）。本需求不改 `mor_card`，不备份该库。

## 10. 需要用户决定

| # | 事项 | 建议 |
|---|---|---|
| D-1 | ops-web 以 **c4b99e2d**（现网实际来源）+ 本需求 11 个文件构建，而不是研发基线 5e1f16ab（team-lead 已同意，待用户确认） | 同意：否则回退已上线的文件上传 / 参考数据页签工作并废弃 15 个 key；候选镜像 `aec066e9` 已按此准备并核对 |
| D-2 | 与 `Code-MoR-refclient` 会话的先后（同改 card / Platform / ops-bff，且同一文件 `PlatformClientConfiguration.java`） | 本需求先上线并通知该会话以本需求镜像源码为基础重建；或本需求等待其先上线后重建三个 Java 镜像 |
| D-3 | 与 `Code-MoR-legal` 会话的 ops-web 先后 | 同 D-2：后上线者以先上线者的源码为基础 |
| D-4 | card 候选随带已提交的共享库变化（platform-support-api 新类型、http-client-support 422 字段错误、messaging 事件目录） | 接受：均已提交并在其他服务上线；card 自身类与现网 0 差异 |
| D-5 | 语言包 #18 废弃旧 BIN 页 68 个 `card` key | 接受：新页面不再使用；回滚时用 c4b99e2d 源重新发布恢复 |
| D-6 | 执行时段：Platform 重启约 30—60 秒（运营 / 客户门户登录、Gateway 快照刷新暂停、文件 / 多语言 / 工作流 / 数据查询）、card 约 30—60 秒（payment 取卡属性 / 3DS） | 选并行会话空闲的时段，执行前通知 |
