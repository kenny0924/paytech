import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 阶段 7 mor-test 验收夹具（由阶段 5 verification/BillingFactPublisher.java 改写）：以正式发布器
 * （RabbitDomainEventPublisher + DomainEventCodec）编码 BillingFactRecorded，但只投到 reconciliation 自己的一致性哈希交换机
 * {@code reconciliation.billing.hash}（路由键 = aggregateId，与 billing.events 扇出后的路由相同），
 * 不经 {@code billing.events}，因此 mor-test 的 account 不会收到合成事实。
 * 标准输入每行一个 BillingFactRecorded Schema v1 Payload；输出每行事件 ID。
 * 环境变量：RECON7_RABBIT_HOST / _PORT / _USER / _PASSWORD / _VHOST、RECON7_TARGET_EXCHANGE（默认 reconciliation.billing.hash）；不打印。
 * 运行：java --class-path '<reconciliation 候选镜像 JAR 的 BOOT-INF/lib/*>' ReconBillingFactPublisher.java < facts.jsonl
 */
public class ReconBillingFactPublisher {
    public static void main(String[] args) throws Exception {
        String target = System.getenv().getOrDefault("RECON7_TARGET_EXCHANGE", "reconciliation.billing.hash");
        CachingConnectionFactory factory = new CachingConnectionFactory(System.getenv().getOrDefault("RECON7_RABBIT_HOST", "127.0.0.1"),
            Integer.parseInt(System.getenv().getOrDefault("RECON7_RABBIT_PORT", "25672")));
        factory.setUsername(System.getenv("RECON7_RABBIT_USER"));
        factory.setPassword(System.getenv("RECON7_RABBIT_PASSWORD"));
        factory.setVirtualHost(System.getenv("RECON7_RABBIT_VHOST"));
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        // 只改交换机：编码、消息头、路由键、确认与退回处理全部沿用正式发布器。
        RabbitTemplate template = new RabbitTemplate(factory) {
            @Override
            public void send(String exchange, String routingKey, Message message, CorrelationData correlationData) {
                if (!"billing.events".equals(exchange)) {
                    throw new IllegalStateException("unexpected exchange " + exchange);
                }
                super.send(target, routingKey, message, correlationData);
            }
        };
        template.setMandatory(true);
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher("billing", Duration.ofSeconds(10),
            new DomainEventCodec(), template, new MessagingMetrics(new SimpleMeterRegistry()));
        JsonMapper json = JsonMapper.builder().build();
        List<String> ids = new ArrayList<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode payload = json.readTree(line);
            DomainEvent event = DomainEvent.builder().eventType("BillingFactRecorded").schemaVersion(1)
                .aggregate("BILLING_FACT", payload.get("factId").asString())
                .aggregateVersion(Long.parseLong(payload.get("factVersion").asString()))
                .occurredAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .sourceService("billing").instanceId("recon7-fixture").payload(payload).build();
            publisher.publish(event);
            ids.add(event.eventId());
        }
        ids.forEach(System.out::println);
        System.err.println("published " + ids.size() + " to " + target);
        factory.destroy();
    }
}
