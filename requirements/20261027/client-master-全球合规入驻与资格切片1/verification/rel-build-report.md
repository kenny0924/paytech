# rel-build 报告：发布镜像、候选与回滚覆盖（2026-10-27）

> 范围：基线漂移核对、6 个镜像构建与核对、候选 / 回滚覆盖文件、本机 e2e 制品暂存、runbook 镜像回填。
> 未提交、未推送；未启停或替换任何 mor-test / 本机容器或进程；对运行容器只做 `docker inspect`、`docker exec ... cat / tar / printenv`。

## 1. 基线漂移核对（结论：6 个服务均无漂移，可发布）

方法：在干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-onboard-base`（ea0272f3，无改动）离线构建 4 个 Java exec JAR 与 2 个 Web dist，与运行容器中的 `/app/application.jar` / nginx 目录对比。JAR 对比递归展开 `BOOT-INF/lib/paytech-*.jar`，按条目 CRC 比较（忽略 MANIFEST / classpath.idx / layers.idx）。

| 服务 | 运行镜像 | 现网 vs ea0272f3 | 结论 |
|---|---|---|---|
| client-master-service | `eeed4e63…`（relsnap-20261025a） | 1674 / 1674 条目，0 差异 | 无漂移 |
| compliance-service | `ca158545…`（relsnap-20261025a） | 681 / 681 条目，0 差异 | 无漂移 |
| operations-console-bff | `feefe73f…`（relsnap-20261025a） | 2728 / 2728 条目，0 差异 | 无漂移 |
| client-console-bff | `9033b8fa…`（i18n-20261023a） | 现网 932、基线 965：现网独有 0；基线多 33、变 13，全部在依赖合同库 `paytech-client-master-api`（entityadmin、checkout 候选）、`paytech-compliance-api`（资格合同）、`paytech-platform-support-api`（`InternalFileApi`），BFF 自身类 0 差异 | 现网落后于 ea0272f3（已提交未上线的合同库变化，块 137 / 文件上传），无现网独有内容；新镜像会顺带上这些合同库 |
| client-console-web | `280a2f38…`（i18n-20261023a） | dist 3 / 3 文件逐字节相同 | 无漂移 |
| operations-console-web | `4f44725e…`（relsnap-20261025a） | dist 226 / 226 文件逐字节相同 | 无漂移 |

### 1.1 channel-connector

onboard worktree 中 `paytech/channel-connector` 无任何改动（`git status` 为空）。沿用运行镜像 `sha256:23679d45…`，只加两个出站放行键（infra-dev 草稿）。

### 1.2 Platform

主会话确认 `console-resource-capabilities.json` 属于本切片范围（S-11），随本切片上线。漂移核对：现网 `sha256:4859efd7…`（fileupload-20261024a）JAR 与 ea0272f3 构建逐条目相同（3095 / 3095，0 差异）。镜像构建与核对见第 7 节。

## 2. 构建与镜像

源快照：onboard worktree 有 dev agent 在跑测试（`mvn ... test`），在该目录 `clean package` 会清掉其 target，所以在独立 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-onboard-rel` 构建：ea0272f3 + onboard 全部未提交改动（修改、未跟踪、删除，含暂存删除）。复制后，两边全部非忽略文件的 sha1 清单完全一致。

- 首次快照 2026-10-27T02:45Z（清单指纹 d93805c216bb8b7a），用于构建 4 个 Java JAR 与 client-web。
- 构建后 ops-fe 又改了 ops-web 5 个文件（onboardingConfig 3 个、client-review 文案 2 个）；client-master 只改了测试代码（不进 exec JAR）。02:52Z 重新全量同步（指纹 **6f985f45736395e6**，与 onboard 逐文件一致），只重建 ops-web。首次 ops-web 镜像 `sha256:a143f726…` 作废，不要使用。

构建：JDK 25，`./mvnw -B -o -DskipTests -pl <4 个 app> -am clean package`，私有仓库 `~/.m3-onboard`（离线）；Web 用 `npm ci && npm run build`。镜像使用 build.sh / compose 同一套 Dockerfile 与参数（`test-environment/java/Dockerfile` + `JAR_FILE=<service>.jar`，health-probe 用 `javac --release 25` 从同一源码编译，与现网镜像内 5 个 class 大小相同；Web 用各自的 `Dockerfile`）。没有运行 build.sh，`:development` 标签未改动。

