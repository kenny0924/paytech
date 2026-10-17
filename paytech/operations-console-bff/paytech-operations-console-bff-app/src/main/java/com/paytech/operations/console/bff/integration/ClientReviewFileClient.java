package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;

/** Platform 入驻材料审核下载；单次调用，不自动重试。 */
public interface ClientReviewFileClient {
    /**
     * 签发原版本短时下载地址。
     *
     * @param operator 当前运营身份
     * @param fileId 十进制文件标识
     * @return 与请求文件一致的下载授权
     */
    ReviewFileDownloadResponseBody download(OperatorContext operator, String fileId);
}
