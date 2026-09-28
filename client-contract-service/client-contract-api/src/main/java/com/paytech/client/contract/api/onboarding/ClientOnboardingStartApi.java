package com.paytech.client.contract.api.onboarding;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;
/** C01 的 CCT 申请事实查询；工作区及文件展示由 BFF 聚合。 */
public interface ClientOnboardingStartApi {
    /** 返回真实当前账号的有界申请摘要，GET 不创建或恢复申请。 */
    @GetExchange("/client-onboarding/v1/start")
    StartResponseBody start(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);
}
