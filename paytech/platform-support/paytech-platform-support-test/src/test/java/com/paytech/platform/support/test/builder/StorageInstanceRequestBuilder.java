package com.paytech.platform.support.test.builder;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;

/**
 * 新建存储实例请求：默认是指向本机 MinIO 的 MINIO STATIC 实例
 * <li>只组装请求，不访问外部资源；负例可显式设置空值或非法值，build 不修复。</li>
 */
public final class StorageInstanceRequestBuilder {

    /** 默认操作原因。 */
    public static final String REASON = "登记文件存储实例";

    private final CreateStorageInstanceRequestBody body = new CreateStorageInstanceRequestBody();

    private StorageInstanceRequestBuilder() {
    }

    /**
     * 合法默认请求
     *
     * @param code 实例代码
     * @param endpoint 本机 MinIO 地址
     * @param certificatePem 本机 MinIO 证书
     */
    public static StorageInstanceRequestBuilder template(String code, String endpoint, String certificatePem) {
        var builder = new StorageInstanceRequestBuilder();
        builder.body.setInstanceCode(code);
        builder.body.setStorageType(FileStorageType.MINIO.getCode());
        builder.body.setEndpoint(endpoint);
        builder.body.setRegion("us-east-1");
        builder.body.setPathStyleAccess(true);
        builder.body.setTrustedCertificatePem(certificatePem);
        builder.body.setCredentialMode(FileStorageCredentialMode.STATIC.getCode());
        builder.body.setAccessKey("UT-ACCESS-" + code);
        builder.body.setSecretKey("ut-secret-" + code.toLowerCase(java.util.Locale.ROOT) + "-value");
        builder.body.setReason(REASON);
        return builder;
    }

    /** 存储类型 Code。 */
    public StorageInstanceRequestBuilder type(String type) {
        body.setStorageType(type);
        return this;
    }

    /** 服务地址。 */
    public StorageInstanceRequestBuilder endpoint(String endpoint) {
        body.setEndpoint(endpoint);
        return this;
    }

    /** 证书 PEM。 */
    public StorageInstanceRequestBuilder certificate(String pem) {
        body.setTrustedCertificatePem(pem);
        return this;
    }

    /** 凭据方式与密钥。 */
    public StorageInstanceRequestBuilder credential(String mode, String accessKey, String secretKey) {
        body.setCredentialMode(mode);
        body.setAccessKey(accessKey);
        body.setSecretKey(secretKey);
        return this;
    }

    /** 实例代码。 */
    public StorageInstanceRequestBuilder code(String code) {
        body.setInstanceCode(code);
        return this;
    }

    /** 构建请求。 */
    public CreateStorageInstanceRequestBody build() {
        return body;
    }
}
