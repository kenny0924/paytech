# 商业关系快照与真实解析接入 · 阶段 6 构建报告（源码、基线、候选镜像、覆盖文件）

2026-10-25 11:00—11:25Z。本报告的第 1 节、第 3 节可以直接并入 release.md 的「§1 源码与镜像」和「§3 配置」。

本阶段只做了以下几类操作：
- `docker inspect` / `docker image inspect`；
- 用临时容器从**镜像**取出 JAR、health-probe、dist 和 nginx.conf；
- `docker exec … sha256sum /app/application.jar`；
- 读 `docker logs`；
- `compose.sh … config` 渲染。渲染文件含密钥，比较完后已删除；环境变量只比较键名和取值是否一致，不输出值；
- 在干净 worktree 里构建，并 `docker build` 出 7 个带新 tag 的镜像。

**没有做的事：** 没有停 / 起 / 替换任何 mor-test 容器；没有对 mor-test 执行 SQL；没有登录或调用接口；没有 `docker cp` 进运行容器；没有改 `:development`；没有提交；没有 `mvn install`。

## 1. 源码与镜像

### 1.1 构建源

| 项 | 内容 |
|---|---|
| 本需求源码 | 开发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-relsnap`（detached `8a86a31c`，改动未提交；main 仍是 `8a86a31c`）。<br>补丁 = `git diff --cached --binary 8a86a31c`，用私有 `GIT_INDEX_FILE` 生成：先 `read-tree 8a86a31c`，再 `add -A`，没有碰共享 index 和开发目录的 index。<br>排除：`*.png`、node_modules / target / dist、`verification/local-e2e/evidence/**` 与 `*-result.json`（运行输出）、`release/**`。<br>共 **419 个文件**（新增 217、修改 200、删除 2），补丁 SHA-256 `118a4b0aac5e6e323d2329990e4431a2746ea77d02bb2ec0ba1f4bd178d84ac4`，1 743 487 字节（[清单](./source-diff-8a86a31c.txt)；补丁文件在 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/relsnap-rel/relsnap.patch`）。<br>11:23Z 复算补丁哈希，结果不变，说明开发目录在构建期间没有改动 |
| 范围 | compliance 93（生产 70、测试 18、文档 5）、client-master 111（91 / 15 / 5）、commercial 49（31 / 17 / 1）、acquiring 51（25 / 20 / 6）、risk-decision 18（6 / 8 / 4）、ops-bff 30（25 / 4 / README 1）、ops-web 35。<br>不进镜像：platform-support 只有 `docs/database/sql/platform-support-initial-data.sql`（块 137）；arch-rules 3、`scripts/pdmaner` 3、requirements-site 2、agent-docs 5、AGENTS.md、需求目录 17（`db/` 3 + `verification/` 脚本与报告）。<br>**Platform 没有生产代码改动，不发镜像** |
| 构建 worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-relsnap-rel`（`git worktree add --detach … 8a86a31c`，绝对兄弟路径）。<br>先在纯 `8a86a31c` 上构建基线（第 1.2 节），再 `git apply --binary` 上述补丁。<br>与开发目录 `diff -rq`（排除 node_modules、target、dist、i18n-out）：compliance、client-master、commercial、acquiring、risk-decision、operations-console-bff、platform-support、foundation、build、operations-console-web **全部相同** |
| Java | JDK 25。命令：`paytech/mvnw -B -o -q -DskipTests -Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository -pl compliance/paytech-compliance-app,client-master/paytech-client-master-app,commercial/paytech-commercial-app,acquiring/paytech-acquiring-app,risk-decision/paytech-risk-decision-app,operations-console-bff/paytech-operations-console-bff-app -am clean package`。没有 install。<br>产物取 `*-exec.jar`。<br>镜像用 `test-environment/java/Dockerfile`（与主工作区逐字节相同）。`health-probe/` 经临时容器从运行镜像取出：6 个运行镜像的 health-probe 完全相同（5 个 class），候选镜像内与之逐字节相同 |
| Web | 依次执行：`npm ci` → `npx tsc --noEmit`（通过）→ `npm test`（**1519 / 1519**）→ `npm run build`（dist 226 个文件）→ `operations-console-web/Dockerfile`（上下文只放 `dist/` 和 `deploy/nginx.conf`）。<br>`deploy/nginx.conf` 本需求未改，与现网逐字节相同 |

### 1.2 基线证明（不加本需求，与运行容器比较）

**方法：** [jarcmp.py](./jarcmp.py)，与文件上传入口发布相同：
- `BOOT-INF/classes` 与嵌套的 `paytech-*` JAR 逐条目比较 CRC；第三方 JAR 整包比较 SHA-256；
- 排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引和目录项；
- 运行 JAR 经临时容器从运行镜像取出，6 个 SHA-256 都与 `docker exec … sha256sum` 一致；
- Web 逐字节比较 `dist/` 与 `nginx.conf`。

**归属方法：** 每个差异条目都对到源文件，再用 `git log --since=<镜像构建时间> 8a86a31c -- <路径>` 找到引入它的已提交改动。

| 容器 | 运行镜像（构建 / 启动） | 与 `8a86a31c` 基线的差异 | 归属 / 结论 |
|---|---|---|---|
| compliance-service | `2f62a26a…`，**按 tag `mor-test/compliance-service:development` 运行**（tag 当前仍指向该 ID）；10-18 15:55Z 构建，10-22 05:37Z 起；JAR `afb27fb4416d38bf` | 574 项，只差 2 项：`paytech-messaging!catalog/events.yaml`、`YamlEventCatalog.class`（[明细](./jar-diff-live-vs-8a86a31c-compliance-service.txt)） | Foundation messaging 在构建之后的已提交改动：`YamlEventCatalog.java` 来自 `9a209e6f` / `1d39cd94`；`events.yaml` 来自 `98210e0b` 等。**compliance 自身模块 0 差异** |
| client-master-service | `2e495b40…`（pubbucket-20261023a，10-22 20:39Z 构建，10-23 05:23Z 起；JAR `795461b0b2962663`） | 1485 → 1536：+51、~8，全部在 Foundation / `paytech-platform-support-api`（[明细](./jar-diff-live-vs-8a86a31c-client-master-service.txt)） | 多语言 API 49 项 + `LocalizationErrorCode`（`ed361c26`）；`FileUploadConstraintsResponseBody` 与 `InternalFileApi`（`3baef5f0`）；workflow API 4 类与 http-client-support 2 类（`f707f2da`）；`events.yaml`（`98210e0b`）。**client-master 自身模块 0 差异** |
| commercial-service | `c6c9b8f6…`（capability-20261021c，10-20 21:53Z 构建，10-22 05:37Z 起；JAR `0e3bc94ab646a2b1`） | 1110 → 1107：−3、~9（[明细](./jar-diff-live-vs-8a86a31c-commercial-service.txt)） | `client-master-api`：`ClientFileTarget*` 删除 3 项、`ClientDocumentModels` 3 项（`73a2460d`）。<br>`CommercialCodes$CardBrand`（`de4f7ba4` 新增 MADA / DISCOVER / JCB / DINERS）。<br>`$BillingFactType` 用 javap 核对，只有行号表变化（同一文件上方新增了行）。<br>http-client-support 2 项（`f707f2da`）；messaging 2 项（同 compliance）。<br>**commercial 自身 core / app 0 差异**；现网已含 `5a103f36`（开通项一期） |
| acquiring-service | `f6d55ade…`（f1-result-20261023，10-23 09:38Z 构建，10-23 10:03Z 起；JAR `dca404ec4d301876`） | 1506 → 1557：+51、~1，全部在 `paytech-platform-support-api`（[明细](./jar-diff-live-vs-8a86a31c-acquiring-service.txt)） | 多语言 API（`ed361c26`，10-23 09:55Z 提交，晚于镜像构建）；`FileUploadConstraintsResponseBody` / `InternalFileApi`（`3baef5f0`）。**acquiring 自身模块 0 差异** |
| risk-decision-service | `d69ca678…`（card3ds-20261022，10-22 17:11Z 构建；JAR `3b0195e5267cf692`） | 1371 项：**0 差异** | 现网 = `8a86a31c` |
| operations-console-bff | `0bc67410…`（fileupload-20261024a；JAR `f84caa925b7105b6`） | 2612 项：**0 差异** | 现网 = `8a86a31c` |
| operations-console-web | `70d3fdfb…`（fileupload-20261024a） | `dist/` **219 个文件逐字节一致**，`nginx.conf` 一致（[记录](./web-dist-live-vs-8a86a31c.txt)） | 现网 = `8a86a31c` |

**结论：**
- 所有差异都能由 `8a86a31c` 之前的已提交改动解释，而且全部落在 Foundation 和其他 Owner 的 api 模块；7 个服务的自身模块都是 0 差异。
- 没有未提交的现网改动需要带上，候选镜像也不会回退任何现网内容。
- 上述跨模块差异随本次发布一起生效。这些都是只增接口 / 文档 / 常量，或调用方并不使用的移除（`ClientFileTarget*` 在 commercial 内没有引用，否则编译不过）。

### 1.3 候选镜像（tag `relsnap-20261025a`）

| 服务 | 候选镜像 ID | JAR / dist | 相对基线（[明细](.)） |
|---|---|---|---|
| compliance-service | `sha256:ca1585452f9c60835ecb099db694e36bd5d2fdcfa197016a242d204954ce3545` | `5bd849ff153c1cf3` | +63、~11：compliance-api +14；core +46 / ~8；app classes +3 / ~2；`application.yml`（`/compliance-admin` 只记元数据） |
| client-master-service | `sha256:eeed4e63235de481ee6a02777de25a454197f2993af84fe90f14e0d1ee0df8a8` | `25cfb1fa9e084fc5` | +76、~81：client-master-api +18 / ~12；core +35 / ~49；app +3 / ~6；`application.yml`（`/client-master-admin`）；commercial-api +6 / ~13、compliance-api +14（依赖同步） |
| commercial-service | `sha256:e5c1ee6b0d037c4c899f43bf3583dcfdea8175f408f661d6f51580a36d3dd28c` | `1dae75ab534567c3` | −1、+86、~61：commercial-core +12 / ~32 / −1（`UnavailableCheckoutQualificationClient`）；commercial-api +6 / ~13；client-master-api +18 / ~12；**新依赖** compliance-api +50；app ~3；`application.yml`（`relationship-snapshot.enabled`） |
| acquiring-service | `sha256:e0a1f618a050e30b85059aa4214fea43bedeb35cf48438374d444e735e107299` | `d64644a67a9e7713` | +159、~27：acquiring-core +11 / ~23；**新依赖** commercial-api +145；app +3 / ~3；`application.yml`（commercial-service 客户端、`resolution-mode`） |
| risk-decision-service | `sha256:288b0ff6a607a8e82bbf7e7cd03023bcca9159fc72e599281ec2fda81fe28a88` | `0196699b7bac2fac` | ~3：`DecisionReleaseStaticValidatorImpl`、`RuleAstValidatorImpl`、`CheckoutPrepareEvaluator`（删除 PLATFORM）。risk-api 的源码改动只是注释，不影响字节码 |
| operations-console-bff | `sha256:feefe73f0aa35e556e5bf1b11da9144ac3d7414285740bf22d9cbe94f086e6af` | `818dc53305ffd646` | +60、~32：app classes +22 / ~6；`application.yml`（`/entity-admin`、`/qualification-admin`）；client-master-api +18 / ~12、commercial-api +6 / ~13、compliance-api +14 |
| operations-console-web | `sha256:4f44725eb26f7569b40719f29f48da7cc60f46c3c6bdd4d45ba0c44827662d3a` | dist 226 个文件 | 逻辑新增 `Qualifications`、`OperatingEntities`、`LocalPartners`、`masterData`（js + css）、`useAdminCommand`（js + css）；没有逻辑删除；其余变化是内容哈希改名，另有 `index.html`（[清单](./web-dist-diff-candidate.txt)）。依赖不变 |

- **第三方 JAR：** 6 个服务都是 0 改动，Foundation 也 0 改动（每份候选明细里只有本服务模块和 Owner api 模块的条目）。
- **镜像核对**（[image-verify.txt](./image-verify.txt)）：
  - 内容：候选镜像内 `/app/application.jar` = 构建产物，`health-probe/` = 运行镜像；ops-web 镜像内 `dist/` = 构建产物，`nginx.conf` = 现网。
  - 配置：Entrypoint / Cmd / User / WorkingDir / ExposedPorts / Healthcheck / StopSignal / Volumes / 平台（linux/arm64）与运行镜像相同。唯一例外是 compliance 的 Labels：现网镜像由 `compose build` 生成，多 3 个 `com.docker.compose.*` 标签。这 3 个标签不影响运行，容器标签由 compose 设置。
  - 镜像 Env：6 / 6 键（ops-web 7 / 7）逐键、逐值相同。

## 3. 配置

### 3.1 覆盖文件

- [`../compose-candidate.yaml`](../compose-candidate.yaml) 与 [`../compose-rollback.yaml`](../compose-rollback.yaml)：7 个服务，都钉 sha256 镜像 ID。
- 回滚 ID 就是 7 个运行容器在 11:05Z 的 `.Image`。compliance 的回滚钉 `2f62a26a…`，不用 `:development`。

### 3.2 环境变量

**历史键扫描：** 扫描主工作区 `requirements/**/compose-*.y*ml`（含 `!reset` 标签的文件按普通映射读入），这 7 个服务出现过的 environment 键如下：
- **commercial-service：** 19 个键只存在于历史覆盖文件 `requirements/20261020/commercial-运营控制台完善/release/compose-candidate.yaml`，不在主 compose 里。它们是：
  - `COMMERCIAL_RABBITMQ_*` 5 个、`COMMERCIAL_REDIS_*` 3 个；
  - `COMMERCIAL_SNOWFLAKE_REGION_ID` / `_WORKER_ID`（=17）；
  - `COMMERCIAL_COMPENSATION_INSTANCE_ID`、`COMMERCIAL_XXL_JOB_ENABLED`；
  - `XXL_JOB_*` 7 个。

  该文件另加了 `depends_on` rabbitmq / redis。主 compose 的 commercial 段仍是旧形状，缺这些键时 commercial 因 Snowflake 变量缺失无法启动。**候选与回滚都原样带上这 19 个键与 depends_on**（密钥行是 `${…}` 变量引用，逐字复制）。
- **acquiring 的 `PAYTECH_INSTANCE_ID`、ops-bff 的 `OPERATIONS_{LEDGER,ACCOUNT,BILLING,RECONCILIATION}_BASE_URL`：** 主 compose 已包含。
- **compliance / client-master / risk-decision / ops-web：** 历史覆盖文件里没有额外的键。

**渲染比较**（[env-compare.txt](./env-compare.txt)）：`compose.sh -f <文件> config --format json` 渲染后，期望 Env = 镜像 Env 叠加 compose Env，与运行容器 `Config.Env` 逐键、逐值比较。

| 服务 | compose 键 / 运行键 | 候选 | 回滚 |
|---|---|---|---|
| compliance | 33 / 39 | 0 缺、0 多、0 不同 | 同左 |
| commercial | 31（回滚 30）/ 36 | 0 缺、0 不同；只多 `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED` | 0 / 0 / 0 |
| client-master | 34 / 40 | 0 / 0 / 0 | 0 / 0 / 0 |
| acquiring | 37（回滚 36）/ 42 | 0 缺、0 不同；只多 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE` | 0 / 0 / 0 |
| risk-decision | 27 / 33 | 0 / 0 / 0 | 0 / 0 / 0 |
| ops-bff | 14 / 20 | 0 / 0 / 0 | 0 / 0 / 0 |
| ops-web | 0 / 7 | 0 / 0 / 0 | 0 / 0 / 0 |

