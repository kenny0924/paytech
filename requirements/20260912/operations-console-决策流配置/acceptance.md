# RCO-17 综合验证记录

状态：阶段 5 综合验证已完成并获用户“可以”确认；后续见[阶段 6 发布包](./release.md)。两项补充均已获“都同意”授权并完成，结论与本轮证据见文末；前文保留首轮发现和收尾过程。当前环境和实施状态以 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

## 已复用的开发证据

[精简修订开发检查](./simplification-development-checks.md) 的 Risk 208 项、最终定向 8 项、BFF 1,051 项、Web 900 项、模型/Schema 30 项证据继续适用于未改动的业务逻辑。本阶段没有重复计数或声称重新执行这些全量检查。它们覆盖真实 Risk HTTP/MySQL/Redis、BFF 入口授权与严格转发、模型/Hash/兼容性；并非浏览器到真实 Gateway/Platform/BFF/Risk 的全链路证明。

## 浏览器检查：真实页面 + 合成 API

在 Codex 内置浏览器使用 1440×900、1024×768，以及导航展开/收起状态操作真实 Web。数据由隔离 loopback 预览服务提供，未写入 Risk/Platform 数据库。可复现脚本见 [ui-preview.mjs](./assets/stage5/ui-preview.mjs)，运行方式为 `node requirements/20260912/operations-console-决策流配置/assets/stage5/ui-preview.mjs`；进入 `http://127.0.0.1:18311/risk/decision-flows`。预览脚本只用于表内已描述场景，不实现真实筛选、规则选择、历史持久化或删除；静态校验恒返回通过，不能用于证明 Owner 校验正确。

| 操作 | 已观察结果 |
|---|---|
| 列表操作菜单 | 可展开详情、编辑、历史版本、删除；删除为危险操作样式；[截图](./assets/stage5/list-actions-1440.png) |
| 详情选择条件线 | 右侧显示来源/目标、条件文字和出口顺序；无保存或修改表单；[截图](./assets/stage5/detail-readonly-1440.png) |
| 只读节点拖动与 Backspace | 六个节点数量和原 position transform 均不变；未触发编辑 |
| 历史版本与浏览器返回 | 详情→历史面板→准确版本；Back 依次恢复历史面板、详情、列表，未跳审计页 |
| 条件出口排序 | “USD 转审核”从第二项上移为第一项，画布编号同步为 1/2，“其他情况”留在最后，无移动控件 |
| 未保存时 Back 与取消 | 弹出离开确认；留在当前页后名称/图/调整后的顺序不丢失；再次确认离开才回列表 |
| 模拟保存 409 | 将流程名设为“并发冲突预览”并保存，显示版本冲突及 Request ID，名称、条件和出口顺序保留；[截图](./assets/stage5/save-conflict-preserves-edit.png) |
| 新建默认值 | 仅开始和一个待配置结束节点，无默认放行；结束决定为空时保存禁用；[截图](./assets/stage5/create-incomplete-end.png) |
| 显式结束决定与合成成功保存 | 选择人工复核后可以保存；成功显示配置版本 #1、进入编辑 URL、清理未保存状态，变更原因需要重新输入；[截图](./assets/stage5/create-save-success.png) |
| 浏览器运行日志 | 此批操作结束读取 warn/error 为空；不代表完整后端错误矩阵已完成 |

已对照 Pencil 的有序出口画板检查主画布、条件属性及出口顺序的结构。本轮不是六张画板全部状态的最终验收，尚未完成的浏览器场景见下文。

## 本轮修复：说明区遮挡结束节点

在 1024 宽度、导航收起后执行适应画布，结束节点矩形与底部说明区交叠，实际遮住节点文字：[修复前](./assets/stage5/before-note-overlap-1024.png)。DOM 矩形实测：节点纵向 562.918–644.324，说明区 592.672–658，横向也相交。

