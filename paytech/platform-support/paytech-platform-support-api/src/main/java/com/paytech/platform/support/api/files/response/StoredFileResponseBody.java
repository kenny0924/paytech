package com.paytech.platform.support.api.files.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件元数据；不含存储实例、桶、对象坐标或下载地址。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileResponseBody {

    /** 文件 ID，十进制字符串。 */
    private String fileId;

    /**
     * 文件地址 {@code /files/v1/{fileId}}
     * <li>业务数据保存此地址；格式与解析见 {@code FileAddress}。</li>
     */
    private String fileAddress;

    /** 来源系统，即开始上传时的内部调用服务名；Platform 自行发起为 platform-support。 */
    private String sourceSystem;

    /** 来源系统声明的上传原因代码。 */
    private String reasonCode;

    /**
     * 展示文件名
     * <li>清除路径与控制字符后的名称；非上传来源为空。</li>
     */
    private String displayName;

    /** 服务端按文件头识别的媒体类型。 */
    private String mimeType;

    /** 实际字节数，十进制字符串。 */
    private String sizeBytes;

    /** 实际字节的 SHA-256 小写十六进制。 */
    private String contentHash;

    /**
     * 扫描状态
     * <li>{@code FileScanStatus} Code：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED；只有 AVAILABLE 可下载。</li>
     */
    private String scanStatus;

    /**
     * 对象状态
     * <li>{@code FileObjectStatus} Code：ACTIVE、UNAVAILABLE、RETIRED。</li>
     */
    private String objectStatus;

    /** 上传人账号 ID，十进制字符串；纯系统上传为空。 */
    private String uploaderAccountId;

    /** 文件登记时间（UTC）。 */
    private Instant createTime;

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "StoredFileResponseBody[" + fileId + "]";
    }
}
