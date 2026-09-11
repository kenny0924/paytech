# RCO-14 运营规则编辑 V2：Platform Support 影响评估

- 日期：2026-09-10。
- 性质：Assessment / 条件性建议，不是合同批准、功能实现或上线验收。
- 范围：Platform Permission、PLT-I02、Console Resource、公共 Route/Guard、PLT-I20 Snapshot 和目录 SQL。
- 需求来源：[RCO-14 需求与链路评估](../2026-09-10-risk-rule-editor-v2-requirements-assessment.md)。已核对
  [design.pen](../../../operations-console-web/docs/design.pen) 的 `bi8Au`、`wO9RC`、`AXojG`、`fulZG` 四个画板及
  [对应预览](../../../operations-console-web/docs/design-previews/)。
- 现行权威：[Console 集成设计](../../../docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md)、
  [公共控制面设计](../../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)和
  [应用架构](../../APPLICATION_ARCHITECTURE.md)。Risk 的规则、Reject 和 Action 业务语义仍由 Risk Owner 确认。

## 1. 结论

**推荐最小方案：Risk 扩展现有 Authoring Context、Rule Set Detail/Save 合同，外部仍使用现有九条 Route；Platform
Support 对 RCO-14 无需修改生产 Java、Schema、初始化/增量 SQL、Permission、Console Resource、Guard 或 Snapshot
Wire，只需回归现有目录和授权链。相对工作量为小。**

该结论要求同时满足：

1. V2 的 Reject、多个受控 Action、参数和非终态表达随 Risk-owned 合同读取，并在一次完整新 DRAFT Save 中保存；
2. 页面读取继续使用 `RISK_DECISION_CONFIG_READ`，完整保存继续使用 `RISK_RULE_SET_SAVE`；
3. Authoring Context、Detail、Save 的 Method/Path 不变，Save 实际 UTF-8 Body 不超过 65,536 Bytes；
4. V2 没有“立即冷却主体”或“立即添加标签”的独立命令；画板中的动作只表示规则配置；
5. 现有 Freeze、TEST Candidate Create/Validate 生命周期和三项权限不变。

Platform 不解释 Rule AST、Reject Code/Message、Action 类型/参数、FIRST_TERMINAL 或 Hash，也不把这些内容放入
Permission、Console Resource 或 Snapshot。配置目录与真实副作用执行必须分开授权和评估。

### 1.1 当前实现状态不能混用

本次核对的 `platform-support-service` 位于本地 `main`、HEAD `470365a`。五项 Risk Permission、六个 Console
Resource、九条 Route、65,536 上限及其测试存在于当前未提交工作区；它们是源码评估证据，不是已提交、已应用或
已部署证明。当前状态台账仍记录 `mor-test` 为 27 Route / 15 Resource / 14 ACTIVE Permission，Risk Operations
UI/BFF/Gateway/PLT-I02 联调与部署仍是前置。

因此本报告的“无 Platform 生产改动”准确含义是：**相对于已设计的 RCO-I12 Platform 目录，RCO-14 不再增加
Platform delta**；RCO-I12 自身尚未完成的提交、数据库应用和 rollout 不能被本结论跳过。

## 2. 现有能力与可复用证据

### 2.1 五项 Permission

当前工作区初始化目录在
[`platform-support-initial-data.sql`](../../../platform-support-service/docs/database/sql/platform-support-initial-data.sql)
第 203–207 行定义：

| Permission | 当前用途 | RCO-14 结论 |
|---|---|---|
| `RISK_DECISION_CONFIG_READ` | Authoring Context、Rule Set/Candidate list/detail | 复用 Reject/Action 目录读取和 V2 历史回显 |
| `RISK_RULE_SET_SAVE` | 保存完整新 Rule Set Version | 复用条件、顺序、启用、Reject 配置、多个受控 Action、参数和非终态规则的整体保存 |
| `RISK_RULE_SET_FREEZE` | Freeze | 不变；Risk 扩展后的完整不可变闭包仍由 Freeze 校验 |
| `RISK_TEST_CANDIDATE_CREATE` | 创建 TEST Candidate | 不变 |
| `RISK_TEST_CANDIDATE_VALIDATE` | 执行静态检查 | 不变；新增字段的静态检查由 Risk 扩展，不由 Platform 解释 |

`PLATFORM_ADMIN` 拥有五项，`OPERATIONS_VIEWER` 仅拥有 READ（同文件第 229–234 行）。RCO-14 不要求把 Reject、
Cooldown、Tag 各拆成一个 Permission；它们仍是同一个完整版本 Save 的业务字段。

### 2.2 PLT-I02 与 Navigation

