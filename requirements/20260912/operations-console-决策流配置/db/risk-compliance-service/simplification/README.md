# RCO-17 精简修订：SQL 执行说明

对应 [精简方案](../../../simplification.md)。本机三表已另获用户确认并执行，具体证据见 [本机应用记录](./local-application.md)。test 等其他环境尚未获本次执行授权；不能从原四张流程表或本机授权推断其他环境也获批。

目标仅为 Risk 自有 `mor_risk`，具体 local/test 环境分别确认。初始化 DML、Platform 注册 DML 为 0；本目录 `dml.sql` 是历史引用回填，不是新的初始化数据权威。

## 执行顺序

1. 研发完成、具体库授权后，在相关配置写入停下的窗口核对目标。保留三张受影响表的数据及结构、旧历史读取/Hash 清单；原 Point Version/Input Field/Binding 历史不删除。
2. 运行 [precheck.sql](./precheck.sql)，所有 `bad_*` 为 0；另用 Risk 原历史 Reader 确认完整内容及 Hash 有效、所有字段定义属于支持目录。否则停止，不能用当前合同补齐损坏来源。
3. 人工执行 [ddl.sql](./ddl.sql) 添加 4 个可空列。MySQL DDL 不能依赖事务回滚；记录每条 ALTER 成功与否，失败后核对结构，不能盲目重跑整个文件。
4. 同一人工 MySQL 会话执行 [dml.sql](./dml.sql)，它保留打开的事务。核对三类更新行数等于本次预检，然后执行 [postcheck.sql](./postcheck.sql)，全部异常为 0 后人工 `COMMIT`；否则 `ROLLBACK`。不要断开连接后另起会话提交。
5. 以不开放写入的新 Reader 核对迁移后的规则、协议和 Release 规范字节、Hash、回放、原顺序图，以及原字段 ID 可准确还原。只有 Reader 实际实现并验证通过才允许删列，本轮设计检查不满足此条件。
6. 执行 [finalize-ddl.sql](./finalize-ddl.sql)：收紧 2 列为 NOT NULL，删除原 2 个字段及索引。同步后的唯一模型/最终 Schema 与此结果一致。
7. Risk/BFF/Web 对齐后恢复入口。旧 BFF/Web 对 null ID 的严格校验不兼容，不能只发布 Risk。迁移期不增加双写或双读模式；应用/静态资源刷新按发布清单执行。

## 恢复界限

回填提交前可在原事务回滚数据；扩展 DDL 需按实际结构单独处理。删列后不能只换回旧应用：需要恢复原三表/列或依准确旧快照还原，并核对 Hash。新规则已写入后没有旧配套协议，旧应用无法读取，禁止凭空补造协议 ID 作为回退；届时应停止受影响写入并优先修复新版，数据恢复范围须根据实际新增数据另行确认。

## 本轮只读证据

[预检快照](../../../assets/table-review/simplification-precheck.json)：本机回填 0 行；test 33 个规则版本、418 个 Context 操作数、11 个 Context 动作参数，共 462 行；三个 `bad_*` 均为 0。这是预检时的数据量，不是 test 已回填记录，也不是后续环境执行时的固定断言。本机执行前重新核对仍为空；其执行结果以本机应用记录为准。
