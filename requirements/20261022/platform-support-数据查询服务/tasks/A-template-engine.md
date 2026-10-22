# 任务卡 A：data-query 模块骨架、统一模板引擎与只读校验（纯 Java）

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`（detached worktree，基于 main `281a217d`）。**只在这个目录改文件**，不要动主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`。
- 授权：普通文件编辑、编译、运行本模块的测试。**不提交、不推送、不执行任何 SQL、不启动或修改 local-environment 容器。**
- 权威：需求 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-数据查询服务/requirement.md`（第 7 节统一模板语法），技术方案同目录 `technique.md`（第 2、3、4.3、4.4、5.1 节中“整实例连接”和“系统库”两条）。两者冲突时以 technique.md 为准；仍有疑问就在报告里列出，不要自行发明第三种方案。

## 开工前必读（实际读取，不凭记忆）

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/TECHNOLOGY_STACK.md` 第 2、3、5 节。参考现有模块写法：`paytech/platform-support/paytech-platform-support-file-document`（pom、包结构、JavaDoc 风格）。工作流模块的纯 Java 测试放在模块自己的 `src/test/java/.../auto/...`，可参照 `/Users/kenny/Desktop/Workspace/Code-MoR-workflow/paytech/platform-support/paytech-platform-support-workflow`（只读参考，不要改它）。

## 范围

1. **新模块** `paytech/platform-support/paytech-platform-support-data-query`，包根 `com.paytech.platform.support.dataquery`；加入 `paytech/platform-support/pom.xml` 的 modules（放在 delivery 之后、core 之前）和 dependencyManagement（若该 pom 对兄弟模块有版本管理）。本卡只建 `template`、`engine` 两个包和必要的 `core/enums`；DataObject、Mapper、Repository、Service、Vault、连接池**不在本卡**。
2. **统一依赖清单**：在 `paytech/build/paytech-dependencies` 中加入 JSqlParser（`com.github.jsqlparser:jsqlparser`）。版本：选 5.x 中本机 Maven 仓库（`~/.m3/repository`）或已配置的 Nexus 能解析到的最新稳定版；不要用 SNAPSHOT。在报告里写明版本和解析来源。`TECHNOLOGY_STACK.md` 的登记由另一张卡负责，本卡不改文档。
3. **模板引擎（`template` 包，不依赖 Spring）**，按 technique.md 第 3 节实现：
   - 词法/语法解析器 → 语法树：文本、值 `#{path}`、片段 `@{name}`、`if/elseif/else`、`each`（`as item, index`、`separator=`）、`join`（分隔符、`prefix=`、`suffix=`，子节点只能是 `part`/`if`/`each`/空白）、`part`、注释 `{{!-- --}}`、转义 `\#{` `\@{` `\{{`。错误带行号、列号和中文原因。
   - 表达式：路径、字符串/数字/true/false/null 字面量、`! && || == != > >= < <=`、括号；无函数调用。只写路径时按“非空”判断（非 null、字符串非空白、集合非空、布尔 true）。比较两边类型不兼容时报错，不做隐式转换。
   - 参数声明模型与校验：STRING、INTEGER、DECIMAL、BOOLEAN、DATE、DATETIME、ENUM、FRAGMENT、LIST（elementType 不能是 FRAGMENT/LIST；可为 OBJECT，带 fields）。约束：required、default、maxLength、min、max、allowedValues、maxSize、choices。未声明参数拒绝；`__` 开头参数调用方传入即拒绝；错误带字段路径如 `params.conditions[2].minAmount`。片段原文不得含 `#{`、`@{`、`{{`、`;`、注释符号。
   - 系统参数注入：`__operator.accountId`、`__cursor.*`。
   - 渲染：输出 `RenderedQuery(text, bindings)`；值节点交给 `ValueBinder`（接口）；本卡实现 `JdbcValueBinder`（输出 `?`，集合输出 `?, ?, ?`，空集合直接进 `#{}` 报错）。值永远不拼接进文本。
   - 保存校验：解析、引用检查（已声明 / 循环变量 / 系统参数）、未使用参数警告、片段原文合法、**展开校验**（if 各分支、集合空/一个元素、片段各取值；组合数 ≤ 256 时全列举，否则用每个分支至少出现一次的覆盖集并给出提示），每个组合渲染后交给只读校验。问题列表带级别（ERROR/WARNING/INFO）、行、列、原因。正文最大长度由调用方传入的上限决定。
