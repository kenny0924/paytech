package com.paytech.platform.support.filedocument.integration.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** 存储连接的私有 CA 信任材料；实例未登记证书时使用 JDK 默认信任库。 */
public final class StorageTrust {
    private final X509TrustManager trustManager;
    private final SSLContext context;

    private StorageTrust(X509TrustManager trustManager, SSLContext context) {
        this.trustManager = trustManager;
        this.context = context;
    }

    /**
     * 只信任实例登记的 PEM 证书文本，不叠加默认信任库。
     *
     * @param pem PEM 文本（公开内容）
     * @return 信任材料
     * @throws IllegalArgumentException 文本不含合法 X.509 证书
     */
    public static StorageTrust fromPemText(String pem) {
        return fromPem(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    /** 从 PEM 流建立只含这些证书的信任管理器与 TLS 上下文。 */
    private static StorageTrust fromPem(InputStream input) {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (Certificate value : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                store.setCertificateEntry("storage-" + index++, value);
            }
            if (index == 0) {
                throw new IllegalArgumentException("存储信任证书不含 X.509 证书");
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            X509TrustManager manager = Arrays.stream(factory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无法建立存储信任管理器"));
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {manager}, null);
            return new StorageTrust(manager, context);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("无法读取存储信任证书", exception);
        }
    }

    /** 供 OkHttp 等客户端使用的信任管理器。 */
    public X509TrustManager trustManager() {
        return trustManager;
    }

    /** 已初始化的 TLS 上下文。 */
    public SSLContext context() {
        return context;
    }
}
