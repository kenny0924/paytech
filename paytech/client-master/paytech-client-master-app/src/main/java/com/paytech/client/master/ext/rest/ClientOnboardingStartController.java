package com.paytech.client.master.ext.rest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.paytech.client.contract.api.onboarding.ClientOnboardingStartApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.ClientDraftProcessor;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** C01 独立路径，不继承 applications 的资源前缀。 */
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientOnboardingStartController implements ClientOnboardingStartApi {
    private final ClientDraftProcessor processor;
    /** 复用草稿用例读取授权后的导航事实。 */
    public ClientOnboardingStartController(ClientDraftProcessor processor) { this.processor=processor; }
    /** 只允许可信 BFF 通道，当前会话由 Processor 交给 Platform 验证。 */
    @Override public StartResponseBody start(String caller,String account,String session) {
        if(!"client-console-bff".equals(caller)) { throw new FailException(APPLICATION_NOT_FOUND); }
        long accountId,sessionId;
        try { accountId=ClientDraftCommand.id(account);sessionId=ClientDraftCommand.id(session); }
        catch(FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); }
        return processor.start(accountId,sessionId);
    }
}
