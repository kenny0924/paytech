package com.paytech.operations.console.bff.service.model.tax.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 税票下载授权：单号与短时下载地址；临时地址不进入日志。 */
@Getter
@AllArgsConstructor
public class TaxDocumentDownloadResponseBody {

    /** 单据 ID */
    private final String documentId;

    /** 单号 */
    private final String documentNumber;

    /**
     * 下载地址
     * <li>HTTPS，指向税票文件当前版本，以附件方式返回。</li>
     */
    private final String url;

    /** 下载地址失效时间（UTC），最长 60 秒 */
    private final Instant expiresAt;

    /** 临时签名地址不进入日志。 */
    @Override
    public String toString() {
        return "TaxDocumentDownloadResponseBody[" + documentId + "]";
    }
}
