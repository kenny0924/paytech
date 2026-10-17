# 第 09 步报告 · 数据服务骨架与 Doris / Flink CDC 容器

- 分支：`refactor/step-09-data-service-skeleton`，起点 `refactor-main@5f1fc10`。实现提交 `b1b3ed1`；报告与 README 磁盘数据修正 `1183a05`；门禁反馈的 parent 链与 Enforcer 修正为第三次提交（第 7 节）。
- 结论：第 3 节 6 项全部真实执行。
  - 1、3、4、5、6 通过。
  - 2 的 analytics 部分全部通过，但脚本仍以退出码 1 结束，原因是 4 行既有失败，详见第 2 节第 2 项。
  - 演示管道在本机跑通：MySQL 执行 `UPDATE` 后约 11 秒，Doris 只读账号查到新值。
- **需要先看的风险：** 本步镜像使 Docker 磁盘增长约 14 GB，宿主可用空间从 18 GiB 降到 6.3 GiB，见第 4 节。镜像未删除，删除命令在第 4 节。

## 1. 改动摘要

1. `paytech/data-service/` 下新增服务聚合 POM 和四个模块。`paytech/pom.xml` 只取消了 `data-service` 一行注释。
   - parent 链（门禁反馈后修正）：四个模块的 parent 是服务聚合 POM `paytech-data-service`（`../pom.xml`），聚合 POM 再以 `../build/paytech-parent/pom.xml` 继承 `paytech-parent`。
   - 聚合 POM 承载服务级 Enforcer `bannedDependencies`（`searchTransitive`）：禁止 `com.paytech:*-core`、`com.paytech:*-app`、`org.flywaydb:*`、`org.liquibase:*`、`org.testcontainers:*`、`org.mockito:*`；放行自身四个模块与 Foundation 的 `paytech-common-core`（它也匹配 `*-core`）。
2. `-api`：只有 `com.paytech.data.api` 的 `package-info.java`。
3. `-core`：`WhitelistCatalog` 接口与 `WhitelistCatalogImpl` 实现，校验逻辑在 `WhitelistDocumentParser`，配置类为 `CdcWhitelistProperties`（`data-service.cdc.whitelist-location`）。`paytech/data-service/cdc/whitelist.yaml` 只有一份，构建期打包为 `classpath:cdc/whitelist.yaml`。本模块无 Mapper。
4. `-app` 的内容：
   - 启动类 `DataServiceApplication`，端口 18120，管理端口 19120。
   - 依赖 `paytech-mvc-web-starter`。
   - `DorisDataSourceConfiguration` 在 `data-service.doris.enabled` 为 true 时创建 Hikari 只读连接池和 `doris` 健康指示器（`SELECT 1`）。开关默认开，关闭时两个 Bean 都不存在。
   - 刻意不引入 `spring-boot-starter-jdbc`，因此不存在 DataSource 自动配置。
5. `-test`：
   - `auto`：`DataServiceApplicationAutoTest`、`WhitelistCatalogAutoTest`（1 个合法样例和 12 个非法情形）、`ArchitectureAutoTest`（R1–R12）。
   - `manual`：`DorisConnectivityManualTest`。
   - 配置文件：`application-mock.yml`（同时是配置字段清单）与 `application-manual.yml`。
6. `local-environment/compose.analytics.yaml` 定义 6 个服务：
   - `doris-fe` 与 `doris-be`：Doris FE / BE 单节点。
   - `doris-init`：一次性容器，设置 root 密码、注册 BE、建库 `mor_data_service`、建只读账号 `data_service_app`。
   - `flink-jobmanager` 与 `flink-taskmanager`：Flink 集群。
   - `flink-cdc-submit`：一次性容器，提交管道；同名管道已在 RUNNING 时跳过。
   - 端口只绑定 127.0.0.1：19030、18030、18091。
   - 两个卷：`mor-local-doris-fe-meta`、`mor-local-doris-be-storage`。
7. 新增配套文件：
   - `local-environment/doris/conf/{fe,be}.conf`：在镜像默认配置上做本地化修改。
   - `doris/init/init-doris.sh`：幂等；带密码的语句走 stdin。
   - `flink/Dockerfile`：构建期 `sha256sum -c` 校验 4 个下载文件。
   - `flink/pipelines/demo-currency-spec.yaml` 与 `flink/submit-pipeline.sh`。
