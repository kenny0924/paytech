# commercial 价格组件表格维护：确认记录

## 阶段安排（提议，待确认）

| 阶段 | 安排 |
|---|---|
| 1–3 需求、现状、方案 | 合并评审稿：[requirement.md](./requirement.md)（含页面设计与验收）、[technique.md](./technique.md)、Pencil 画板「商务 · 10 编辑价格（组件表格）」 |
| 4–5 研发与本地验收 | 合并：改 ops-web、单元测试、构建、浏览器核对 |
| 6–7 test 发布与验收 | 合并，可与「产品目录层级展示」同一次 ops-web 发布；执行前单独授权 |
| 8 提交与归档 | 本地提交（不推送），单独授权 |

## 记录

- 2026-10-20：用户提出组件改为表格维护（截图：组件卡片字段重复、占用空间大）；登记需求，阶段 1–3 合并评审稿提交审阅。
- 2026-10-20：**用户确认阶段 1–3 评审稿（含费率按百分比输入、组件币种自动推导两处行为变化）与阶段安排，进入阶段 4–5。** 研发交由 Opus 5.5 agent 在独立 worktree 实施。

## 阶段 4–5 结果（2026-10-20）

- **代码**（worktree `.claude/worktrees/agent-a692fd2805d3aed3d`，基于 main 5f12cf3e，未提交）：新增 `src/commercial/componentTable.ts`、`ComponentTable.tsx`、`componentTable.css`、`componentTable.test.ts`，改 `PriceEditorDrawer.tsx`；`commercial.ts` / `commercial.css` 未改。主会话浏览器核对后补了一处样式：组件代码列 14% → 16%（「CHARGEBACK_FIXED」被截断），阶梯基数 / 模式下拉被表格的 `width: 100%` 覆盖成整行，改为 160px 并排。
- **单测与构建**：`npm --prefix operations-console-web test` 1165 通过 / 0 失败（新增 10）；`run build` 通过。AC-01 单测：`pricesBody(toSubmitDrafts(toFormDrafts(p)))` 与 `pricesBody(p.map(priceDraft))` 深度相等。
- **浏览器**：本地 Vite（worktree 代码）+ mor-test 数据，[browser_check.py](./verification/browser_check.py) 17/17 通过，截图在 [assets/](./assets/)。
  - 验收用数据：在 mor-test 为 DEMO-CL-001-ACQ 复制 V1 建了 DRAFT 版本 v3（`238348866326433792`），保留供查看，不会生效。
  - AC-01：不改值保存的请求体被 Playwright 截下后中止，11 条价格与读回的原价格逐字段一致（含组件币种；读模型结算字段在 `settlement` 子对象、非阶梯组件 bands 读回 `[]`，按同义比较）。
  - AC-07：把价格 2 优先级改成与价格 1 重复后真实提交，commercial 拒绝，抽屉顶部显示 Owner detail「同一 OE 与币种下优先级重复」；库内 v3 价格优先级核对未变，未写入。
  - 其余：默认折叠、摘要、百分比读回（0.029 → 2.9，档位 2.9 / 2.5）、「—」不可编辑、1080 抽屉内无横向滚动、6 组件表格可在 900 视口内显示、阶梯展开 / 收起、复制 / 删除行、行下错误 + 红框、校验失败时出错价格自动展开。
- **证据边界**：同产品目录项，API 经 Playwright 转发到 `http://localhost:28080`；验的是 worktree 代码，不是 mor-test 已部署镜像。
- **与方案的差异（agent 报告，已核对）**：用原生 `<table>` 而非 antd `Table`（为合并四列、行下错误行、档位子行常驻挂载参与校验）；`ComponentTable.tsx` 与 `componentTable.ts` 在大小写不敏感文件系统上同名，import 带扩展名；百分比校验收紧为 0–100；commercial 若对已隐藏的组件 `currency` 字段返回字段错误，页面无处显示（币种已自动推导，正常不会出现）。

- 2026-10-20：**用户授权阶段 6–7**：在干净集成 worktree（main 5f12cf3e + opsux 已上线改动 + 本需求文件，不含对账会话未验收改动）构建 ops-web，只替换 mor-test `operations-console-web`，回滚目标 `sha256:105afba9…`；无 SQL / 注册 / 数据变更；两需求同一次发布。

## 阶段 6–7 结果（2026-10-20，两需求同一次发布）

- **构建**：干净集成 worktree = main 5f12cf3e + opsux 会话主工作区 34 个已上线文件 + 两需求 9 个文件；不含对账会话（pageCapabilities.ts、ConsolePage.tsx、configurationModel.test.ts、Recon*.tsx、src/reconciliation/）。先单独构建「5f12cf3e + opsux」基线，产物与线上 `105afba9…` 的 137 个静态文件逐字节一致，证明不回退、不夹带。发布构建 test 1182/0、build 通过。
- **镜像**：`mor-test/operations-console-web:commercial-20261020c` = `sha256:5853565b777f4b3a9c04ff3d7cb000d99bb846d9cbd1c708945be2beb67bc4ab`，入口 `assets/index-RX7TQcU9.js`；候选文件 [compose-candidate.yaml](../commercial-产品目录层级展示/release/compose-candidate.yaml)。回滚 `sha256:105afba9d9a4dddf2501729fb63ac3cce22ab07da0d9dfae1f7fdcdbf84d69e6`（opsux-20261020n）。
- **替换**：2026-10-20 17:25:33Z 只替换 `operations-console-web`；healthy、重启 0、日志无 error，`http://localhost:28080/` 引用新入口。无 SQL / 注册 / 数据变更。替换前通知 opsux 会话（code-mor-b2）暂停 ops-web 部署，完成后告知新基线。
- **真实入口验收**：产品目录 [browser_check.py](../commercial-产品目录层级展示/verification/browser_check.py) 14/14；价格组件 [browser_check.py](../commercial-价格组件表格维护/verification/browser_check.py) 17/17（DRAFT v3 `238348866326433792`，保存请求截下比对 / Owner 真实拒绝，未写入）；全部 48 个运营页面只读回归 [stage7_regression.py](../commercial-产品目录层级展示/verification/stage7_regression.py) 48/48。截图 `assets/stage7/`。
- **保留数据**：DEMO-CL-001-ACQ 的 DRAFT v3 留作演示，不会生效。

- 2026-10-20：**用户授权阶段 8 本地提交**（不推送）；私有索引只提交本需求范围文件，`design.pen` 与其他会话文件不纳入。