仅修改 [decisionFlow.css](../../../operations-console-web/src/risk/decisionFlow.css)：画布容器采用纵向布局，工具栏和说明区占据独立空间，React Flow 使用剩余高度。图模型、节点坐标、条件、接口和保存逻辑未改变。

修复后 1024 宽度结束节点纵向 494.429–558.201，说明区仍为 592.672–658，不再相交；1440 宽度复查通过。截图：[1024](./assets/stage5/after-note-layout-1024.png)、[1440](./assets/stage5/after-note-layout-1440.png)。`npm run build`（TypeScript + Vite）通过，日志 `/tmp/rco17-stage5-web-build.log`，保留既有公共 chunk 大小提示。该纯 CSS 修复采用浏览器前后证据与构建检查，未新增镜像式单元测试。

## 真实入口联调：已执行的本机范围

用户对具体本机方案回复“可以”。已备份 8 张配置表，并向 `127.0.0.1:13306/mor_platform_rco16_validation` 插入 118 条关闭状态注册，管理员角色版本 3→4；随后临时开放 12 Route、3 Resource、2 RolePermission，版本 4→5。没有新建库、表、用户或数据库授权。备份：`/Users/kenny/Code-MoR-backups/rco17-platform-stage5-20260912T150157Z`。

启动重新 package 的四个真实应用，制品 SHA-256 见 [本机制品记录](./assets/stage5/real-local-artifacts.json)：Platform、Risk、BFF、Gateway 均使用当前源码打包，Maven `-DskipTests package` 成功，不把它统计为测试重新通过。Platform 使用独立验证库，Risk 使用 `mor_risk`，复用本机 MySQL/Redis。Loopback SimpleDiscoveryClient 提供实际进程地址，Nacos 关闭；Gateway 的业务 Route/Target 则来自实际 Platform 数据库 Snapshot，没有加入静态业务路由。

身份使用三位一次性本机合成账号，密码校验、会话、权限查询和 JWT 均走真实 Platform。Email Vault/签名使用已存在的开发适配器及临时生成的密钥，不代表真实 Vault/KMS 验证。浏览器通过本机 Vite 代理访问 Gateway，代理只模拟可信 Nginx 覆盖入口 Channel/IP，未修改 Gateway 防护条件。

| 真实链路场景 | 观察结果 |
|---|---|
| 管理员、只读人员、仅保存人员登录 | 三者均成功进入真实会话 |
| 数据库临时开放/关闭 | 同一 Gateway 进程新接口 404→200→404；Snapshot Route 从 36→48→36，无重启；[关闭前](./assets/stage5/closed-route-result.json)、[开放后](./assets/stage5/opened-route-result.json)、[收尾关闭](./assets/stage5/closed-after-result.json) |
| 新规则首版与第二版保存 | 无动作规则可以保存，`pointVersionId=null`；未生成旧顺序组成 |
| 流程引用新旧两种规则 | 静态校验通过，规则准确引用及旧点准确读取返回 200 |
| 权限隔离 | 只读账号查询 200、校验/保存 403；仅保存账号保存 200、删除 403 |
| 保存与幂等 | 首版保存成功；相同 key 重放原结果；追加第二版成功；旧 base 保存 409 |
| 历史 | 两个版本倒序展示；第一版图及出口顺序未随第二版修改；节点/边集合按稳定业务键比较，不将 JSON 数组物理顺序误当作业务变化 |
| 查询边界 | 精确流程编码返回一行，不存在的编码返回零；未声明 Query 在 Gateway 拒绝 400 |
| 整体删除 | 独立流程删除成功；相同 key 重放；当前详情 404，准确历史版本仍 200 |
| 缺少结束决定的静态校验 | 200 且 `valid=false`，返回准确问题 |
| 缺少结束决定直接保存 | **未通过**：Risk 422，被 BFF 转成 502，见下文 |

[补充 HTTP 场景记录](./assets/stage5/real-http-additional-scenarios.json)只保存后半段逐项输出，不冒充所有场景的自动化汇总或完整运行日志。临时手动验收脚本位于 `/tmp/rco17-stage5-*.py`；所有调用都连接实际进程，不替换业务方法。

