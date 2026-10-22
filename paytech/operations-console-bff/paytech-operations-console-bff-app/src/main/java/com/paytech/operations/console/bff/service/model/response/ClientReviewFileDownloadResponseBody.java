package com.paytech.operations.console.bff.service.model.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O10 审核材料下载：文件展示信息与短时下载地址；临时地址不进入日志。 */
@Getter
@AllArgsConstructor
public class ClientReviewFileDownloadResponseBody {

    /** 文件标识，十进制字符串 */
    private final String fileId;

    /** 客户上传时的展示文件名 */
    private final String displayName;

    /** 服务端识别的媒体类型 */
    private final String mimeType;

    /** 实际字节数，十进制字符串 */
    private final String sizeBytes;

    /**
     * 下载地址
     * <li>HTTPS，指向原不可变版本，以附件方式返回。</li>
     */
    private final String url;

    /** 下载地址失效时间（UTC），最长 60 秒 */
    private final Instant expiresAt;

    /** 临时签名地址不进入日志。 */
    @Override
    public String toString() {
        return "ClientReviewFileDownloadResponseBody[" + fileId + "]";
    }
}
