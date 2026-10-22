package com.paytech.channel.connector.protocol.ext.outbound;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionErrorException;

/**
 * 出站渠道 HTTP（原子 API 结构第 5.1 节）：JDK HttpClient，不重定向、不保存 Cookie、不做隐式认证，也不重试。
 *
 * <p>
 * 请求体与响应体各最多 1 MiB，请求 Header 合计最多 16 KiB；Host、Content-Length、Transfer-Encoding、 Connection 等由传输层处理，配置中的同名 Header
 * 不发送。任何传输失败只报固定文字。
 *
 * <p>
 * 本类是全服务唯一直接调用 {@code HttpClient#send} 的位置（架构门禁 R15）。支付渠道与服务渠道各用独立的 HttpClient 实例（执行舱），
 * 连接池与选择器线程互不共享；发送前经 {@link ChannelAddressGuard} 解析并校验目标主机的全部地址，被拦截时不发出请求。
 */
@Component
public class ChannelHttpClient {

    /** 请求体、响应体上限。 */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    /** 请求 Header 合计上限（名称 + 值的 UTF-8 字节）。 */
    public static final int MAX_HEADER_BYTES = 16 * 1024;

    /** 由传输层处理、不能配置的 Header。 */
    static final Set<String> TRANSPORT_HEADERS = Set.of("host", "content-length", "transfer-encoding", "connection",
        "expect", "upgrade", "keep-alive", "te", "trailer", "proxy-connection");

    private final Map<ChannelType, Map<Duration, HttpClient>> clients = new EnumMap<>(Map.of(
        ChannelType.PAYMENT, new ConcurrentHashMap<>(), ChannelType.SERVICE, new ConcurrentHashMap<>()));

    private final ChannelAddressGuard addressGuard;

    /** 注入出站地址防护。 */
    public ChannelHttpClient(ChannelAddressGuard addressGuard) {
        this.addressGuard = addressGuard;
    }

    /**
     * 在渠道类型对应的执行舱中发送一次请求。
     *
     * @param channelType
     *            渠道类型，决定使用哪个执行舱的 HttpClient
     * @param request
     *            最终请求
     * @return 响应
     * @throws ExecutionErrorException
     *             目标地址被拦截（未发出）、超出大小限制、连接或读取失败、超时
     */
    public ChannelResponse send(ChannelType channelType, ChannelRequest request) {
        HttpRequest httpRequest = build(request);
        requireAllowedAddress(request);
        try {
            HttpResponse<InputStream> response = client(channelType, request.getConnectTimeout()).send(httpRequest,
                HttpResponse.BodyHandlers.ofInputStream());
            return new ChannelResponse(response.statusCode(), headers(response), read(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExecutionErrorException("渠道请求被中断");
        } catch (IOException failure) {
            throw new ExecutionErrorException("渠道请求失败：" + failure.getClass().getSimpleName());
        }
    }

    /** 组装请求并检查大小；被禁止的传输层 Header 不发送。 */
    HttpRequest build(ChannelRequest request) {
        byte[] body = request.getBody();
        if (body != null && body.length > MAX_BODY_BYTES) {
            throw new ExecutionErrorException("请求体超过 1 MiB");
        }
        var builder =
            HttpRequest.newBuilder(request.getUri()).timeout(request.getResponseTimeout()).method(request.getMethod(),
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        int size = 0;
        for (var header : request.getHeaders().entrySet()) {
            if (TRANSPORT_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            size += header.getKey().getBytes(StandardCharsets.UTF_8).length
                + header.getValue().getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_HEADER_BYTES) {
                throw new ExecutionErrorException("请求 Header 合计超过 16 KiB");
            }
            try {
                builder.header(header.getKey(), header.getValue());
            } catch (IllegalArgumentException invalid) {
                throw new ExecutionErrorException("请求 Header 名称或值不合法：" + header.getKey());
            }
        }
        return builder.build();
    }

    /** 解析目标主机并校验全部地址；被拦截时不发出请求。无法解析交给 HttpClient 按网络失败处理。 */
    void requireAllowedAddress(ChannelRequest request) {
        if (addressGuard.check(request.getUri().getHost()) == ChannelAddressGuard.Verdict.BLOCKED) {
            throw new ExecutionErrorException("出站地址被拦截：" + request.getUri().getHost());
        }
    }

    /**
     * 按执行舱与连接超时（向上取整到秒，配置上限 10 秒）复用客户端，每个执行舱最多 10 个：不跟随重定向，没有 Cookie 处理器与认证器。
     * 等待响应的超时仍按毫秒精确设置在每个请求上。
     */
    HttpClient client(ChannelType channelType, Duration connectTimeout) {
        long seconds = Math.max(1, (connectTimeout.toMillis() + 999) / 1000);
        return clients.get(channelType).computeIfAbsent(Duration.ofSeconds(seconds),
            timeout -> HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).build());
    }

    /** 同名多值以 “, ” 连接；名称大小写不敏感。 */
    static Map<String, String> headers(HttpResponse<?> response) {
        var result = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        response.headers().map().forEach((name, values) -> result.put(name, String.join(", ", values)));
        return result;
    }

    /** 最多读取 1 MiB，超出即失败。 */
    static byte[] read(InputStream body) throws IOException {
        try (body) {
            byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new ExecutionErrorException("响应体超过 1 MiB");
            }
            return bytes;
        }
    }
}