| 服务 | 镜像 ID（tag `onboard-20261027a`） | 制品 SHA-256 | 镜像内核对 |
|---|---|---|---|
| client-master-service（最终，06:32Z 重建，见 7.18） | `sha256:0af5b79239ef791cf17400fc6f479ec21089a65d9c03af6dd2c1068f915bec33` | JAR `f6bc8f3b4031976ece5f84d26350c35146d2ff49f7cb86e0ffd8d55babf440ba` | `/app/application.jar` 相同 |
| compliance-service | `sha256:f5a9b13a0f0551d407f87e9efba138a45faafd7b37556b6d75f8a2a5f40a1b81` | JAR `2264afa0d7d4b60c1b0c0092e265d3b903324e03e69e79150c2e8d4daf1acc3a` | 相同 |
| client-console-bff | `sha256:83386ff51561cb26311571a7c952e39a762da3a1266c6fd477bd0b30c24323d4` | JAR `dcd2ccad2803037f39853510b94c2e7f2a9fe103940f18afbeb61969ca0cffc7` | 相同 |
| operations-console-bff | `sha256:c5ed58df68333390bc7c99eedf737e73d4e54d9c02bcd6da54d58089cb332f44` | JAR `895edb6ac1e44e1f2992138d5786729ef6f17fb60982709a9c51b65dae2869e7` | 相同 |
| client-console-web（最终，06:30Z 重建，见 7.17） | `sha256:34d3d6e303fd7f62fa7da825599391a2f34ba569e4bbf92a8839d5859a133998` | dist 11 个文件 | 11 / 11 逐文件相同 |
| operations-console-web（最终，06:26Z 重建，见 7.16） | `sha256:88ab111a7fdabeea6893c307f36a32baec8082b74b218fc2400f12b2cd031872` | dist 235 个文件 | 235 / 235 逐文件相同 |

新代码存在性：
- client-master：`paytech-client-master-api` 含 24 个 `com/paytech/client/master/api/onboarding/` 类；core 含 50 个 onboarding 类。相对 ea0272f3：新增 223、删除 24、变更 124 个条目。
- compliance：`ProgramResolutionApi`、`ProgramResolutionResponseBody` 和 core `ProgramResolutionResponse*`；新增 436、删除 21、变更 104。
- client-bff：新增 72、删除 14（form-options 等）、变更 49；ops-bff：新增 86、删除 12、变更 53。
- client-web：入口 JS 含 `onboardingSchema`；ops-web：入口 JS 含 `onboarding/codes` 路由。

## 3. 候选与回滚覆盖（`release/`）

- 分服务：`compose-candidate.<service>.yaml`、`compose-rollback.<service>.yaml`，覆盖 client-master-service、compliance-service、client-console-bff、operations-console-bff、client-console-web、operations-console-web。
- 合并（runbook P-2 / 第 1 节 `$O` / `$RB`）：`compose-candidate.yaml` = 6 个服务 + infra-dev 的 channel-connector 草稿（同一镜像，加 `ALLOWEDSCHEMES=https,http`、`TRUSTEDNETWORKS=172.30.73.43/32`，带 3DS 覆盖文件的 5 个 RabbitMQ 键）；`compose-rollback.yaml` = 6 个现网镜像 + channel-connector 去掉两个键。
- screening-mock 不在覆盖文件中，用 `test-environment/compose.screening-mock.yaml`（仅 onboard worktree 中有；`SCREENING_MOCK_SCRIPT` 见 P-9；固定 IP 172.30.73.43）。

env 来源：运行容器 `printenv`、主 compose、所有历史 `requirements/*/release/compose-*.yaml`（主工作区、onboard、relsnap、fileupload worktree）。主 compose 已包含全部现网键；历史覆盖文件中只有 ops-bff 的 4 个 `OPERATIONS_*_BASE_URL`，主 compose 中也有。覆盖文件显式写出全部非敏感键；敏感值只写主 compose 中同一个 `${...}` 引用。

