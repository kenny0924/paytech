# Pencil 页面与交互方案

本期在现有 [Operations Pencil 文件](../../../operations-console-web/docs/design.pen)追加 7 张画板，保留原有 47 张。沿用 Noto Sans SC、蓝色主操作、左侧导航、统一表格与字段间距。图中示例记录用于展示布局，不代表 test 中已有同名配置。

可整体查看 [7 页设计 PDF](./assets/export.pdf)。画板和导出图：

| 页面 | Pencil ID / 图片 | 关键交互 |
|---|---|---|
| 菜单与按钮 | d6tuF / [预览](./assets/d6tuF.png) | 资源树与编辑区分栏；图标、缩进及引导线区分层级；选中菜单显示按钮数量，停用项单独标识；表单按基本信息、页面与权限分组 |
| 权限目录 | hdzG8 / [预览](./assets/hdzG8.png) | 精确编码和状态/风险筛选；操作菜单为详情、编辑、使用关系、启用/停用 |
| 新建权限 | a39qA / [预览](./assets/a39qA.png) | 三项稳定身份 + 风险等级 + 原因；保存为停用 |
| 权限详情与引用 | dB1HB / [预览](./assets/dB1HB.png) | 只读属性，无保存；资源、API、角色三个分页标签，按权限跳转 |
| Target 编辑 | nl50h / [预览](./assets/nl50h.png) | Code/Channel 固定，Discovery 可改；下面显示受影响 Route，独立分页 |
| Route 列表 | Kbs7J / [预览](./assets/Kbs7J.png) | 同一张列表覆盖六类 Channel，Target/Route 切换；详情/编辑/启停统一菜单 |
| Route 编辑 | F0772 / [预览](./assets/F0772.png) | 左侧基本转发与认证，右侧请求限制/Header/Query/Guard；图示 CLIENT_API 的 Scope 模式 |

## 1. 路径与浏览器历史

- 保留 `/gateway-configuration`；对象标签/筛选/分页放 Query。新建与详情编辑为 `/gateway-configuration/targets/new`、`/targets/:code`、`/targets/:code/edit` 和对应 `/routes/...`，均属于该前缀。
- 新页面 `/console-resources`、`/console-resources/new`、`/console-resources/:code`、`/console-resources/:code/edit`；目录树选择与 URL 同步，详情右侧不可改，无保存按钮。点击新建/编辑进入新历史条目，选择项切换保持可回退。
- 权限 `/permissions`、`/permissions/new`、`/permissions/:code`、`/permissions/:code/edit`，引用标签使用 `?tab=references&kind=ROUTE&page=1`。
- 使用 Router navigation 与真实 history，保留列表查询/分页。保存成功 replace 当前编辑地址为详情，浏览器后退回原列表/来源页；不使用固定跳到审计页的回退逻辑。
- 不加“返回来源页/返回列表”按钮。未保存修改离开时使用现有草稿拦截；刷新/关页由浏览器原生提示，取消离开不丢草稿。直接深链进入后浏览器返回仍尊重真实历史，不伪造来源。

## 2. 表单细节

### 菜单树页面优化（2026-09-13）

按用户“菜单树的页面能否再优化一下”调整原 d6tuF 画板，不增加接口、数据库字段、画板或业务功能。

- 页面使用浅灰工作区，左侧约 360px 的资源树、右侧自适应详情/编辑区；两栏各自构成清楚的操作区域。保留整个系统的左侧导航。
- 目录、页面、按钮分别用文件夹、页面、鼠标操作图标表示，底部附简短图例；用缩进和按钮分组引导线表达父子关系，不再把“目录/页面/按钮 · 启用”重复拼在每行名称后。
- 已启用用状态点标记，停用项降低强调并明确显示“停用”；状态点提供“已启用”悬停文本及可访问名称，颜色不是唯一可获取的状态信息。图标同样提供资源类型标签。
- 搜索名称/编码与既有状态筛选放在树上方；提供展开/收起全部。搜索匹配项保留祖先并展开路径；清除搜索恢复之前的展开状态，不丢当前选择。
- 点击展开箭头只改变展开状态，点击节点名称进入该资源详情；新建/编辑仍使用此前定义的 URL 和草稿保护。选中项使用浅蓝底和边框；页面菜单下方显示真实按钮数量，右侧省略号展开详情、编辑、启停等获授权操作。数量和状态来自 A05，不增加统计接口。
- 图稿收起系统管理和平台配置，展开风控管理及规则配置以展示层级；实际初始展开当前选中路径，其他目录保留用户在当前页面的展开状态，不将示例分组硬编码。
- 右侧标题显示资源名称、类型与状态；固定编码作为次要只读信息，支持复制。基本信息一行呈现名称、上级目录、排序；页面关联和可见性权限成组选择，页面路径单独只读展示。
- 变更原因放在可编辑内容下方，保存位于编辑区底部且不遮挡内容。只读模式隐藏保存/编辑提示，保留同样的信息布局；窄屏按既有顺序纵向展示。接口合同、权限行为及业务约束保持原方案。

A17 的页面能力清单以以下 11 项为本期交付基线；代码支持目录随 Platform 制品发布，真实组件/handler 仍须与 Web 一致。按钮编码前缀均为 `OPERATIONS_`，表中用后缀缩写列举；“无”表示该页面没有独立按钮资源。