8. 基础 Profile 的改动只涉及 mysql 服务：
   - `compose.yaml` 的 mysql `command` 增加 5 个 binlog / GTID 参数，mysql `environment` 增加 `MOR_CDC_DB_PASSWORD`；rabbitmq 段未改。
   - `mysql/init/01-create-databases-and-users.sh` 新增 `cdc_reader`：全局只有 `REPLICATION SLAVE, REPLICATION CLIENT`，`SELECT` 只授予 `mor_finance.*`。
   - `.env.example` 新增三个变量。
9. `scripts/verify-local-environment.mjs` 新增 analytics 校验，内容如下：
   - 服务、镜像摘要、端口、内存、健康检查、依赖条件和卷。
   - Dockerfile 摘要与 4 个 SHA-256。
   - 管道文件只引用白名单表，密码只以 `${MOR_*_PASSWORD}` 出现。
   - 两个脚本的语法，以及 mysql 参数与 `cdc_reader` 授权范围。
   - 同时修复两处基线失败：MinIO 空值导致的插值失败，以及变量清单漂移。
10. `paytech/tools/arch-rules-reactor-test` 中的 `ReactorArchitectureAutoTest`：第 00 步按扁平 Reactor 计数，`data-service` 是第一个嵌套聚合 POM，改为按全部聚合 POM 声明的模块总数核对。
11. README：`paytech/data-service/README.md` 已重写；`local-environment/README.md` 新增 Analytics profile 一节，并在 `cdc_reader` 账号说明中补一行。

## 2. 第 3 节验证（实际命令与结果）

所有 Maven 命令前都执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。Compose 命令通过一个本机辅助脚本执行，等价命令如下：

```bash
PAYTECH_REDIS_PASSWORD=<取自 .env.local 的 MOR_REDIS_PASSWORD> \
docker compose --env-file /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local \
  -f compose.yaml -f compose.analytics.yaml --profile analytics <子命令>
```

任务卡写的 `docker compose --env-file .env.local --profile analytics config` 不带 `-f` 时只读取 `compose.yaml`，看不到 analytics 服务。仓库也没有 `compose.sh`。本机 `.env.local` 仍使用已退役的 `MOR_REDIS_PASSWORD`，因此只为插值导出 `PAYTECH_REDIS_PASSWORD`，做法与 README 中 storage / vault 两节一致。

1. **`cd paytech && ./mvnw -B clean verify`**：BUILD SUCCESS，退出码 0。

   | 模块 | 测试数 | 失败 / 错误 |
   |---|---|---|
   | paytech-arch-rules | 45 | 0 / 0 |
   | paytech-data-service-test | 16 | 0 / 0 |
   | paytech-instance-manifest | 16 | 0 / 0 |
   | paytech-arch-rules-reactor-test | 2 | 0 / 0 |
   | 合计 | 79 | 0 / 0 |

   - data-service-test 的 16 个测试是 `ArchitectureAutoTest` 1 个、`DataServiceApplicationAutoTest` 2 个、`WhitelistCatalogAutoTest` 13 个。
   - `ArchitectureAutoTest` 是 R1–R12 第一次在真实服务模块上执行，结果 0 违规。
   - 为证明规则不是空跑，临时在 core 放入一个带 `@Controller` 的类，R5 报出 2 条违规，测试失败。该类已删除，并在之后的 `clean verify` 中清除。
   - `paytech/scripts/verify-reactor.sh` 输出 `total tests=79 failures+errors=0`，退出码 0。
   - 唯一警告是第 00 步已有的 reactor-test "JAR will be empty"。
   - manual：`./mvnw -B -Pmanual-test verify -pl data-service/paytech-data-service-test -am` 在 analytics 运行时执行。`DorisConnectivityManualTest` 1 个测试 0 失败：Doris 演示表行数大于 0，`doris` 健康组件为 UP。密码从当前终端导出，测试不读取 `.env.local`。
2. **`node scripts/verify-local-environment.mjs`**：退出码 1，analytics 相关检查全部通过。
   - 在 HEAD 基线上执行（`git archive HEAD` 到临时目录），原本有 3 类失败：
     - MinIO 空值导致 base 与 scheduler 模型插值失败。
     - 环境变量清单漂移。
     - 4 行 Gateway 文档引用缺失。
   - 本步修复了前两类。只剩下面 4 行，与基线逐字一致：

     ```text
     active Gateway contract does not reference the hardening authority: agent-docs/JAVA_PROJECT_STRUCTURE.md
     active Gateway contract does not reference the current transport authority: agent-docs/JAVA_PROJECT_STRUCTURE.md
     active Gateway contract does not reference the hardening authority: agent-docs/UNIT_TEST_GUIDE.md
     active Gateway contract does not reference the current transport authority: agent-docs/UNIT_TEST_GUIDE.md
     ```

   - 这两个 agent-docs 文件不在本步范围内，未修改。
   - 变异验证：管道加入非白名单表、写入明文密码、把 Web UI 端口改成 18081，脚本分别报出 3 条对应错误；恢复后报错为 0 条。
