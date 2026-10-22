# P2-web 报告：第二段运营控制台渠道页改版

- 任务卡：[P2-web.md](P2-web.md)（主工作区）；合同：[P2.md](P2.md) 第 6 节。
- 范围：只改 worktree `operations-console-web/`；未改 `.pen`（只用 Pencil MCP 读取 01–12 画板文字与截图）；未提交、未推送、未启停容器。
- 预览：用 scratchpad 中按合同构造的 Mock（Node HTTP）+ Vite + Playwright 前台跑完即关，截图核对了画板 01、02、03、04、05、06、07、08、09、10、11、12 以及旧路由跳转。Mock 与截图不在仓库内。

## 1. 改动清单

新增（`src/channel/`）

| 文件 | 内容 |
|---|---|
| `channelWorkspace.ts` | 纯逻辑：类型分段与地址栏（`?type=`）、详情页签按类型（`?tab=`）、协议子导航（`?section=`）、授权视图（`?view=`）、工作区路由、按编码入口 `/channel/channels/code/:code[子路径]`、旧接入方地址映射、授权矩阵组装、受影响授权归组、调用方候选与格式、出站地址引用汇总 / 输入校验 / Owner 错误定位到行 / 请求体 |
| `channelWorkspace.test.ts` | 上述逻辑与第二段合同解码的单测（9 组） |
| `ChannelOverview.tsx` | 概览（出站地址表、描述、激活条件、关联）与「编辑出站地址」弹窗（画板 12） |
| `ChannelCredential.tsx` | 服务渠道凭据页签（画板 05），无凭据时「新建凭据」 |
| `ChannelGrants.tsx` | 调用授权：矩阵 / 列表、右侧详情与留痕、新增授权、启停（画板 07–09） |
| `ChannelProtocol.tsx` | 协议配置页签：左侧子导航 + 原接入方页的三张列表（画板 10） |
| `ChannelChanges.tsx` | 变更记录页签：按目标类型与对象过滤，支持 `?targetType=&targetId=` 预置 |
| `ChannelResolve.tsx` | 按编码查渠道 ID 后 `replace`；旧 `/external-api/integrations/...` 跳转；查不到显示「渠道不存在」 |

修改

- `pages/ChannelChannels.tsx`：重写。列表（类型分段带计数、新列）、新建（类型卡片二选一，编码 `[A-Z][A-Z0-9_]{1,63}`，描述）、详情（按类型的页签与计数、配置版本）、编辑（名称 + 描述，类型只读）、状态变更（激活前置文案改为"出站地址已配置"；暂停服务渠道必填原因并列出受影响 ACTIVE 授权，画板 11），协议 / 沙箱子页路由。
- `channel/channel.ts`：合同字段（`channelType`、`description`、`endpoints`、`version`、`endpointCount`、`activeGrantCount`）、授权模型与解码、新路径（endpoints、channel-call-grants）、新按钮 `OPERATIONS_CHANNEL_CALL_GRANT_MANAGE`、`entityNo` 可空、变更目标类型扩展（含 `CHANNEL_CALL_GRANT`、`CHANNEL_API` 等；历史未知类型按原值展示）。
- `channel/ChannelParts.tsx`：`StatusChangeModal` 支持必填原因、附加字段（`expectedVersion`）与插槽；409 横幅显示 Owner 原因；新增侧栏用 `ChangeTrail`；`ChannelSelect` 可限定渠道类型。
- `channel/useChannelCommand.ts`：409 保留 Owner 原因；暴露最近一次原始错误（供出站地址按行定位）。
- `channel/AccountConfigurationModal.tsx`：新增服务渠道「凭据」外形（参数 → 凭据 → 确认三步、无 OE、只写不读）；标题在无 OE 时不显示括号。
- `externalApi/*`：编辑器改为挂在渠道下（`channelId` + 渠道编码），面包屑与跳转改为渠道路由；原子 API 的出站地址选项来自渠道详情 `endpoints`（不再读已删除的接入方详情）；写按钮改用 `manageProtocol`（见偏差 W5）；`decodeMutation` 兼容 `CHANNEL_*` 对象类型；删除被 409 拒绝时显示 Owner 原因；验证账号 `entityNo` 可空。删除接入方 Summary / Detail 模型与解码。
- `pages/ExternalStandardApis.tsx`：「已实现接入方」改为「已实现渠道」，链接经按编码入口进入渠道详情。
- `pages/ChannelMids.tsx`、`pages/ChannelCostPlans.tsx`：渠道选择只列支付渠道；`entityNo` 可空的类型适配。
- 菜单与注册：`pageCapabilities.ts` 删除 `OPERATIONS_EXTERNAL_INTEGRATIONS`，渠道页改名「渠道」并加「调用授权」按钮；`menu.ts` 增加渠道子路由、删除接入方路由；`ConsolePage.tsx` 移除接入方页面；`App.tsx` 增加旧地址跳转路由（需要当前会话有渠道页面）。
- 删除 `pages/ExternalIntegrations.tsx`（接入方列表、新建、编辑及其工作区）。
- 测试同步：`channel.test.ts`、`menu.test.ts`、`configurationModel.test.ts`、`externalApiModel.test.ts`。

