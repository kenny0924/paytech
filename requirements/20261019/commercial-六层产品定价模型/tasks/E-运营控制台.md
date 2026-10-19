# 任务卡 E · Operations 控制台：BFF 聚合、页面、Platform 注册

> 依赖 B、C 合入。执行者 Opus 5.5 agent。范围：`paytech/operations-console-bff`、Operations Web 前端项目、`requirements/.../db/platform-support-service/registration-dml.sql`（只写不执行）。参考 `requirements/20261018/operations-console-财务基础资料与账本余额查询/` 的 BFF、页面与注册方式。接口合同：[technique.md §2.9](../technique.md)。

## 交付

1. BFF `/commercial-admin/v1/**`：合同（列表 / 详情聚合：合同 + 参与方 + 订阅 + 开通项 + 当前与排期版本）、创建、签署登记、激活、终止、订阅替换、开通项增改；画像列表 / 创建 / 修改 / 激活 / 退役；条款 Plan / Version / 价格集合 / 提交 / 审批 / 排期 / 激活；权限判定按 `COMMERCIAL_CONTRACT_*`、`COMMERCIAL_PRICING_*`（APPROVE 独立）。BFF 无状态，不编排跨系统事务。
2. Web 页面：合同列表与详情（订阅、开通项、签署）、画像维护、条款版本编辑器（价格矩阵：OE × 币种 × 画像 × 事件，组件表单，Band 表格，提交 / 审批 / 排期按钮按权限显示）。
3. Platform 注册 DML：权限、角色绑定（REVOKED 初始，按既有做法）、Console Resource 三菜单、Gateway OPERATIONS Route；本机 `activation-local.sql` 同样式。**ID 块固定用 95（`900000000000095xxx`）**，其他块已被其他需求占用（ledger 科目分级用 91）。
4. 测试：BFF auto 测试（真实 commercial 端点用测试 DTO Mock 服务）、前端单测与构建；本机浏览器走查记录到 `verification/`。

## 协同约束（2026-10-19 夜间）

- 其他会话今晚同时在改 ops-bff / Web（ledger 科目分级，块 91）；本线在独立 worktree 从基点提交分支，只改本需求新增文件与必要的注册 / 路由入口，合并前 rebase 到最新 main，合并后把 ops-bff / Web 改动文件路径列表写进报告，供对方叠加。
- 共享文件（`requirements/README.md`、`CURRENT_IMPLEMENTATION.md`、arch-rules 表归属）只按 hunk 提交。

## 不做

Client 门户费率页（另行登记）、Partner 门户。
