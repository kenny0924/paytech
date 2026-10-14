package com.paytech.client.contract.service.processor;
import lombok.AllArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domain.onboarding.ClientFormOptions;
import com.paytech.client.contract.domainservice.service.ClientFormCatalogService;
import com.paytech.client.contract.integration.ClientDraftAccountClient;
import com.paytech.foundation.common.error.InvalidParameterException;
/** 当前会话在目录读取前后核验，外部查询不进入本地数据库事务。 */
@AllArgsConstructor
public class ClientFormOptionsProcessor {
    private final ClientDraftAccountClient accounts;
    private final ClientFormCatalogService catalog;
    /** 只读用例不分配申请、Party、身份键或任何业务状态。 */
    public ClientFormOptions options(long account,long session,String type) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("目录查询不能加入已有事务"); }
        ClientType clientType;
        try { clientType=ClientType.valueOf(type); }
        catch(IllegalArgumentException | NullPointerException failure) { throw new InvalidParameterException("客户类型无效"); }
        accounts.authorize(account,session);
        var result=catalog.options(clientType);
        accounts.authorize(account,session);
        return result;
    }
}
