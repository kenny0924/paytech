# 交付记录 · paytech-messaging 连接恢复后重声明拓扑

| 日期 | 阶段 | 结果 | 状态 |
|---|---|---|---|
| 2026-10-18 | 实现与模块验证 | `MessagingTopologyRecovery` 连接监听器按启动时登记的参数幂等重声明；监听容器不再把队列缺失视为致命；4 个真实 Broker 恢复用例。messaging 71、acquiring 804、card 38、client-master 63、ledger 204、account 40 全绿。合入 main fecae671（需求目录移回根 70d3ea8b）。 | 完成 |
| 2026-10-18 | mor-test 重建验证 | `build.sh` 重打 18 个镜像，重建 8 个 AMQP 服务后 `force-recreate` RabbitMQ（节点 `rabbit@rabbitmq`）：**不重启任何服务**，26 个队列与 13 个交换机立即恢复，20 个分区队列全部重新挂上消费者，30 个容器 healthy；消费方日志各有一条"连接恢复后已重声明拓扑"。修复前同一操作需重启 8 个服务。 | 完成 |

遗留（见 `tasks/01-report.md` 待确认）：重声明失败项不自动重试；连接不断而队列被删时不触发重声明；mor-test 旧节点目录仍留在 RabbitMQ 卷内。
| 2026-10-18 | 遗留处理 | ① 失败重试、② 连接不断队列被删的恢复：立项为任务卡 02（agent 执行中）；③ mor-test RabbitMQ 卷内三个旧节点目录（rabbit@4df0dd04c154 / 9f18b768bbb5 / ac527bae5eef）已打包到 `test-environment/private/backup-20261018/rabbitmq-old-nodes-20261018.tgz`（223 项，SHA256 已登记）后删除，当前节点 rabbit@rabbitmq 与 26 个队列不受影响。 | 任务卡 02 已合入 main（失败项退避重试、队列 / 交换机被删自动恢复；messaging 73、五个消费方模块全绿）；mor-test 以新镜像验证见下一行 |