## 2. 路由

| 页面 | 地址 |
|---|---|
| 列表 | `/channel/channels?type=PAYMENT\|SERVICE&keyword&status&page&size` |
| 详情 | `/channel/channels/:channelId?tab=overview\|accounts\|mids\|credential\|protocol\|verify\|grants\|changes` |
| 协议子导航 | `?tab=protocol&section=implementations\|apis\|scripts` |
| 协议编辑器 | `/channel/channels/:channelId/protocol/implementations/:standardApiCode`、`…/protocol/apis/:apiCode`、`…/protocol/scripts/:scriptCode`（`new`、`?mode=edit\|create` 与原来一致） |
| 沙箱子页 | `/channel/channels/:channelId/verify/accounts/:code`、`…/verify/cases/:code`、`…/verify/runs/:runCode`（任务卡未规定，本卡选定） |
| 授权视图 / 留痕 | `?tab=grants&view=list`；`?tab=changes&targetType=CHANNEL_CALL_GRANT&targetId=…` |
| 按编码进入 | `/channel/channels/code/:channelCode[/protocol/:section/:code]` |
| 旧地址 | `/external-api/integrations/:code?tab=basic\|apis\|scripts\|implementations\|verify` → 概览 / 协议子导航 / 沙箱验证；`/:code/apis\|scripts\|implementations\|verify-accounts\|verify-cases\|verify-runs/:x` → 对应子页（保留 `mode`）；列表与 `/new` → 渠道列表 |

## 3. 与画板的差异及理由

| 编号 | 画板 | 实现 | 理由 |
|---|---|---|---|
| W1 | 01 列表服务渠道「账号 / MID」显示凭据末 4 位 | 显示「已建凭据 / 凭据未配置」 | 渠道摘要合同没有 Token 末 4 位；详情「凭据」页签与概览激活条件显示末 4 位 |
| W2 | 03 / 04 标题区「更新 … · 运营 1001」 | 只显示更新时间 | 渠道详情合同无 `updatedBy` |
| W3 | 03 关联「成本方案 NI-2026Q4 v2」「最近沙箱验证 PASSED」 | 成本方案为跳转链接；沙箱验证为跳转链接 | 合同无这两项摘要；不为概览额外拼接成本与验证查询 |
| W4 | 05 / 06 参数「明文可查看」、按字段显示末 4 位与"未被引用" | 只显示整组 Token 末 4 位；确认页只列名称与输入长度 | Owner 不回显参数与凭据名称 / 值，Vault 结构整体一个 Token（第一段账号配置口径）；不新增读接口 |
| W5 | — | 协议配置 / 沙箱验证写操作用按钮 `OPERATIONS_EXTERNAL_STANDARD_API_MANAGE` | 接入方页面停用后其按钮不再下发；块 71 中两个按钮绑定同一权限，正是 `/external-api-admin/v1/integrations/{code}/…` 要求的权限。已告知 p2-backend；如块 129 另有安排需调整常量 `manageProtocol` |
| W6 | 10 实现列表「状态」「最近沙箱验证」列 | 显示流程节点 / 出口、总预算、版本、更新与「验证」跳转 | 实现摘要合同无状态与验证结果 |
| W7 | 07 矩阵示例有无授权的 `risk-service` 行 | 行只来自已有授权；新调用方经「新增授权」加入 | 不维护服务名册（已确认决定 4） |
| W8 | 08 列表底部「共 4 条 · 3 ACTIVE / 1 DISABLED」 | 相同，计数取本渠道全部授权 | — |
| W9 | 11 弹窗主按钮「暂停渠道」 | 相同；原因必填仅用于暂停服务渠道 | 支付渠道沿用原可选原因 |
| W10 | 12 地址行「生产 / 沙箱」下拉 | 相同；改为沙箱时二次确认（沿用原接入方页的防误标提示） | — |

## 4. 对 BFF 合同的依赖或偏差

按 P2.md 第 6 节实现，另有以下假设。p2-backend 已回复确认第 1、2、3、6（渠道账号部分）、7、8 条与实现一致：按钮 ID 900000000000129301 挂在 92302 下并绑定权限 129101；出站地址的私网 / localhost 与静态校验错误为 422 `endpoints[i].baseUrl|endpointCode`，引用保护与版本冲突为 409；块 129 只停用 71303 / 71305，71304 与权限 71102 不变（W5 成立）。

