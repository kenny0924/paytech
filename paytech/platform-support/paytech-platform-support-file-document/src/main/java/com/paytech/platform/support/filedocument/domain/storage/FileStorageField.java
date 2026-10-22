package com.paytech.platform.support.filedocument.domain.storage;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 存储管理审计记录的字段；Code 与 Operations 配置审计字段同名，由组合根按 Code 转换
 * <li>密钥只记 SET / REPLACED / CLEARED，证书只记 SHA-256 摘要，不记录 Token 或原值。</li>
 */
public enum FileStorageField implements CodeEnum {
    /** 实例代码或路由来源系统。 */
    CODE,

    /** 存储类型。 */
    STORAGE_TYPE,

    /** 服务地址。 */
    ENDPOINT,

    /** 签名区域。 */
    REGION,

    /** 路径风格访问。 */
    PATH_STYLE_ACCESS,

    /** 信任证书 PEM 的 SHA-256 摘要。 */
    TRUSTED_CERTIFICATE_DIGEST,

    /** 凭据方式。 */
    CREDENTIAL_MODE,

    /** 密钥变化：SET、REPLACED、CLEARED。 */
    CREDENTIAL,

    /** 连接超时毫秒数。 */
    CONNECT_TIMEOUT_MILLIS,

    /** 读写超时毫秒数。 */
    READ_TIMEOUT_MILLIS,

    /** 状态。 */
    STATUS,

    /** 存储桶名称。 */
    BUCKET_NAME,

    /** 存储桶检查结果。 */
    CHECK_RESULT,

    /** 路由目标实例代码。 */
    STORAGE_INSTANCE,

    /** 路由目标存储桶名称。 */
    STORAGE_BUCKET,

    /** 路由扫描策略代码。 */
    SCAN_POLICY,

    /** 路由上传期限秒数。 */
    MAX_UPLOAD_SECONDS;

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return name();
    }
}
