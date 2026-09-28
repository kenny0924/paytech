# CARD + PAY 动作名称人工切换

本次仅维护目标模型、Schema 和代码；没有数据库执行授权。表结构仍以[完整 Schema](../../../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql)为准；旧库的字段精简和公共命令/补偿表前置仍按[原迁移说明](./execution-simplified-migration.md)核对。

1. 在目标环境停写并盘点 `t_payment_action`、`t_channel_transaction_ref`、当前指针、命令和补偿任务；备份原始数据与 SHOW CREATE。
2. 仅当确认都是同一自有业务语义时，将旧 Action `RISK_CHECK → PAY_RISK`、旧 Action 与 Transaction Ref `SALE → PAY`。检查 Ref 唯一键冲突、历史 Reader 和事件消费者；不能假定新旧字符串并存时可直接批量改写。
3. 旧 Action 的参数、父级和幂等键不得重新生成。旧任务类型不自动改成 `PAYMENT_CARD_PAY_ACTION`；已受理但没有第一条 Action 的历史 Attempt 不自动补发。逐笔确认是否发生过外部操作及可恢复身份，再制定独立方案。
4. 只变更经审阅的列注释和数据；不修改外部协议、Channel 能力代码，不迁移其他系统数据库。若无历史数据，仍须核对实际表结构后再使用当前空库脚本；不对非空库执行 DROP/重建。
5. Risk/Channel、调度扫描、回调及上游验收完成前不启用真实支付。部署及 SQL 执行须有目标环境的单独授权。

本文件是迁移约束，不是已执行脚本或完成回执。当前验证与待迁移状态见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

删除 Action.CREATED 后，目标动作初态为 PROCESSING。旧 t_payment_action 中的 CREATED 行须连同原执行位、版本、幂等身份及是否存在在途调用一起盘点；不得仅替换字符串后自动发起执行。本次只改目标模型与代码，没有执行历史数据或数据库注释迁移。退款及争议 Action 的独立状态机不在本次删除范围。
