# commercial 产品目录层级展示：确认记录

## 阶段安排（提议，待确认）

| 阶段 | 安排 |
|---|---|
| 1–3 需求、现状、方案 | 合并为一份评审稿：[requirement.md](./requirement.md)（含页面设计与验收）、[technique.md](./technique.md) |
| 4–5 研发与本地验收 | 合并：改 ops-web、单元测试、构建、浏览器核对 |
| 6–7 test 发布与验收 | 合并：替换 mor-test ops-web 单镜像，真实入口浏览器验收；执行前单独授权 |
| 8 提交与归档 | 本地提交（不推送），单独授权 |

## 记录

- 2026-10-20：登记需求；阶段 1–3 合并评审稿提交用户审阅。
- 2026-10-20：用户要求可开通项 / 计费事件改为上下排列、分组可折叠；画板「商务 · 06b 产品目录（层级）」与 requirement / technique 已同步。
- 2026-10-20：**用户确认阶段 1–3 评审稿与阶段安排，进入阶段 4–5（研发与本地验收）。** 发现 `commercial/commercial.ts`、`commercial.css` 有其他需求的未提交改动，实现改为只新增 `catalogTree.ts` / `catalog.css`，不碰这两个文件。

## 阶段 4–5 结果（2026-10-20）

- **代码**（worktree `.claude/worktrees/agent-a6babef407b71bdb5`，基于 main 5f12cf3e，未提交）：新增 `src/commercial/catalogTree.ts`、`catalogTree.test.ts`、`catalog.css`，重写 `src/pages/CommercialCatalog.tsx`；`commercial.ts` / `commercial.css` 未改。
- **单测与构建**：`npm --prefix operations-console-web test` 1169 通过 / 0 失败（新增 14）；`run build` 通过（jsx-runtime chunk > 500 kB 为既有警告）。主会话复跑结果一致。
- **浏览器**：本地 Vite（worktree 代码）+ mor-test 真实目录数据，[browser_check.py](./verification/browser_check.py) 14/14 通过：AC-01 汇总、AC-04 两种筛选、AC-05 缺口、AC-07 接口失败整页失败、AC-08 1440 / 1024 / 390 无页面横向滚动、AC-09 默认展开 / 单组折叠 / 全部展开收起 / 筛选后保持。截图在 [assets/](./assets/)。控制台仅有应用壳既有的 antd 弃用警告（Spin `tip`、Space `direction`），与本页无关。
- **证据边界**：本地 Vite 的 Origin/Host 不在 mor-test 运营入口白名单内，脚本把 API 请求经 Playwright 转发到 `http://localhost:28080`；页面代码是 worktree 版本，不是 mor-test 已部署镜像。AC-10 深色主题：应用当前只配置浅色主题，样式使用 antd 变量，无法在本期验证深色效果。
- **与方案的差异（agent 报告，已核对）**：数量为 0 的分组标题不可点击（无内容可展开）；筛选时汇总追加「筛选命中 N 个产品 · N 个计费事件」，分组标题事件数显示「命中 / 全部」；命中当前筛选的条款类型标签为蓝色；主表点整行可展开；筛选无命中时表格空状态为「没有命中该条款类型的产品」。

- 2026-10-20：**用户授权阶段 6–7**：在干净集成 worktree（main 5f12cf3e + opsux 已上线改动 + 本需求文件，不含对账会话未验收改动）构建 ops-web，只替换 mor-test `operations-console-web`，回滚目标 `sha256:105afba9…`；无 SQL / 注册 / 数据变更；两需求同一次发布。

## 阶段 6–7 结果（2026-10-20，两需求同一次发布）

- **构建**：干净集成 worktree = main 5f12cf3e + opsux 会话主工作区 34 个已上线文件 + 两需求 9 个文件；不含对账会话（pageCapabilities.ts、ConsolePage.tsx、configurationModel.test.ts、Recon*.tsx、src/reconciliation/）。先单独构建「5f12cf3e + opsux」基线，产物与线上 `105afba9…` 的 137 个静态文件逐字节一致，证明不回退、不夹带。发布构建 test 1182/0、build 通过。
- **镜像**：`mor-test/operations-console-web:commercial-20261020c` = `sha256:5853565b777f4b3a9c04ff3d7cb000d99bb846d9cbd1c708945be2beb67bc4ab`，入口 `assets/index-RX7TQcU9.js`；候选文件 [compose-candidate.yaml](../commercial-产品目录层级展示/release/compose-candidate.yaml)。回滚 `sha256:105afba9d9a4dddf2501729fb63ac3cce22ab07da0d9dfae1f7fdcdbf84d69e6`（opsux-20261020n）。
- **替换**：2026-10-20 17:25:33Z 只替换 `operations-console-web`；healthy、重启 0、日志无 error，`http://localhost:28080/` 引用新入口。无 SQL / 注册 / 数据变更。替换前通知 opsux 会话（code-mor-b2）暂停 ops-web 部署，完成后告知新基线。
- **真实入口验收**：产品目录 [browser_check.py](../commercial-产品目录层级展示/verification/browser_check.py) 14/14；价格组件 [browser_check.py](../commercial-价格组件表格维护/verification/browser_check.py) 17/17（DRAFT v3 `238348866326433792`，保存请求截下比对 / Owner 真实拒绝，未写入）；全部 48 个运营页面只读回归 [stage7_regression.py](../commercial-产品目录层级展示/verification/stage7_regression.py) 48/48。截图 `assets/stage7/`。
- **保留数据**：DEMO-CL-001-ACQ 的 DRAFT v3 留作演示，不会生效。

- 2026-10-20：**用户授权阶段 8 本地提交**（不推送）；私有索引只提交本需求范围文件，`design.pen` 与其他会话文件不纳入。
