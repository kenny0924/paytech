# mor-test 发布基线（阶段 6 输入，2026-10-20 由财务会话 code-mor-9c 提供）

财务三项已于 06:09–06:20Z 发布 mor-test（接口 28/28、浏览器 8/8）。本需求之后的构建与发布以此为基线：

| 项 | 基线 |
|---|---|
| 源码 | main `d565f85f` + worktree `/Users/kenny/Desktop/Workspace/Code-MoR-fin4`（财务三项，尚未提交；提交后以其 commit 为准） |
| 镜像（tag `fin-20261020`） | platform-support `sha256:d800c3ce358637594d1357ec51e76003600baf7497eb3f09e552467c853ecfc9`；ops-bff `sha256:adec3f63f1ad1109373614417d1c014d88582cece2db4d1f4f5a49299d1e3a23`；ops-web `sha256:c6661078a8f1fdbe616109dada519c076b0511428848b6155676b3f9957ed7f9`；ledger `d846efae`；billing `99839156` |
| mor-test `mor_platform` | 块 120–122 已启用，PLATFORM_ADMIN 角色版本 35；风控控制台另占 101xxx–104xxx、111xxx–114xxx（不在 initial-data.sql），发布前查库确认 92xxx 空闲 |
| Compose | ops-bff 新增 `OPERATIONS_BILLING_BASE_URL`，ledger 新增 XXL 执行器变量（写在 fin4 worktree 的 `compose.applications.yaml`；本次以发布覆盖文件加入）。重建 ops-bff 时必须保留 `OPERATIONS_BILLING_BASE_URL` |

本需求阶段 6 构建顺序：核对 fin4 是否已提交 → 在 main（或 main + fin4 diff）之上叠加本需求 → 构建 channel-connector、platform-support、ops-bff、ops-web 四个镜像（固定 sha256）→ `mor_channel_connector` DDL、`mor_vault` PROTECT 规则、`mor_platform` 块 92 + 激活 → 验收。
