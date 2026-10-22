# 任务卡 P2-web：第二段 运营控制台渠道页改版

- 依据：需求 F、AC-V01～V03；technique 第 7 节；已确认画板 `operations-console-web/docs/design.pen`「渠道 · 第二段 01–12」（.pen 已加密，只能用 Pencil MCP 工具读取；可以 `TakeScreenshot` / `Get` 查看，**不得修改 .pen**）。
- 工作目录：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-ccu2` 的 `operations-console-web/`。**只改这个目录**；同一 worktree 中另有 agent 按 [P2.md](P2.md) 修改 `paytech/` 与需求目录。
- BFF JSON 合同见 P2.md 第 6 节；以合同为准实现，Mock / 测试数据按合同构造。
- 执行者：Opus 5.5 agent；主会话评审。

## 开工前必读

- `operations-console-web/` 下：README / 开发说明，以及现有实现 `src/pages/ChannelChannels.tsx`、`src/pages/ExternalIntegrations.tsx`、`src/channel/*`、`src/externalApi/*`、`src/navigation/menu.ts`、`src/navigation/pageCapabilities.ts`。
- 画板 01–12：逐张截图，对照实现。

## 范围

1. **渠道列表（画板 01）**
   - 类型分段（全部 / 支付渠道 / 服务渠道），写入地址栏 `?type=PAYMENT|SERVICE`，刷新保持。
   - 列：类型、状态、出站地址数、账号 / MID（服务渠道显示凭据状态）、调用授权数（支付渠道显示"—"）。
2. **新建渠道（画板 02）**
   - 两张类型卡片二选一，写明各类型包含与不适用的内容；类型创建后不可改。
   - 编码前端校验 `[A-Z][A-Z0-9_]{1,63}`；可填描述。
3. **渠道详情（画板 03、04）**
   - 页签按类型不同：
     - 支付渠道：概览、技术账号、MID、协议配置、沙箱验证、变更记录。
     - 服务渠道：概览、凭据、协议配置、沙箱验证、调用授权、变更记录。
   - 页签写入地址栏 `?tab=`。
   - 概览：出站地址表、描述、激活条件（只有出站地址是必需项）、关联信息。
4. **编辑出站地址（画板 12）**
   - 增删改地址；被引用的地址不可删，显示 Owner 原因。
   - 私网与 `localhost` 的错误展示在对应行。
   - 版本冲突时保留输入并提示。
5. **服务渠道·凭据（画板 05、06）**
   - 复用现有账号配置弹窗 `AccountConfigurationModal` 的能力，但按服务渠道外形展示：一份凭据、无 OE、参数与凭据分组、只写不读、显示末 4 位。
   - 无凭据时显示新建入口。
6. **协议配置页签（画板 10）**
   - 左侧子导航：标准 API 实现、原子 API、公共脚本。
   - 复用原接入方页的列表与编辑器组件（字段表、脚本、流程画布、字段映射）。
   - 新路由：`/channels/:channelId/protocol/implementations/:standardApiCode`、`…/protocol/apis/:apiCode`、`…/protocol/scripts/:scriptCode`。
   - 原子 API 编辑中的出站地址选项来自渠道详情的 `endpoints`。
7. **沙箱验证页签**：原接入方的验证页签整体迁入（验证账号、用例、运行、运行详情）。
8. **调用授权（画板 07、08、09）**
   - 矩阵视图：行为调用方，列为本渠道已实现的标准 API；单元格显示 ACTIVE、DISABLED 或空，空格悬停显示"+ 授权"。选中单元格后，右侧显示详情、启用 / 停用与留痕。
   - 列表视图：可筛选，写入 `?view=matrix|list`。
   - 新增授权弹窗：调用方是可输入下拉（候选为已有授权中的调用方加一份预置名单 `counterparty-service, commerce-service, risk-service, payment-service, acquiring-service, client-master-service`），校验 `[a-z][a-z0-9-]{1,63}`；标准 API 单选，只列已实现的；显示预览；409 时提示启用原记录。
   - 写操作需要 `CHANNEL_CALL_GRANT_MANAGE` 对应的页面能力，没有权限时只读。
9. **暂停服务渠道（画板 11）**：确认弹窗列出受影响的 ACTIVE 授权，必填原因。
10. **变更记录页签**：可按目标类型过滤（含 `CHANNEL_CALL_GRANT`、`CHANNEL_API` 等新类型），授权"留痕"可跳到这里并预置过滤条件。
11. **菜单与旧路由**
    - 移除「接入方」菜单（`OPERATIONS_EXTERNAL_INTEGRATIONS`），保留「标准 API」。
    - 旧地址 `/external-api/integrations/:code/...` 跳转到对应渠道详情的同名页签：前端按编码查渠道 ID 后 `replace`；查不到时显示"渠道不存在"。
    - `pageCapabilities.ts` 与菜单测试同步。
12. 删除不再使用的接入方列表、新建与编辑代码。

## 约束

- 不改 `operations-console-web/` 之外的文件，不改 `.pen`；不提交、不推送；不启动或停止任何容器。
- 保持现有代码风格与 antd 用法；新组件放 `src/channel/`，复用 `src/externalApi/` 的编辑器，不复制。
- 前端只展示 Owner 返回的原因，不在前端重复实现业务规则（编码格式等输入校验除外）。

## 完成标准与报告

- `npm --prefix operations-console-web test`、`npm --prefix operations-console-web run lint`（如有）、`npm --prefix operations-console-web run build` 通过。现有失败与本卡无关的，单独列出。
- 为新逻辑补单测：类型分段与地址栏同步、授权矩阵的数据组装、旧路由映射、出站地址行错误映射。
- 报告写入 worktree 内 `requirements/20261020/channel-connector-统一对外渠道出口/tasks/P2-web-report.md`：
  - 改动清单；
  - 与画板的差异及理由；
  - 对 BFF 合同的依赖或偏差；
  - 测试与构建结果；
  - 未完成项。
