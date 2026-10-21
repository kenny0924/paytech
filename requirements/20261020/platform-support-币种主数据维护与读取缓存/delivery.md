# platform-support：币种主数据维护与读取缓存 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-20 | 来源 | 用户：币种在 Platform 维护；ledger 等改为读取 Platform 并用 Caffeine 缓存，不再用只读副本（取代 D41 副本部分） | 已确认 |
| 2026-10-20 | 1 需求评审 | [需求](./requirement.md)；用户“继续”，Q1—Q5 按建议采纳（阶段 2、3 合并评审） | 已确认 |
| 2026-10-20 | 2—3 现状差异与技术方案 | [技术方案](./technique.md)；用户“继续”，Q6—Q8 按建议采纳。决定记录 [2026-10-20 币种快照读取](../../../agent-docs/decisions/2026-10-20-currency-spec-platform-snapshot-read.md) | 已确认 |
| 2026-10-20 | 4—5 研发与本地验证 | 4 个研发代理分别在独立 worktree 完成 Platform、ledger、billing、ops-bff + ops-web，合入集成 worktree `Code-MoR-currency`（基线 `04e127c3`，未合入主工作区、未提交）。本地 SQL：经用户授权给本地 `mor_platform.t_currency_spec` 加 `version` 列并更新表注释（首次执行客户端字符集错误致列注释乱码，已用 utf8mb4 重做）；本地 `mor_ledger` / `mor_billing` 旧副本暂不删除。验证见下 | 已确认（2026-10-21 用户“继续”） |
| 2026-10-21 | 6 发布准备 | 主工作区已前进到 `2f5f37f7`（体验优化、对账切片 1）：改动移植到 `Code-MoR-currency2`（提交候选，`2f5f37f7` + 本需求）并全量复验；发布树 `Code-MoR-currency-rel` = `2f5f37f7` 合并商务未提交但已上线的集成头 + 本需求。基线证明：不含本需求时构建的四个 JAR 与 mor-test 运行版逐类一致、ops-web `dist/` 与运行容器逐字节一致。镜像与发布包见 [release/release.md](./release/release.md)。未改 mor-test | 完成 |
| 2026-10-21 | 7 mor-test 发布与验收 | 用户授权四项（Platform SQL、替换五个镜像、验收与清理、删除副本）。执行记录见 [release.md 第 8 节](./release/release.md)：五个镜像已替换，块 125 已激活，验收 32/34 + 补验 4/4（两项为脚本缺陷），浏览器冒烟通过；冒烟中发现并修复候选覆盖文件漏掉 ops-bff 对账地址的问题；ledger、billing 副本已删除 | 已确认（2026-10-21 用户“继续”） |
| 2026-10-21 | 8 本地提交 | 用户授权。父提交 `5a103f36`（商务一期，其父为对账 `2f5f37f7`）。通过独立 worktree `Code-MoR-currency3` 的索引构建，只含本需求文件；共享文件（Platform 初始化 DML 块 125 追加到末尾、页面注册数 57、表归属与断言、`OperationsCatalog`、`compose.applications.yaml` 的 ledger / billing 两段、`requirements/README.md`、`CURRENT_IMPLEMENTATION.md`、D41 行、SCN-PLT-09 段）只取本需求的改动块。提交前在该树上复验，见下 | 完成 |

### 阶段 4—5 说明

**集成验证（`Code-MoR-currency`，JDK 25 离线，本地共享库，串行）：**

| 范围 | 结果 |
|---|---|
| 受影响模块编译 | 通过 |
| arch-rules | 61/61 |
| Platform：币种两类 + Operations 全部 + 集成配置 + 网关锁 | 115/115 |
| ledger 全部 auto | 340/340 |
| billing 全部 auto | 98/98 |
| ops-bff 全部 auto | 1869/1869 |
| PDManer 模型脚本（ledger、billing） | 12/12 |
| ops-web 测试 | 1184/1184 |
| ops-web 构建 | 通过 |

