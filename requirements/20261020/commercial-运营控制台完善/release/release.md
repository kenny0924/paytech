# commercial 运营控制台完善（第二切片）：mor-test 发布清单

> 状态：阶段 6 准备稿（2026-10-20，未执行）。本目录只有材料，没有构建镜像，也没有改 mor-test 的容器、配置或数据。执行人在阶段 7 按第 4 节逐步执行，结果写入第 6 节。
> 先例：[首切片发布](../../../20261019/commercial-六层产品定价模型/release/release.md)。当前实现与部署状态以 `agent-docs/CURRENT_IMPLEMENTATION.md` 为准。

## 1. 范围

| 组件 | 动作 | 说明 |
|---|---|---|
| commercial-service | 替换镜像，固定 sha256 | H 线四组只读接口：合同列表、方案 / 版本列表、产品目录、BCR 读取。没有 DDL 或 DML，`mor_commercial` 不变 |
| operations-console-bff | 替换镜像，固定 sha256 | I1 和 I2 共 11 个新端点（工作台、费率预览、价格试算、合同列表、方案 / 版本列表、版本对比、产品目录三个、变更历史）。包含 Foundation F9 开关 `HttpServiceClientOptions.withOwnerProblemDetail()`，默认关闭，只在 `CommercialClientConfiguration` 的商务客户端工厂打开 |
| operations-console-web | 替换镜像，固定 sha256 | 新增工作台、费率预览、价格试算、产品目录、变更历史、合同列表、价目总览和版本对比页面；表单下拉改为读目录接口；导航顺序调整 |
| `mor_platform` | 执行 [test-dml.sql](./test-dml.sql)，再执行 [test-activate.sql](./test-activate.sql) | 共 140 行 INSERT 和 2 行 UPDATE，没有新增权限或授权。I1 续写块 95 的 37 行；I2 的块 93 是 103 行 |
| platform-support-service | **不发镜像** | 见第 3 节 R3：能力清单缺新组件键是既有缺口，不影响导航与路由 |
| Compose | 只用 [compose-candidate.yaml](./compose-candidate.yaml) | 三个 image 改为本次镜像；commercial 的环境变量与 depends_on 从首切片候选文件原样带过来 |
| 不动 | acquiring、card、counterparty、billing、channel-connector、account、ledger、Gateway、Platform、Nginx、RabbitMQ、Redis、XXL | `mor_commercial` 不执行 SQL；XXL 分组 14 与任务 16、17 不变 |

**构建基线：** second-slice 先 rebase 到最新 main，再取 rebase 后的头作为基线。最新 main 必须不早于 `5b825160`，并包含 counterparty 改名 `c661e9fc`。基线提交号写作 `<发布时填>`。2026-10-20 核对时 slice2 头为 `4d8f4ec3`（`41e3cf1c` 之后只改了两个 BFF 测试文件），还没有 rebase；当时 main 为 `4543bfc1`。

**镜像 tag：** `mor-test/<服务>:commercial-20261020b`，三个服务都用这个 tag。发布时把 compose-candidate.yaml 的 image 改成 `sha256:<镜像 ID>`，SHA 由主会话填写。

## 2. 执行前核对（只读）

### 2.1 2026-10-20 准备时的快照

阶段 7 第 1 步要重跑这些核对。

