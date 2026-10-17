package com.paytech.platform.support.delivery.integration.webhook;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.platform.support.delivery.domain.WebhookAttemptOutcome;
import com.paytech.platform.support.delivery.domain.WebhookSignature;

/**
 * 向 Client 端点发送一次签名事件
 * <li>DNS 由 {@link WebhookAddressGuard} 解析并校验，连接只用校验过的地址；TLS 仍按主机名校验证书</li>
 * <li>不跟随重定向、不自动重试；响应体最多读取 1 KiB 后丢弃，不记录</li>
 * <li>不抛出网络异常：所有失败都转换为失败代码，由调用方记录并按重试计划处理</li>
 */
@Component
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookHttpSender implements DisposableBean {

    private static final int MAX_RESPONSE_BYTES = 1024;
    private static final String USER_AGENT = "Code-MoR-Webhook/1";

    private final CloseableHttpClient client;

    /** 启动时构造连接池与超时；配置变更需重启。 */
    public WebhookHttpSender(WebhookProperties properties, WebhookAddressGuard guard) {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(new GuardedDnsResolver(guard))
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(properties.getConnectTimeout()))
                .setSocketTimeout(Timeout.of(properties.getResponseTimeout())).build())
            .build();
        this.client = HttpClients.custom().setConnectionManager(connections)
            .setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false)
                .setResponseTimeout(Timeout.of(properties.getResponseTimeout())).build())
            .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().build();
    }

    /**
     * 发送规范 Body 与签名 Header
     *
     * @param url 端点地址
     * @param body Envelope 规范字节
     * @param signature Code-MoR-Signature Header 值
     * @return 发送结果，不抛网络异常
     */
    public WebhookAttemptOutcome send(URI url, byte[] body, String signature) {
        var post = new HttpPost(url);
        post.setHeader("User-Agent", USER_AGENT);
        post.setHeader(WebhookSignature.HEADER, signature);
        post.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
        long started = System.nanoTime();
        try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
            int status = response.getCode();
            discardBody(response);
            return WebhookAttemptOutcome.response(status, elapsedMillis(started));
        } catch (IOException failure) {
            return WebhookAttemptOutcome.failure(classify(failure), elapsedMillis(started));
        }
    }

    /** 最多读取 1 KiB 后关闭；未读完的连接随响应关闭而丢弃，不复用。 */
    static void discardBody(ClassicHttpResponse response) throws IOException {
        if (response.getEntity() == null) {
            return;
        }
        try (InputStream content = response.getEntity().getContent()) {
            content.readNBytes(MAX_RESPONSE_BYTES);
        }
    }

    /** 网络异常转换为失败代码；不保留异常原文。 */
    static WebhookFailureCode classify(IOException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebhookAddressException address) {
                return address.getFailureCode();
            }
        }
        if (failure instanceof UnknownHostException) {
            return WebhookFailureCode.DNS_FAILED;
        }
        if (failure instanceof SSLException) {
            return WebhookFailureCode.TLS_FAILED;
        }
        if (failure instanceof ConnectTimeoutException || failure instanceof InterruptedIOException) {
            return WebhookFailureCode.TIMEOUT;
        }
        return WebhookFailureCode.CONNECT_FAILED;
    }

    /** 自开始以来的毫秒数。 */
    static int elapsedMillis(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    /** 关闭连接池。 */
    @Override
    public void destroy() throws IOException {
        client.close();
    }

    /** 每次建立连接都重新解析并校验地址。 */
    record GuardedDnsResolver(WebhookAddressGuard guard) implements DnsResolver {

        /** 返回已校验的地址；被拒绝时抛 WebhookAddressException。 */
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            return guard.resolve(host);
        }

        /** 不使用规范主机名。 */
        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }
}
