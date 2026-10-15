package com.paytech.client.contract.integration;
import java.util.*;
import com.paytech.client.contract.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.contract.domain.onboarding.*;
/** Vault 只保护和读取原值；联系用途、规范化及展示掩码仍归 CCT。 */
public interface ClientDraftContactClient {
    Map<String,ClientDraftContact> protect(ClientDraftPatch patch);
    Map<String,String> reveal(List<ClientDraftContact> contacts);
}