渲染核对：用 `compose.sh -f <文件> config --format json` 只读渲染，与运行容器 `printenv` 逐键、逐值比较（只输出键名，不输出值）：

| 文件 | client-master | compliance | client-bff | ops-bff | 两个 Web | channel-connector |
|---|---|---|---|---|---|---|
| 候选（合并与分服务） | 34 / 34 相同 | 32 / 33：只少 `ONBOARDING_MATERIALS_ENABLED`（P-3，用 `!reset null` 删除，已验证生效） | 10 / 10 相同 | 14 / 14 相同（含 4 个 BASE_URL） | 无 env | 32 + 2 个新键，其余相同 |
| 回滚（合并与分服务） | 34 / 34 | 33 / 33（保留 `ONBOARDING_MATERIALS_ENABLED=true`） | 10 / 10 | 14 / 14 | — | 32 / 32 |

compliance 新键：`ONBOARDING_REVIEW_ENABLED=true` 保留（现网已有）；`COMPLIANCE_SCREENING_CHANNEL_CODE` 不设（缺省 `S_SCREENING_MOCK`）。基线与发布源码之间，4 个服务的 `application*.yml` 只多出这一个占位符（带缺省值），没有新的必填 env。

## 4. 本机 e2e 制品（只暂存，未替换）

- `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/jars/paytech-{client-master,compliance,client-console-bff,operations-console-bff}-app-exec.onboard-20261027a.jar`（与上表 JAR 逐字节相同；没有覆盖已有文件）。
- `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/staging/onboard-20261027a/{client-console-web,operations-console-web}/`（与镜像 dist 相同；ops-web 为 02:52Z 重建版本）。
- 注意：本机 jars 目录没有 client-console-bff 的现用 JAR（本机 client-bff 运行方式不同）；本机 ops-web 当前由 Vite 开发服务器（`:18301`）运行，替换方式由主会话决定。

## 5. runbook 回填

`runbook.md` 中 ⑥ ⑧ ⑫ ⑬ 的 6 个 `sha256:<TBD-…>` 已替换为上表 ID；P-1 / P-2 状态已更新。

## 6. 待主会话决定 / 注意

1. **Platform 镜像会额外带上 `catalog/events.yaml` 的变化**（第 7 节）：这是本切片 Foundation 事件目录的改动，6 个服务镜像也都带有它；Platform 不生产也不消费这两个事件。另外，能力清单中有非本切片的组件（文件上传、资格、OE / LP、风控 3 个、Vault 6 个、工作流「我发起的」），会一起上线。
2. **源码仍在变化**：如果 dev agent 在 02:52Z 之后又改了生产代码或 Web，需要重新同步并重建受影响的服务（`Code-MoR-onboard-rel` 保留，可以复用）。阶段 7 前可用指纹核对：在两个 worktree 分别对 `git ls-files --cached --others --exclude-standard` 的全部文件做 sha1，比较两份清单。
3. 两个临时 worktree 仍保留：`/Users/kenny/Desktop/Workspace/Code-MoR-onboard-base`（ea0272f3 基线构建）、`/Users/kenny/Desktop/Workspace/Code-MoR-onboard-rel`（发布源快照）。发布完成后由主会话决定是否删除。

## 7. 追加（2026-10-27 02:55Z 起）

### 7.1 client-master 遗留 schema_id 空值修复（第一次核对，已被 7.3 取代）

02:54Z 核对时，`OnboardingPackServiceImpl` 的 409 修复已在首次镜像 `7a6dd0e0…` 中，当时判断无需重建。之后 cm-dev 又修改了 `GetApplicationRequirementsProcessor`，见 7.3。

### 7.2 Platform 镜像