[`OperationsAuthorizationApi`](../../../platform-support-service/platform-support-api/src/main/java/com/paytech/platform/support/api/authorization/OperationsAuthorizationApi.java)
第 14–25 行表明 PLT-I02 只接收 Gateway 提供的可信 Actor、Session 和 `routeCode`。当前授权实现会校验 Session、
Route 的当前状态与 HUMAN_PERMISSION 绑定、ACTIVE Permission 和实时角色授权，拒绝时 Fail-Closed；Navigation
按同一当前授权过滤 MENU/BUTTON。对应实现与 SQL 证据是：

- [`OperationsAuthorizationServiceImpl`](../../../platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/OperationsAuthorizationServiceImpl.java)
  第 36–86 行；
- [`OperationsDirectoryMapper.xml`](../../../platform-support-service/platform-support-access-control/src/main/resources/mapper/access-control/OperationsDirectoryMapper.xml)
  第 244–316 行。

PLT-I02 不读取请求 Body，不能在同一个 Save Route 内判断操作员选择了哪一种 Action 或参数。若未来需要字段级或
Action 级不同授权，必须先由 Risk/BFF 把它定义成独立业务命令和精确 Route；不能要求 Platform 解析策略正文。

### 2.3 Console Resource

当前六个 Risk Resource 是一级“风控管理”、`OPERATIONS_RISK_RULE_CONFIGURATION` 页面，以及 Save、Freeze、
Create Candidate、Validate 四个按钮；初始化目录见 `platform-support-initial-data.sql` 第 252–257 行。

RCO-14 的展开/收起、拖动、条件控件、恢复默认 Reject Message、添加/删除 Action 和“不产生决策”都是页面内编辑
行为，不应逐个创建 Console Resource。页面继续使用现有 Component Key，保存栏继续使用
`OPERATIONS_RISK_RULE_SET_SAVE`。按钮显隐只是体验，最终权限仍由 BFF 调 PLT-I02 决定。

### 2.4 Route、Guard 与 Snapshot

当前九条精确 Route 位于 `platform-support-initial-data.sql` 第 550–560 行：五条 GET、Save、Freeze、Candidate
Create 和 Validate。它们都使用现有 `operations-console-bff` Target、`OPERATIONS + OPERATIONS_JWT +
HUMAN_PERMISSION`、`DROP/NONE` Cookie；Save 为 65,536 Bytes，其他三个命令为 16,384 Bytes，GET 为零 Body。
Header、Query 和每 Route 六项 Guard 分别位于第 562–596、598–607、609–663 行。

Platform 的 PLT-I20 是完整、无分页的当前有效 Target/Route/子项投影；Snapshot 不包含 Console Resource、Role、
Reject/Action 目录或规则内容。生产 DTO
[`GatewayRouteSnapshot`](../../../platform-support-service/platform-support-api/src/main/java/com/paytech/platform/support/api/gatewayconfiguration/snapshot/GatewayRouteSnapshot.java)
只发布路由元数据。完整读取、编译和组装分别见：

- [`GatewaySnapshotServiceImpl`](../../../platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/GatewaySnapshotServiceImpl.java)
  第 25–40 行；
- [`GatewayConfigurationCompiler`](../../../platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/gatewayconfiguration/GatewayConfigurationCompiler.java)
  第 93–125、199–319、435–503 行；
- [`GatewaySnapshotAssembler`](../../../platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domain/gatewayconfiguration/GatewaySnapshotAssembler.java)
  第 22–45 行。

业务 JSON 的字段扩展不改变 Snapshot Schema。只要 Method/Path、权限、Header/Query、限额、超时和 Guard 不变，
Platform Route/Snapshot 生产逻辑无需感知 V2。

## 3. V2-01 至 V2-08 的 Platform 判定

| 需求 | Platform 判定 | Owner / 约束 |
|---|---|---|
| V2-01 列表展开/收起、选中编辑、未保存 | 无生产改动 | Web 内存状态；不创建 Platform Draft、Session 字段或 Resource |
| V2-02 增删、排序、名称、启用 | 复用 SAVE | 一次完整新 DRAFT；启用开关不是 Platform 即时状态命令 |
| V2-03 条件和嵌套组 | 复用 READ + SAVE | AST、NOT、Decimal 和目录校验由 Risk；Platform 只授权/转发 |
| V2-04 PASS/REVIEW/REJECT | 无 Permission/Route 变化 | Web/Risk 映射现行 Decision Code，Platform 不增加枚举 |
| V2-05 Reject Code/Message | 推荐随现有 Authoring Context 读取、Save 保存 | 目录、默认文案、版本冻结、历史和披露由 Risk；不得复制到 Platform |
| V2-06 多个附加 Action/参数 | 配置阶段复用 READ + SAVE | 白名单、参数类型、主体字段、顺序/去重/冲突和 Handler 均由 Risk |
| V2-07 不产生决策 | 无 Platform 执行变化 | 非终态、继续匹配、默认决定和静态检查由 Risk；不代表运行期已实现 |
| V2-08 描述与保存新版本 | 只读描述无变化；持久描述仍复用 SAVE | 是否新增持久字段由 Risk 决定，不进入 Console Resource 或 URL |

