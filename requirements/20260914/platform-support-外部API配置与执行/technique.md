# 外部 API 配置与执行：技术说明

本文服务于[现行需求](./requirement.md)，说明分层、系统边界、历史资料取代关系与待决项。表结构见[数据设计](./data-design.md)，执行过程见[流程设计](./flow-design.md)。

## 1. 分层

```text
Payment 等业务系统 ──内部 HTTP──▶ Platform 标准 API 入口
                                   │  按 (integration, standardApi) 加载渠道实现（一次只读加载）
                                   ▼
                          flow_definition_json
                          ├─ initializers  依赖初始化 → resources
                          ├─ nodes         原子 API 节点 → steps
                          ├─ edges         条件连线（Aviator，按 priority）
                          └─ return        最终返回映射 → executeStatus + outputs
                                   │
                                   ▼
                          渠道原子 API（definition_json）── 单次外部协议操作 ──▶ 外部渠道
```

| 层 | 职责 | 不负责 |
|---|---|---|
| 标准 API | 统一业务请求/响应字段 | 渠道协议细节 |
| 渠道标准 API 实现 | 初始化依赖、组合原子节点、条件分支、返回映射 | 资金反向操作、跨调用恢复 |
| 渠道原子 API | 一次外部操作的组装、脚本、传输、解析、结果映射 | 调用其他 API、自动重试 |
| 公共脚本/注册函数 | HTTP 工作对象加工、签名加密等受控能力 | 任意程序、SQL、任意 URL |

标准 API 模块是 Platform 内部的代码与配置分组，不新增部署服务或第七核心系统。

## 2. 系统边界

| 系统 | 职责 | 本需求变化 |
|---|---|---|
| Platform Support | 六表配置、标准 API 内部 HTTP 入口、流程执行 | 由“单次执行”扩展为“标准 API 同步流程执行”，见 [2026-10-13 决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md) |
| Payment | Channel/Account/MID/Capability 五表、路由与冻结身份、交易与资金状态、业务恢复 | 不变；调用 Platform 标准 API；已实现的[账号与 MID 查询](../../20260921/payment-渠道账号与MID能力查询/requirement.md)不迁移 |
| Operations Web/BFF | 配置维护页面与管理请求适配 | 页面待按六表结构后续重设计 |
| Vault / Card | 秘密与卡材料受控访问 | Vault 仅为 Platform 新增 `CHANNEL_ACCOUNT_CONFIG_V1` 的 `CHANNEL_EXECUTION` Reveal（[决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)）；Card 不变 |
| Gateway | 入站路由 | 不变；Integration 是出站调用配置，与 Gateway Target/Route 不同 |

事务约束：Platform 在短只读事务中一次加载配置，事务结束后才执行初始化函数、外部通信与流程；Payment 在本地事务外调用 Platform，结果另开短事务落库。

## 3. 命名区分

`t_integration` 表示外部接入方（渠道等出站对象），与 Platform 既有的 Client **Integration Readiness**（PLT-I13：Client API Credential、Endpoint、Subscription 的准备度）无关。物理设计阶段评估是否改名为 `t_external_integration` 等更明确的名称；在此之前文档统一用“接入方”描述，避免混淆。

## 4. Operations 页面差异

2026-10-13 用户确认后，`design.pen` 中 30 张前序画板（名称以 CAPI、GAPI 开头）已删除；本目录 `assets/pencil*` 中的历史 PNG 保留，原画板可从 git 提交 `fff35d2` 的 `operations-console-web/docs/design.pen` 恢复。

本目录 `assets/pencil`、`assets/pencil-scripts`、`assets/pencil-models` 截图描述的是前序结构：接口配置、数据模型（M01～M08）、公共脚本（S01～S05）。与现行设计的差异：

- “数据模型”页及模型引用由共用字段表取代；
- 没有标准 API 维护页与渠道标准 API 流程编辑页；
- 响应场景/响应合同改由原子 API 结果映射与流程返回映射表达。

现行页面方案见[页面方案](./page-design.md)，第一版画板 PNG 在 `assets/pencil-v2/`；历史截图保留原日期与适用范围。

## 5. 历史资料与取代关系