4. **只读校验与分页改写（`engine` 包）**，按 technique.md 第 4.3、4.4 节和 5.1 节：
   - `ReadOnlySqlGuard`：字符串字面量外出现 `/*`、`*/`、`--`、`#` 拒绝；JSqlParser `parseStatements` 必须恰好 1 条 Select（PlainSelect、SetOperationList、WITH）；拒绝 INTO（含 OUTFILE/DUMPFILE/@var）、FOR UPDATE、FOR SHARE、LOCK IN SHARE MODE；拒绝黑名单函数（见 technique.md）；拒绝引用系统库表：MySQL 方言 `mysql`、`sys`、`performance_schema`，Doris 方言 `mysql`、`__internal_schema`（`information_schema` 允许）；**数据源无默认库时**，未限定库名的表报错；正文有顶层 LIMIT 时拒绝（分页由服务追加）。解析失败一律拒绝并说明“解析器不支持该语法”。
   - 分页改写：NONE → 顶层追加 `LIMIT maxRows+1`；OFFSET → `LIMIT size+1 OFFSET n`；CURSOR → `LIMIT size+1`；计数 SQL：`SELECT COUNT(*) FROM (<去掉顶层 ORDER BY 的原句>) t`。改写在语法树上完成，保留 `?` 占位与绑定顺序。
   - 方言用一个小枚举或接口表达（MYSQL、DORIS），只放本卡需要的差异（系统库名单）；连接与账号检查不在本卡。
   - 定义 `QueryEngine` SPI 接口签名（technique.md 4.1）供下一张卡实现，本卡不实现 execute/check。
5. **测试**（JUnit 5，纯 Java，放在本模块 `src/test/java/com/paytech/platform/support/dataquery/auto/...`，不需要 Spring、数据库）：
   - `QueryTemplateEngineTest`：requirement.md 第 7 节两个示例（JDBC 渲染结果逐字比对；ES 示例只验证解析与 join 拼接，可用一个测试内的 JSON 绑定器）、全部语法、错误行列号、`' OR 1=1 --` 作为值时只进入 bindings、空集合、对象集合、片段非法值、系统参数拒绝、展开组合与覆盖集。
   - `ReadOnlySqlGuardTest`：requirement.md AC-06 负例（INSERT/UPDATE/DELETE、DDL、FOR UPDATE、INTO OUTFILE、多语句）、可执行注释 `/*! */`、黑名单函数、系统库引用、整实例下未限定库名、顶层 LIMIT、UNION 与 WITH 正例、分页改写与计数 SQL 的逐字结果。
   - 测试使用已有枚举/常量，不散落魔法字符串（JSON/SQL 样例除外）。
6. 编码规范要点：普通 class 不用 record（Context/DTO/DataObject 规则）；每个手写方法中文 JavaDoc；方法 ≤ 30 行、嵌套 ≤ 3 层、if 必须带 `{}`；构造器注入；异常用 `InvalidParameterException` / `BizCheckFailException` 等公共异常（看 `paytech-common-core`），不要为每种错误建异常类——但解析器内部可以用一个携带行列号与问题列表的结果对象返回问题，而不是抛异常，由你按可读性决定并在报告说明。

## 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech
./mvnw -q -pl platform-support/paytech-platform-support-data-query -am install -DskipTests   # 先装依赖（含 BOM）
./mvnw -pl platform-support/paytech-platform-support-data-query test
./mvnw -q -pl platform-support -amd test-compile -DskipTests   # 确认 platform-support 其余模块仍可编译
```

不要运行整个 reactor 的测试（会清理共享本地数据库）。若离线解析失败，报告具体缺失的构件，不要改 Maven 设置。

## 交付报告（中文）

改动文件清单；JSqlParser 版本与来源；设计取舍（解析器实现方式、问题对象 vs 异常、覆盖集算法）；测试命令与实际输出摘要（通过数/失败数）；未完成项和需要主会话裁决的问题。**完成后直接结束，不要等待后台事件。**
