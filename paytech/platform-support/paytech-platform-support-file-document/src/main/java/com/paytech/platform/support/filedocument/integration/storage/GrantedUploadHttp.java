package com.paytech.platform.support.filedocument.integration.storage;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 按签发的 PUT 授权写入对象：供 Platform 进程内模块（如多语言语言包）在没有浏览器参与时完成上传
 * <li>与存储 SDK 属于同一存储边界（O5 对象存储出口例外），使用本模块已登记的 OkHttp，不经 channel-connector；超时取实例设置，
 * 不跟随重定向、不自动重试，不记录地址与正文。</li>
 * <li>请求头只取授权原样携带的值（如 Content-Type、If-None-Match），正文不附加媒体类型，避免改写已签名的 Content-Type。</li>
 * <li>实例登记了私有证书时只信任该证书（与 SDK 一致），否则使用 JDK 默认信任库。</li>
 */
public final class GrantedUploadHttp implements AutoCloseable {

    private final OkHttpClient client;

    /** 按实例的超时与信任设置构建。 */
    public GrantedUploadHttp(FileStorageInstance instance) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(instance.getConnectTimeoutMs()))
            .readTimeout(Duration.ofMillis(instance.getReadTimeoutMs()))
            .writeTimeout(Duration.ofMillis(instance.getReadTimeoutMs()))
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false);
        if (instance.getTrustedCertificatePem() != null) {
            StorageTrust trust = StorageTrust.fromPemText(instance.getTrustedCertificatePem());
            builder.sslSocketFactory(trust.context().getSocketFactory(), trust.trustManager());
        }
        this.client = builder.build();
    }

    /**
     * 按授权写入全部字节
     *
     * @param grant 本次上传的 PUT 授权
     * @param content 对象字节
     * @return HTTP 状态码
     * @throws IOException 连接、超时或写入失败
     */
    public int put(Grant grant, byte[] content) throws IOException {
        Request.Builder request = new Request.Builder().url(URI.create(grant.url()).toURL())
            .put(RequestBody.create(content, null));
        for (Map.Entry<String, String> header : grant.headers().entrySet()) {
            request.header(header.getKey(), header.getValue());
        }
        try (okhttp3.Response response = client.newCall(request.build()).execute()) {
            return response.code();
        }
    }

    /** 释放调度线程与连接池。 */
    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
