# data-service

数据服务：建在 Apache Doris 上的近实时只读数据服务（[D25 / D35](../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)）。各服务白名单表的 MySQL DML 经 Flink CDC 同步到 Doris，不经 MQ。白名单与 PII 屏蔽是公开契约。

阶段 0 只建骨架，内容如下：

- 四个模块。
- CDC 白名单契约与启动校验。
- Doris 连通性健康检查。
- 本地 `analytics` 容器与一条演示管道。

不包含查询实现、Client / Partner 查询模板和服务端租户过滤，这些属于 D25 后续切片。

- spring.application.name：`data-service`
- 端口：业务 18120，管理（Actuator）19120
- 数据库：Doris `mor_data_service`，只读账号 `data_service_app`；无 MySQL 库
- 来源现有工程：无（新增骨架）

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-data-service-api` | 空壳，只有 `com.paytech.data.api` 的 `package-info.java`，不发布接口 |
| `paytech-data-service-core` | `WhitelistCatalog`（`interface + WhitelistCatalogImpl`），启动时读取并校验 `classpath:cdc/whitelist.yaml`；构建期把 `cdc/whitelist.yaml` 打包进本 JAR；无 Mapper、无业务 |
| `paytech-data-service-app` | `DataServiceApplication`；`data-service.doris.enabled` 控制 Doris DataSource（Hikari + Connector/J，MySQL 协议）与 `doris` 健康指示器，关闭时两者都不创建 |
| `paytech-data-service-test` | `auto`：`DataServiceApplicationAutoTest`、`WhitelistCatalogAutoTest`、`ArchitectureAutoTest`（R1–R12）；`manual`：`DorisConnectivityManualTest` |

配置键（完整清单见 `paytech-data-service-test/src/test/resources/application-mock.yml`）：

| 键 | 正式默认 | 说明 |
|---|---|---|
| `data-service.cdc.whitelist-location` | `classpath:cdc/whitelist.yaml` | 必填 |
| `data-service.doris.enabled` | `true` | `mock` Profile 为 `false` |
| `data-service.doris.url` | `jdbc:mysql://127.0.0.1:19030/mor_data_service?...` | 可用 `DATA_SERVICE_DORIS_URL` 覆盖 |
| `data-service.doris.username` | `data_service_app` | 可用 `DATA_SERVICE_DORIS_USERNAME` 覆盖 |
| `data-service.doris.password` | 无默认 | 取环境变量 `MOR_DATA_SERVICE_DORIS_PASSWORD`，缺失即启动失败 |
| `data-service.doris.connect-timeout` | `2s` | 取连接与健康查询超时 |

## 白名单契约

文件：[`cdc/whitelist.yaml`](cdc/whitelist.yaml)，结构如下：

```yaml
services:
  - service: <逻辑服务名>
    database: <mor_ 开头的来源库>
    tables:
      - name: <t_ 开头的物理表名>
        maskedColumns: []
        excludedColumns: []
```

契约规则：

- 进入白名单就允许同步到数仓。
- 含 PII 的列必须登记在 `maskedColumns`。
- 未列出的表不得同步。
- 任何 Flink CDC 管道文件只能引用白名单表。`node scripts/verify-local-environment.mjs` 会核对管道文件与白名单。

`WhitelistCatalogImpl` 在启动时拒绝以下内容：

- 未知字段与空列表。
- 不以 `mor_` 开头的库名，以及登记给两个服务的同一个库。
- 不以 `t_` 开头的表名，以及重复的 `库.表`。
- 空列名、表内重复列名，以及同时出现在屏蔽与排除中的列。

阶段 0 只登记演示表 `mor_finance.t_currency_spec`。它现在仍在 Finance 库。第 07 步后它归 Platform（`mor_platform`），届时同步修改白名单与 `local-environment/flink/pipelines/demo-currency-spec.yaml`。

## 本地演示（人工执行）