1. `PUT /channel-admin/v1/channels/{channelId}/endpoints` 响应为渠道详情；409 = 版本冲突或引用保护（横幅显示 Owner 原因并保留输入）；私网 / localhost 拒绝以 422 `endpoints[i].baseUrl` 或 400 原因返回——前端按字段路径定位行，400 原因按含有的地址编码（整词）或主机名定位行，定位不到时放在弹窗顶部。
2. `POST /channel-admin/v1/channel-call-grants`、`POST …/{id}/status` 响应为单条授权对象；状态变更请求体 `{targetStatus, expectedVersion, reason}`；409 原因直接显示。前端在提交前也按已加载的授权提示"授权已存在（状态，ID），请在矩阵中启用"并禁用提交。
3. `GET /channel-admin/v1/channel-call-grants` 支持 `channelId / callerService / standardApiCode / grantStatus / page / size`，分页外形同渠道列表；矩阵一次按 200 条拼接读取。
4. 渠道列表透传 `channelType` 过滤；类型分段计数用同一关键字 / 状态条件各查一次总数。
5. 创建渠道请求 `{channelType, channelCode, channelName, description}`；更新请求 `{channelName, description}`（不带类型，无 `expectedVersion`，与现有更新接口一致）。
6. 服务渠道账号创建不带 `entityNo`，响应 `entityNo` 为 null（已确认）。验证账号表 `entity_no` 仍为 NOT NULL，后端不会返回 null：前端解码放宽但表单仍要求填写，因此服务渠道暂时无法建立无 OE 的验证账号（列为遗留，需后端放宽）。
7. 新按钮资源 `OPERATIONS_CHANNEL_CALL_GRANT_MANAGE` 挂在 `OPERATIONS_CHANNEL_CHANNELS` 下（displayName「调用授权」），`console-resource-capabilities.json` 需与 `pageCapabilities.ts` 一致：删除接入方页面、渠道页改名「渠道」、加该按钮。
8. 变更记录 `/config-changes` 仍要求 `targetType + targetId`。协议对象（`CHANNEL_API / CHANNEL_SCRIPT / CHANNEL_IMPLEMENTATION`）的行 ID 不在任何摘要中，前端只能提供手工输入 ID；如需按类型浏览，需要 Owner 支持按 `channelId` 查询或在协议摘要中返回 ID（列为遗留）。
9. 外部 API 写结果的 `objectType` 若改为 `CHANNEL_*` 命名，前端两种写法都接受。

## 5. 测试与构建

在 worktree `operations-console-web/` 下（`node_modules` 临时链接主工作区，结束后已删除；`dist/` 已删除）：

- `npx tsc --noEmit`：通过。
- `npm test`：1251 项全部通过（基线 1242；新增 `channelWorkspace.test.ts` 9 项，同步修改既有菜单 / 能力 / 渠道 / 外部 API 模型测试）。
  - 覆盖：类型分段与地址栏同步、页签按类型、授权矩阵数据组装（含已删除实现的孤立授权与计数）、受影响授权归组、调用方候选与格式、旧路由映射（列表、各页签、子页、非法路径）、按编码入口路径、出站地址引用汇总 / 输入校验 / Owner 错误行映射（字段路径、主机名、整词编码、无法定位）、请求体、第二段合同解码。
- `npm run build`：通过；"chunk 大于 500 kB" 警告来自 `jsx-runtime` 公共块，基线同样存在（主工作区构建 511 kB），与本卡无关。
- `npm run lint`：项目没有 lint 脚本。
- Mock 预览：01–12 与旧地址跳转均渲染正常；浏览器控制台只有既有的 antd `Spin tip` 弃用警告和 Mock 未实现接口的 404/422（预期）。

## 6. 未完成项

- 未与真实 BFF 联调：依赖 p2-backend 的第 6 节实现与块 129 注册；第 4 节假设如有出入需同步调整。
- 协议对象变更记录只能按手工输入的行 ID 查询（见第 4 节第 8 条）。p2-backend 说明：变更记录表没有 `channel_id`，按渠道查询需加列、改全部写入方并回填，本轮不做。
- 服务渠道的沙箱验证账号受 `t_channel_verify_account.entity_no NOT NULL` 限制，需后端另行放宽。
- 列表服务渠道凭据末 4 位、详情更新人、实现状态与最近验证结果受合同所限未展示（W1–W3、W6）。
- 菜单分组本身（「渠道」目录、标准 API 挂在其下）由 Platform 注册数据决定，前端无需改动；未在 mor-test 验证。
