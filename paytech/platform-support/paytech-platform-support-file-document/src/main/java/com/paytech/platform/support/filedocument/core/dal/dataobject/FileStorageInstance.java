package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;

import lombok.Getter;
import lombok.Setter;

/** t_file_storage_instance 映射对象；只在 file-document 模块内使用，快照中的对象只读。 */
@Getter
@Setter
public class FileStorageInstance {

    /** 存储实例 Snowflake 主键。 */
    private Long instanceId;

    /**
     * 实例代码
     * <li>即文件与上传记录上的 storage_provider；创建后不可修改。</li>
     */
    private String instanceCode;

    /** 存储类型，决定使用的 SDK。 */
    private FileStorageType storageType;

    /**
     * HTTPS 根地址
     * <li>S3 可空（使用区域默认地址）；MINIO 必填。</li>
     */
    private String endpoint;

    /** 签名区域。 */
    private String region;

    /** 是否使用路径风格访问。 */
    private Boolean pathStyleAccess;

    /**
     * 私有 CA 或自签名证书 PEM
     * <li>可空；非空时只信任该证书。</li>
     */
    private String trustedCertificatePem;

    /** 凭据方式。 */
    private FileStorageCredentialMode credentialMode;

    /**
     * 静态密钥的 Vault Token
     * <li>STATIC 必填，IAM_ROLE 为空；不进入日志、审计与响应。</li>
     */
    private String credentialVaultToken;

    /** 最近一次录入或更换密钥的时间；可空。 */
    private Instant credentialUpdateTime;

    /** 连接超时毫秒数。 */
    private Integer connectTimeoutMs;

    /** 读写超时毫秒数。 */
    private Integer readTimeoutMs;

    /** 实例状态。 */
    private FileStorageInstanceStatus status;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;

    /** 不输出 Vault Token 与证书。 */
    @Override
    public String toString() {
        return "FileStorageInstance[" + instanceCode + "]";
    }
}
