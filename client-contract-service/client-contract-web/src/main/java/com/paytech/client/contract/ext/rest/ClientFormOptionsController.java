package com.paytech.client.contract.ext.rest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.paytech.client.contract.api.onboarding.ClientFormOptionsApi;
import com.paytech.client.contract.api.onboarding.response.*;
import com.paytech.client.contract.domain.onboarding.ClientDraftCommand;
import com.paytech.client.contract.service.processor.ClientFormOptionsProcessor;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** C03 私网目录协议，不将配置对象直接发布到网络。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientFormOptionsController implements ClientFormOptionsApi {
    private final ClientFormOptionsProcessor processor;
    /** 验证可信调用坐标后只转换 Owner 已确定的目录事实。 */
    @Override public ClientFormOptionsResponseBody options(String caller,String account,String session,String type) {
        if(!"client-console-bff".equals(caller)) { throw new FailException(APPLICATION_NOT_FOUND); }
        long accountId,sessionId;
        try { accountId=ClientDraftCommand.id(account);sessionId=ClientDraftCommand.id(session); }
        catch(FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); }
        var result=processor.options(accountId,sessionId,type);
        return new ClientFormOptionsResponseBody(type,result.status().getCode(),
            result.organizationTypes().stream().map(o -> new CatalogOptionResponseBody(o.code(),o.label())).toList(),
            result.businessModels().stream().map(o -> new CatalogOptionResponseBody(o.code(),o.label())).toList(),
            result.identifierTypes().stream().map(o -> new IdentifierOptionResponseBody(o.code(),o.label(),o.issuingCountryMode())).toList());
    }
}