以下各项全部与运行容器相同：
- 挂载（这 7 个服务都没有 bind 挂载）；
- 固定 IP；
- mem_limit；
- entrypoint（jvm-medium / jvm-small）、healthcheck、User、只读根目录。

### 3.3 新增变量（只在候选文件里）

| 服务 | 变量 | 部署值 | 说明 |
|---|---|---|---|
| acquiring-service | `ACQUIRING_COMMERCIAL_RESOLUTION_MODE` | `MOCK` | 缺省也是 MOCK（`matchIfMissing`），显式写出便于切 REAL 时只改这一个值；切 REAL 另行决定（technique 2.4 第 6 步门禁） |
| commercial-service | `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED` | `"false"` | 缺省也是 false；快照只记录，不生效（`effectiveModelVersion=0`） |

回滚文件不含这两个键，与运行容器现状一致；旧镜像也不读它们。

### 3.4 新增的服务间调用（不需要新的 compose 配置）

| 调用 | 方式 | mor-test 现状（只读核对） |
|---|---|---|
| acquiring → commercial | `lb://commercial-service`（REAL 时才注册客户端） | commercial 日志 10-22 05:37:49Z「DEFAULT_GROUP commercial-service 172.30.73.34:18108 register finished」（05:36 首次注册失败后重试成功）。technique 3.7「commercial 在 Nacos 注册」**已确认**，不需要直连 base-url |
| commercial → compliance、commercial → client-master | 服务发现名 `compliance-service` / `client-master-service` | compliance 05:37:38Z 注册到 DEFAULT_GROUP 172.30.73.32:18112；client-master 10-23 05:23:38Z 注册 172.30.73.26:18101 |
| ops-bff → compliance / client-master Admin | `EntityQualificationClientConfiguration` 复用 `ClientReviewClientConfiguration.factory`，按服务发现名调用；测试直连属性只用于 auto 测试 | **ops-bff 不需要新的 base-url 变量**（与 ledger / account / billing / reconciliation 的直连不同，这两个服务都在 Nacos） |
| 五个相关容器的发现参数 | — | acquiring、commercial、compliance、client-master、ops-bff 的 `SPRING_CLOUD_NACOS_DISCOVERY_GROUP`、`NACOS_NAMESPACE`、`NACOS_SERVER_ADDR` 取值都相同（只比较是否相等） |