| componentKey（均以 OPERATIONS_ 开头） | 路径 | 已支持按钮编码后缀 |
|---|---|---|
| ACCOUNTS | /accounts | ACCOUNT_CREATE、ACCOUNT_DISABLE、ACCOUNT_ENABLE、ACCOUNT_DELETE、ACCOUNT_ASSIGN_ROLES |
| ROLES | /roles | ROLE_CREATE、ROLE_EDIT、ROLE_DELETE |
| AUDIT | /audit | 无 |
| GATEWAY_CONFIGURATION | /gateway-configuration | GATEWAY_CONFIGURATION_MANAGE |
| RISK_RULE_CONFIGURATION | /risk/rule-configuration | RISK_RULE_SET_SAVE、RISK_RULE_SET_FREEZE、RISK_RULE_SET_DELETE、RISK_RULE_SET_HISTORY、RISK_RULE_SET_REFERENCES |
| RISK_DECISION_FLOWS | /risk/decision-flows | RISK_DECISION_FLOW_SAVE、RISK_DECISION_FLOW_DELETE |
| RISK_REJECTION_REASONS | /risk/rejection-reasons | RISK_REJECTION_REASON_CREATE、RISK_REJECTION_REASON_UPDATE、RISK_REJECTION_REASON_STATUS |
| RISK_SUBJECT_TAGS | /risk/subject-tags | RISK_SUBJECT_TAG_CREATE、RISK_SUBJECT_TAG_UPDATE、RISK_SUBJECT_TAG_STATUS |
| RISK_VARIABLES | /risk/variables | RISK_VARIABLE_MANAGE、RISK_VARIABLE_DELETE |
| CONSOLE_RESOURCES（本期新增） | /console-resources | CONSOLE_RESOURCE_MANAGE（本期新增） |
| PERMISSIONS（本期新增） | /permissions | PERMISSION_MANAGE（本期新增） |

旧 TEST Candidate 资源不在当前新建按钮选项中，不删除其记录或改历史入口；读取时按存量不受支持资源显示。该表是部署能力设计基线，不是新的权限或 Route 表，实际业务权限仍从数据库查询。

- 资源类型 UI 显示“目录 / 页面菜单 / 按钮”，Wire 只有 MENU/BUTTON，目录由组件是否为空决定。组件选择后路径自动显示为只读。按钮显示其所属页面及具体已支持操作，不能输入未知 action code。
- 新建 Target/Route 的 Channel 必填，编辑固定；Target 下拉按 Channel 搜索现有目标，包含停用目标并标注状态，保存/启用分别按合同校验。Target 的引用直接复用 Route 列表过滤。
- Route 按 A16 切换字段：人类权限模式选择 Permission；Client API 输入 Scope；其他模式隐藏二者，切换时先提示将清除不适用草稿值，确认后只清理这些值。只读详情显示原配置、完整子项和保护原因。
- Header/Query 编辑展开到原表单内，不另开页面；Header 每行名称+用途，Query 每行名称，提供添加/移除行。Header 配的是名称，不显示任何实际请求值。Guard 使用每维度一行的 Rate/Concurrency 成对编辑：capacity/refillTokens/refillPeriodMillis/concurrencyLimit；必需维度不可删除，允许维度由 A16 决定。
- Route 右侧约束在常规桌面与左侧并列；窄屏按顺序纵向排列。图稿长页面在浏览器自然滚动，保存区保持可见；内容不能被固定页脚遮挡。
- 新建保存按钮“保存为停用”；编辑“保存修改”；详情无保存。受保护对象允许的字段以 managementProtection 为准，不能只用前端规则判断。
- 操作菜单支持点击及鼠标展开，键盘 Enter/Space、方向键和 Escape；打开后可获取焦点，不依赖 hover。此期没有删除项；停用使用警示色，避免与删除混淆。

## 3. 状态、影响与授权

- 初始加载局部骨架；空目录/筛选无结果分别显示“暂无配置 / 没有符合条件的结果”，有管理权限者给新建入口。无 READ 权限展示无权限，不调用数据接口。
- 字段错误出现在对应行，表单顶部给简短汇总；503 显示 Request ID 和重试，400/409 保留草稿。版本冲突提供重新加载已保存内容，并明确重新加载会覆盖草稿，未经选择不自动重试提交。
- 启停弹层显示对象、受影响引用数、变更原因；父菜单提示子项导航隐藏、Target 提示有效 Route 受影响、Permission 提示 API 拒绝且角色关联保留。弹层是该次业务操作的确认，不是审批流程。
- 可见性与执行权限分别判断；新权限首次授权由现有角色页完成，先展示平台管理员可分配的停用配置引用；普通角色不能因为目录创建自动获得权限。
- Resource/Permission 保存成功后重新获取当前 navigation；没有新授权时页面消失按现有无权限状态处理。角色修改后的后续请求使用 PLT-I02 当前判断。
- 保存文案沿用“Platform 当前配置已保存；此页面不确认 Gateway 实例刷新状态。”不增加第二个发布按钮。

## 4. 设计检查

本期逐画板检查对齐、文本对比度、操作与表单位置、容器裁剪；导出在最终画板稳定后完成。Pencil 是静态交互方案，不能将图片审阅记为 React 页面或真实 API 已验证。研发时使用本文件及 [AC/UI](./requirement.md)验证动态行为。
