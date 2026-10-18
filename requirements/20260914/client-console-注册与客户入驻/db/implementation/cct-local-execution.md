# CCT 入驻资料：本机十表执行候选

用户针对本文件的十表范围回复“可以”，已授权本机执行及隔离回归。[执行回执](../../assets/stage4/cct-onboarding-table-execution-20260915.json)记录该次十条 ALTER 的成功结果，实际状态见[当前台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。本文件保留精确授权与核验范围，**不是可重复执行的迁移入口**。草稿写链还须完成[依赖补齐](../../cct-write-dependencies.md)，不能仅凭十表迁移启用入驻。

## 精确范围

| 项目 | 候选 |
|---|---|
| 环境 | Docker `desktop-linux` / Compose `mor-local` / `mor-local-mysql-1` |
| 连接与库 | `127.0.0.1:13306`，已有 `mor_cct` |
| 唯一 SQL 来源 | [client-contract-empty-closure.sql](./client-contract-empty-closure.sql)，十条 `ALTER TABLE`，按文件顺序；连接使用 utf8mb4 |
| SQL 文件 SHA-256 | `dc091d30620d30374fa0e5a0b54f93614201bf4e5c46016d1e1958ebc76a0815` |
| 变更目标 | 单份资料、可部分填写的草稿、账号唯一活动申请、稳定关系 ID、提交身份及运营分类确认字段 |
| 验证 | 每表与当前 CCT 完整 Schema 的列、类型、顺序、可空性、默认值、主键、索引及注释逐项比较；其他 28 表保持原实际结构 |

十表依次为：`t_party_profile`、`t_individual_profile`、`t_organization_profile`、`t_party_address`、`t_party_contact`、`t_party_profile_identifier`、`t_party_identity_key`、`t_party_relation`、`t_client_business_profile`、`t_client_application`。不创建库、不清表、不执行完整 Schema 或初始化 DML；不改 Risk、Platform、Commerce、Payment、Finance 或共享测试环境。

`t_compensation_event` 不在这十表内。它仍是旧二十列结构，与现行 Foundation 不兼容；其后续模型/人工 SQL 必须另行补齐，不在执行十表时临时添加第十一条语句。此次也不迁移 CCT 的命令幂等机制。

## 只读证据与范围差异

[预检记录](../../assets/stage4/cct-onboarding-preflight-20260915.json)保存源提交、冻结模型、源 Schema、SQL 和代码指纹，以及结构备份位置。实际 CCT 38 表均为空；16 表保守闭包及 14 项本库引用检查均为零。38 表的列、索引、引擎与排序规则均匹配源 Schema；受影响十表连同注释完全一致。

其余 28 表中，`t_commercial_term_price` 与 `t_payment_method_profile` 的表注释仍用 Provider，而源文件已用 Channel；已核对差异仅为该术语替换。此次保留这两张表的实际注释，不把差异悄悄修进入驻迁移。

其他 Owner 使用各自选定数据库分别只读盘点，无跨库 JOIN：本机 Commerce 业务表为空，另有三条 FAILED 的 `SUBMISSION_EVENT_TYPE` 任务；当前 [RefundSubmissionHandler](../../../../../paytech/acquiring/paytech-acquiring-core/src/main/java/com/paytech/acquiring/ext/job/RefundSubmissionHandler.java)证明该类型只恢复 Commerce 退款并调用 Payment，不是 CCT 入驻回调。任务保持原样，不认定为可删除测试残留。Risk 非空记录属于规则目录，Case/申请来源表为空；Platform 现有 Tenant 均为 INTERNAL，未发现 CLIENT/PARTNER 投影；Payment/Finance 已有表为空。该结论仅覆盖记录列出的本机实际库，不覆盖其他环境或尚未落地的表。

盘点时 CCT 无其他数据库连接；测试结束后未发现 CCT Java 进程或 18101 监听。这是瞬时证据，执行前仍须再次核验。已有结构备份仅含 SHOW CREATE，目录为 0700、文件为 0600；没有业务行导出或恢复演练，不能据此声称具备生产回滚能力。

## 本次授权的执行与核验步骤

1. 复核本文件对应的用户授权、精确环境、SQL 文件指纹与冻结源。再次运行[只读预检](../../verification/preflight-cct-local.py)，任何受影响表结构漂移、有行或活动 CCT 写入者均立即停止；不改变非空环境的数据来凑空库条件。
2. 排除 CCT 应用/测试写入并取得本机维护协调锁；重新保存 38 表 SHOW CREATE 与计数。该锁只串行化遵守同一锁的维护动作，不能代替应用停写。管理员凭据只从已核验容器内存读取，不读 `.env.local`、不落文件或命令参数。
3. 通过指定 `mor_cct` 的连接按顺序执行十条 ALTER。MySQL DDL 自动提交；某条失败立刻停止并保存已执行语句的结果，不继续、不自动 DROP/回填/还原历史，不使用 `--force` 或全量 Schema 掩盖问题。
4. 核对十表目标定义；逐字比较其余 28 表 SHOW CREATE 未变；38 表仍为空。保存每条 SQL 指纹、前后结构指纹、时间和核验结果。若部分执行失败，保持入口关闭并根据准确现场另行决定恢复操作。
5. 使用已核验的本机 `cct_app` 凭据运行现有 CCT 真实 HTTP/MySQL Checkout 回归及相关策略测试。测试只创建和回收自身成功插入的合成记录，之后再核对各表计数；Risk 资格依赖仍必须拒绝，不能为通过测试加入默认批准实现。

本次授权仅覆盖上述十表切换及隔离验证，没有启用公共路由、部署、提交或修改旧补偿任务。数据库操作与普通研发分别授权的要求来自根 [AGENTS.md 的 Change discipline](../../../../../AGENTS.md#change-discipline)。本次使用用户对 CCT 精确范围的新授权，没有沿用前一次 Platform 单表授权。

最终十表 99 列/27 索引与目标 Schema 完整匹配，其余 28 表 SHOW CREATE 未变；完整 CCT `clean verify` 35/35 通过，详见[数据库回归](../../assets/stage4/cct-checkout-mysql-verification-20260915.json)。MySQL 对批准 SQL 中的 `TINYINT(1)` 给出一条 1681 显示宽度弃用警告，已记录，未临时修改批准类型。迁移和各轮测试后均核对 38 表为空；不把该本机结果视为生产回滚演练或完整入驻验收。