3. **analytics 的配置、启动与健康状态**：
   - `config --quiet` 成功。`config --services` 列出 6 个 analytics 服务，不含任何 base 服务。
   - `build flink-jobmanager` 成功，构建期 4 个文件的 SHA-256 校验全部通过。
   - `up -d doris-fe doris-be doris-init flink-jobmanager flink-taskmanager`：首次启动时 TaskManager 内存配置过小而退出（Total Flink Memory 不足）。改为 process 1152m、managed 64m 后启动成功。
   - 状态：doris-fe、doris-be、flink-jobmanager、flink-taskmanager 均为 healthy；doris-init 以 Exited (0) 结束。
   - `SHOW BACKENDS` 显示 `Host: doris-be`、`Alive: true`、`doris-3.0.8-rc01-09b0cc49a6`。
   - doris-init 第二次运行仍为 Exited (0)，证明幂等。
   - `docker stats --no-stream` 见第 4 节。
4. **演示管道**：
   - `up flink-cdc-submit` 输出 `Pipeline has been submitted to cluster.`，Job `demo-currency-spec` 状态为 RUNNING，无异常。
   - 快照同步后，`data_service_app` 查询 `mor_data_service.t_currency_spec` 得到 `1001 USD 2 HALF_UP ACTIVE 2026-10-15 18:56:37.367001`。
   - 本地 `mor_finance` 已有这张表和 1 行数据（由 Finance 此前人工建立），因此跳过 README 第 1 步的 DDL / DML。
   - 在 MySQL 执行 `UPDATE t_currency_spec SET update_time = UTC_TIMESTAMP(6) WHERE currency = 'USD'`，新值为 `2026-10-17 14:47:34.465267`。
   - Doris 在 14:47:45 UTC 查到 `USD 2026-10-17 14:47:34.465267`，约 11 秒，与 10 秒 checkpoint 一致。
   - 自动建出的 Doris 表为 `UNIQUE KEY(id)`，`replication_allocation = tag.location.default: 1`。
   - 重启验证：down 之后在原卷上再次 up，并重新提交管道，耗时 34 秒。再次运行 submit 时输出 `already RUNNING; nothing submitted`，没有产生重复作业。
5. **`--profile analytics down`**：
   - 先执行 `--dry-run`，列出的只有 6 个 analytics 容器。
   - 实际执行后删除这 6 个容器。网络提示 "Resource is still in use" 并保留。
   - `docker ps` 显示 base 的 mysql、redis、rabbitmq、nacos 均为 "Up 3 weeks (healthy)"，mailpit、minio、vault-service 也未变化。
   - 两个 Doris 卷保留，没有使用 `-v`。
   - 结束时 analytics 已停止。
6. **`git status`**：两次提交后工作区干净。

另外，`npm --prefix requirements-site test` 无法执行：worktree 中没有 `node_modules`，报 `vinext: command not found`。本步未改 requirements-site。

## 3. 镜像版本与校验值

| 组件 | 固定方式 | 发布 / 镜像时间 |
|---|---|---|
| Doris FE 3.0.8 | `apache/doris:fe-3.0.8@sha256:749afa2be75cd58d54a0bdb3edaa805d5974646f67500f094912f8dbc4a96b8f` | 2025-10-01，arm64 |
| Doris BE 3.0.8 | `apache/doris:be-3.0.8@sha256:a337e2b17a65c86c22a1205467a825d46dc3380c94ba8024e53eea033641f12e` | 2025-10-01，arm64 |
| Flink 1.20.5（Java 17） | `flink@sha256:80401a8ee20e2246718c01303712c10ccaf13961e31316d9b8c4549f7b2cfc9b` | 版本发布于 2026-06-08，镜像由 Docker 官方于 2026-10-17 重建 |
| Flink CDC 3.7.0-1.20 发行包 | SHA-256 `c7c48f9011e2835ceebaf25d98074e44716a106248da285d0a723fa738385a78`，已与 `.sha512` 比对 | 2026-09-29 |
| MySQL pipeline 连接器 3.7.0-1.20 | SHA-256 `59ed5bbf92f6e8b7d2291d6365a2d0b12438fb011892d8898b756f1d718339f9`，已与 `.sha1` 比对 | 2026-09-29 |
| Doris pipeline 连接器 3.7.0-1.20 | SHA-256 `72537db0a9b832732f756b0c846dcdc1e83a24d23671e3a126181e4c6be6e626`，已与 `.sha1` 比对 | 2026-09-29 |
| mysql-connector-java 8.0.27 | SHA-256 `d670e85fa1075a80d91b8f17dbd620d76717bc20c74ab4aea8356e37a8545ffe`，已与 `.sha1` 比对 | Flink CDC 文档指定的版本 |

