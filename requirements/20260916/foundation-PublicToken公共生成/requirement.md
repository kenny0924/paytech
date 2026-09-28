# Public Token 公共生成

用户确认将 Checkout Public Token 生成能力放入 Foundation 静态工具，只保留可变混入参数。
业务系统自行拼接前缀，工具内部使用当前系统时间，不接收 `prefix` 或 `createdAt`。

本期仅涉及 Foundation Common Core 与 Commerce 已有 Checkout 消费者。保留 128 位安全随机 Payload、
现有 30 位主体、CRC 与 `chk_` Wire；相同参数重复调用仍产生新随机值。旧链接继续按原 Token 校验和查询。
数据库唯一约束、订单创建时间、过期时间、归属和授权判断由业务保留，不引入新服务或存储。

同日后续确认每次创建只生成一个 Token，不预生成多个候选；唯一冲突时创建事务回滚，不自动换 Token 重试。

不涉及 SQL、PDManer、Gateway、Payment、发布或部署。确认与交付入口见[交付记录](delivery.md)。

## Commerce 公开订单号与退款号后续统一

用户要求 orderNo 和 refundRequestNo 一起采用现有 publicToken 格式。本增量扩展 Commerce 的消费者：新 orderNo 使用 `ord_`，新 refundRequestNo 使用 `rfd_`，二者均直接拼接公共工具生成的 30 位主体，总长 34 位。字段名及作为公开业务标识的语义保持；Client API 仍按可信 Client 校验归属。

只改变新编号的生成，旧订单和退款号不重建。查询入口继续接收历史编号，不把统一格式扩大为数据迁移。按用户此前要求，本轮不处理测试、不运行验证。