- 源：`Code-MoR-onboard-rel`（02:54Z 同步后），`-pl platform-support/paytech-platform-support-app -am`，同一 Dockerfile 与 health-probe。
- 镜像 `sha256:cad6dc14cc7c2c7687cc7aacdaf0a77804e529c6f492adacab56ba1045659ba2`（tag `onboard-20261027a`），JAR SHA-256 `478c53dea4ac7f92afa6fc46a97fad460abdd7a6144962c1a600b30eb04a6f6c`，从镜像中读出的 `/app/application.jar` 与构建产物相同；User / Entrypoint 与现网镜像相同。
- **镜像 JAR 与现网 JAR 的差异：3095 / 3095 条目，只有 2 个资源不同**：
  1. `paytech-platform-support-access-control.jar!/operations/console-resource-capabilities.json`（S-11 目标）；
  2. `paytech-messaging.jar!/catalog/events.yaml`：本切片的 Foundation 事件目录改动（ClientReviewDecided 升 v2，新增 QualificationChanged）。它是 onboard 改动的一部分，`YamlEventCatalog` 会在运行时加载；Platform 不生产也不消费这两个事件。如果要求严格只差 JSON，就需要用「ea0272f3 的 Foundation + 新 JSON」的混合源构建，这与其他 6 个镜像的源不一致，所以没有这样做。如主会话要求，可以重建。
- 覆盖文件：`release/compose-candidate.platform-support-service.yaml` / `compose-rollback.platform-support-service.yaml` 只换镜像，并已并入合并文件。主 compose 的 platform 段（含 env_file `private/platform.env`、`private/client-onboarding.env`）已包含现网全部 53 个键，包括 `PLATFORM_SUPPORT_RABBITMQ_*`、`PLATFORM_SUPPORT_INTERNAL_AUTH_MODE`、`DATAQUERY_HTTP_CALLERS_0`、`WEBHOOK_*`、`SPRING_APPLICATION_JSON`；上一个 Platform 覆盖文件（fileupload）也只换镜像。渲染与运行容器 printenv 逐键、逐值比较：候选 53 / 53、回滚 53 / 53，无缺失、多余或值差异。
- 本机 e2e：`local-environment/e2e/jars/paytech-platform-support-app-exec.onboard-20261027a.jar`（`478c53de…`，新文件，未替换）。
- runbook：新增 ⑪b（块 138 休眠注册之后、激活之前换 Platform），③ 门禁改用候选 Platform JAR，第 5 节回滚表新增 Platform 行，P-1 改为 7 个镜像。

### 7.3 client-master 重建（03:01Z，已被 7.5 取代）

- 同步结果：生产代码新增 2 个改动：`GetApplicationRequirementsProcessor`（schema_id 为空时 CM-7 返回 200、ready=false、items 为空）和 `ClientOnboardingApplicationApi`（只改了注释，编译出的 class 与之前逐字节相同）；另有 1 个测试文件 `LegacyApplicationReadTest`。
- 4 个 Java 服务全部重建后与已打包的 JAR 对比：client-master 只有 `GetApplicationRequirementsProcessor.class` 不同；compliance、client-console-bff、operations-console-bff 都是 0 差异，**它们的镜像 ID 不变**。
- client-master 新镜像 `sha256:6c6f6450ef4ca358b4bb4c0be720e0c0495eb049281bd5a4679fee9b6d9e5645`，JAR `adcfc3468739a6f0…`，与镜像内 JAR 相同。已替换到合并候选文件、分服务候选文件和 runbook ⑥，没有残留旧 ID；渲染后 env 与现网对比为 34 / 34。作废镜像：`7a6dd0e0…`。
- 本机 e2e：`paytech-client-master-app-exec.onboard-20261027a.jar` 是本次发布暂存的文件，未被任何进程使用，已通过临时文件改名替换为新 JAR（`adcfc346…`）。

### 7.4 operations-console-web 重建（ops-fe P1 修复，03:18Z；已被 7.7 取代）

- ops-fe 的修复：决定返回 422 NOT_READY 时重新拉取案件，并在决定对话框中列出 approvalBlockers。只改了 `src/clientReview/{clientReview.ts, reviewParts.tsx, ReviewDecision.tsx, clientReview.test.ts}`，以及 en / zh-CN `client-review.json`（新增 2 个 key）。同步时其余改动都是文档 / 验收资料，没有 Java 生产代码变化，4 个 Java 镜像与 Platform 镜像不变。
- 新镜像 `sha256:a6b61d812e57b8b02bf3621de56ac5f2ef0113512cbc3afb1b85f8f3f6a82c95`，dist 234 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID。作废镜像：`1ef3add6…`、`a143f726…`。
- 本机暂存 `local-environment/e2e/staging/onboard-20261027a/operations-console-web/` 已更新为该 dist。
- 语言包：client-review 命名空间新增 2 个 key，阶段 7 的 ⑬b 必须用最终 Web 源（`Code-MoR-onboard-rel` 或 onboard）重跑计划，计划中的新增数会比 P-7 记录的多 2。

