# paytech-messaging：Broker 连接恢复后重新声明拓扑

- 日期：2026-10-18；系统：Foundation（`paytech-messaging`）；来源：mor-test 重建 RabbitMQ 容器（固定主机名）后 3 分钟内无任何服务重新声明 V2 拓扑（0 队列、0 交换机），需重启 8 个使用 AMQP 的服务才恢复。
- 现状：`MessagingTopologyDeclarer` 只在应用启动时声明 `<producer>.events` Fanout、`<consumer>.<producer>.hash` 一致性哈希交换机、`p0..pN-1` Quorum 分区队列与 DLQ；Spring AMQP 连接恢复后不重复声明。
- 目标：Broker 重建、节点更换或长时间断连后，连接一恢复即按原参数幂等重声明本服务的发布与订阅拓扑，监听容器自动恢复消费；无需重启服务。
- 范围：`paytech-messaging`（生产）与其 `auto` 测试；不改事件目录、分区数、队列参数；不改业务服务代码（装配经 Starter 自动生效）。
- 验证：messaging `auto` 测试用本机 Broker 真实验证"删除队列与交换机 → 触发连接重建 → 拓扑恢复、消息可继续投递与消费"；全 Reactor 不回归；mor-test 以新镜像重建 RabbitMQ 再验证一次（阶段 2，由主会话执行）。