## 4. 配置规则与直接执行动作的权限分界

### 4.1 本需求内：配置未来规则行为

运营人员在 Rule Set 中选择“冷却主体”或“添加风险标签”，本质是编辑 Risk 配置并保存一个完整新 DRAFT。即使
该配置未来会产生副作用，当前命令仍只创建规则版本，不立即修改任何主体。因此：

- 使用 `RISK_RULE_SET_SAVE`；
- 使用现有 Save Route 和 Save 按钮；
- 不新增 `EXECUTE_COOLDOWN`、`APPLY_TAG` 等 Permission；
- Freeze、Candidate、验证、激活和真实执行仍是后续独立生命周期。

### 4.2 本需求外：立即对业务对象执行副作用

“立即冷却某主体”或“立即给某主体加标签”是另一项高风险业务命令，不是 Save 的细粒度变体。当前系统没有为它
实现授权合同，不得借 `RISK_RULE_SET_SAVE` 执行。若未来单独批准，至少需要：

1. Risk Owner 定义精确对象类型、资源授权、不变量、幂等、审计、撤销/恢复和错误合同；
2. 独立 BFF command endpoint 与精确公共 Route；
3. 独立 HIGH/CRITICAL Permission（Code 在业务合同冻结前不预造）；
4. 仅在 UI 确有独立按钮时新增 BUTTON Resource；
5. PLT-I02、撤权、目标越权、重放和真实副作用的端到端验证。

真实 Handler、Shadow、审批、激活、生产 Release 和运行期 Evaluation 都不能从四个画板推导授权。

## 5. 条件触发的 Platform 改动

### 5.1 推荐：复用现有路径

如果 Reject/Action 目录进入现有 Authoring Context，V2 内容进入现有 Detail/Save：

- 生产 Java：无；
- Schema / PDManer：无；
- 初始化 DML / 增量 SQL：无；
- Permission / Role Grant：无；
- Console Resource：无；
- Route/Header/Query/Guard：无；
- PLT-I20 Snapshot Wire：无；
- 工作内容：既有目录、PLT-I02、Navigation、64 KiB 和完整 Snapshot 回归。

### 5.2 新增独立目录查询

若 Risk/BFF 不复用 Authoring Context，而新增一个目录 GET，Platform 的精确范围是：

1. **Permission：** 复用 `RISK_DECISION_CONFIG_READ`，除非 Owner 证明目录含另一类受限能力；不因为“新 URL”新增
   Permission。
2. **Route：** 每个确定的 Method/Path 新增一个稳定 Route Code；沿用现有 BFF Target、OPERATIONS、
   OPERATIONS_JWT、HUMAN_PERMISSION、READ、DROP/NONE、Body 0、5s/10s 和现有六项 Guard。
3. **Header/Query：** GET 仅允许现有认证/CORS/Origin 合同；Query 必须由 Risk/BFF 给出精确名称，未声明则为空。
4. **Approved Root：** 优先放在既有 `/decision-rule-sets/` 或 `/decision-releases/` 下；若新建根，Gateway 还需
   修改 Approved Target Registry 和负例门禁，不能由 Platform 单独完成。
5. **Console Resource：** 仍在同一规则配置页内消费时不新增；目录本身不是菜单或按钮。
6. **SQL：** Fresh Database 的唯一初始化 DML和代码冻结的 Canonical Oracle都要加入完整 Route/Header/Query/Guard
   tuple；既有环境使用独立、日期化、可重放且遇漂移回滚的手工增量 SQL，建议路径为
   `platform-support-service/docs/database/sql/changes/2026-09-10-risk-rule-editor-v2-catalog.sql`。新增 Route 初始
   `DISABLED`，Java、测试和启动流程不得自动应用。

精确 Method/Path/Query 未由 Risk/BFF 冻结前，不分配 Route ID/Code，也不先写 SQL。

### 5.3 显式 V2 Path

如果选用 `/v2` 而不是 V1 同路径兼容扩展：

