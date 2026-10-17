package com.paytech.platform.support.test.builder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;

/**
 * 入驻材料的场景参数：同一规格既可生成存储端口使用的 Upload/File 事实，也可由本地 Mocker 插入数据库。
 *
 * <p>默认是扫描可用、对象有效的 PDF；只描述数据，不访问数据库或存储。
 */
public final class OnboardingFileBuilder {

    /** PDF 文件头开头的合成内容。 */
    public static final byte[] PDF = "%PDF-1.7\nunit-test material\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** PNG 文件头开头的合成内容。 */
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    /** JPEG 文件头开头的合成内容。 */
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    /** 无法识别的纯文本内容。 */
    public static final byte[] TEXT = "plain text material".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** 存储加密引用的默认合成值，与本机 SSE-S3 存储桶的算法一致。 */
    public static final String SSE_S3 = "AES256";

    private final long fileId = OperatorBuilder.nextId();
    private final long uploadId = OperatorBuilder.nextId();
    private final String provider;
    private final String container;
    private final String objectRef;
    private String mime = "application/pdf";
    private byte[] bytes = PDF;
    private String contentHash;
    private String version = "unregistered-version";
    private String encryption = SSE_S3;
    private String displayName = "certificate-of-incorporation.pdf";
    private long policyId;
    private FileScanStatus scanStatus = FileScanStatus.AVAILABLE;
    private FileObjectStatus objectStatus = FileObjectStatus.ACTIVE;
    private Instant expiresAt;

    private OnboardingFileBuilder(String provider, String container, String objectRef) {
        this.provider = provider;
        this.container = container;
        this.objectRef = objectRef;
    }

    /** 指定原存储坐标的默认 PDF 材料。 */
    public static OnboardingFileBuilder template(String provider, String container, String objectRef) {
        return new OnboardingFileBuilder(provider, container, objectRef);
    }

    /** 登记的媒体类型，可与实际字节不一致以构造负例。 */
    public OnboardingFileBuilder mime(String mime) {
        this.mime = mime;
        return this;
    }

    /** 实际字节；摘要与大小默认由字节推导。 */
    public OnboardingFileBuilder bytes(byte[] bytes) {
        this.bytes = bytes.clone();
        return this;
    }

    /** 显式覆盖登记摘要，用于字节与登记不符的负例。 */
    public OnboardingFileBuilder contentHash(String contentHash) {
        this.contentHash = contentHash;
        return this;
    }

    /** 已固定的存储版本。 */
    public OnboardingFileBuilder version(String version) {
        this.version = version;
        return this;
    }

    public OnboardingFileBuilder encryption(String encryption) {
        this.encryption = encryption;
        return this;
    }

    public OnboardingFileBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /** 冻结的扫描策略修订。 */
    public OnboardingFileBuilder policyId(long policyId) {
        this.policyId = policyId;
        return this;
    }

    public OnboardingFileBuilder scanStatus(FileScanStatus scanStatus) {
        this.scanStatus = scanStatus;
        return this;
    }

    public OnboardingFileBuilder objectStatus(FileObjectStatus objectStatus) {
        this.objectStatus = objectStatus;
        return this;
    }

    /** 上传授权期限。 */
    public OnboardingFileBuilder expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    /** 构建不可变规格。 */
    public Spec build() {
        return new Spec(fileId, uploadId, OperationsCatalog.INTERNAL_TENANT_ID, OperatorBuilder.nextId(),
            OperatorBuilder.nextId() + ":" + OperatorBuilder.nextId(), displayName, provider, container, objectRef, mime,
            bytes.clone(), contentHash == null ? sha256(bytes) : contentHash, version, encryption, policyId, scanStatus,
            objectStatus, expiresAt);
    }

    /** 小写十六进制 SHA-256，与生产登记格式一致。 */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * 入驻材料的不可变规格。
     *
     * @param ownerRefId CCT 的 applicationId:subjectPartyId
     * @param bytes 实际字节
     * @param contentHash 登记摘要
     */
    public record Spec(long fileId, long uploadId, long tenantId, long uploaderId, String ownerRefId,
        String displayName, String provider, String container, String objectRef, String mime, byte[] bytes,
        String contentHash, String version, String encryption, long policyId, FileScanStatus scanStatus,
        FileObjectStatus objectStatus, Instant expiresAt) {

        /** 存储端口使用的上传目标。 */
        public Upload upload() {
            return new Upload(uploadId, tenantId, uploaderId, ownerRefId, displayName, bytes.length, mime, provider,
                container, objectRef, policyId, expiresAt, FileUploadStatus.CREATED);
        }

        /** 存储与扫描端口使用的已登记文件。 */
        public File file() {
            return new File(fileId, provider, container, objectRef,
                new Verified(version, contentHash, bytes.length, mime, encryption), policyId, scanStatus, objectStatus);
        }

        /** 同一材料改为存储实际生成的版本。 */
        public Spec withVersion(String objectVersion) {
            return new Spec(fileId, uploadId, tenantId, uploaderId, ownerRefId, displayName, provider, container,
                objectRef, mime, bytes, contentHash, objectVersion, encryption, policyId, scanStatus, objectStatus,
                expiresAt);
        }

        /** 实际字节数。 */
        public long size() {
            return bytes.length;
        }
    }
}
