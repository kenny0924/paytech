# platform-support：币种主数据维护与读取缓存 · mor-test 发布包

阶段 6（2026-10-21）。只做了只读核对、干净 worktree 构建镜像和发布包准备，**没有改 mor-test 的任何容器、数据、队列或 XXL 登记**。阶段 7 须用户另行授权后按本清单执行，结果写第 8 节；最后一步删除 ledger / billing 副本（第 5 节第 9 步）再单独授权。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：** mor-test 的 platform-support、operations-console-bff、ledger、billing、operations-console-web 五个容器换成新镜像。`mor_platform.t_currency_spec` 加 `version` 列并改表注释；`mor_platform` 注册并激活块 125（54 行注册，激活 4 / 2 / 2 / 1）。ledger、billing 的 Compose 段新增 Platform 地址和对 platform-support-service 的健康依赖。验收后清理合成数据。最后一步删除 `mor_ledger`、`mor_billing` 的 `t_currency_spec` 副本。其余容器不动。

## 1. 源码与镜像

| 项 | 内容 |
|---|---|
| 源码 | worktree `Code-MoR-currency-rel`：main `2f5f37f7`（对账切片 1）合并商务集成头，加本需求补丁。Java 合并的是 `c216ff92`，Web 合并的是 `aa259ea8`（多 F4–F6，只改 9 个 `src/commercial` 文件）。商务会话 code-mor-98 的改动还没提交，但已发布在 mor-test 上，所以基线不能只取 HEAD，否则会回退商务功能。本需求补丁和 commit 候选 `Code-MoR-currency2`（`2f5f37f7` + 本需求）一致；ledger、billing、platform-support-api 两棵树逐文件相同 |
| Java 基线证明 | 同一合并树去掉本需求补丁后构建，四个服务的 JAR 与运行容器逐个比较类文件和资源，0 差异（`BOOT-INF/classes` 以及 `paytech-*` 嵌套 JAR 内全部条目；只排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引） |
| Web 基线证明 | 2026-10-21 03:58Z 商务发布 `capability-20261021e`（`139c157e…`）。阶段 6 在它之上重做了证明：`2f5f37f7` 合并 `aa259ea8` 后构建的 `dist/` 与运行容器 157 个文件逐字节一致，`nginx.conf` 也一致。候选的 Web 源码与这一基线相比，只差本需求的 7 个文件：`CurrencySpecs.tsx`、`currencySpecModel.ts`、`currencySpecModel.test.ts`、`ConfigurationStatusModal.tsx`、`ConsolePage.tsx`、`pageCapabilities.ts`、`configurationModel.test.ts` |
| 构建 | `./mvnw -B -o -DskipTests -pl <四个 app> -am clean package`（JDK 25）；Java 镜像用 `test-environment/java/Dockerfile`，`health-probe/` 从运行容器 `docker cp`；Web 先 `npm run build` 再 `docker build`（`operations-console-web/Dockerfile`）。没有改 `:development` tag 和 `test-environment/artifacts` |
| 测试证据 | 阶段 5（`Code-MoR-currency2`）：Platform 115、ledger 340、billing 99、arch-rules 62；ops-bff 全量、PDManer 与 Web 的结果以 delivery.md 为准。发布树 Web 1230/1230（含商务 F4–F6 测试）、构建通过 |

