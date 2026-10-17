package com.paytech.platform.support.filedocument.integration.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** 存储连接的私有 CA 信任材料；未配置时使用 JDK 默认信任库。 */
public final class StorageTrust {
    private final X509TrustManager trustManager;
    private final SSLContext context;

    private StorageTrust(X509TrustManager trustManager, SSLContext context) {
        this.trustManager = trustManager;
        this.context = context;
    }

    /**
     * 只信任指定 PEM 证书（私有 CA 或本机自签名），不叠加默认信任库。
     *
     * @param certificate PEM 文件路径
     * @return 信任材料
     */
    public static StorageTrust fromPem(Path certificate) {
        try (InputStream input = Files.newInputStream(certificate)) {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (Certificate value : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                store.setCertificateEntry("storage-" + index++, value);
            }
            if (index == 0) {
                throw new IllegalArgumentException("存储信任证书文件不含 X.509 证书");
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
