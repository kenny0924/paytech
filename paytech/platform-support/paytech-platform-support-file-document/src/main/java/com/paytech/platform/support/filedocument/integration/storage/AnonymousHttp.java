package com.paytech.platform.support.filedocument.integration.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/**
 * 不带凭据的 HTTP GET：存储桶检查用它确认私有桶拒绝匿名读取、公开桶可经对外基础地址读取
 * <li>与存储 SDK 属于同一存储边界（O5 对象存储出口例外），使用本模块已登记的 OkHttp，不经 channel-connector；超时取实例设置，
 * 不跟随重定向、不自动重试，不记录响应正文。</li>
 * <li>实例登记了私有证书时只信任该证书（与 SDK 一致），否则使用 JDK 默认信任库。</li>
 */
public final class AnonymousHttp {

    /** 读取响应正文的上限；探测对象远小于此值。 */
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final OkHttpClient client;

    /** 按实例的超时与信任设置构建。 */
    public AnonymousHttp(FileStorageInstance instance) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(instance.getConnectTimeoutMs()))
            .readTimeout(Duration.ofMillis(instance.getReadTimeoutMs()))
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false);
        if (instance.getTrustedCertificatePem() != null) {
            StorageTrust trust = StorageTrust.fromPemText(instance.getTrustedCertificatePem());
            builder.sslSocketFactory(trust.context().getSocketFactory(), trust.trustManager());
        }
        this.client = builder.build();
    }

    /**
     * 不带凭据读取一个地址
     *
     * @param uri HTTPS 地址
     * @return 状态码与有界正文
     * @throws IOException 连接、超时或读取失败
     */
    public Response get(URI uri) throws IOException {
        Request request = new Request.Builder().url(uri.toURL()).get().build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body == null) {
                return new Response(response.code(), new byte[0]);
            }
            try (InputStream bytes = body.byteStream()) {
                return new Response(response.code(), bytes.readNBytes(MAX_BODY_BYTES));
            }
        }
    }

    /** 释放调度线程与连接池。 */
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    /**
     * 匿名读取结果
     *
     * @param status HTTP 状态码
     * @param body 有界正文
     */
    public record Response(int status, byte[] body) {
        /** 不输出正文。 */
        @Override
        public String toString() {
            return "Response[" + status + "]";
        }
    }
}
