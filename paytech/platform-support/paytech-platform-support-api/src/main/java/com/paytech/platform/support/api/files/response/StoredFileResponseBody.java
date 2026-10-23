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
     * <li>当前版本上传时清除路径与控制字符后的名称；非上传来源为空。</li>
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

    /** 首次上传的上传人账号 ID，十进制字符串；纯系统上传为空。 */
    private String uploaderAccountId;

    /** 文件登记时间（UTC）。 */
    private Instant createTime;

    /**
     * 访问级别
     * <li>PRIVATE 或 PUBLIC；上传时声明，之后不变。</li>
     */
    private String accessLevel;

    /**
     * 当前对外提供的版本号
     * <li>从 1 开始；替换内容检查通过后递增。元数据（类型、大小、摘要、扫描状态）均为该版本的值。</li>
     * <li>Platform 总是返回；声明为包装类型，便于先升级的消费方兼容尚未升级的 Platform。</li>
     */
    private Integer versionNo;

    /**
     * 长期公开地址
     * <li>{@code 公开桶基础地址/对象名}；只有 PUBLIC 且当前版本 AVAILABLE 时返回，否则为空。替换后地址不变。</li>
     */
    private String publicUrl;

    /**
     * 带版本参数的公开地址
     * <li>{@code publicUrl?v=versionNo}，用于需要绕开 CDN 缓存的场景；与 publicUrl 同时为空或非空。</li>
     */
    private String versionedUrl;

    /**
     * 检查中的替换版本号
     * <li>有已完成上传、尚在检查的替换版本时返回；没有时为空。</li>
     */
    private Integer pendingVersionNo;

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "StoredFileResponseBody[" + fileId + "]";
    }
}
