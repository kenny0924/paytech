package com.paytech.platform.support.test.builder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;

/**
 * 上传文件的场景参数：同一规格既可生成存储端口使用的 Upload/File 事实，也可由本地 Mocker 插入数据库。
 *
 * <p>默认是扫描可用、对象有效的 PDF；只描述数据，不访问数据库或存储。
 */
public final class FileBuilder {

    /** PDF 文件头开头的合成内容。 */
    public static final byte[] PDF = "%PDF-1.7\nunit-test material\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** PNG 文件头开头的合成内容。 */
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    /** JPEG 文件头开头的合成内容。 */
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    /** 纯 ASCII 文本内容，按 UTF-8 文本识别为 text/csv。 */
    public static final byte[] TEXT = "plain text material".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** 测试上传的原因代码，与 FileTestMapper 插入的 reason_code 一致。 */
    public static final String REASON_CODE = "CLIENT_ONBOARDING_MATERIAL";
    /** 卡 BIN 库标准表头与一行数据的 CSV，含 BOM 与多字节发卡行名称。 */
    public static final byte[] CSV = ("\uFEFFbin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,"
        + "issuer_name\r\n41111111,VISA,CREDIT,CLASSIC,CONSUMER,AE,\"Banque Misr — بنك مصر\"\r\n")
        .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /** 既无已知文件头也不是 UTF-8 文本的二进制内容（含 NUL 与非法 UTF-8 字节）。 */
    public static final byte[] BINARY = {'b', 'i', 'n', 0, (byte) 0xC3, (byte) 0x28, (byte) 0xFE, 0x01};

    /**
     * 开头 8 KiB 是合法 CSV、其后出现 NUL 的内容：完成上传时按开头识别为 text/csv，全文格式检查不通过。
     *
     * @return 新数组
     */
    public static byte[] csvWithLateNul() {
        byte[] bytes = new byte[8192 + 64];
        java.util.Arrays.fill(bytes, (byte) 'A');
        System.arraycopy(CSV, 0, bytes, 0, CSV.length);
        bytes[8192 + 10] = 0;
        return bytes;
    }

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
    private long tenantId = OperationsCatalog.INTERNAL_TENANT_ID;
    private long uploaderId = OperatorBuilder.nextId();
    private FileAccessLevel accessLevel = FileAccessLevel.PRIVATE;

    private FileBuilder(String provider, String container, String objectRef) {
        this.provider = provider;
        this.container = container;
        this.objectRef = objectRef;
    }

    /** 指定原存储坐标的默认 PDF 材料。 */
    public static FileBuilder template(String provider, String container, String objectRef) {
        return new FileBuilder(provider, container, objectRef);
    }

    /** 登记的媒体类型，可与实际字节不一致以构造负例。 */
    public FileBuilder mime(String mime) {
        this.mime = mime;
        return this;
    }

    /** 实际字节；摘要与大小默认由字节推导。 */
    public FileBuilder bytes(byte[] bytes) {
        this.bytes = bytes.clone();
        return this;
    }

    /** 显式覆盖登记摘要，用于字节与登记不符的负例。 */
    public FileBuilder contentHash(String contentHash) {
        this.contentHash = contentHash;
        return this;
    }

    /** 已固定的存储版本。 */
    public FileBuilder version(String version) {
        this.version = version;
        return this;
    }

    public FileBuilder encryption(String encryption) {
        this.encryption = encryption;
        return this;
    }

    public FileBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /** 冻结的扫描策略修订。 */
    public FileBuilder policyId(long policyId) {
        this.policyId = policyId;
        return this;
    }

    public FileBuilder scanStatus(FileScanStatus scanStatus) {
        this.scanStatus = scanStatus;
        return this;
    }

    public FileBuilder objectStatus(FileObjectStatus objectStatus) {
        this.objectStatus = objectStatus;
        return this;
    }

    /** 访问级别；默认 PRIVATE。 */
    public FileBuilder accessLevel(FileAccessLevel accessLevel) {
        this.accessLevel = accessLevel;
        return this;
    }

    /** 上传授权期限。 */
    public FileBuilder expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    /** 构建不可变规格。 */
    public Spec build() {
        return new Spec(fileId, uploadId, tenantId, uploaderId, displayName, provider, container, objectRef, mime,
            bytes.clone(), contentHash == null ? sha256(bytes) : contentHash, version, encryption, policyId, scanStatus,
            objectStatus, expiresAt, accessLevel);
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
     * 上传文件的不可变规格。
     *
     * @param bytes 实际字节
     * @param contentHash 登记摘要
     */
    public record Spec(long fileId, long uploadId, long tenantId, long uploaderId,
        String displayName, String provider, String container, String objectRef, String mime, byte[] bytes,
        String contentHash, String version, String encryption, long policyId, FileScanStatus scanStatus,
        FileObjectStatus objectStatus, Instant expiresAt, FileAccessLevel accessLevel) {

        /** 存储端口使用的上传目标。 */
        public Upload upload() {
            return new Upload(uploadId, tenantId, uploaderId, FileSourceCodes.PLATFORM_SUPPORT,
                REASON_CODE, displayName, bytes.length, mime, provider,
                container, objectRef, policyId, expiresAt, FileUploadStatus.CREATED, accessLevel, null, 0, null);
        }

        /**
         * 替换本文件内容的上传目标：同一对象，期望类型与大小取自替换字节
         *
         * @param replacementNo 替换序号
         * @param baseObjectVersion 开始替换时的当前版本
         * @param replacementBytes 替换内容
         * @param replacementMime 替换声明类型
         */
        public Upload replacementUpload(int replacementNo, String baseObjectVersion, byte[] replacementBytes,
            String replacementMime) {
            return new Upload(uploadId + replacementNo, tenantId, uploaderId, FileSourceCodes.PLATFORM_SUPPORT,
                REASON_CODE, displayName, replacementBytes.length, replacementMime, provider, container, objectRef,
                policyId, expiresAt, FileUploadStatus.CREATED, accessLevel, fileId, replacementNo, baseObjectVersion);
        }

        /** 存储与扫描端口使用的已登记文件。 */
        public File file() {
            return new File(fileId, provider, container, objectRef,
                new Verified(version, contentHash, bytes.length, mime, encryption), policyId, scanStatus, objectStatus, 1);
        }

        /** 同一材料改为存储实际生成的版本。 */
        public Spec withVersion(String objectVersion) {
            return new Spec(fileId, uploadId, tenantId, uploaderId, displayName, provider, container,
                objectRef, mime, bytes, contentHash, objectVersion, encryption, policyId, scanStatus, objectStatus,
                expiresAt, accessLevel);
        }

        /** 实际字节数。 */
        public long size() {
            return bytes.length;
        }
    }
}