## 真实浏览器补充检查

| 操作 | 已观察结果 |
|---|---|
| 真实详情、准确规则引用 | 新规则 #2 只读显示“未配置动作”，无保存按钮；[截图](./assets/stage5/real-rule-reference.png) |
| 历史版本 | [列表](./assets/stage5/real-flow-history.png)显示 #2、#1；[第一版](./assets/stage5/real-flow-version1.png)仍为“1 命中后拒绝、2 USD 转审核”，第二版相反 |
| 浏览器返回 | 规则引用返回来源详情，历史第一版返回历史列表，未跳审计页 |
| 旧顺序编排 | 按真实旧 binding/point ID 展示，标记“原顺序配置 · 只读”，无编辑/保存；[截图](./assets/stage5/real-legacy-flow.png) |
| 静态校验与定位 | 在浏览器草稿添加未连接且未选决定的结束节点，真实 Owner 返回两项问题；点击“节点无法从开始到达”选中并定位该节点；[截图](./assets/stage5/real-validation-focus.png) |
| 未保存草稿收尾 | Back 弹出离开确认，确认离开后草稿丢弃；没有提交该浏览器草稿。真实登录已退出 |
| 浏览器告警 | 观察到既有 AntD `Spin.tip`、`Space.direction` 弃用提示，未发现此次浏览器操作的崩溃；不把告警写为零 |

## 首轮阻塞与必要补充（已由本次重验解决）

1. **规则版本选择器的本机依赖路由遗漏。** 原开放方案只含 12 Route，漏了已有只读 `operations-risk-rule-set-versions`（012001）。真实列表 200、版本查询 404 已复现；[证据](./assets/stage5/rule-history-missing-route.json)。[额外开放 SQL](./db/platform-support-service/local-validation/rule-history-open.sql)与[关闭 SQL](./db/platform-support-service/local-validation/rule-history-close.sql)已准备，补充授权尚未收到，未执行。
2. **字段校验错误转发缺口。** [Risk 原始合规 Problem](./assets/stage5/owner-invalid-graph-problem.json)返回 422；Foundation HTTP Client 未识别公共 `VALIDATION_ERROR`，BFF 最终 502。错误发生在公共码分类，不是 Risk 保存成功或 Gateway 路由丢失。[必要修复范围](./stage5-validation-forwarding-fix.md)已具体化并请求确认，尚未改 Foundation；不通过变更既有错误合同掩盖问题。

以上为首轮停止时状态；补充授权、修复和重验结果见文末。

## 本轮收尾

按原授权执行 close.sql，逐项改动数 12/3/2/1 核对后提交；管理员版本 5→6，12 Route/3 Resource 为关闭、两项写绑定撤销，118 条注册保留。额外规则历史 GET 仍为 DISABLED/version=1。

本次四个 Java 进程与 Vite 预览服务均已停止，浏览器验收页已关闭。Risk 本次规则、流程、原因、命令事实已清理；Platform 三个账号、会话、一次性签名键、临时角色及 8 条本次审计已清理。Platform 原夹具因 Gateway 正常生成的 UUID Request ID 不匹配测试前缀而拒绝自动删除；核对审计 actor 全部属于本次三账号后，按准确 chain/actor/key/role ID 完成手动清理，没有放宽生产审计规则。SQL 注册数量仍与插入后相同；[核对结果](./assets/stage5/cleanup-verification.json)。本次 Redis 命名空间扫描无残留 Key，未执行 FLUSH。

凭据、Token 和私钥未写入需求工件。没有 test 数据库变更、共享制品发布、部署、提交或推送。

文档收尾检查：`node requirements/20260912/operations-console-决策流配置/check-design.mjs` 通过，确认 118 插入/1 更新、示例及反例、202 个本地链接；root/Web 的 `git diff --check` 通过。上述检查不改变未通过的阶段 5 结论。


## 补充授权后的修复与重验

