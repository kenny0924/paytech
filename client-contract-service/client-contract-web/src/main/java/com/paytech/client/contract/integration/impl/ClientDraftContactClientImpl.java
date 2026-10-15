package com.paytech.client.contract.integration.impl;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.contract.domain.onboarding.*;
import com.paytech.client.contract.integration.ClientDraftContactClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.vault.client.*;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 不缓存原值或 Token；失败不绑定引用，重试复用同一逻辑 Protect 键。Email/Phone 按全系统 Data Type 目录使用 STABLE 模式。 */
public final class ClientDraftContactClientImpl implements ClientDraftContactClient {
    private static final String PURPOSE="CLIENT_ONBOARDING_CONTACT";
    private final VaultClient vault;
    public ClientDraftContactClientImpl(VaultClient vault) { this.vault=vault; }
    public Map<String,ClientDraftContact> protect(ClientDraftPatch patch) {
        outsideTransaction();
        Map<String,ClientDraftContact> result=new LinkedHashMap<>();
        for (String field:List.of("email","phone")) {
            if (!patch.values().containsKey("contact."+field)) continue;
            String value=patch.values().get("contact."+field),type=field.toUpperCase(Locale.ROOT);
            if (value==null) { result.put(type,null);continue; }
            // 每次保存使用随机 Vault 操作键：STABLE 模式下同值仍得到同一 Token；不用联系方式原值派生键，避免把可撞库的摘要交给 Vault。
            String key=UUID.randomUUID().toString();
            try {
                var token=vault.protect(new ProtectCommand(field.equals("email")?"EMAIL_ADDRESS_V1":"PHONE_E164_V1",
                    TokenMode.STABLE,value,PURPOSE,key));
                String mask=field.equals("email")?"***@"+value.substring(value.lastIndexOf('@')+1):"***"+value.substring(value.length()-4);
                result.put(type,new ClientDraftContact(type,token.token(),mask));
            } catch (RuntimeException failure) { throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE); }
        }
        return result;
    }
    public Map<String,String> reveal(List<ClientDraftContact> contacts) {
        outsideTransaction();
        Map<String,String> result=new LinkedHashMap<>();result.put("email",null);result.put("phone",null);
        try {
            for (var contact:contacts) result.put(contact.contactType().toLowerCase(Locale.ROOT),
                vault.reveal(new RevealQuery(contact.token(),PURPOSE)).value());
            return result;
        } catch (RuntimeException failure) { throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE); }
    }
    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Vault 调用不能位于数据库事务内");
    }
}