### 7.5 client-master 重建（cm-dev AC-07 / B3 修复，03:52Z；镜像已被 7.6 取代，依赖说明仍有效）

- 同步结果：生产代码改动 4 个文件，全部在 client-master core / app：新增 `ClientReviewResultClient(+Impl)`，修改 `GetApplicationRequirementsProcessor` 和 `ClientSubmissionConfiguration`；另有 3 个测试文件。API 模块和 `application*.yml` 都没有变化，所以不需要新增 env；BFF、compliance、Platform 和两个 Web 的镜像不变。
- 与 7.3 的 JAR 相比：新增 3 个条目（`ClientReviewResultClient`、`$ReturnedError`、`ClientReviewResultClientImpl`），改动 2 个（`ClientSubmissionConfiguration`、`GetApplicationRequirementsProcessor`）。
- 新镜像 `sha256:595d20bc178dbeb81208dcafe5eab71ca599a14bcad7aa875b2fc4a68c5aaebf`，JAR `cf4ac2cc3fc77202…`，与镜像内 JAR 相同。已替换到合并候选文件、分服务候选文件和 runbook ⑥，没有残留旧 ID；env 渲染对比 34 / 34。作废镜像：`6c6f6450…`、`7a6dd0e0…`。
- 本机 e2e：`paytech-client-master-app-exec.onboard-20261027a.jar` 没有进程打开（lsof 为空），已通过临时文件改名替换为 `cf4ac2cc…`。
- **新增运行时依赖**：申请处于 RETURNED 状态时，CM-7 以 `client-master-service` 身份调用 compliance CP-4（`GET /client-onboarding-review/v1/applications/{id}/result`，经服务发现）。runbook 中 client-master（⑥）先于 compliance（⑧）上线，两步之间如果有 RETURNED 申请，CM-7 会调用旧 compliance。不过 P-8 的清理会删除全部在途（PENDING）申请，窗口内不应出现 RETURNED 申请。请主会话确认该顺序是否接受，或把 CM-7 的 RETURNED 验证放在 ⑧ 之后。

### 7.6 client-master 重建（应用级审核错误归属，03:55Z；已上线，已被 7.13 取代）

- cm-dev 在 B3 之上再改一处：`subjectPartyId` 为空（应用级）的审核错误原先被丢弃，现在挂到申请的主 party 上。同步到的生产代码改动是 client-master core 的 3 个类，外加 `paytech-compliance-api` 的 `ClientReviewResultApi`，后者只改了 1 行注释。
- 4 个 Java 服务与 Platform 全部重建后对比：client-master 有 3 个 class 不同（`ClientReviewResultClient$ReturnedError`、`ClientReviewResultClientImpl`、`GetApplicationRequirementsProcessor`）；compliance、client-bff、ops-bff、Platform 都是 0 差异，**它们的镜像 ID 不变**。
- 新镜像 `sha256:997f8bf81f8ad85e2ed8dc0b5d757e0b4d763ee2035863b3d6cd25cbbf27985c`，JAR `5a23108375fc9588…`，与镜像内 JAR 相同。已替换到合并候选文件、分服务候选文件和 runbook ⑥，没有残留旧 ID；env 渲染对比 34 / 34。作废镜像：`595d20bc…`、`6c6f6450…`、`7a6dd0e0…`。
- 本机 e2e JAR 没有进程打开，已通过临时文件改名替换。

### 7.7 operations-console-web 重建（已被 7.9 取代）（命名空间 onboardingSchema 改为 onboarding-schema，04:03Z）

