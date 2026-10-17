package com.paytech.gateway.transport;

import java.util.Objects;

import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import io.netty.channel.ChannelOption;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpRequestDecoderSpec;

/** 在 Boot Netty defaults 之后锁定 application listener 的 transport envelope。 */
@Component
public final class GatewayNettyServerConfiguration
    implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory>, Ordered {
    static final int MAX_CONNECTIONS = 1_000;
    static final int LISTEN_BACKLOG = 512;
    private static final int BOOT_NETTY_CUSTOMIZER_ORDER = 0;
    private static final String LISTENER_POLICY_ERROR = "Gateway application listener 配置不符合固定传输策略";

    private final ServerProperties serverProperties;

    public GatewayNettyServerConfiguration(ServerProperties serverProperties) {
        this.serverProperties = Objects.requireNonNull(serverProperties, "Server 配置不可为空");
    }

    @Override
    public void customize(NettyReactiveWebServerFactory factory) {
        validateListener();
        factory.addServerCustomizers(server -> {
            HttpRequestDecoderSpec decoder = server.configuration().decoder();
            GatewayHeaderReceiveDeadlineHandler.DecoderSettings decoderSettings =
                new GatewayHeaderReceiveDeadlineHandler.DecoderSettings(decoder.maxInitialLineLength(),
                    decoder.maxHeaderSize(), decoder.maxChunkSize(), decoder.validateHeaders(),
                    decoder.initialBufferSize(), decoder.allowDuplicateContentLengths(), decoder.allowPartialChunks());
            return server.maxConnections(MAX_CONNECTIONS).option(ChannelOption.SO_BACKLOG, LISTEN_BACKLOG)
                .protocol(HttpProtocol.HTTP11).doOnChannelInit((observer, channel,
                    remoteAddress) -> GatewayHeaderReceiveDeadlineHandler.install(channel.pipeline(), decoderSettings));
        });
    }

    @Override
    public int getOrder() {
        return BOOT_NETTY_CUSTOMIZER_ORDER + 100;
    }

    private void validateListener() {
        if (serverProperties.getHttp2().isEnabled()) {
            throw new IllegalStateException(LISTENER_POLICY_ERROR);
        }
    }
}