| 服务 | 运行（回滚目标） | 候选 | 与运行版逐类比较的差异 |
|---|---|---|---|
| platform-support-service | `79e33d43…`（JAR `51c15367…`） | `sha256:b2a63ff4eadc6bafa67d27f94e8adf0c13928dec3b0b67cd62fec9b51a2dec38`（JAR `90d10c2b…`） | 新增 21 个类：维护 Controller、Converter、三个 Processor、领域 Service、Context 和请求体。改动 23 项：`CurrencySpec` DataObject、Mapper（含 XML）、Repository、`CurrencySpecResponseBody`（+version）、`OperationsAuditFact`（`ConfigurationField` 加 `MINOR_UNIT`、`ROUNDING_MODE`，嵌套类随之重编）、`application.yml`、`console-resource-capabilities.json`（+`OPERATIONS_CURRENCY_SPECS`） |
| operations-console-bff | `9520a545…`（JAR `d3db3584…`） | `sha256:fddb1993f88f5712b13683836d4829253f6502ed3ee421e2ca17a5e68f9c7f1b`（JAR `044e43c1…`） | 新增：币种 Controller、Platform 币种 Client 及实现、合同转换、页面模型，以及 platform-support-api 的维护接口与请求体。删除：ledger-api 的两个币种响应类。改动：`FinanceSetupController`、`FinanceSetupClient(+Impl)`、`PlatformClientConfiguration`、`ConfigurationManagementRequestConfiguration`、`LedgerSetupQueryApi`、`application.yml`（元数据日志前缀 `/currency-admin`） |
| ledger-service | `b84f8f97…`（JAR `b984c18b…`） | `sha256:fa21520def8c319d32798d89d739fb752ae4fdcc27723df2bbb39f34a9a17053`（JAR `7d8d2466…`） | 新增：快照、Platform Client、`CurrencyRuleService`、刷新调度、健康指示器、配置。删除：`CurrencySpec` DataObject、Mapper、Repository、两个响应类。改动：9 个读币种的 Processor / Service、`LedgerSetupQueryController` / `Api`（去掉 `/currencies/v1`）、持久化配置、`application.yml` |
| billing-service | `b773d2cd…`（JAR `5a70aaaa…`） | `sha256:90d7401abdd689bee94e149880bf419890a8ede59bb0b5be5119a88cb47053e8`（JAR `72987591…`） | 同 ledger 形态（无 `requireActive`）。另外新增依赖 `paytech-http-client-support`、`spring-boot-http-client`、`spring-boot-restclient`：billing 原来没有出站 HTTP。改动：`SubmitBillingFactProcessor`、`BillingFact*ServiceImpl`、持久化配置、`application.yml` |
| operations-console-web | `139c157e…`（商务 `capability-20261021e`） | `sha256:366f8b956dfe50ef6ed5a74c0037f4aa27d83eb3de84ca09344da02c60210031`（`currency-20261021b`，入口 `assets/index-GSwx0qPN.js`） | 158 个文件：多 `CurrencySpecs` 页；67 个与运行版同名同内容，其余因 Vite 内容哈希级联改名 |

阶段 6 先构建过一版 Web 候选 `5613e3f2…`，它基于商务上一版 `333e7f2b`。商务 03:58Z 换版后这一候选作废，`currency-20261021` 这个 Web tag 已删除，只用上表的 `currency-20261021b`。

## 2. 兼容性核对（决定顺序）

- **旧 Platform 加列后照常运行：** `2f5f37f7` 的 Platform `CurrencySpecMapper.xml` 只查显式列（id、currency、minor_unit、rounding_mode、status、create_time、update_time），没有写入，所以 ALTER 先于新 Platform 执行。
- **旧 ops-bff 依赖 ledger 的 `/currencies/v1`：** 旧 ops-bff 的财务币种列表调用 ledger `GET /currencies/v1`，新 ledger 删除了这个接口。**ops-bff 必须先于 ledger 替换**。新 ops-bff 改读 Platform，不再调 ledger 的币种接口。
- **ledger / billing 用严格 JSON 读 Platform：** 新 ledger / billing 读 Platform 币种查询时开启了 `FAIL_ON_UNKNOWN_PROPERTIES`。响应里的 `version` 已在新 DTO 中，旧 Platform 不返回 `version` 时为默认值，不报错，客户端也不校验它。所以哪一边先换都能通过，但先换 Platform，快照第一次加载就是新形态。新 ops-bff 的维护写接口必须有新 Platform。
- **旧 ledger / billing 只读本库副本，不调 Platform：** 副本删除前，image 回滚不需要任何 SQL。
- **mor-test 网络已通：** ledger 容器内 `platform-support-service` 解析为 `172.30.73.21`。Platform 业务端口 `18106`、Context Path `/platform-support`，与 `compose.applications.yaml` 里 Platform 健康探针使用的地址一致。新 ledger / billing 只多读 `*_PLATFORM_SERVICE_URL`。`ledger.currency-snapshot.*`、`billing.currency-snapshot.*` 都有默认值（检查 5 秒、刷新 60 秒、调度开），mor-test 不需要别的环境变量。

## 3. 只读核对（阶段 6 已做）