- 每个 distinct Method/Path 增一条 Route；V1 Route/Code 先保留，不原地复用成无关能力；
- 权限按操作类别继续映射 READ/SAVE/FREEZE/CREATE/VALIDATE，不按 API 版本复制权限；
- Route Header、Query、六项 Guard 和 Body 上限逐条完整配置，不能从 V1 隐式继承；
- Save 仍最多 65,536，其他命令仍 16,384，GET 仍零 Body，除非真实样本证明并另行批准提高上限；
- Snapshot 仍可使用 `schemaVersion=1`，因为只是目录内容增加，不是 Snapshot 协议升级；
- V1/V2 每个 Route Code 形成独立 Guard Key，需由 Platform/Gateway确认并存时的合计容量；
- Fresh DML、Canonical Oracle、既有环境增量 SQL、Snapshot、Gateway exact-match 和 rollout 测试同步扩展。

### 5.4 细粒度字段权限或提高 64 KiB

PLT-I02 只按 Route Code 授权，因此同一个 Save Body 不能对 Reject、Cooldown、Tag 做三套 Platform 权限。若确需
分权，先拆业务命令/Route，再设计 Permission；不在 Platform 增加 Payload Parser。

65,536 是 Platform Compiler、Gateway Validator 与 BFF 配置协议的共同绝对上限。RCO-14 当前没有最终 V2
Payload 测量证据，不自动提高。若后续样本证明不足，必须由跨组件合同同步调整并重新评估 Nginx 外层边界。

## 6. 兼容、数据和安全影响

1. **历史业务数据不由 Platform 迁移。** Platform 不存 Rule Set、Action、Reason、Hash、Candidate 或幂等记录，
   不重算 V1 数据。V1/V2 Descriptor、Hash Schema 和旧同键重放由 Risk 负责。
2. **Risk 目录不能转移到 Platform。** Reject 默认文案、Action/参数目录、标签、单位、主体字段绑定和 Handler
   登记是 Risk 业务事实。不得借 Console Resource、Permission、通用字典或 Route 元数据保存。
3. **授权继续实时。** JWT 不携带 Permission；BFF 每次使用 Gateway Route Code 调 PLT-I02。Permission/Role 撤销
   后下一次请求必须拒绝，页面按钮显隐不能代替该检查。
4. **策略内容不进入授权审计。** Platform 的授权/审计只记录受限 Actor、Session、Route、Permission、结果、
   Request ID 和时间等元数据；不得记录 Reject Message、AST、Literal、阈值、标签或 Action 参数。
5. **Snapshot 保持最小。** 只发布 ACTIVE Target/Route/Header/Query/Guard；Console Resource 和 Risk 目录都不进入
   Snapshot。任何非法新行拒绝整份 Platform 响应，不返回部分配置。
6. **Route 状态不是业务版本。** 新 Route 初始 DISABLED、V1/V2 共存或下线只影响入口可达性，不代表 Risk
   Rule Version 被迁移、激活或回滚。

## 7. 验证门禁

### 7.1 推荐“无生产改动”方案仍需执行

| 门禁 | 复用位置 | RCO-14 需要证明 |
|---|---|---|
| 五 Permission / 六 Resource / 九 Route 精确目录 | [`OperationsPersistenceContractAutoTest`](../../../platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java) 第 1868–1984 行 | V2 不改变既有 tuple、Role Grant、Header/Query/Guard 和旧 Route 上限 |
| PLT-I02 / Role / 撤权 | [`OperationsAuthenticationHttpAutoTest`](../../../platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationHttpAutoTest.java) 第 925–944、1055–1100 行 | Admin 五项、Viewer 只读、自定义角色和撤权后立即拒绝 |
| Navigation | [`OperationsHttpChecker`](../../../platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsHttpChecker.java) 第 184–247 行 | 同一页面/按钮 Resource；子控件不新增目录项 |
| 完整 Snapshot / 64 KiB | [`GatewaySnapshotHttpAutoTest`](../../../platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java) 第 93–210、229–246 行 | 九 Route 完整投影；65,536 接受、65,537 整体失败；Schema 仍为 1 |
| Compiler | [`GatewayConfigurationCompiler`](../../../platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/gatewayconfiguration/GatewayConfigurationCompiler.java) 与其 AutoTest | Permission XOR、精确 Path、Header/Query、Guard、重叠和 DISABLED 行 |
| 保密 | Platform HTTP/审计日志回归 | 成功、拒绝、异常都不含 Reject Message、规则内容或 Action 参数 |

数据库测试必须复用已手工准备的 repository-owned Local Environment；不使用 Flyway、Testcontainers 或启动 Seed。
当前工作区 README 仅记录 RCO-I12 目录的 DB-free 70/70，真实数据库目录、完整 `clean verify`、覆盖率和跨系统
链仍有前置，不能由本评估冒充已通过。

