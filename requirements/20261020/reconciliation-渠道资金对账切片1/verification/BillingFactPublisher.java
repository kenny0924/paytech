import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 阶段 5 验收夹具：扮演 billing，以正式发布器（RabbitDomainEventPublisher + DomainEventCodec，与 reconciliation-test 的
 * BillingEventPublisher 相同）向本机 RabbitMQ 临时 vhost 的 billing.events 发布 BillingFactRecorded。
 * 标准输入每行一个 BillingFactRecorded Schema v1 Payload（字符串标量）；输出每行事件 ID。
 * 连接参数取环境变量 RECON5_RABBIT_USER / RECON5_RABBIT_PASSWORD / RECON5_RABBIT_VHOST，不打印。
 * 运行：java --class-path '<reconciliation exec JAR 的 BOOT-INF/lib/*>' BillingFactPublisher.java < facts.jsonl
 */
public class BillingFactPublisher {
    public static void main(String[] args) throws Exception {
        CachingConnectionFactory factory = new CachingConnectionFactory("127.0.0.1", 5672);
        factory.setUsername(System.getenv("RECON5_RABBIT_USER"));
        factory.setPassword(System.getenv("RECON5_RABBIT_PASSWORD"));
        factory.setVirtualHost(System.getenv("RECON5_RABBIT_VHOST"));
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMandatory(true);
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher("billing", Duration.ofSeconds(10),
            new DomainEventCodec(), template, new MessagingMetrics(new SimpleMeterRegistry()));
        JsonMapper json = JsonMapper.builder().build();
        // 每条发布同步等待 Broker 确认；大批量（性能夹具）用多线程并行发布，事件之间无顺序依赖（strictOrder=false）。
        int threads = Integer.parseInt(System.getenv().getOrDefault("RECON5_PUBLISH_THREADS", "1"));
        factory.setChannelCacheSize(Math.max(25, threads * 2));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> futures = new ArrayList<>();
        long started = System.nanoTime();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode payload = json.readTree(line);
            futures.add(pool.submit(() -> {
                DomainEvent event = DomainEvent.builder().eventType("BillingFactRecorded").schemaVersion(1)
                    .aggregate("BILLING_FACT", payload.get("factId").asString())
                    .aggregateVersion(Long.parseLong(payload.get("factVersion").asString()))
                    .occurredAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                    .sourceService("billing").instanceId("recon5-fixture").payload(payload).build();
                publisher.publish(event);
                return event.eventId();
            }));
        }
        for (Future<String> future : futures) {
            System.out.println(future.get());
        }
        pool.shutdown();
        System.err.println("published " + futures.size() + " in " + (System.nanoTime() - started) / 1_000_000 + " ms");
        factory.destroy();
    }
}
