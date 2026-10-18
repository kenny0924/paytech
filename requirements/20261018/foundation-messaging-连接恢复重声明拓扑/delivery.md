# 交付记录 · paytech-messaging 连接恢复后重声明拓扑

| 日期 | 阶段 | 结果 | 状态 |
|---|---|---|---|
| 2026-10-18 | 实现与模块验证 | `MessagingTopologyRecovery` 连接监听器按启动时登记的参数幂等重声明；监听容器不再把队列缺失视为致命；4 个真实 Broker 恢复用例。messaging 71、acquiring 804、card 38、client-master 63、ledger 204、account 40 全绿。合入 main fecae671（需求目录移回根 70d3ea8b）。 | 完成 |
| 2026-10-18 | mor-test 重建验证 | `build.sh` 重打 18 个镜像，重建 8 个 AMQP 服务后 `force-recreate` RabbitMQ（节点 `rabbit@rabbitmq`）：**不重启任何服务**，26 个队列与 13 个交换机立即恢复，20 个分区队列全部重新挂上消费者，30 个容器 healthy；消费方日志各有一条"连接恢复后已重声明拓扑"。修复前同一操作需重启 8 个服务。 | 完成 |

遗留（见 `tasks/01-report.md` 待确认）：重声明失败项不自动重试；连接不断而队列被删时不触发重声明；mor-test 旧节点目录仍留在 RabbitMQ 卷内。
