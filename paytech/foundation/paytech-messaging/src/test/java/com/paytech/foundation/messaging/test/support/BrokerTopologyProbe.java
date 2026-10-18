package com.paytech.foundation.messaging.test.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 经本地 RabbitMQ Management API 只读观察本次运行的队列、交换机与绑定定义（类型、持久、参数），用于断言拓扑按原参数恢复。
 * <li>地址固定为 Loopback，端口默认 15672，可用 {@code -Dpaytech.test.rabbitmq.management-port} 覆盖；账号与 AMQP 相同。</li>
 * <li>只读，不保存状态，不修改 Broker。</li>
 */
@TestComponent
public final class BrokerTopologyProbe {

    /** Management API 端口。 */
    public static final int MANAGEMENT_PORT = Integer.getInteger("paytech.test.rabbitmq.management-port", 15672);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String authorization;
    private final String virtualHost;

    /**
     * 创建观察器。
     */
    public BrokerTopologyProbe() {
        LocalRabbitEnvironment environment = LocalRabbitEnvironment.load();
        String credentials = environment.username() + ":" + environment.password();
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.virtualHost = encode(environment.virtualHost());
    }

    /**
     * 队列定义。
     *
     * @param queue 队列名
     * @return 定义；不存在为 null
     */
    public JsonNode queue(String queue) {
        return get("/api/queues/" + virtualHost + "/" + encode(queue));
    }

    /**
     * 交换机定义。
     *
     * @param exchange 交换机名
     * @return 定义；不存在为 null
     */
    public JsonNode exchange(String exchange) {
        return get("/api/exchanges/" + virtualHost + "/" + encode(exchange));
    }

    /**
     * 交换机到队列的绑定。
     *
     * @param exchange 源交换机
     * @param queue 目标队列
     * @return 绑定列表；源或目标不存在时为空
     */
    public List<JsonNode> queueBindings(String exchange, String queue) {
        return bindings("/api/bindings/" + virtualHost + "/e/" + encode(exchange) + "/q/" + encode(queue));
    }

    /**
     * 交换机到交换机的绑定。
     *
     * @param source 源交换机
     * @param destination 目标交换机
     * @return 绑定列表；源或目标不存在时为空
     */
    public List<JsonNode> exchangeBindings(String source, String destination) {
        return bindings("/api/bindings/" + virtualHost + "/e/" + encode(source) + "/e/" + encode(destination));
    }

    private List<JsonNode> bindings(String path) {
        JsonNode array = get(path);
        List<JsonNode> bindings = new ArrayList<>();
        if (array != null) {
            array.forEach(bindings::add);
        }
        return bindings;
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + LocalRabbitEnvironment.HOST + ":"
                + MANAGEMENT_PORT + path))
            .header("Authorization", authorization).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw unavailable(exception.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("management request interrupted", interrupted);
        }
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw unavailable("HTTP " + response.statusCode() + " for " + path);
        }
        return MAPPER.readTree(response.body());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static AssertionError unavailable(String reason) {
        return new AssertionError("Local RabbitMQ Management API is not reachable on " + LocalRabbitEnvironment.HOST
            + ":" + MANAGEMENT_PORT + " (" + reason + "). Start the repository local-environment base profile with "
            + "the rabbitmq_management plugin enabled, or pass -Dpaytech.test.rabbitmq.management-port.");
    }
}