- 原因：Platform 不接受驼峰形式的命名空间编码。04:02Z 同步到的改动只涉及两个 Web 的源码与 release / verification 文档，没有 Java 生产代码改动（代码冻结期内无意外 diff）。
- 新镜像 `sha256:4ee66de83973f273bab1bda14dfe9ef4dea97a7a4869eb776e23fe88f157a99e`，dist 234 个文件，与镜像内逐文件相同；bundle 中 `onboarding-schema` 命中 3 个文件，旧的 `"onboardingSchema"` 命中 0 个。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID。作废镜像：`a6b61d81…`、`1ef3add6…`、`a143f726…`。本机暂存 dist 已更新。
- client-console-web 的同类改动见 7.8。

### 7.8 client-console-web 重建（命名空间改名，04:04Z；已上线，已被 7.12 取代）

- client-fe 确认后，04:03Z 再次同步：没有新的源码改动（client-web 的改名文件在 04:02Z 已同步），也没有任何 Java 或其他生产代码改动。
- 新镜像 `sha256:4659a92ad820645c233221a560377835491b9a15ebc9e02af6524ad5cf46f09e`，dist 11 个文件，与镜像内逐文件相同。bundle 中 `onboardingSchema.` 只剩两处，都是有意保留的：把后端 labelKey 前缀映射为 `onboarding-schema.` 的转换函数，以及经该函数转换的 relationType labelKey。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID。作废镜像：`15557b63…`。本机暂存 dist 已更新。
- 语言包：两个应用都需要在 Platform 上先有 `onboarding-schema` 命名空间，再导入语言包；runbook P-7 / ⑬b 中的命名空间名应改为 `onboarding-schema`。

### 7.9 operations-console-web 补丁（采集 Schema 页签 organizationTypeCode '*'，05:33Z；已上线，已被 7.10 取代）

- 修复内容：`decodeSchemas` 把 organizationTypeCode `'*'` 解码为 null，页面显示为「默认（全部组织类型）」。改动文件为 `src/onboardingConfig/{onboardingConfig.ts, OnboardingConfigView.tsx, onboardingConfig.test.ts}`，以及 en / zh-CN `client-review.json`（新增 `config.defaultOrganizationType`，删除未使用的 `config.allOrganizationTypes`）。05:32Z 同步时其余改动都是测试（client-web `countrypack.test.ts`，不进 dist）和文档，没有 Java 生产代码改动。
- 新镜像 `sha256:4d4c11308b6d23a91b557748b030354303cacd6c894ea3799eaaf08bd4ad53da`，dist 234 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 当前现网（05:32Z docker inspect）：本切片 7 个镜像都已上线，ops-web 运行的是 `4ee66de8…`。补丁回滚使用新文件 `release/compose-rollback-patch.operations-console-web.yaml`（回到 4ee66de8）；整切片回滚仍用 `compose-rollback.yaml`。
- 语言包：client-review 新增 1 个 key、删除 1 个 key，下次导入 operations-console 语言包时一并处理。

### 7.10 operations-console-web 补丁（工作台 P0 / P1 修复，05:47Z；已上线，已被 7.11 取代）

- 修复内容：P0 工作台解码改为 documents 使用 fileAddress（fileId 由它推导），industries 使用 labelKey 并经 onboarding-schema 翻译；P1 值分组与字段的标签增加可读回退，审核列表增加辖区过滤，并修复 1440px 布局。client-review 新增 23 个 key。05:46Z 同步时，Web 之外只有 requirements 下的文档与 ui-smoke 证据文件，没有 Java 或其他生产代码改动。
- 新镜像 `sha256:35b61f12ff26eddfa4d2581484124a98f583120891749b61c21189406ff8c1cb`，dist 235 个文件，与镜像内逐文件相同；测试专用的 `uiSmokeBodies.json` / `uiSmokeReplay.test.ts` 没有进入 dist。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 当前现网 ops-web 是 `4d4c1130…`（7.9 已上线）。`compose-rollback-patch.operations-console-web.yaml` 已改为回到 4d4c1130。
- 语言包：operations-console 的 client-review 命名空间需要导入新增的 23 个 key，以及 7.9 的 +1 / −1。

### 7.11 operations-console-web 补丁（ui-smoke 第二批，06:10Z；已上线，已被 7.16 取代）