### 7.2 新目录或 V2 Path 的增量门禁

- Canonical 初始化 DML、代码冻结 Oracle 和增量 SQL 对 Route/Permission/Resource/Header/Query/Guard 做完整 tuple
  比较；同内容重放安全，ID/Code/内容漂移回滚，不重新启用人工停用项；
- 新 GET Body=0、Query Name 白名单、未知/重复输入 Fail-Closed；
- V1 与 V2 exact route 共存，无固定/变量 Path 冲突；旧九条 Route 完整回归；
- 新 ACTIVE Route 进入完整 Snapshot，DISABLED Route 不进入；无效新目录不产生部分 Snapshot；
- Gateway 证明 Approved Root、Snapshot 原子切换和 Last Valid；BFF 证明 PLT-I02 先于 Risk，拒绝时 Risk 零调用；
- 联合链覆盖 READ/SAVE 权限分离、撤权、新旧路径、Body 边界、安全日志和 rollout/撤回。

## 8. 依赖顺序与相对工作量

建议后续顺序（需要独立实现授权）：Risk 冻结 V2 Wire/Descriptor、Reject/Action/参数与兼容策略 → BFF/Web
提供准确 Method/Path/Query、Permission 诉求和 UTF-8 样本 → Platform 判定是否存在目录 delta → Gateway 核对
Approved Root/Route/Guard → 完成 PLT-I02 与 Risk 的联合验收。数据库应用、Route 激活和部署仍需单独授权。

| 条件 | Platform 相对工作量 | 原因 |
|---|---|---|
| 同路径复用现有目录 | 小 | 零生产/数据改动，主要是目录、授权、Snapshot 和安全回归 |
| 一个或少量根内目录/版本 Route | 小至中 | 增加 DML/Oracle/增量 SQL、完整 Route 子项、数据库与 rollout 门禁；通常不改 Java/Schema |
| 成套 V2 Route 并存 | 中 | 每条 Route 的权限、Header/Query/六项 Guard、容量与 V1 共存都需精确验证 |
| 字段级权限或直接 Action 命令 | Platform 中；全链路大，需重新设计 | 现有 Route-Code 授权不能解析 Payload；涉及 Risk 资源授权、副作用、幂等和审计 |
| 提高公共硬上限 | 需重新评估 | 跨 Platform/Gateway/BFF/Nginx 的资源与安全合同，无当前量化依据 |

工作量不包含 RCO-I12 尚未提交/部署的基础目录，也不包含 Risk 数据模型、Hash/静态检查或真实 Handler 实现。

## 9. 待确认问题

1. **Risk：** Reject/Action/参数目录是否并入现有 Authoring Context？推荐并入；若独立 Endpoint，请提供准确
   Method/Path/Query、响应上限和版本合同。
2. **Risk + BFF：** V2 使用原 `/v1` 兼容扩展、独立 `/v2`，还是新 Descriptor？旧严格 Decoder、历史详情和同键
   重放怎样保持可读？
3. **Risk + Product：** Reject Message 是否只用于内部记录，默认值/覆盖值如何随版本冻结，历史未知 Code 如何
   展示；Platform 不参与外部文案映射。
4. **Risk：** 第一批 Action 白名单、参数类型/上限、可绑定主体字段、重复/冲突/顺序/合并以及非终态语义是什么？
5. **Product + Security：** 是否接受“配置动作仍使用 SAVE，直接执行动作另行授权”的分界？若不接受，需要先拆出
   独立用例，不能在同一个 Save Route 上做字段权限。
6. **Web + BFF + Risk：** 典型、P95 和字段上界的紧凑 JSON 分别多少 UTF-8 Bytes？无测量证据时保持 65,536。
7. **Integration Owner：** 目标环境何时完成 RCO-I12 五 Permission、六 Resource、九 Route 和 64 KiB 前置？RCO-14
   不得在该基础未交付时被描述为“Platform 已就绪”。
8. **Risk：** 真实冷却/标签 Handler、Shadow、审批、激活和生产执行何时另行设计？当前没有已实现的直接执行授权。

## 10. 本轮交付边界

- 已核对需求、四个画板、现行 Owner 文档和当前源码/测试证据；结论不依赖其他系统先完成报告。
- 本轮未运行 Platform Maven、真实 HTTP/MySQL/Redis 或 Snapshot Smoke；未测量最终 V2 Payload。
- 仅新增本评估文件；未修改业务代码、测试代码、共享规范、requirements、状态台账、Schema、SQL 或部署配置，
  未提交、推送或部署。