`requirements-site` 测试 127 通过、10 失败，失败项均为 Commerce / Payment / 治理文档断言，与本需求无关（主工作区原样存在）。

**与方案的偏差（已在主会话确认）：**
- **状态变更接口：** 用 PATCH。
- **新建重复币种：** 返回 400 业务校验失败（“币种规则已存在: XXX”），与权限目录一致。
- **页面组件键：** `OPERATIONS_CURRENCY_SPECS`，与资源编码一致。
- **审计动作码：** `OPERATIONS_CURRENCY_SPEC_CREATE` / `_UPDATE` / `_STATUS`，资源类型 `CURRENCY_SPEC`。
- **Platform 事务：** 复用 core 既有 `OperationsTransactionExecutor`。
- **快照查不到币种时报可重试的系统异常（`ErrorException`），不报业务拒绝：**
  - **原因：** ledger 的 `AccountPosted` 消费与 billing 的补偿事件都把业务拒绝当作不可重试，一律进死信。
  - **ledger 效果：**
    - 自动记账消费重投，并在刷新后自动恢复；
    - 追平命令返回 500，不推进进度；
    - 开账簿仍返回 `CURRENCY_NOT_ACTIVE`。
  - **billing 效果：** 发布事实时转换为可重试异常，同步登记仍是 400。
- **billing 不提供 `requireActive`：** billing 没有只接受 ACTIVE 的调用方。
- **刷新组件：** 刷新组件始终装配，只有定时开关可关，测试经公开 `refresh()` 触发。
- **币种规则服务被其他领域服务调用：** 按方案作为唯一入口保留。
- **生成脚本补登记：** arch-rules 生成脚本补登记 `t_channel_config_change`（`5f12cf3e` 只手改了 YAML；与主工作区对账会话未提交的同一行一致）。

**发布物：** `release/test-activate.sql` / `test-deactivate.sql`（块 125：路由 4、资源 2、授权 2、角色版本 1）。各库变更 SQL：
- `platform-support/docs/database/sql/changes/2026-10-20-currency-spec-version.sql`
- `ledger/.../changes/2026-10-20-drop-currency-spec-copy.sql`
- `billing/.../changes/2026-10-20-drop-currency-spec-copy.sql`

**阶段 6 复验（2026-10-21）：**

| 范围 | `Code-MoR-currency2` | `Code-MoR-currency-rel` |
|---|---|---|
| arch-rules | 62/62 | 62/62 |
| Platform 币种 + Operations 等 | 115/115 | 115/115 |
| ledger 全部 auto | 340/340 | 与左侧源码相同 |
| billing 全部 auto | 99/99 | 与左侧源码相同 |
| ops-bff 全部 auto | 1964/1964 | 2009/2009 |
| PDManer ledger、billing | 12/12 | — |
| ops-web 测试；构建 | 1199/1199；通过 | 1230/1230；通过 |

遗留（不阻塞）：ledger 读 Platform 币种响应的严格 JSON 上限 8 KB，约 60 个币种时会超出（billing 为 64 KB），后续调大。

**阶段 8 提交前复验（`Code-MoR-currency3`，父 `5a103f36`）：**
- **测试：** 受影响模块编译通过；arch-rules 62/62，Platform 115/115，ops-bff 全部 auto 2009/2009，ops-web 1231/1231，构建通过，PDManer 12/12。
- **ledger、billing：** 源码与 `Code-MoR-currency2` 逐文件相同（340/340、99/99）。
- **requirements-site：** 127 通过、10 失败，失败项与 `5a103f36` 原样一致，均为 Commerce、Payment、治理文档断言。

**主工作区合并注意：** 另一会话在 billing（计费事实引用）、`platform-support-initial-data.sql`、arch-rules 三个文件上有未提交改动，与本需求文件重叠。提交前需三方合并，或等对方提交后再合入。
