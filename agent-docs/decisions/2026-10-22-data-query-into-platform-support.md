# 2026-10-22 决定：数据查询并入 Platform Support，查询改读运营登记的只读库

- Decision status: Confirmed
- Implementation status: In progress（需求 [platform-support：数据查询服务](../../requirements/20261022/platform-support-数据查询服务/requirement.md) 研发中）
- Deployment status: Not deployed
- Decision date: 2026-10-22（用户）
- Supersedes / Superseded sections：
  - [目标架构 V2 决定草案](./2026-10-17-target-architecture-v2.md) D25 的查询部分（数据服务建在近实时数仓上、CDC 同步白名单表、白名单与 PII 屏蔽是公开契约、每实例一套）与 D35 全部（Doris + Flink CDC 作为数据服务底座）；D25 中“详情与操作前校验实时读 Owner”保留，Client / Partner 查询模板与服务端租户过滤留待后续切片另行决定。
  - 独立服务 `data-service`（`paytech/data-service`、端口 18120 / 19120、Doris 库 `mor_data_service` 作为服务库）的规划与阶段 0 骨架。
  - [`TECHNOLOGY_STACK.md`](../TECHNOLOGY_STACK.md) 第 9.1 节“禁止跨数据库查询”增加下述受控读取例外；写入、外键、视图、触发器与跨库事务的禁止不变。
- 详细材料：[需求](../../requirements/20261022/platform-support-数据查询服务/requirement.md)（第 9 节 C1—C15）、[技术方案](../../requirements/20261022/platform-support-数据查询服务/technique.md)。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 背景

D25 / D35 规划的数据服务要求 Flink CDC 把各服务白名单表同步进 Doris，再在 Doris 上提供查询。阶段 0 只做了骨架：模块、CDC 白名单校验与 Doris 健康检查，没有查询能力；本地 Doris + Flink 约需 4.75 GiB 内存，`mor-test` 未接入。Operations 的列表、统计与核对查询仍要逐个开发 Owner 只读接口。生产数据库通常读写分离，运营可以直接查从库。

## 决定

1. **归属（C1、C12）：** 数据查询是 Platform Support 的业务模块 `paytech-platform-support-data-query`（包根 `com.paytech.platform.support.dataquery`），配置表放 `mor_platform`，由 Platform 进程承载，不独立部署、不占独立端口与库。本期调用方只有 `operations-console-bff`（C3）。
2. **数据源（C2、C4、C15）：** 查询读取运营在 Platform 登记的只读数据源，通常是读写分离的从库；数据源可以不指定默认库而连接整个实例，此时模板必须写 `库.表`。首期只实现 JDBC 类引擎（MySQL 协议，覆盖 MySQL 与 Doris），其余引擎经 SPI 预留。Doris 降为可接入的数据源类型之一，不再是查询底座。
3. **CDC 白名单契约废止（C7）：** 删除 `paytech/data-service` 骨架与 `cdc/whitelist.yaml`，不再有“进入白名单才可被查询”的公开契约。本地 `analytics` Profile 保留为 Doris 方言的 manual 测试环境，Flink 演示管道只演示把数据放进 Doris。
4. **受控跨库读取例外（C6、C8）：**
   - 只经数据源登记的只读账号读取已登记的数据源；启用前检查账号权限，含任何写、DDL、`FILE`、`SUPER` 或角色授权的账号不能启用。
   - 只用于 Operations 查询和后续工作流验证；查询结果不作为业务执行依据，业务服务执行时仍须读自己的数据并自行校验。
   - 任何层面都不修改被查库：渲染后的 SQL 只放行单条只读查询。
   - 系统库不可查询：MySQL 拒绝 `mysql`、`sys`、`performance_schema`，Doris 拒绝 `mysql`、`__internal_schema`；`information_schema` 允许。
   - 本例外不改变各服务独立数据库与独立账号的所有权边界，不授权业务服务之间跨库读取。
5. **其余已确认取舍（详见需求）：**
   - 模板不做复核，保存即生效并保留版本历史；出参屏蔽为可选能力（C9）。
   - 模板不绑定权限码，执行权限由 `DATA_QUERY_EXECUTE` 控制（C11）。
   - 所有引擎使用同一套自研模板语法，不用 MyBatis（C13）。
   - 不做查询记录，不建查询记录表与页面；数据源不做主机白名单（C14）。
6. **新依赖：**
   - JSqlParser：渲染后 SQL 的只读校验，由统一 BOM `paytech-dependencies` 固定版本，见 [`TECHNOLOGY_STACK.md`](../TECHNOLOGY_STACK.md) 第 3 节。
   - CodeMirror 6（`@codemirror/*`）：operations-console-web 模板编辑器的高亮与错误定位，只在模板编辑器页面懒加载。

## 影响

- `paytech/data-service/` 删除；`paytech/pom.xml`、instance-manifest `services.yaml`、arch-rules `ServiceCatalog`、`YamlEventCatalog` 中的 `data-service` 删除，V2 逻辑服务数相应减少一个；本地端口表不再预留 18120 / 19120。
- `scripts/verify-local-environment.mjs` 不再核对 Flink 管道与 CDC 白名单的一致性；Doris / Flink 容器、`mor_data_service` 库与 `data_service_app` 只读账号保留，作为 Doris manual 测试的被查库。
- 本地自动测试的被查库为 `mor_data_query_fixture`（只读账号 `data_query_fixture_reader`、写权限账号 `data_query_fixture_writer`），只在本地使用，不进入 `mor-test`。
- 文档中“读模型经 CDC 进 Doris、由 data-service 提供”的规划改读为“经 Platform data-query 读取登记的只读数据源”；已完成需求与历史计划正文保留原文。
- 表设计、接口、模板语法与页面以需求技术方案为准；数据库是否执行、代码与部署进度只记录在当前台账。
