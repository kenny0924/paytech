package com.paytech.card.domain.binlibrary;

import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** BIN 库导入文件在 Platform 文件服务的元数据（只含 Card 判断所需字段，不含存储坐标）。 */
@Getter
@AllArgsConstructor
public class BinLibrarySourceFile {
    /** 上传方 operations-console-bff 对应的来源系统。 */
    public static final String SOURCE_SYSTEM = "operations-console-bff";

    /** BIN 库导入的上传原因代码。 */
    public static final String REASON_CODE = "CARD_BIN_LIBRARY_IMPORT";

    /** 服务端识别的 CSV 媒体类型。 */
    public static final String CSV_MIME_TYPE = "text/csv";

    /** 可用的对象状态。 */
    static final String OBJECT_ACTIVE = "ACTIVE";

    /** 扫描通过、可以下载的扫描状态。 */
    static final String SCAN_AVAILABLE = "AVAILABLE";

    /** 已隔离的扫描状态。 */
    static final String SCAN_QUARANTINED = "QUARANTINED";

    /** 已拒绝的扫描状态。 */
    static final String SCAN_REJECTED = "REJECTED";

    /** Platform 签发的文件地址 /files/v1/{fileId}。 */
    private final String fileAddress;

    /** 来源系统。 */
    private final String sourceSystem;

    /** 上传原因代码。 */
    private final String reasonCode;

    /** 展示文件名；可能为 null。 */
    private final String displayName;

    /** 服务端识别的媒体类型。 */
    private final String mimeType;

    /** 实际字节的 SHA-256 小写十六进制。 */
    private final String contentHash;

    /** 扫描状态：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。 */
    private final String scanStatus;

    /** 对象状态：ACTIVE、UNAVAILABLE、RETIRED。 */
    private final String objectStatus;

    /**
     * Platform 登记的文件地址与请求地址一致（按 {@link FileAddress} 比较；Platform 返回的地址缺失或格式不符视为不一致）
     *
     * @param requested 请求中已解析的文件地址
     * @return 是否一致
     */
    public boolean isAddressedBy(FileAddress requested) {
        try {
            return fileAddress != null && requested.equals(FileAddress.parse(fileAddress));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** 来源、上传原因与类型都是 BIN 库导入文件。 */
    public boolean isBinLibraryCsv() {
        return SOURCE_SYSTEM.equals(sourceSystem) && REASON_CODE.equals(reasonCode) && CSV_MIME_TYPE.equals(mimeType);
    }

    /** 扫描已判定不可用（隔离或拒绝），或对象已不可用。 */
    public boolean isRejected() {
        return SCAN_QUARANTINED.equals(scanStatus) || SCAN_REJECTED.equals(scanStatus) || !OBJECT_ACTIVE.equals(objectStatus);
    }

    /** 扫描通过且对象可用，可以下载。 */
    public boolean isAvailable() {
        return SCAN_AVAILABLE.equals(scanStatus) && OBJECT_ACTIVE.equals(objectStatus);
    }
}