本地构建镜像为 `mor-local/flink-cdc:1.20.5-cdc3.7.0`。上述版本已记录在 `paytech/data-service/README.md` 与 `local-environment/flink/Dockerfile`，`TECHNOLOGY_STACK.md` 正文未改。

## 4. 实测资源

管道 RUNNING 且空闲时，`docker stats --no-stream` 的结果：

| 容器 | 内存使用 / 上限 |
|---|---|
| doris-fe | 796 MiB / 1.25 GiB |
| doris-be | 1.06 GiB / 1.5 GiB |
| flink-jobmanager | 318 MiB / 768 MiB |
| flink-taskmanager | 504 MiB / 1.25 GiB |
| 合计 | 约 2.7 GiB（上限合计 4.75 GiB） |

- 内存：Docker 上限为 15.84 GiB。启动前 mor-local、mor-test 与 Nexus 已用约 11 GiB，因此 base、mor-test 与 analytics 可以同时运行，但余量只有约 2 GiB。
- 磁盘：
  - 镜像压缩后合计 5.9 GB。
  - Docker Desktop 的镜像存储同时保留压缩层和解压层，`docker system df` 的 Images 从 64.21 GB 增长到 78.12 GB，Build Cache 增长约 0.7 GB。
  - 宿主可用空间从 18 GiB 降到 6.3 GiB，低于 `LOCAL_DEVELOPMENT_ENVIRONMENT.md` 要求的 15 GiB。这期间其他会话也在构建，降幅不全来自本步。
  - 镜像未删除，以便立即重跑演示。需要释放空间时执行下面的命令，下次 build / up 会重新拉取：

    ```bash
    docker image rm mor-local/flink-cdc:1.20.5-cdc3.7.0 flink:1.20.5-scala_2.12-java17 apache/doris:be-3.0.8 apache/doris:fe-3.0.8
    ```

## 5. 本机一次性操作（不在仓库中）

