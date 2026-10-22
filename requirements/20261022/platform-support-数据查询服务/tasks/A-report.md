# 任务卡 A 交付摘要（主会话核对后整理，2026-10-22）

- 主会话复跑：`./mvnw -o -pl platform-support/paytech-platform-support-data-query test` → QueryTemplateEngineTest 27、ReadOnlySqlGuardTest 15，全部通过。
- 改动：`paytech/build/paytech-dependencies/pom.xml`（`jsqlparser.version=5.4`，来源本地 Nexus maven-public release）；`paytech/platform-support/pom.xml`（modules 与 dependencyManagement 加入 data-query）；新模块 `paytech-platform-support-data-query`（`core/enums` 6 个、`template` 52 个类、`engine` 11 个类、2 个测试类与 `QueryTemplateFixtures`）。
- 入口：`template/QueryTemplateEngine`（parse / bindParameters / render / validate）；`engine/ReadOnlySqlGuard`、`engine/SqlPagingRewriter`、`engine/QueryEngine`（SPI）、`engine/SqlDialect`、`engine/QuerySource`。
- 错误模型：语法与渲染错误 `TemplateCheckException`（继承 BizCheckFailException，带行列）；参数绑定返回 `ParameterBindingResult`（字段错误列表，`requireValues()` 抛 InvalidParameterException）。
- 已知限制：`-pl platform-support -amd` 会拉入 acquiring-core，其依赖 `paytech-counterparty-api:1.0.0-SNAPSHOT` 本地不可解析；改用显式列出 platform-support 9 个子模块 `-am`。

## 主会话裁决（供任务卡 C 执行）

1. SPI：`prepare` 增加 `QuerySource`（引擎类型 + 默认库）接受；`execute`、`check` 及其类型由卡 C 一并加入。technique.md 4.1 按此更新。
2. 参数字段错误：不另起格式，复用 Foundation 公共的 `RequestBodyValidationException`（422 `VALIDATION_ERROR` + `errors[{field,message}]`），field 用 `params.xxx[i].yyy` 路径。technique.md 3.2 的“400”改为此口径。
3. `SqlDialect` 与 `DataSourceEngineType`：卡 C 引入 MySQL/Doris 方言实现时合并为一套（以 `DataSourceEngineType` 选方言实现，系统库名单进入方言），不保留两个同义枚举。
4. 语义细节全部接受：join 每段去首尾空白；大小比较遇 null 为 false，只有 `== null`/`!= null` 判空；字符串字面量不与日期比较；正文长度按 UTF-8 字节。