`sql/00-precheck.sql`（2026-10-21 04:0xZ）全部等于期望：

| 核对项 | 结果 |
|---|---|
| `version` 列 | 不存在 |
| 块 125 | 全空 |
| 权限码 `CURRENCY_SPEC_*`、组件键 `OPERATIONS_CURRENCY_SPECS` | 都不存在 |
| 已用最大块号 | 124 |
| 平台配置菜单 562 | ACTIVE |
| `PLATFORM_ADMIN` 角色 | 存在，版本 38 |
| `mor_platform`、`mor_ledger`、`mor_billing` 的币种行 | 各只有 USD 一行（2 位、HALF_UP、ACTIVE） |
| XTS | 不存在 |

五个容器 healthy、重启 0。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件（`compose.sh -f <本文件> config --quiet` 已通过） |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 执行前只读核对 |
| [sql/01-platform-currency-spec-version.sql](./sql/01-platform-currency-spec-version.sql) | `mor_platform` ALTER（`changes/2026-10-20-currency-spec-version.sql` 的发布副本） |
| [sql/10-registration-block125.sql](./sql/10-registration-block125.sql) | 块 125 休眠注册 54 行（初始化 DML 末尾「Operations currency spec console」块的逐字副本，自带事务） |
| [test-activate.sql](./test-activate.sql) / [test-deactivate.sql](./test-deactivate.sql) | 激活 / 停用（4 / 2 / 2 / 1） |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 激活后只读核对 |
| [sql/90-ledger-drop-currency-spec-copy.sql](./sql/90-ledger-drop-currency-spec-copy.sql) / [sql/91-billing-drop-currency-spec-copy.sql](./sql/91-billing-drop-currency-spec-copy.sql) | 最后一步删副本（含回退建表语句） |
| [scripts/backup.sh](./scripts/backup.sh) / [scripts/apply_sql.sh](./scripts/apply_sql.sh) | 备份与 SQL 执行包装（root 口令只经 `MYSQL_PWD`，不回显；输出进备份目录） |
| [acceptance/](./acceptance/) | `stage7_acceptance.py`、`stage7_cleanup.py`、`ccy7_common.py`、`CurrencySnapshotMetricsFetch.java` |

执行包装：`scripts/apply_sql.sh <备份目录> precheck|platform-alter|platform-reg|platform-act|platform-deact|postcheck|ledger-drop|billing-drop`。

## 5. 执行步骤（阶段 7，主会话执行）

按 mor-test 部署经验：停容器、起容器、root SQL 都由主会话执行。命令中的 `$O` 指本目录 `compose-candidate.yaml` 的绝对路径，`$B` 指备份目录。

0. **协调：**
   - **发消息：** SendMessage 给商务会话 **code-mor-98** 和对账会话。本次会替换 platform、ops-bff、ops-web、ledger、billing，其中前三个与商务共用，后两个与对账共用。
   - **确认：** 对方当前不在部署，近期也没有待发的改动，然后约一个窗口。
   - **失效条件：** 期间若对方又发布了这五个服务中的任何一个，本包作废，要在新基线上重建并重做第 1 节的证明。03:58Z 商务换 ops-web 就是一例。
   - **完成后：** 把新镜像 ID 和源码基线告诉对方。
1. **复核基线：** `docker inspect` 五个容器的 `.Image`，必须等于 [compose-rollback.yaml](./compose-rollback.yaml)；mor-test 容器全部 healthy。有任何不同就停下。
2. **备份：** `B=$(release/scripts/backup.sh)`，得到 `~/Code-MoR-backups/<UTC>-currency-spec-mor-test/`。
   - **数据：** `mor_platform` 的 `t_currency_spec` 与注册 8 表、`mor_ledger.t_currency_spec`、`mor_billing.t_currency_spec`。
   - **其他：** 运行镜像、JAR SHA-256、两个覆盖文件、precheck 输出、SHA256SUMS。
   - 打开 `$B/00-precheck.out`，确认与第 3 节一致。
3. **Platform SQL：**
   - `apply_sql.sh $B platform-alter`：在线 ALTER，旧 Platform 不受影响。
   - `apply_sql.sh $B platform-reg`：休眠 54 行。