前提：
- `base` Profile 在跑。
- `local-environment/.env.local` 已含 `MOR_CDC_DB_PASSWORD`、`MOR_DORIS_ROOT_PASSWORD`、`MOR_DATA_SERVICE_DORIS_PASSWORD`。
- 运行中的 MySQL 已有 `cdc_reader`，见 [local-environment/README.md](../../local-environment/README.md#analytics-profile)。

以下命令都在仓库根目录执行，不回显密码。

1. 确认 `mor_finance.t_currency_spec` 存在并有数据。若不存在，从 Finance 的评审 SQL 中只取这张表的 DDL 与初始化插入，人工执行一次。这一步不进入任何自动化脚本：

   ```bash
   set -a; source local-environment/.env.local; set +a
   export MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD"
   docker exec -e MYSQL_PWD mor-local-mysql-1 mysql -uroot -N -e \
     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_finance' AND table_name='t_currency_spec'"
   # 结果为 0 时才执行下面两行
   awk '/^CREATE TABLE `t_currency_spec`/,/;$/' finance-service/docs/database/sql/finance-schema.sql \
     | docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4 mor_finance
   awk '/^INSERT INTO `t_currency_spec`/,/;$/' finance-service/docs/database/sql/finance-initial-data.sql \
     | docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4 mor_finance
   ```

2. 启动 `analytics` Profile 并提交管道。命令见 [local-environment/README.md](../../local-environment/README.md#analytics-profile)；`flink-cdc-submit` 输出 `Pipeline has been submitted to cluster.`。

3. 在 MySQL 执行一条 `UPDATE`。只改技术列 `update_time`，不改业务值：

   ```bash
   docker exec -e MYSQL_PWD mor-local-mysql-1 mysql -uroot mor_finance -e \
     "UPDATE t_currency_spec SET update_time = UTC_TIMESTAMP(6) WHERE currency = 'USD'; SELECT currency, update_time FROM t_currency_spec"
   ```

4. 约 10 秒后，即一个 checkpoint 周期之后，用只读账号在 Doris 查到新值：

   ```bash
   docker exec -e MYSQL_PWD="$MOR_DATA_SERVICE_DORIS_PASSWORD" mor-local-doris-fe-1 \
     mysql -h127.0.0.1 -P9030 -udata_service_app -e "SELECT currency, update_time FROM mor_data_service.t_currency_spec"
   ```

5. 可选：运行真实 Doris 联调测试。Maven 不读取 `.env.local`，密码由当前终端导出：

   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
   cd paytech && ./mvnw -B -Pmanual-test verify -pl data-service/paytech-data-service-test -am
   ```

## 测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd paytech && ./mvnw -B verify -pl data-service/paytech-data-service-test -am
```

`auto` 不连接任何中间件：`mock` Profile 关闭 Doris。`ArchitectureAutoTest` 从四个模块的 `target/classes` 导入类，需要先编译，因此在 Reactor 中执行。

## 版本与校验值

| 组件 | 版本 | 固定方式 |
|---|---|---|
| Apache Doris FE | 3.0.8 | `apache/doris:fe-3.0.8@sha256:749afa2be75cd58d54a0bdb3edaa805d5974646f67500f094912f8dbc4a96b8f`（arm64 / amd64） |
| Apache Doris BE | 3.0.8 | `apache/doris:be-3.0.8@sha256:a337e2b17a65c86c22a1205467a825d46dc3380c94ba8024e53eea033641f12e`（arm64 / amd64） |
| Apache Flink | 1.20.5（Java 17） | `flink:1.20.5-scala_2.12-java17@sha256:80401a8ee20e2246718c01303712c10ccaf13961e31316d9b8c4549f7b2cfc9b` |
| Flink CDC 发行包 | 3.7.0（Flink 1.20 线） | `flink-cdc-3.7.0-1.20-bin.tar.gz` SHA-256 `c7c48f9011e2835ceebaf25d98074e44716a106248da285d0a723fa738385a78` |
| MySQL pipeline 连接器 | 3.7.0-1.20 | SHA-256 `59ed5bbf92f6e8b7d2291d6365a2d0b12438fb011892d8898b756f1d718339f9` |
| Doris pipeline 连接器 | 3.7.0-1.20 | SHA-256 `72537db0a9b832732f756b0c846dcdc1e83a24d23671e3a126181e4c6be6e626` |
| MySQL JDBC（Flink 侧） | mysql-connector-java 8.0.27 | SHA-256 `d670e85fa1075a80d91b8f17dbd620d76717bc20c74ab4aea8356e37a8545ffe` |

- 下载文件在 `local-environment/flink/Dockerfile` 构建期用 `sha256sum -c` 校验。
- 上表中的 SHA-256 已先与上游发布的 `.sha512`（Apache dist）或 `.sha1`（Maven Central）逐一比对。
- 构建出的本地镜像为 `mor-local/flink-cdc:1.20.5-cdc3.7.0`。
- 以上版本是 D35 新增，`agent-docs/TECHNOLOGY_STACK.md` 正文尚未登记。