端口、网络与 Gateway 都不需要改：新接口都在已有监听端口上，外部经块 137 的路由进入 ops-bff。

### 3.5 Snowflake / 发号器 Worker

运行容器中的取值：

| Worker | 服务 |
|---|---|
| 17 | commercial |
| 18 | tax |
| 19 | reconciliation |
| 20 | platform（认证） |
| 21 | payment |
| 22 | card |
| 23 | ledger |
| 24 | vault |
| 25 | risk-decision |
| **26** | **client-master（`ONBOARDING_ID_WORKER`）、channel-connector** |
| 27 | counterparty |
| 28 | acquiring |
| 29 | compliance |
| 30 | billing |
| 31 | account |

- 本次涉及的 compliance 29、commercial 17、acquiring 28、risk-decision 25 不冲突，候选文件也不改任何 Worker。
- **既有重号：** client-master 与 channel-connector 都是 26，region 也都是 0。两者写各自的库，ID 不跨库作主键，本次不处理。本需求会让 client-master 用这个发号器为新表（`t_party_role` 等）发号，仍只写 `mor_client_master`。建议另立事项改号（不阻塞）。

## 4. 发现与待决

1. **compliance 运行容器按 `:development` tag 启动**，`RestartCount` 36。commercial 的 `RestartCount` 也是 25。
   - 两者的日志都有 10-22 05:36 Nacos 注册失败的记录，05:37 起健康至今。据此推测重启发生在 Nacos 不可用期间，具体时间没有核实。
   - 回滚只认 `2f62a26a…`。阶段 7 换 compliance 后，主 compose 的 tag 仍指向旧镜像；不要再用不带覆盖文件的 `compose up` 重建 compliance。
2. **commercial 的 19 个键只存在于历史覆盖文件：** 本包已带上。若以后有人只用主 compose 重建 commercial，会启动失败。建议另立事项把这些键并入 `test-environment/compose.applications.yaml`（不在本阶段范围）。
3. **跨模块随带变化**（第 1.2 节）：随本次发布进入 compliance / client-master / commercial / acquiring 的有 Foundation messaging 事件目录、http-client-support 2 个类、platform-support-api 只增接口，以及 commercial 的 `ClientFileTarget*` 移除。它们都已提交，并且已在 Platform / ops-bff 等其他服务的现网中运行。
4. **候选的有效前提：** 开发目录 `Code-MoR-relsnap` 若在 11:23Z 之后再有改动，本候选作废，须按第 1.1 节重建（补丁 SHA 会变化）。构建 worktree `Code-MoR-relsnap-rel` 与 7 个镜像保留到发布完成。
5. **未在本报告范围内**（由其他阶段 6 任务负责）：
   - DDL / 数据 SQL 及其离线演练；
   - 块 137 Gateway 快照离线编译；
   - mor-test 中 PLATFORM 来源行清单、AE001 根 BCR、卖方披露 AE001 × US × en；
   - 多语言命名空间。