| 项 | 结果 |
|---|---|
| 运行镜像（回滚目标） | commercial `sha256:88dbf74155a9ceb5c82f01376b1bb3ddec7a9fe7ba9077bf42b50b7da04cd71d`，03:10:37Z 重启，镜像未变，Worker 17。ops-bff `sha256:971c9238091c61eba5002133d167ca6a55521171214b00b1be45cca8248dbc92`，03:12:09Z 起运行，由科目分级 release 的 compose-candidate 部署。ops-web `sha256:a8feab2c14f405e8794482d3c154a5cef5578ca258bde64405e85e3b1e408a40`，03:12:10Z 起运行。Platform `sha256:33c196ec9f9a1d18af5876f7c24b32563580bc9b5bc895ea5bc8bb4f494a5b82`，不替换 |
| ops-bff 环境 | 运行容器的环境变量键集合，与首切片 compose-candidate 渲染结果完全一致，所以只换 image 不会丢配置 |
| `mor_platform` 冲突 | I1 段（95309–95313、95425–95427、95629–95636、95706–95708、95945–95962）全部 0 行；块 93 全部 0 行。11 个新 route_code 与 `(http_method, external_path_template)` 不重复（唯一键 `uk_gateway_route_code`、`uk_gateway_route_path`）。5 个新 resource_code 也不重复。首切片块 95 路由 24 条；95303 定价画像 sort 20，95304 价目 sort 30；PLATFORM_ADMIN 版本 **33** |
| 其他会话的 ID 块 | channel-connector 用块 92，尚未写入 mor-test；finance 人工调整预定块 100–102；main 的初始化 DML 没有块 93。本需求的 140 行与首切片 319 行没有交集 |
| SQL 一致性 | `db/platform-support-service/registration-dml.sql`（去掉 3 行阅读头）与 slice2 中 `platform-support-initial-data.sql` 末尾的 I1 + I2 两块逐字一致。**rebase 后需重新 diff 一次** |
| SQL 演练 | 在一次性 `mysql:8.4.11` 容器中，导入 mor-test 当前 9 张相关表的只读转储，依次执行：test-dml 试跑（COMMIT 改 ROLLBACK）后无残留；正式执行得 5 / 3 / 8 / 3 / 18 / 8 / 16 / 31 / 48，排序调整为 95303 → 30、95304 → 20；test-activate 3 / 8 / 5 / 1；test-deactivate 3 / 8 / 5 / 1。容器和转储已删除 |
| `mor_commercial` | 有一份真实 Client 的 ACTIVE 合同 `CL-F1105E`：agreement `238132079668301824`，client `237610139979456513`，OE `AE001`。版本和画像都是 0。验收不得修改或删除这份合同 |
| F9 开关 | slice2 中 `withOwnerProblemDetail()` 只在 BFF 的 `CommercialClientConfiguration` 中调用，其余客户端保持默认关闭 |
| 页面数 | slice2 的 `pageCapabilities.ts` 有 42 个页面，main 有 37 个；新增商务五页。如果 rebase 时 main 又加了页面，以 rebase 后的计数为准 |

### 2.2 前置（阶段 7 开始前完成）

1. **通知 code-mor-9c。** 本次替换 ops-bff 和 ops-web。新镜像基于 rebase 后的 main，已经包含其科目分级 `e73764d1`；发完把镜像 sha256 和基线提交号发给它。
2. **与 channel-connector 运营控制台需求对齐。** 它的块 92 也要替换 ops-bff 和 ops-web。后发的一方必须从包含先发一方改动的 main 构建，否则会回退对方页面。发布前确认对方的 BFF / Web 改动是否已经进入 main。
3. **不重建 acquiring 和 card。** code-mor-32 已把 consumer 切到 counterparty，本次不动这些容器。
4. 确认没有其他会话正在替换 ops-bff、ops-web 或 commercial，也没有其他会话在执行 `mor_platform` DML。
5. 本机阶段 5 冒烟通过，见 `verification/stage5-smoke.md`。rebase 后在发布源码上跑一次 commercial、ops-bff 和 ops-web 的测试与构建。

## 3. 风险与取舍

- **R1 路由先于镜像：** 新路由激活后，如果 BFF 仍是旧镜像，Gateway 会转发到不存在的端点，返回 404。所以 test-activate 必须在三个容器都 healthy 之后执行，见第 4 节顺序。休眠的注册行对任何版本都没有影响，可以先执行 test-dml。
- **R2 菜单排序：** test-dml 会立即交换「价目」和「定价画像」两个 ACTIVE 菜单的顺序（95304 → 20，95303 → 30），这一步不受激活控制。旧 Web 只是显示顺序变化，没有功能影响。停用时不回退排序。
- **R3 Platform 能力清单：** slice2 给 `console-resource-capabilities.json` 加了五个商务组件键，但运行中的 Platform JAR（`33c196ec`）连首切片的 `OPERATIONS_COMMERCIAL_*` 键都没有。这个文件只在「菜单资源」管理页做组件校验和展示，不影响导航、授权和 Gateway 路由；首切片也一直在这个状态下运行。因此本次不发 Platform。影响是：在菜单资源管理页对商务资源做改状态或绑定，可能被识别为未知组件。下次发 Platform 时会一并补齐。
- **R4 Compose 主文件：** `test-environment/compose.applications.yaml` 的 commercial-service 段仍是旧形状。任何不带候选覆盖文件的 `compose.sh up` 或 `deploy.sh`，都会让 commercial 因缺少 Snowflake 变量而启动失败。本次继续只用覆盖文件，主文件的回写仍是用户待办。

## 4. 执行顺序（任一步不符就停）

