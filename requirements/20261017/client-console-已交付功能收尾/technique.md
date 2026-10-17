# Client Console 已交付功能收尾 — 技术方案（阶段 2+3）

> 状态：2026-10-17 阶段 2+3（现状差异 + 方案设计，按 Q4 合并）评审稿，待用户确认第 4 节待决项。范围只含 F-02、F-03；F-01 待用户提供已验证邮箱后单独执行（只做验证）。

## 1. 现状差异

| 需求项 | 当前行为及证据 | 缺口 |
|---|---|---|
| F-02 开发者浏览器验收上 test | `developer_browser.mjs:19-20` 写死本机 Client 入口 `https://127.0.0.1:18443` 与 Platform 私网 `http://127.0.0.1:18106`；Webhook 步骤用本机未监听地址 `https://127.0.0.1:1/browser-hook` 建端点，并直接调 Platform 私网接口提交 `order.paid`，再看投递列表 / 详情、重发 | test 不允许内网 / 本机地址建端点（只允许可解析到公网的 HTTPS）；test 的 Platform 没有宿主机端口；按 2026-10-16 决定，test 不向外网真实推送（`developer_flow.py` 用 `example.com` 建端点后立即停用，不发测试事件与业务事件） |
| F-03 资料批准时间 | `t_party_profile.approved_time` 列已存在（`client-contract-schema.sql:24`，“资料首次批准并永久锁定的 UTC 时间”），但没有任何代码写入；批准在 `ClientReviewOutcomeServiceImpl.conclude` 一个事务内完成（申请结论、Client 激活、生命周期历史、投影任务）；工作区摘要 `approvedAt` 取最近一份 APPROVED 申请的 `MAX(update_time)`（`ClientApplicationMapper.xml:42-49`） | 批准时不写 `approved_time`；摘要日期会随申请记录后续更新而变化；存量已批准客户全部为空 |

读取 `approved_time` 的现有代码只有“被拒后重新申请”转移草稿资料（`ClientDraftMapper.xml:41` `approved_time IS NULL`）：被拒的申请从未批准，写入批准时间不影响该流程。资料“永久只读”现在由申请 APPROVED 状态保证，写入后不改变可编辑判断。

## 2. 影响

| 系统 | 改动 | 模型 / 配置 |
|---|---|---|
| CCT | `PartyProfileRepository` 新增“标记批准”（条件 `approved_time IS NULL`，行数必须为 1）；`ClientReviewOutcomeServiceImpl.conclude` 在激活分支同一事务调用；摘要 SQL 的 `approvedAt` 改为 `COALESCE(f.approved_time, 最早 APPROVED 申请 update_time)`；测试 | 无 DDL；新增回填 DML（需求目录 `db/dml.sql`） |
| Client BFF / Web | 不改（字段名与格式不变） | — |
| 验收脚本 | `developer_browser.mjs` 增加 `E2E_TARGET=test` | — |
| Platform、Gateway | 不改 | — |

## 3. 方案

### 3.1 F-03 写入与读取

- **写入**：`conclude` 在 `context.activates()` 分支内，与 `clients.activate` 同一事务，用 Client 的 `party_id`（`context.getActivation().partyId()`）执行

  ```sql
  UPDATE t_party_profile SET approved_time=#{now}, update_time=#{now}
  WHERE party_id=#{partyId} AND approved_time IS NULL
  ```

  影响行数必须为 1，否则抛出并回滚（消息重投处理，与现有条件更新一致）。只写 Client 主体自己的资料，不写关联人员资料（见 D1）。
- **读取**：摘要 `approvedAt` 取 `COALESCE(f.approved_time, approved.firstApprovedAt)`，其中子查询改为 `MIN(update_time)`；“只返回存在 APPROVED 申请的 Client”条件不变。回填后正常情况下都取 `approved_time`，`COALESCE` 只保护“新 CCT 已上线、回填尚未执行”的窗口。
- **回填 DML**（可重复执行，只填空值）：

  ```sql
  UPDATE t_party_profile f
  JOIN t_client c ON c.party_id = f.party_id
  JOIN (SELECT client_id, MIN(update_time) AS approved_at FROM t_client_application
        WHERE status = 'APPROVED' GROUP BY client_id) a ON a.client_id = c.client_id
  SET f.approved_time = a.approved_at, f.update_time = UTC_TIMESTAMP(6)
  WHERE f.approved_time IS NULL;
  ```

  头部写明：回填值是“最早一份 APPROVED 申请的最后更新时间”的近似值（Q3）；回滚为把本次填写的行改回 NULL（执行前先导出受影响行）。
- **测试**：批准结果监听器测试补断言（批准后 `approved_time` 非空且等于结论时间；退回 / 拒绝不写）；`ClientSummariesTest` 补“申请记录批准后再更新，`approvedAt` 不变”。

### 3.2 F-02 test 浏览器验收

- `E2E_TARGET=test` 时 Client 入口改为 `https://localhost:28443`。
- API 凭证部分（新建、一次性密钥弹窗、列表、轮换、撤销）与本机相同。
- Webhook 部分在 test 只做：新建端点（`https://example.com/code-mor-acceptance`）→ 一次性密钥弹窗 → 列表 → 停用；**跳过**测试事件、提交 `order.paid`、投递列表 / 详情、重发（D2），并在汇总里逐项标为“test 跳过”而不是 PASS。
- 390px 两页无横向溢出与本机相同。
- 前置账号：沿用 `team_browser_accounts.py` 的“已批准所有者”构造（只取 owner），test 已有 Scope `orders.read`。

## 4. 待决项

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | `approved_time` 只写 Client 主体资料，还是也写组织的关联人员资料？ | 只写 Client 主体：工作区摘要只用它；关联人员资料可能被其他申请共享，写入“永久锁定”时间会让以后的共享语义变复杂，目前没有消费者 |
| D2 | test 浏览器验收是否发送测试事件 / 业务事件？ | 不发送：保持 2026-10-16 决定，不向外网真实推送；送达、签名、投递详情、重发已由本机 `developer_flow.py` 与 `developer_browser.mjs` 覆盖 |
| D3 | 发布范围 | 只替换 test 的 CCT（BFF、Web、Platform 不变）；DML 在 CCT 替换前执行（旧 CCT 不读该列，先执行无影响） |

## 5. 研发与验证计划

| 顺序 | 内容 | 验证 |
|---|---|---|
| 1 | CCT 写入、读取、回填 DML、测试 | CCT 全量 auto（本机真实 MySQL / RabbitMQ） |
| 2 | `developer_browser.mjs` test 目标 | 本机先跑一遍确认行为不变 |
| 3 | 本机全链路：本机 `mor_cct` 执行回填、替换本机 CCT，`workspace_flow.py` 回归，首页日期检查 | API 与浏览器 |
| 4 | test（阶段 6+7 另行确认）：备份、回填 DML、替换 CCT、`workspace_flow.py` 回归、`E2E_TARGET=test developer_browser.mjs` | test 正式入口 |

验收映射：AC-03 → 3.2；AC-04、AC-05 → 3.1 写入与读取及其测试；AC-06 → 回填 DML；AC-07 → 既有可编辑判断与“被拒后重新申请”测试回归。
