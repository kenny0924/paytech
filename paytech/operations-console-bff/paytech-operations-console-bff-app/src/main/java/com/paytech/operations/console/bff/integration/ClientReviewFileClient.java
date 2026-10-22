package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

/** Platform 内部文件接口上的审核材料读取；单次调用，不自动重试。 */
public interface ClientReviewFileClient {
    /**
     * 查询文件元数据。
     *
     * @param fileId 十进制文件标识
     * @return 与请求文件一致的元数据
     */
    StoredFileResponseBody file(String fileId);

    /**
     * 以运营操作人签发原版本短时下载地址；Platform 据操作人记审计。
     *
     * @param operator 当前运营身份
     * @param fileId 十进制文件标识
     * @return 带地址与到期时间的下载授权
     */
    FileDownloadResponseBody download(OperatorContext operator, String fileId);
}