1. **复核：** 重跑第 2.1 节的核对，包括运行镜像、ID 段、PLATFORM_ADMIN 版本和 SQL 一致性，并完成第 2.2 节前置。
2. **备份：** 备份到 `~/Code-MoR-backups/<UTC>-commercial-ops2-test/`，目录权限 0700、文件 0600，附 SHA256SUMS。内容如下：
   - `mor_platform` 的 `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role`；
   - 三个容器的 `docker inspect` 和 `images-before.txt`；
   - 容器清单；
   - `compose-rollback.yaml`，写入三个当前运行镜像 ID，见 2.1 节；
   - `compose.applications.yaml` 副本。
   
   `mor_commercial` 不执行 SQL，备份可选，用于验收清理前后核对行数。
3. **构建：** 从 rebase 后的基线建干净 worktree，用 JDK 25 离线执行：
   ```
   ./mvnw -B -o -DskipTests -pl commercial/paytech-commercial-app,operations-console-bff/paytech-operations-console-bff-app -am clean package
   ```
   Web 执行 `npm ci && npm run build`。三个镜像用 `docker build` 和显式 tag `commercial-20261020b` 构建，不经过 `test-environment/artifacts` 和 `compose.sh build`，也不改写 `:development`。核对以下内容：
   - commercial JAR 里有 H 线的四个只读 Controller；
   - BFF JAR 里有 I1 / I2 的 Controller 和 `paytech-http-client-support` 的 `withOwnerProblemDetail`；
   - Web 有新入口，并且有 `CommercialWorkbench`、`CommercialCatalog`、`CommercialHistory` 等分块；
   - 与当前运行的 BFF JAR 比较，条目只增不减。
   
   把三个 sha256 写入 compose-candidate.yaml，并用 `config --quiet` 校验。
4. **数据库（旧版本仍在运行，休眠行不生效）：** 先以 `platform_app` 执行 test-dml 试跑：`sed 's/^COMMIT;/ROLLBACK;/'` 生成副本，执行后第 3 段结果应与第 0 段相同。再执行原文件，第 3 段应为 5 / 3 / 8 / 3 / 18 / 8 / 16 / 31 / 48，sort 30 / 20。
5. **替换：** 用 `compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait`，依次替换 commercial → ops-bff → ops-web。每个容器都要 healthy、重启 0、启动日志无 ERROR，并且运行镜像 ID 等于候选 ID。commercial 日志要确认 RabbitMQ / Redis 连接、Nacos 注册和 XXL 心跳（分组 14）。Web 的 `index.html` 要引用新入口。
6. **启用：** test-activate 先在事务内试跑：COMMIT 改 ROLLBACK，结果 3 / 8 / 5 / 1。再提交，PLATFORM_ADMIN 版本 33 → 34（以第 0 段实测为准）。等 Gateway 快照刷新，周期 5 秒。
7. **验收：** 按 [acceptance.md](./acceptance.md) 执行，结果写入 `verification/stage7-acceptance.md`，截图放在 `assets/stage7/`。
8. **清理：** 按 ID 删除验收合成的商务数据，先 SELECT 核对再 DELETE，SQL 写入 `verification/acceptance-cleanup-test.sql`。`CL-F1105E` 不动。合成审批人员要停用、合成角色要清空权限，Platform 审计不回滚。最后删除构建 worktree。
9. **记录：** 写入第 6 节：时间、命令摘要、核对数、三个 sha256、JAR SHA-256、Web 入口和基线提交号。然后更新 delivery.md，并通知 code-mor-9c 和 channel-connector 会话。

## 5. 回滚

| 情况 | 处理 |
|---|---|
| 某个容器替换后不健康 | 用备份目录的 `compose-rollback.yaml` 执行 `compose.sh -f … up -d --no-deps --no-build --pull never --wait <service>`，把它回退到上一个 sha256：commercial `88dbf741…`、ops-bff `971c9238…`、ops-web `a8feab2c…`。如果此时还没执行 test-activate，不需要处理数据库 |
| 启用后发现阻断缺陷 | 先执行 [test-deactivate.sql](./test-deactivate.sql)（3 / 8 / 5 / 1），关闭新菜单和路由；再按上一行回退镜像。首切片的 24 条路由、8 个资源和 5 个授权不受影响 |
| 只回退 BFF | 必须先执行 test-deactivate，否则新路由会 404。回退 commercial 不影响 BFF 的旧端点；新 BFF 端点调用旧 commercial 会失败，所以同样先停用 |
| 注册数据 | 140 行在休眠时无影响，通常保留。删除需另行授权：按 test-dml.sql 头注释的 ID 段删除子表，并恢复 95303 sort 20、95304 sort 30 |
| 不可逆项 | 没有 DDL，也没有向外投递。验收产生的 commercial BCR 和 Platform 审计会留下记录；BCR 按 ID 清理，审计不回滚 |

