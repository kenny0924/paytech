package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;

/** 税票文件的下载授权端口：经 Platform 内部文件接口签发短时地址；单次调用，不自动重试。 */
public interface TaxDocumentFileClient {

    /**
     * 以运营操作人签发税票文件当前版本的短时下载地址；Platform 据操作人记审计
     *
     * @param operator 当前运营身份
     * @param fileId 从 tax 单据文件地址解析出的十进制文件 ID
     * @return 带地址与到期时间（最长 60 秒）的下载授权
     */
    FileDownloadResponseBody authorizeDownload(OperatorContext operator, String fileId);
}