用户回复“都同意”，确认既有规则历史 GET 临时开放/关闭及 Foundation 必要修复。先完成新增 BFF 用例的失败证据（应为 422、实际为 502），再修改 Foundation HTTP Client、公共 Problem 载体/MVC 映射及 BFF 消费适配。

- Foundation 仅对显式启用选项、带 `@RequestBody` 的调用识别严格合规的 `422 VALIDATION_ERROR`；其他客户端默认不变，非法 Profile 仍为协议错误。一次解码只保留字段坐标，不传播上游文案。
- BFF 仅给决策流管理 API 启用该选项；字段须存在于本次实际 Body，数组不能越界，未知路径丢弃。使用本地文案、当前 Request ID、新时间生成 422；没有新增 BFF Problem 解析器或业务异常码。
- 浏览器实测发现孤立节点保存错误只有 `graph.nodes`，点击后误开基本信息。Web 复用已有静态校验取得准确节点/连线和说明；失败时保留原字段提示与草稿，不再误定位。无新增 API、表、字段或注册数据。此缺陷使用真实浏览器前后证据及构建/现有回归验证。

### 本轮开发回归

[测试结果](./assets/stage5/recheck-test-results.json)记录实际范围：Foundation 相关 reactor **347/347**（common-core 103、http-problem 9、http-client-support 110、mvc-web-starter 125）；BFF 全量执行 **1,052 项**，唯一失败为旧代理工厂数量断言，更新后两个受影响测试类 **16/16** 通过，最终报告均通过，未再执行第二次完整 reactor；Web **900/900** 及 TypeScript/Vite 构建通过。Foundation 已本机 install，BFF 已重新 package；[制品摘要](./assets/stage5/recheck-local-artifacts.json)。Risk/Platform/Gateway 业务代码未变化，沿用首轮打包制品及前述开发回归证据，没有声称本次重新执行全部 Risk 测试。

日志：`/tmp/rco17-validation-foundation-full.log`、`/tmp/rco17-validation-bff-full.log`、`/tmp/rco17-validation-bff-recheck.log`、`/tmp/rco17-stage5-recheck-web-tests.log`、`/tmp/rco17-stage5-recheck-web-build.log`。

### 真实链路与浏览器结果

本轮临时开启 **13 Route、3 Resource、2 RolePermission**，复用原 118 条注册，无新增行。Gateway Snapshot 从数据库读取 49 条有效路由，规则历史 GET 返回两个准确版本。新的临时三账号仍走真实 Platform 登录、权限和会话；新旧规则、流程、字典均为本机一次性合成事实。

[HTTP 重验记录](./assets/stage5/recheck-http-scenarios.json)包含首轮重验 22 项、追加查询/422/删除 8 项，以及已删除规则引用检查；后续夹具准备重复记录了根查询，不能把记录总数当成唯一测试数。字典创建和已删除规则夹具由真实 Risk HTTP 准备，其他列明场景经过实际 Gateway/BFF；既有规则删除 Gateway 路由保持关闭，未扩大临时开放范围。

| 场景 | 本轮证据与结果 |
|---|---|
| 规则选择器 | 编码精确查询后展示 #2 草稿、#1 已退休；#2 可选择、#1 禁用，选定后节点保留准确版本；[截图](./assets/stage5/recheck-rule-version-picker.png) |
| 保存字段失败 | 真实链路返回 422、公共码和合法字段坐标，本地文案；[Problem](./assets/stage5/recheck-validation-problem.json) |
| 浏览器错误定位 | 孤立结束节点使保存失败，修复后显示“节点无法从开始到达”，点击选中节点；名称、原因和七个节点保留；[修复前](./assets/stage5/recheck-save-422-before-location.png)、[修复后](./assets/stage5/recheck-save-422-preserves-edit.png) |
| 修正并保存 | 删除本地孤立节点后保存成功生成 #3，清理 dirty，历史显示 #3/#2/#1；[保存截图](./assets/stage5/recheck-save-success.png) |
| 已删除规则历史 | 未激活规则集删除后，原流程准确版本仍显示规则名称、版号、规则数及已删除提示；[流程历史](./assets/stage5/recheck-deleted-rule-history.png)、[规则内容](./assets/stage5/recheck-deleted-rule-content.png)均只读，无保存按钮 |
| 浏览器返回 | 无热更新的重新加载页面中，规则引用 → 来源详情 → 原列表逐级返回，未跳审计页面 |
| 浏览器整体删除 | 确认明确说明历史保留、规则集不受影响；删除本次流程后回到“暂无决策流”；[截图](./assets/stage5/recheck-delete-empty.png) |
| 开放后收尾 | 同一 Gateway 进程从路由 49 恢复 36，新接口 200→404；[关闭前](./assets/stage5/recheck-before-close.json)、[关闭后](./assets/stage5/recheck-after-close.json) |