| 资料 | 性质 | 现行替代 |
|---|---|---|
| [legacy-requirement.md](./legacy-requirement.md)、[legacy-technique.md](./legacy-technique.md) | 2026-09-14 原需求与逻辑评审稿 | 本目录 `requirement.md`、`technique.md` |
| [generic-execution-amendment.md](./generic-execution-amendment.md) | 通用执行边界 | 输入/返回/无持久化语义保留；“单次一个目标操作”被流程两层取代 |
| [platform-http-execution-amendment.md](./platform-http-execution-amendment.md) | 2026-09-21 Platform 归属与 HTTP 边界 | 第 1～4 节边界保留；第 5 节单次限制、第 6 节五表被取代 |
| [model-library-and-response-scenes.md](./model-library-and-response-scenes.md) | 公共模型与响应场景 | 被共用字段表取代；`response_contract_id` 不再是现行关联 |
| [public-script-http-context-design.md](./public-script-http-context-design.md) | 公共脚本与 HTTP 工作对象 | 脚本阶段与工作对象权限、原始响应验签和最终发送字节约束继续适用 |
| [model-and-mapping-design.md](./model-and-mapping-design.md) | 取值方式与映射 | 上下文路径/Aviator/注册函数三种取值继续适用；三表方向被取代 |
| [design.md](./design.md)、[api.md](./api.md)、[ui.md](./ui.md) | Payment 管理落点、管理 Wire 草稿、页面 | 历史；管理 Wire 需按六表重做 |
| [definition-contract.md](./definition-contract.md)、[capability-contract.md](./capability-contract.md)、[registry-contract.md](./registry-contract.md)、[query-contract.md](./query-contract.md) | 前序配置/能力/函数合同 | 类型、精度、受控能力原则可参考；不可作为新 JSON 导入定义 |
| [ni-refund-example.md](./ni-refund-example.md)、`assets/*.json`、`assets/*.py` | NI 样例与静态检查 | 只证明前序样例自洽，不证明新执行器 |
| [gap-analysis.md](./gap-analysis.md) | 2026-09-14 现状差异 | 历史 |
| [db/README.md](./db/README.md)、`db/**/*.sql` | 旧 Payment DDL 与 Platform 管理注册 DML | **禁止执行**；不作为六表 Schema 来源 |
| [delivery.md](./delivery.md) 第 1～22 节 | 历史阶段证据 | 保留原日期与范围 |

## 6. 待决事项

1. 原子 API `definition_json`：映射、脚本、传输、解析与状态规则的完整结构。2026-10-13 [结构](./integration-api-definition-contract.md) A1～A7 已确认；注册函数签名与编码细节随函数目录细化。
2. `flow_definition_json`：入口、初始化依赖、节点身份、返回映射、循环/执行预算及字段缺失处理，以及静态校验。2026-10-13 [结构](./flow-definition-contract.md) Q1～Q6 已确认，节点已按 A3 增加 `params`。
3. Payment → Platform 内部请求响应合同、调用授权、Problem 映射。2026-10-13 [内部接口](./internal-api.md) W1～W6 已确认（含第 4 项超时；敏感日志暂不处理，后续统一处理）。
4. 两段 HTTP 超时：已在[内部接口第 5 节](./internal-api.md#5-超时两段-http)确认。
5. 账号材料交接：初始化函数参数、Caller/Purpose、内存与日志边界。2026-10-13 [交接方案](./material-handoff.md)已确认：Payment 传 Vault 引用 Token，Platform 直接 Reveal，见[授权决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)。
6. 字段路径转义、根数组、多值 Header、条件必填。数值精度、并发编辑、配置大小与路径长度已在[物理设计](./physical-design.md)确定。
7. 六表物理类型、索引、模块 Owner 与 `t_integration` 命名；获准后同步 Platform PDManer/SQL。2026-10-13 [物理设计](./physical-design.md) P1～P7 已确认（白名单暂不做，基础地址入库）；同日六表进入 Platform PDManer 与完整 Schema，增量 SQL 已写，均未执行。
8. Operations 标准 API 与流程编辑页面设计。2026-10-13 [页面方案](./page-design.md) U1～U7 已确认，24 张画板见其第 11 节；2026-10-13 起草[管理接口与注册数据评审稿](./admin-api.md)（30 个端点、344 行注册 SQL），待确认 R1～R7。

以上细节未定前不得自行补成已确认合同。