## 6. 执行记录（阶段 7 填写）

| 步骤 | 结果 |
|---|---|
| 1 复核（05:34Z） | slice2 已 rebase 到 main `4543bfc1`，包含 `5b825160`、`c661e9fc` 和 `e73764d1`，当前头 `886edda7`。D1 修复尚未提交，基线等主会话给定。SQL 阅读副本与 initial-data 末尾的 I1 + I2 两块仍逐字一致，其后没有别的块；main 没有块 92 / 94。30 个容器 healthy；三个运行镜像与 2.1 节一致（commercial `88dbf741…`、ops-bff `971c9238…`、ops-web `a8feab2c…`，重启 0），ops-bff 环境键也与 2.1 节一致。`mor_platform`：I1 段和块 93 全部 0 行，块 92 也是 0 行；新 route_code、(method, path) 和 resource_code 都没有重复。首切片 24 条路由 ACTIVE；95303 sort 20、95304 sort 30；PLATFORM_ADMIN 版本 33；路由共 330 条、资源 100 个。`mor_commercial` 只有 CL-F1105E（ACTIVE），版本、画像和补偿事件都是 0。前置：9c 已确认可发（主会话）；channel-connector 的块 92 不在 main；本机冒烟见 `verification/stage5-smoke.md`，Web 缺陷 D1 修复中。主机上只有 I2 agent 在 slice2 跑本地 Maven，没有其他 mor-test 部署或 SQL |
| 2 备份（05:34Z） | 备份目录 `~/Code-MoR-backups/20261020T053437Z-commercial-ops2-test/`（目录 0700、文件 0600，附 SHA256SUMS），内容：`mor_platform` 8 张注册表、`mor_commercial` 整库（20 张表）及逐表行数、`compose.applications.yaml` 副本、三个容器的 inspect 和 `images-before.txt`、容器清单、`compose-rollback.yaml`。注意：回退 commercial 时要同时传首切片的 compose-candidate.yaml，因为主 compose 文件的 commercial 段缺少环境变量 |
| 3 构建（05:37—05:39Z） | 基线提交 **`19ccd78a`**：`886edda7` 加冒烟 D1 修复，共 6 个文件，其中 BFF 改了 `CommercialBusinessChangeProcessor`，SQL 不变。在干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-release2` 构建：JDK 25 离线 `clean package` BUILD SUCCESS；Web `npm ci && npm run build` 通过。三个镜像都用 `docker build` 打显式 tag `commercial-20261020b`，没有经过 artifacts 和 `compose.sh build`：commercial `sha256:59bb000da5c0115e1cb3f5021c76322d4bf214a3dbf61cee8304942f43684adb`，JAR `437a46e83ba6dd905365979b7900fbe72f1ea8b70d64e0a0f28f875fc359a2f7`；ops-bff `sha256:e294a4ae90bec06bd3e276cb0ab181269fbbc60af7912b22103ce781bd76505b`，JAR `54c9bf0ca0ef0df15af79630923863bf11a6c554018b9ce69f8413a946883ec0`；ops-web `sha256:f00801f59482069dfd99d309c1538d82b622e5696be644a4c47469991471675e`，入口 `assets/index-D5UHJXEd.js`。内容核对：commercial 含 H 线的 4 个 Controller；BFF 含工作台、试算和变更历史的 Processor，以及 `CommercialCatalogController`、`CommercialPricePreviewController`；`withOwnerProblemDetail` 只出现在 `CommercialClientConfiguration`；与运行中 BFF 相比只增 59 个条目、无删除；Web 有商务 8 页分块，运行版的分块没有缺失。三个 ID 已写入 compose-candidate.yaml，`config --quiet` 通过 |
| 4 数据库（05:34Z，`platform_app`） | 执行的 test-dml.sql SHA-256 前缀为 `bc1f79d09e60b2dd`。试跑时两处 COMMIT 改为 ROLLBACK：第 0 段和第 3 段都是九个 0、sort 20 / 30，没有残留。05:34:52Z 正式执行：第 0 段为九个 0、sort 20 / 30；第 3 段为 **5 / 3 / 8 / 3 / 18 / 8 / 16 / 31 / 48**，95303 sort 30、95304 sort 20。11 条路由和 5 个资源全部 DISABLED，PLATFORM_ADMIN 仍为 33，路由共 341 条。输出见 `sql-4.1-test-dml-trial.out` 和 `sql-4.2-test-dml-commit.out` |
| 5 替换（05:39—05:40Z） | **commercial**：05:39:19Z up，05:39:47Z healthy，镜像 `59bb000d…`，容器内 JAR `437a46e8…`。启动日志 ERROR 0；已连 RabbitMQ 和 Redis，Nacos 注册 `172.30.73.34`，两个 XXL Handler 已注册，05:39:41 心跳。**ops-bff**：05:39:59Z 到 05:40:17Z healthy，镜像 `e294a4ae…`，JAR `54c9bf0c…`，ERROR 0，ledger / account 直连变量保留。**ops-web**：05:40:23Z 到 05:40:34Z healthy，镜像 `f00801f5…`，入口 `assets/index-D5UHJXEd.js`，商务 8 页均为 200。三个容器重启次数都是 0；30 个容器全部 healthy |
| 6 启用（05:40Z，`platform_app`） | test-activate.sql 先试跑（COMMIT 改 ROLLBACK）：第 0 段 3 / 8 / 5 / 33，影响 3 / 8 / 5 / 1；回滚后角色版本仍为 33、ACTIVE 路由 0。05:40:43Z 正式提交：**3 / 8 / 5 / 1**，PLATFORM_ADMIN 33 → **34**，11 条路由和 5 个菜单为 ACTIVE。输出见 `sql-6.1-activate-trial.out` 和 `sql-6.2-activate-commit.out` |
| 7 验收（05:43—05:52Z） | 接口验收 [stage7_acceptance.py](../verification/stage7_acceptance.py) 首轮 **101/102**。唯一不符是 F9-3：用例用 `expectedChangeVersion=1` 再次激活已 ACTIVE 的画像，正好命中 commercial 设计上的「原请求重放」分支，返回 200。补测 `expectedChangeVersion=5` 得 409 `CONCURRENT_REQUEST`，detail 为 Owner 原文，脚本已修正。真实 Gateway 的查询参数白名单对 F1-6 `foo` 与 F6-3 `bad` 均返回 400。页面回归 [stage7_regression.py](../verification/stage7_regression.py) **46/46**：42 页，加 4 个深链接（版本对比、版本工作区、两个合同详情）；D1 的对比页没有出现「无权限」，截图在 `assets/stage7/`。日志 ERROR 0，无口令或 Token；`t_compensation_event` 为 0 行；XXL 任务 16 运行 3 次，全部 200；任务 17 运行 27 次，26 次 200，05:39:30 那次 500 发生在 commercial 重启窗口内。详见 [stage7-acceptance.md](../verification/stage7-acceptance.md) |
| 8 清理（05:53Z） | [acceptance-cleanup-test.sql](../verification/acceptance-cleanup-test.sql)（`commercial_app`）先 SELECT 核对，全部符合期望；随后同一事务删除：BCR 版本 42、记录 10、组件 27、价格 9、版本 2、方案 1、约束 3、开通 2、产品 1、文件 1、参与方 2、条件 3、画像 3。`mor_commercial` 逐表行数与备份时一致，CL-F1105E 未动。合成人员 2 个已停用，合成角色 2 个已清空。构建 worktree 和临时构建上下文已删除；`test-environment/artifacts` 与 `compose.applications.yaml` 自始未改动 |
| 9 记录 / 通知 | 已写本节、`verification/stage7-acceptance.md` 和 compose-candidate.yaml（三个固定 ID）。通知 code-mor-9c 与 channel-connector 会话、更新 delivery.md / CURRENT_IMPLEMENTATION.md、提交，均由主会话负责 |

**当前 mor-test：** commercial `59bb000d…`、ops-bff `e294a4ae…`、ops-web `f00801f5…`，tag 都是 `commercial-20261020b`，基线 `19ccd78a`。第二切片的 11 条路由和 5 个菜单为 ACTIVE，PLATFORM_ADMIN 版本 34；30 个容器 healthy。回滚按第 5 节，旧镜像 ID 在备份目录的 `compose-rollback.yaml`。

**遗留：** ① Platform 能力清单缺商务组件键（R3），等下次 Platform 发布补齐。② `compose.applications.yaml` 的 commercial 段仍是旧形状（R4）。③ acceptance.md 中 F9-3 的示例「重复 activate」需改为「不可能的期望版本」，例如 5。④ I2-gaps 的 G1–G4 未变。
