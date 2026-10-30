# 2026-10-29 决定：数据查询增加联合查询（按业务键类型逐层展开）

- Decision status: Confirmed（2026-10-29 用户在讨论中确认 J1—J8，“继续”接受 Q1—Q7 建议）
- Implementation status: Implemented（需求 [platform-support：联合查询](../../requirements/20261029/platform-support-联合查询/requirement.md) 阶段 1—8 完成，含修订 b 已加载对象跳过、修订 c 按对象合并显示与子行键类型）
- Deployment status: Deployed to mor-test（2026-10-30，见 CURRENT_IMPLEMENTATION）
- Decision date: 2026-10-29
- 依托：[2026-10-22 数据查询决定](./2026-10-22-data-query-into-platform-support.md)（数据源、只读校验、系统库拒绝、出参屏蔽、`DATA_QUERY_EXECUTE`、不做查询记录全部沿用，本决定不新增跨库读取例外）。
- 详细设计：[design-discuss/joint-query/design.md](../../design-discuss/joint-query/design.md) 与 `joint-query.pen` 四张画板。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

运营排查一笔交易、退款或争议时，需要在 acquiring、payment、billing、account、ledger、reconciliation、channel-connector 等多个系统里逐个查单，再用查到的 ID 去下一个系统查。data-query 已有只读数据源与模板，但每次只能执行一个模板。

## 2. 决定

| # | 决定 |
|---|---|
| J1 | 联合查询复用 data-query 模板，不另建一类“联合查询 SQL”。联合查询配置与模板声明分开：存于模板版本新列 `joint_json`（行主体键类型、键参数、列标注），参数与列声明不变，编辑器单独页签维护；随模板版本保存、对比与恢复（2026-10-29 阶段 3 修订）。 |
| J2 | 键类型按业务语义定义（如 `ORDER_ID`、`PAYMENT_INTENT_ID`），由 Platform data-query 维护注册表 `t_query_key_type`（含入口校验正则 `value_pattern` 与提示 `input_hint`）；不靠列名匹配。 |
| J3 | 输出列分三种角色：链路键 `LINK`（参与展开）、维度键 `DIMENSION`（只展示，可点击另起一次查询，不自动展开）、普通列；默认不展开。角色由每个模板的列标注决定；键类型只提供默认角色（如 `CLIENT_ID` 默认维度，入驻类模板可标为链路），标为链路而默认维度时编辑器提醒（2026-10-29 阶段 3 修订）。 |
| J4 | 只有“恰好一个必填参数绑定键类型、其余参数可选或有默认值”的 ACTIVE 模板参与联合查询；键参数建议为 LIST，同层同键类型的多个值合并为一次执行。不符合的模板保存不报错，只给出“不参与”的原因。 |
| J5 | 按键类型图自动推导展开路径，不做预设链路方案；提供执行前的可达预览。 |
| J6 | 展开受护栏约束（最大层数、单键扇出、单模板行数、总执行次数、并发、总超时），触发即停止新执行并返回部分结果与截断说明；单个模板失败只影响该节点。 |
| J7 | 入口由用户选择键类型后输入单号，不做自动识别。 |
| J8 | 结果按系统 / 模板分组展示，并配链路图；每条数据带来源（模板、触发键值、来源行）。链路图按行主体键类型定位：泳道为其分组 `group_name`（数据维护，不写死系统名与颜色，入驻、出款、税务等链路新增分组即可），顺序只由键类型 `sort_no`（业务流程顺序）决定，与从哪个单号进入、离输入几跳无关（2026-10-29 阶段 3 修订）。展开双向，上游对象由“按自身 ID 查”的模板带出。 |
| J9 | 展开规则与执行在 Platform data-query 模块内（参与条件、键规范化、入口校验、LINK 提取、单步并发与预算），**按层分步执行**：每次请求执行一层并返回新发现的链路键，已访问键与累计护栏由 Operations 页面维护并逐层调用（2026-10-29 阶段 2 修订：Gateway 路由响应超时硬上限 10 秒）。Operations BFF 同路径转发；首期调用方只有 `operations-console-bff`。执行不写 `mor_platform`，结果只供排查，不作为业务执行依据。 |

## 3. 已接受的建议（Q1—Q7）

- Q1 护栏默认值（阶段 2 按分步执行修订）：服务端单步——单键值数 50、单模板行数 100、单步执行数 20、并发 8、预算 8 秒；页面累计——层数 6、模板执行总数 60、总时长 60 秒；可配置，mor-test 跑真实链路后调整。
- Q2 `EXTERNAL_TRANSACTION_ID` 首期当作全局键展开，结果显示渠道列供人工辨别。
- Q3 `CLIENT_ORDER_ID` 可以作为入口；命中多个 Client 的订单时全部展开，并在结果顶部提示。
- Q4 多态引用列（`ref_type + ref_id` 等）由模板 SQL 按类型过滤或拆列，首期不做“按另一列值决定键类型”。
- Q5 同一模板被不同键触发时分别执行，不合并结果，按触发键分组展示。
- Q6 不新增执行权限，沿用 `DATA_QUERY_EXECUTE`；键类型维护沿用 `DATA_QUERY_TEMPLATE_MANAGE`。
- Q7 模板由各系统 Owner 维护；首期按讨论稿走数链路在 mor-test 配置一套演示模板。

## 4. 影响

- Platform Support：data-query 新增键类型表、模板标注校验、键类型图与联合执行；新增管理与执行接口、注册块、语言包。
- Operations BFF / Web：转发接口；新增“联合查询”“键类型”页面，模板编辑器增加键类型与角色选择。
- 其他系统：不改代码；其表结构是模板的读取对象，表结构变化时由 Owner 同步自己的模板。
