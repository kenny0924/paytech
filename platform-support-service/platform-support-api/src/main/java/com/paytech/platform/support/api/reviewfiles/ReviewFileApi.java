package com.paytech.platform.support.api.reviewfiles;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;

/** Operations BFF 使用的入驻材料审核读取合同；只开放给可信私网入口。 */
@HttpExchange("/review-files/v1")
public interface ReviewFileApi {
    /**
     * 为运营审核签发入驻材料原版本的短时下载授权。
     * <li>Platform 重检运营会话，至少具备客户入驻审核查看或材料核验权限之一</li>
     * <li>只允许扫描可用且对象有效的入驻材料；空 Body，每次独立授权</li>
     *
     * @param actorAccountId Gateway 验证后的运营账号标识
     * @param sessionId 当前运营会话标识
     * @param fileId 十进制文件标识
     * @return 文件展示信息及最长 60 秒的下载地址
     */
    @PostExchange("/files/{fileId}/download")
    ReviewFileDownloadResponseBody download(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("fileId") String fileId);
}