4. **platform → ops-bff：** 依次执行 `./test-environment/compose.sh -f $O up -d --no-deps --no-build --pull never --wait --wait-timeout 180 platform-support-service`，同一命令再换 `operations-console-bff`。每次换完核对：
   - healthy、重启 0、启动日志 ERROR 0、`.Image` 等于候选；
   - 不要等到 ledger 换完才查 ops-bff：它的财务币种列表要在 ledger 换之前就改走 Platform。
5. **ledger → billing：** 同一命令依次换 `ledger-service`、`billing-service`。每次核对上述四项，并确认：
   - **快照：** 就绪探针（含 `currencySnapshot`）UP，即 healthy；日志里没有 `Currency snapshot refresh failed`；
   - **XXL：** 执行器约 30 秒后重新注册。ledger 和 billing 都有执行器，ledger 是规则版本生效任务，billing 是补偿扫描。
6. **ops-web → 激活：**
   - `up … operations-console-web`；
   - `apply_sql.sh $B platform-act`，期望 4 / 2 / 2 / 1；
   - `apply_sql.sh $B postcheck`，全部等于期望，`PLATFORM_ADMIN` 版本从 38 变成 39；
   - 等 Gateway Snapshot 刷新（≤ 60 秒）后再冒烟。
7. **验收：** `python3 -I release/acceptance/stage7_acceptance.py`，见第 7 节，然后 `python3 -I release/acceptance/stage7_cleanup.py <run.json>`。
8. **浏览器冒烟（管理员，只读）：**
   - **新页面：** 打开「平台配置 → 币种」，列表显示 USD；新建、编辑、启停按钮可见。
   - **回归：** 财务里用到币种的页面（账簿、人工调整、试算）各打开一次，商务 9 个路由、对账 7 页也各打开一次，证明换 ops-web / ops-bff / platform 后没有回退。
   - **方式：** 用 Playwright，经 `/api/operations/**` 中继访问 http://localhost:28080，与对账切片 1 的 `stage7_browser.py` 同法。
   - **提醒用户：** 换了 ops-web 后要刷新已打开的标签页。旧标签页不认识新菜单，会悄悄把它丢掉。
9. **删除副本（另行授权）：** 验收和冒烟都通过、确认新镜像稳定运行后，执行 `apply_sql.sh $B ledger-drop` 和 `apply_sql.sh $B billing-drop`，然后核对：
   - `information_schema` 里两张表都不存在；
   - ledger / billing 仍 healthy，日志 ERROR 0。
10. **记录：**
    - 把第 8 节填完，`delivery.md` 补上环境操作记录；
    - 通知 code-mor-98 和对账会话新的镜像 ID；
    - 把对方以后的回滚目标改成本包候选，避免回退本需求。

## 6. 回滚

| 范围 | 做法 |
|---|---|
| 激活 | `apply_sql.sh $B platform-deact`，期望 4 / 2 / 2 / 1。菜单和路由立即失效，注册行保留 |
| 镜像（副本未删时） | 先停用激活，避免出现能看到入口但调用失败的状态。然后按 **ops-web → billing → ledger → ops-bff → platform** 的顺序，用 `compose.sh -f <compose-rollback.yaml> up … <service>` 换回。ledger 必须先于 ops-bff 回滚，因为旧 ops-bff 要调 ledger 的 `/currencies/v1`。不需要任何 SQL：旧 ledger / billing 读本库副本 |
| 镜像（副本已删后） | 先按 `sql/90`、`sql/91` 回退段重建副本。若 Platform 已启用了其他币种，按 Platform 当前行补入。再按上一行回滚 |
| `version` 列 | 只在 Platform 已回滚后执行 `sql/01` 文件头的回退语句（`DROP COLUMN version` 并恢复旧表注释）。旧 Platform 不读这一列，不回退也能运行 |
| 块 125 注册 | 通常保留，保持休眠即可。需要彻底移除时，按块头 ID 范围 900000000000125101–900000000000125799 删除，子表先删 |
| 验收数据 | 由 `stage7_cleanup.py` 删除 XTS 行和合成只读人员 / 角色，审计保留；结构回滚以备份目录为准 |

## 7. mor-test 验收

`acceptance/stage7_acceptance.py`。管理员取 `test-environment/private/access.md`，经运营入口 http://localhost:28080 执行，运行记录写 `~/Code-MoR-backups/currency-spec-stage7/run-*.json`（0600，含合成人员口令）。验收币种用 **XTS**（ISO 4217 保留给测试的代码），清理时删除。

