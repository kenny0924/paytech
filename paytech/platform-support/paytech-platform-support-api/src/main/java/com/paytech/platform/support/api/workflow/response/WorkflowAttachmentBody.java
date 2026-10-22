package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 实例引用的一个附件：展示与下载所需的元数据，不含存储坐标或下载地址。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAttachmentBody {

    /**
     * 文件 ID，十进制字符串
     * <li>只用于拼下载授权路径（URL 寻址）；业务数据与提交内容使用 {@link #fileAddress}。</li>
     */
    private String fileId;

    /** 文件地址 /files/v1/{fileId}，即启动值、动作输出中保存的值。 */
    private String fileAddress;

    /** 清理后的展示名。 */
    private String displayName;

    /** 服务端识别的媒体类型。 */
    private String mimeType;

    /** 实际字节数，十进制字符串。 */
    private String sizeBytes;

    /**
     * 扫描状态
     * <li>PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED；只有 AVAILABLE 可以下载。</li>
     */
    private String scanStatus;

    /** 上传人账号 ID，十进制字符串；纯系统上传为空。 */
    private String uploaderId;

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "WorkflowAttachmentBody[" + fileId + "]";
    }
}