- 修复内容：入驻码过期 / 用尽时显示为 EXPIRED，签发人标注为账号 ID；工作台与 Client 详情翻译目录、证件类型、组织类型和属性值；修复业务信息网格 CSS；onboarding-schema 镜像扩展到 10 类（206 个 key）；历史轮次显示提示并链接到当前案件。06:09Z 同步时，Web 之外只有 requirements 文档 / 证据，没有 Java 或其他生产代码改动。
- 新镜像 `sha256:1274fefe07968596d77c7f48e22d94e9ff7bdc423b15a0729baae104375105a6`，dist 235 个文件，与镜像内逐文件相同；uiSmoke 测试文件没有进入 dist。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 当前现网 ops-web 是 `35b61f12…`（7.10 已上线）。`compose-rollback-patch.operations-console-web.yaml` 已改为回到 35b61f12。
- 语言包：operations-console 的 onboarding-schema 命名空间从 155 增至 206 个 key；client-review 再新增 3 个（累计在 7.9 的 +1 / −1 与 7.10 的 23 之上）。

### 7.12 client-console-web 补丁（保存体验修复，06:14Z；已上线，已被 7.14 取代）

- 修复内容：自动保存串行化（800 ms 去抖、429 退避），保存失败显示「未保存 · 重试」，错误文案本地化，人员抽屉显示标签，不再发送 address.addressFormatCode。06:13Z 同步。
- 新镜像 `sha256:4aaecccb035a4de03a8bebeb886bb732b023b747019ba95dc7bf75ff483f044d`，dist 11 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 现网 client-web 是 `4659a92a…`。补丁回滚使用新文件 `release/compose-rollback-patch.client-console-web.yaml`。
- **代码冻结期意外 diff，未构建**：同一次同步带入了 client-master 生产代码改动：`ClientDraftPatch.java`（白名单新增 `address.addressFormatCode` TEXT 32）和 `ClientDraftPreparer.java`（diff 为 +82 / −26），修改时间都是 06:11:10Z，另有测试 `DraftAddressFormatTest`、`OnboardingTestMapper`。这些改动没有通知，按主会话要求先报告。client-master 镜像仍是 `997f8bf8…`，冻结副本中已包含这两个文件，但没有重建。

### 7.13 client-master 补丁（P0 地址草稿 address.addressFormatCode，06:16Z；已上线，已被 7.18 取代）

- 主会话把该改动认定为冻结期例外（7.12 报告的意外 diff 即此修复），cm-dev 通知完成，136 个测试通过后构建。06:15Z 同步时，生产代码与 06:13Z 一致，只多了测试 `DraftAddressFormatTest`。
- 与 997f8bf8 的 JAR 相比，只有 4 个 class 不同：`ClientDraftPatch`、`ClientDraftPreparer`、`ClientDraftPreparer$1`、`ClientDraftPreparer$PendingIdentifier`。API 模块没有变化，其他镜像不变。
- 新镜像 `sha256:ba3f136f1a5940bbe388de11f1cd5fc7190c7a8f8a83767de4fb729978323bd9`，JAR `1e3e2ceb…`，与镜像内 JAR 相同。已替换到合并候选文件、分服务候选文件和 runbook ⑥，没有残留旧 ID；env 渲染对比 34 / 34。本机 e2e JAR 已通过临时文件改名替换。
- 现网 client-master 是 `997f8bf8…`。补丁回滚使用新文件 `release/compose-rollback-patch.client-master-service.yaml`（回到 997f8bf8，env 渲染对比 34 / 34）。

### 7.14 client-console-web 补丁（外国地址格式，06:18Z；已上线，已被 7.15 取代）

- 修复内容：地址国家不是辖区国家时，表单改用 form-schema 的 `fallbackAddressFormat`（GENERIC），切换布局并清空不适用的组件。06:17Z 同步时，Web 之外只有 client-master 的 2 个测试文件，没有生产代码改动。后端字段核对：现网 client-master（ba3f136f）与 client-bff（83386ff5）的 API 类都已包含 `fallbackAddressFormat`，不需要改后端。
- 新镜像 `sha256:9f85e61cb663dc328dfefbf4d1ff72d9462f9a60876a552c4f49b8161bac456f`，dist 11 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 现网 client-web 是 `4aaecccb…`。`compose-rollback-patch.client-console-web.yaml` 已改为回到 4aaecccb。