| AC | 检查 |
|---|---|
| AC-01 | 列表中 USD 为 2 位、HALF_UP、ACTIVE、版本 1。XTS 依次经过以下操作，每次核对状态和版本：<br>1. 新建草稿 3 位，版本 1；<br>2. 重复新建，返回 400「币种规则已存在: XTS」；<br>3. 修改草稿为 2 位，版本 2；<br>4. 用过期版本修改，返回 409；<br>5. 启用，版本 3；<br>6. 用过期版本停用，返回 409；<br>7. 停用，版本 4。<br>财务币种列表 `status=ACTIVE` 只在启用期间含 XTS，不筛选时能看到 DRAFT。审计 `t_audit_event`（`resource_type=CURRENCY_SPEC`）记 CREATE 1、UPDATE 1、STATUS 2，均 SUCCEEDED。合成只读人员（只有 `CURRENCY_SPEC_READ`）能看列表，新建和启停都返回 403，导航里有币种页、没有维护按钮 |
| AC-02 | 启用后修改小数位，返回 400，提示带 ACTIVE |
| AC-03 | 停用后财务 `status=ACTIVE` 列表不含 XTS。ledger 开账簿拒绝非 ACTIVE 币种，由本机测试覆盖（`CurrencyRulePostingTest`）。mor-test 不建账簿，避免留下账务数据 |
| AC-04 | 验收前后 USD 行不变，ledger / billing 无报错 |
| AC-06 | 四个 Java 服务验收期间 healthy、未重启、ERROR 0。「Platform 停机时快照继续服务」由本机测试覆盖（`CurrencySnapshotRefreshTest`）。mor-test 不停 Platform：它是共享的 |
| AC-08 | 两个服务的管理端口只绑 127.0.0.1，宿主机访问不到。所以脚本用宿主机 JDK 25 编译 `CurrencySnapshotMetricsFetch`，`docker cp` 到 ledger / billing 容器的 `/tmp/ccy7-probe`（tmpfs，清理时删），再读 Prometheus 的 `http_client_requests_seconds_count{uri="/platform/v1/currency-specs",status="200"}`。判定：启用 XTS 后 75 秒内，两个服务各自至少完成一次成功回源，且期间日志没有 `Currency snapshot refresh failed`。快照只在一次完整成功回源后整体替换，所以这就证明 XTS 已进入快照 |

**AC-08 的指标风险：** 计数依赖 Spring Boot 对 HTTP Service 客户端的 Observation 指标，阶段 6 无法在不改现网容器的情况下预先确认。若候选上没有这条指标，脚本会判 AC-08 失败。此时只剩较弱的证据：启用后 75 秒内无刷新失败 WARN，且就绪探针保持 UP。Platform 现网访问日志只打印「HTTP request completed」，不带路径，不能证明发生过回源。把这一偏差如实记在第 8 节，不改代码；是否接受由用户决定。