热更新中出现过 Router 多 blocker 告警并干扰 Back；重新加载、从列表重新进入后已复验正常。存在既有 AntD `Spin.tip` 弃用提示及 Gateway macOS DNS native fallback 告警，不冒充零告警；本机 HTTP 目标连接和实际联调未受影响。本次测试不覆盖 Vite 热更新期间保存草稿跨代码替换的可靠性。

### 六张 Pencil 画板映射

逐张检查 [六画板](./ui.md)并与上述浏览器证据对照，配置状态和交互已覆盖；没有声称逐像素复刻静态稿。

| 画板 | 验证对应 |
|---|---|
| voTJb 列表与操作 | 一流一行、筛选、详情/编辑/历史/红色删除及实际空列表 |
| vRuFW 首次新建 | 开始→待配置结束、无隐式放行、基本信息必填和保存导航；首轮合成页面证据与真实 Owner 校验共同覆盖 |
| Yad7m 有序条件出口 | 节点侧栏与条件线、上下调整编号、其他情况固定最后；真实历史保留原出口优先级 |
| eNp1l 校验与定位 | 首轮缺少结束决定与不可达问题、本轮保存 422 定位孤立节点及修复成功 |
| cvPOK 历史引用只读 | 已删除规则引用仍显示原准确版本和内容，禁止修改；浏览器返回来源 |
| lf4pt 规则动作可选 | 真实无动作规则保存 v1/v2、准确只读回显“未配置动作”；没有模式选择开关 |

呈现差异保留供用户确认：实现沿用 Console 导航；列表名称/编码分列；基本信息采用弹窗，校验问题置于画布上方，属性面板保留给节点/连线；图形间距由实际保存坐标及缩放控制。上述差异不改变本期配置、历史和错误定位功能。

### 本轮清理及阶段结论

已执行 [recheck-close.sql](./db/platform-support-service/local-validation/recheck-close.sql)，同连接核对 12/1/3/2/1 后提交。原 12 Route/3 Resource/2 RolePermission 到 version=5，额外历史 Route 到 DISABLED/version=3，管理员角色 version=8，118 条注册仍保留。备份 `/Users/kenny/Code-MoR-backups/rco17-platform-recheck-20260912T154835Z`。

四个临时 Java 进程、Vite 和浏览器页均已停止/关闭；本次 Risk 流程、规则、原因及命令，Platform 账号/会话/临时签名键/审计已清理，数据库核对为零：[清理核对](./assets/stage5/recheck-cleanup.json)。[Redis 任务命名空间](./assets/stage5/recheck-redis-cleanup.json)无残留，未 FLUSH；临时凭据及 Token 文件已删除。

**本机综合验证阶段已完成，待用户确认进入阶段 6 test 发布准备。** 本期配置管理验收不代表真实决策执行、test 历史迁移或 test 部署已完成；没有共享制品发布、Git 提交或推送。

最终工件检查：`check-design.mjs` 通过，118 插入/1 更新保持一致，225 个本地链接可解析；根仓库及 Foundation/BFF/Web 的 `git diff --check` 均通过。
