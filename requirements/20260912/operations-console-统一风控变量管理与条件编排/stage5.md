# RCO-19 阶段 5：前后端综合验证

2026-09-13。本阶段本机综合验证完成，等待确认进入阶段 6 发布准备。

**一句话范围：验证 Risk、BFF、Web 三个应用，修复 3 个问题（2 个生产文件、1 个回归测试文件），本阶段新增 0 个接口、0 张表、0 个字段；未部署 test。**

## 1. 验证边界与结果

Web 使用生产构建，通过本机入口适配器调用真实 Operations BFF，再由 BFF 调用真实 Risk；Risk 连接已批准的本机 MySQL/Redis。Platform 登录及授权复用现有 HTTP Simulator，导航使用显式 Fixture，因此这次不是实际 Gateway Snapshot、Platform 注册或 test 环境端到端验收。

本次通过真实接口创建 4 个独占变量（2 常量、1 集合、1 字段映射）、1 个规则集及其 2 个版本、1 个决策流。新增定义后在同一组运行中的应用内保存、查询及回显成功；后续只为加载已发现问题的修复重启 Risk、刷新 Web。

| 验收范围 | 本阶段结果与证据 |
|---|---|
| AC-01/02/03/11/12 目录统一 | 页面创建共享币种，接口创建金额阈值、国家集合、国家字段映射；两处编辑器回显相同编码/类型。规则和开始节点不能选节点输出，规则集节点出口同时保留 NODE_MATCHED/NODE_DECISION。节点输出伪造创建被拒绝。 |
| AC-04 集合维护 | 页面验证非法格式第 4 行、重复值第 3 行定位，显式去重，再替换成员；中文展示名查询通过。修复后保存 200 → 仅使用新版本读取 200，无旧版本 409。 |
| AC-05 条件组合 | 通过真实 BFF 保存变量—变量、字段—集合、字面值—变量、字面值—字面值条件；更完整的类型/单位/区间矩阵复用阶段 4 回归。 |
| AC-06/07 校验与流程 | REJECT（Wire DENY）到下一规则集成功保存并在页面回显；未知决定在合法前置下返回 `VARIABLE_DOMAIN_MISMATCH`，定位到连线条件。目录故障显示重试，已有草稿保留并在恢复后成功保存。 |
| AC-08/09 历史与共享值 | 页面保存规则第 2 版；第 1 版仍可只读查看。阈值 8000→9000 后，原规则/流程返回最新值，规则内容 Hash、保存条件及流程 graph 保持；流程 Hash 不在 BFF 展示模型中，本项 Hash 不变另复用阶段 4 的真实 Risk 回归。历史页面标注“引用变量的当前值”。引用查询返回两个规则版本，删除被 409 阻止。旧 matched/Legacy 回放沿用阶段 4 完整回归。 |
| AC-10 权限及无执行事实 | READ 更新和无 DELETE 权限删除均返回 403；只读菜单无新建/编辑/删除，详情输入禁用、无保存按钮。5 类执行事实仍为 0。 |
| AC-13 test 示例迁移 | **留待 test 部署阶段**，本阶段未写 test，不能标为已完成。 |
| UI-01/02/03/04/06 | 浏览器检查列表概要、统一变量选择、集合反馈、详情与历史只读、当前值标识、浏览器返回上一规则列表。观察分辨率 1280×720，5 张页面证据见下文。 |
| UI-05 Pencil 方案 | 复用阶段 3 已确认的 7 张画板，本阶段未扩大页面方案。 |

## 2. 本阶段修复

1. **中文名称搜索 503。** MySQL 的 ASCII 编码列与中文关键词 LIKE 比较产生 `Illegal mix of collations`。仅在搜索表达式将编码转成 Unicode，并保留二进制大小写比较；不改表结构。新增真实 HTTP 回归覆盖中文名称、编码、`_/%/!` 字面匹配和空结果。
2. **集合保存成功后误报版本冲突。** 页面原来在收到新版本之前重新挂载成员列表，导致携带旧版本读取。现在用保存响应更新根版本/数量，再读取成员，并清除旧加载错误。
3. **只读详情部分控件可操作。** 常量值、布尔值及状态显式 `disabled=false` 覆盖表单禁用状态。现明确合并只读/保存中条件；浏览器确认可编辑文本框 0、可操作下拉框 0、保存按钮 0。修复前也没有越权保存成功。

本次增量文件：

- [Risk 搜索 SQL](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/resources/mapper/decision/RiskVariableMapper.xml)
- [Risk HTTP 回归](../../../paytech/risk-decision/paytech-risk-decision-test/src/test/java/com/paytech/risk/auto/ext/http)
- [Web 变量页面](../../../operations-console-web/src/pages/RiskVariables.tsx)

## 3. 验证及清理

修复后的 Risk 变量 HTTP/MySQL/Redis 回归 **18/18**，Web 相关测试 **13/13**，最终 Web 生产构建通过（保留现有大于 500 kB 的 chunk 提示）。根目录及三个应用 `git diff --check` 通过。阶段 4 的 Risk 254、BFF 734、Web 913 完整回归作为未受影响范围的既有证据，未重复声称本阶段重新执行全量。

[验证清单](./assets/stage5/verification.json)、[边界与 HTTP 记录说明](./assets/stage5/README.md)、[集合保存修复后的请求顺序](./assets/stage5/member-save-fixed.json)、[清理结果](./assets/stage5/cleanup.json)。

页面证据：[中文查询与只读列表](./assets/stage5/variable-search-readonly.png)、[只读定义](./assets/stage5/variable-detail-readonly.png)、[集合中文搜索](./assets/stage5/collection-readonly.png)、[历史当前值](./assets/stage5/rule-history.png)、[流程决定值](./assets/stage5/flow-decision-enum.png)。

独占联调数据和准确命令回执已清理；原 17 个变量、9 个成员保留，示例阈值仍为 8000、币种 USD。临时进程和浏览器页已关闭，共享基础设施保持运行。本轮没有新增 SQL 应用、制品发布、test 部署、示例迁移、Git 提交或推送。

## 4. 下一确认点

确认本阶段结果后，进入阶段 6：形成具体的 test 发布清单、SQL/注册启用顺序、三个应用制品及限定示例迁移方案。发布准备完成后再次确认，才进入部署阶段。
