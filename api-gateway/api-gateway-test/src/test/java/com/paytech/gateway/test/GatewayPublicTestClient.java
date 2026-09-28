package com.paytech.gateway.test;

import java.util.List;

import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.web.GatewayProtocol;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.netty.http.client.HttpClient;

/** 统一模拟 Nginx 公网入口；管理端口直接使用不带入口 Header 的客户端。 */
public final class GatewayPublicTestClient {
    private GatewayPublicTestClient() {}

    public static WebTestClient.Builder operations(int port) {
        return builder(port, List.of(GatewayRouteChannel.OPERATIONS.getCode()));
    }

    public static WebTestClient.Builder clientApi(int port) {
        return builder(port, List.of(GatewayRouteChannel.CLIENT_API.getCode()));
    }

    public static WebTestClient.Builder builder(int port, List<String> channels) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port)
            .defaultHeaders(headers -> headers.put(GatewayProtocol.ENTRY_CHANNEL, channels));
    }

    public static HttpClient operationsHttpClient(int port) {
        return HttpClient.create().host("127.0.0.1").port(port)
            .headers(headers -> headers.set(GatewayProtocol.ENTRY_CHANNEL, GatewayRouteChannel.OPERATIONS.getCode()));
    }

    /** 仅生成一行，不结束 Header，允许 raw/partial 请求保留原有字节分段。 */
    public static String operationsChannelLine() {
        return GatewayProtocol.ENTRY_CHANNEL + ": " + GatewayRouteChannel.OPERATIONS.getCode() + "\r\n";
    }
}