### 7.15 client-console-web 补丁（ui-smoke 第二批，06:23Z；已上线，已被 7.17 取代）

- 修复内容：被 422 拒绝的字段做保存隔离，每页只 PATCH 本页字段；390px 顶栏截断与移动端撤回操作；zh-CN 列表分隔符；仍在校验的上传在后台继续并完成挂接，材料步骤等上传完成后才能离开。06:22Z 同步时只有 client-web 改动，没有 Java 或其他生产代码改动。
- 新镜像 `sha256:14690d94f97caffee856070358174a58d5499b772965fd8165231956bc0d4c00`，dist 11 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 现网 client-web 是 `9f85e61c…`。`compose-rollback-patch.client-console-web.yaml` 已改为回到 9f85e61c。

### 7.16 operations-console-web 补丁（ui-smoke 第三批，06:26Z）

- 修复内容：Client 详情显示责任 PayFac 名称（取自 GET /client-admin/v1/responsible-partners，取不到时显示 ID）；国家码经 Intl.DisplayNames 显示本地化名称（Client 详情与工作台）。没有新的 i18n key。06:25Z 同步时只有 4 个 ops-web 文件改动，没有 Java 或其他生产代码改动。ops-bff 源码（`ClientAdminController`）已有 responsible-partners 端点，现网 ops-bff c5ed58df 与当时源码一致，不需要换 BFF。
- 新镜像 `sha256:88ab111a7fdabeea6893c307f36a32baec8082b74b218fc2400f12b2cd031872`，dist 235 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 现网 ops-web 是 `1274fefe…`。`compose-rollback-patch.operations-console-web.yaml` 已改为回到 1274fefe。

### 7.17 client-console-web 补丁（人员抽屉 422 映射，06:30Z）

- 修复内容：人员抽屉把 422 的 relation.* 与人员字段错误映射到对应控件；只有 INVALID_DRAFT 时显示本地化的通用消息；新关系发送 effectiveFrom = 当天。06:29Z 同步。
- 新镜像 `sha256:34d3d6e303fd7f62fa7da825599391a2f34ba569e4bbf92a8839d5859a133998`，dist 11 个文件，与镜像内逐文件相同。已替换到合并候选文件、分服务候选文件和 runbook ⑬，没有残留旧 ID；本机暂存 dist 已更新。
- 现网 client-web 是 `14690d94…`。`compose-rollback-patch.client-console-web.yaml` 已改为回到 14690d94。
- **代码冻结期意外 diff，未构建**：同一次同步带入了 client-master 生产代码 `ClientRelationValues.java`（06:28:54Z）和 `CreateClientPersonProcessor.java`（06:29:04Z），都没有通知，已报告主会话。client-master 镜像仍是 ba3f136f。

### 7.18 client-master 补丁（P0 #2 组织申请无法添加人员，06:32Z）

- 主会话认定为冻结期例外（7.17 报告的意外 diff）；cm-dev 通知完成，141 个测试通过后构建。修复内容：关系 effectiveFrom 缺省时取当天（UTC），人员 / 关系校验失败一律返回 errors[]（路径为 relation.<字段>）。06:31Z 同步时，生产代码只有 `CreateClientPersonProcessor` 比 06:29Z 更新，另有测试 `PeopleWebBodyTest`、`OnboardingTestMapper`。
- 与 ba3f136f 的 JAR 相比，只有 2 个 class 不同：`ClientRelationValues`、`CreateClientPersonProcessor`。API 模块没有变化，其他镜像不变。
- 新镜像 `sha256:0af5b79239ef791cf17400fc6f479ec21089a65d9c03af6dd2c1068f915bec33`，JAR `f6bc8f3b…`，与镜像内 JAR 相同。已替换到合并候选文件、分服务候选文件和 runbook ⑥，没有残留旧 ID；env 渲染对比 34 / 34。本机 e2e JAR 已通过临时文件改名替换。
- 现网 client-master 是 `ba3f136f…`；`compose-rollback-patch.client-master-service.yaml` 已改为回到 ba3f136f（env 渲染对比 34 / 34）。