1. 向用户的 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local` 追加三个键，值为随机 32 位字母数字，未打印。文件权限仍为 600。
2. 用 root 在运行中的 MySQL 创建 `cdc_reader` 并授权，命令见 `local-environment/README.md` 的 Prerequisites 第 4 条。`SHOW GRANTS` 结果只有两行：`REPLICATION SLAVE, REPLICATION CLIENT ON *.*` 和 `SELECT ON mor_finance.*`。
3. 运行中 MySQL 的现状为 `binlog_format=ROW`、`binlog_row_image=FULL`、`log_bin=ON`、`server_id=1`、`gtid_mode=OFF`。GTID 参数要等 mysql 容器下次重建才生效。Flink CDC 在 GTID 关闭时已验证可用。base 容器未重启或重建。
4. 演示修改了 `mor_finance.t_currency_spec` 中 USD 行的 `update_time`，从 2026-10-15 改为 2026-10-17 14:47:34。业务列未改。

## 6. 待确认

1. **服务级 Enforcer**：已按门禁反馈解决，见第 1 节第 1 项与第 7 节。
2. **管理端口**：按任务卡和 01 §3 使用独立管理端口 19120。03 §1 写的是"业务服务沿用同端口暴露 Actuator"，两份文档不一致。
3. **`MockTestBase` 未继承 `TestBase`**：`TestBase` 注入 `List<Clear>`，要求至少一个 Clear Bean。本服务当前没有任何可变测试状态，不为此造一个空 Clear。首个 Mocker 出现时改为继承。
4. **白名单中的服务名**：演示表登记为 `service: ledger, database: mor_finance`，取 `mor_finance` 在 V2 中的 Owner。第 07 步迁表后改为 `platform-support` / `mor_platform`。
5. **compose.yaml 的 mysql 段**：除 `command` 外，`environment` 也加了 `MOR_CDC_DB_PASSWORD` 一行。init 脚本校验该变量，不加会导致新卷初始化失败。这一行与第 01 步修改的 rabbitmq 段不重叠。
6. **Doris 单节点参数**：
   - FE 以 `start_fe.sh` 直接启动，开启 FQDN 模式。官方 `init_fe.sh` 要求静态 IP，而共享的 `mor` 网络没有自定义子网。
   - `force_olap_table_replication_num = 1`。
   - FE heap 768 MiB；BE `mem_limit = 1024M`，JNI heap 256 MiB。
   - BE 设置 `SKIP_CHECK_ULIMIT=true`，因为 Docker Desktop VM 的 `vm.max_map_count` 为 262144。
   - 以上都不是部署基线。
7. **Doris 写入账号**：Flink sink 使用 Doris root。是否另建一个只能写 `mor_data_service` 的写账号，属于 D25 后续切片。
8. **analytics 是否进入 mor-test**：本步未做。mor-test 已占用约 6 GiB 内存，再加 analytics 的 2.7 GiB 和约 14 GB 磁盘，现有资源不够。
9. **文档登记**：以下端口与 Profile 尚未写入 `LOCAL_DEVELOPMENT_ENVIRONMENT.md` 端口表、`TECHNOLOGY_STACK.md` 和 `CURRENT_IMPLEMENTATION.md`，本步只登记在两份 README 中：
   - 19030、18030、18091。
   - data-service 的 18120、19120。
   - analytics Profile。
10. **对第 00 步产物的修改**：只改了 `ReactorArchitectureAutoTest` 的模块计数断言及类注释，规则库本身未动。
11. **`.env.local` 的 Redis 键**：用户 `.env.local` 仍是 `MOR_REDIS_PASSWORD`。所有 `compose.yaml` 组合命令都需要临时导出 `PAYTECH_REDIS_PASSWORD`。是否在 `.env.local` 中补上该键，由用户决定。
12. **共享 `~/.m3` 的 Foundation 漂移（门禁复验时发现，非本步引入）**：
    - 现象：另一会话于 2026-10-17 18:56–18:59 向共享 `~/.m3` 安装了新的 `paytech-mvc-web-starter` 与 `paytech-foundation`。新 starter 以 optional 方式依赖 `paytech-internal-auth`，版本由新 BOM 管理。
    - 原因：本 Reactor 自带的 `paytech-dependencies` 是旧版，不管理 `paytech-internal-auth`，starter POM 因此判为无效，其传递依赖 `paytech-http-problem` 丢失。
    - 后果：`DataServiceApplicationAutoTest` 两例启动失败，报 `NoClassDefFoundError: com/paytech/foundation/httpproblem/api/ProblemFieldError`。
    - 处理：本步不为绕过而直接声明 `paytech-http-problem`。该问题在 Foundation 迁入 Reactor（第 01 步）或 BOM 合入 refactor-main 后消失；在此之前，用共享 `~/.m3` 构建本分支会失败。

## 7. 门禁反馈修正（parent 链与 Enforcer）

1. 四个模块 POM 的 parent 改为 `paytech-data-service`（`<relativePath>../pom.xml</relativePath>`）；聚合 POM 增加 `enforce-data-service-dependency-scope`（validate 阶段）。
2. 复验命令：`cd paytech && ./mvnw -B clean verify -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`。
   - 用共享 `~/.m3` 执行：退出码 1。Enforcer 在聚合 POM 与四个模块上全部通过；`paytech-data-service-test` 中 `DataServiceApplicationAutoTest` 两例出错，原因是第 6 节第 12 项的共享仓库漂移。
   - 用隔离本地仓库执行（同一命令加 `-Dmaven.repo.local=<scratchpad>/m2-isolated`，依赖从本机 Nexus 的已发布快照解析）：BUILD SUCCESS，退出码 0。arch-rules 45、data-service-test 16（含 `ArchitectureAutoTest` 0 违规）、instance-manifest 16、reactor-test 2，合计 79 个测试 0 失败；`BannedDependencies passed` 5 次（聚合 POM 加四个模块）。
3. 反向验证：临时向 core 加入 `com.paytech:mor-common-core`（匹配 `*-core`）和 test 范围 `org.mockito:mockito-core`，`./mvnw -o validate -pl data-service/paytech-data-service-core -am` 以 BUILD FAILURE 结束，两项均报 `banned via the exclude/include list`。临时修改已撤回。
