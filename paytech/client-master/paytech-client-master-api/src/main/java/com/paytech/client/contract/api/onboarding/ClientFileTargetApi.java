package com.paytech.client.contract.api.onboarding;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.client.contract.api.onboarding.ClientFileTargetModels.FileTargetResponseBody;

/** Platform 验证 CLIENT Session 后调用的私网资源授权，不向浏览器开放。 */
@HttpExchange("/internal/client-onboarding/file-targets")
public interface ClientFileTargetApi {

    /**
     * 查询原申请的准确材料主体，CCT 只校验自身资源，不回调 Platform 验证 Session。
     *
     * @param caller 固定 platform-support-service；网络隔离不能由此 Header 替代
     * @param applicationId 原申请正整数标识
     * @param subjectPartyId 上传或文件读取目标的正整数 Party 标识
     * @param accountId Platform 从刚验证的 CLIENT Session 得到的原账号
     * @return 最小授权事实，非法归属返回标准 404 Problem
     */
    @GetExchange
    FileTargetResponseBody read(@RequestHeader("X-Caller-Service") String caller,
                               @RequestParam String applicationId, @RequestParam String subjectPartyId,
                               @RequestParam String accountId);
}