清理 `stage7_cleanup.py <run.json> [--dry-run]`：
- **XTS：** 只删运行记录里那一行，要求创建时间不早于本次运行；
- **合成人员：** 按记录删合成只读人员和角色，与对账切片 1 清理同一组表，审计保留；
- **探针：** 删容器内的探针；
- **复核：** 最后核对 `t_currency_spec` 只剩原样的 USD 一行。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 04:5x | 0 协调 | 两个会话都同意开始。code-mor-98 当时不在部署，ops-bff、platform 没有待发改动。code-mor-93 原计划用块 125，主动改用 126；我核对后回复：注册脚本只含本需求 109 个 125 号 ID，候选 capabilities 只多 `OPERATIONS_CURRENCY_SPECS` |
| 05:00 | 1 复核基线 | 五个容器 `.Image` 均等于 compose-rollback.yaml，mor-test 全部 healthy |
| 05:00:27 | 2 备份 | `~/Code-MoR-backups/20261021T050027Z-currency-spec-mor-test/`；precheck 17 项全部等于期望，`PLATFORM_ADMIN` 版本 38 |
| 05:0x | 3 Platform SQL | ALTER 后 `version` 列和表注释正确，USD 版本 1；块 125 休眠注册 54 行 |
| 05:01–05:02 | 4 platform → ops-bff | `b2a63ff4…`、`fddb1993…` healthy，重启 0，ERROR 0 |
| 05:02–05:03 | 5 ledger → billing | `fa21520d…`、`90d7401a…` healthy，重启 0，ERROR 0，无刷新失败；`*_PLATFORM_SERVICE_URL` 已生效 |
| 05:03 | 6 ops-web → 激活 | 替换前 ops-web 仍是 `139c157e…`；换成 `366f8b95…` 后 healthy。激活 4 / 2 / 2 / 1，postcheck 全部等于期望，`PLATFORM_ADMIN` 39 |
| 05:04–05:06 | 7 验收 | 第一次运行把探针 `docker cp` 进容器时被权限策略拦截，没有改任何数据。改为旁路容器（`--network container:<目标>`、只读挂载、`--rm`）读取指标后重跑：**32/34**。AC-08 中 ledger 43.7 秒、billing 59.6 秒完成回源。两项失败都是脚本缺陷，已修正并补验（`stage7_recheck.py`，4/4）：① 审计 `resource_id` 记的是币种代码，不是数据库 ID；按 XTS 统计，CREATE 1、UPDATE 1、STATUS 2。② 只读角色还缺父菜单「平台配置」的 `GATEWAY_CONFIG_READ`，补上后导航有币种页、没有维护按钮。被拒的变更（重复、过期版本、启用后修改）审计记为 DENIED，`resource_id=CURRENCY_SPEC_CATALOG`。运行记录 `~/Code-MoR-backups/currency-spec-stage7/run-20261021050447.json` |
| 05:0x | 清理 | XTS 已删（审计保留）；两名合成人员和两个角色已删；`t_currency_spec` 只剩原样的 USD。第一次运行在 ledger 容器 tmpfs 留下空目录 `/tmp/ccy7-probe`，无害，容器重启即消失，未再进入容器删除 |
| 05:08–05:14 | 8 浏览器冒烟（只读） | **第一次发现回退：** 对账 7 页 503。原因是对账切片 1 在 ops-bff 覆盖文件里加的 `OPERATIONS_RECONCILIATION_BASE_URL` 不在主 compose，本包候选漏了。已补进 compose-candidate / compose-rollback，ops-bff 同一镜像重建后 healthy。<br>**第二次：** 27/28，控制台错误 0、4xx/5xx 0；唯一失败是脚本没匹配到 antd 两字按钮文字中间插入的空格（「停 用」），截图 `p01-currency-specs.png` 显示停用按钮正常。<br>**第三次：** 修正匹配后币种页通过；但其中 5 页因三次连跑触发 Gateway 对 `/auth/v1/context` 的限流（429），与本次发布无关，这 5 页在第二次运行中均正常 |
| 05:16:08 | 9 删除副本 | `mor_ledger`、`mor_billing` 的 `t_currency_spec` 已删，`information_schema` 只剩 `mor_platform`。删除前 ledger、billing 运行约 14 分钟，ERROR 0，无刷新失败 |
| 05:1x | 10 通知 | 已把新镜像 ID、源码 worktree、ops-bff 对账地址和 `*_PLATFORM_SERVICE_URL` 注意事项告知 code-mor-98 和 code-mor-93 |

## 9. 风险与遗留

- **共享服务：** 五个服务分 5 次重启，与商务、对账会话共用，必须先协调（第 5 节第 0 步）。商务在验收反馈期间频繁发 ops-web，执行前要再核对一次基线。
- **ledger 响应上限：** ledger 读 Platform 币种用的是 8 KB 严格 JSON 上限（`withStrictJsonResponses(8192)`）；每条规则约 130 字节，约 60 个币种就会超限。超限后快照刷新整体失败，并保留上一份快照。billing 上限是 64 KB。mor-test 只有 1–2 个币种，不受影响；运营大规模维护币种前要调高 ledger 上限，记为后续项。
- **AC-08 指标：** 见第 7 节，可能需要改用日志证据。
- **数据残留：** `data-service/cdc/whitelist.yaml` 与本地 Flink 示例仍指向不存在的 `mor_finance.t_currency_spec`，与本次发布无关，已在 delivery.md 记为遗留。
