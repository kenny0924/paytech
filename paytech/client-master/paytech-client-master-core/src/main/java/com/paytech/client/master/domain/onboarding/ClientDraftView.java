package com.paytech.client.master.domain.onboarding;
import java.util.List;
import com.paytech.client.master.core.dal.dataobject.*;
/** 单次本库快照；Vault Reveal 只能在读取事务结束后进行。 */
public record ClientDraftView(ClientDraftIdentity identity,ClientDraftFields fields,List<ClientDraftContact> contacts,
    List<ClientPersonView> people,List<ClientPartyRelation> relations,List<ClientApplicationDocument> documents) {
    @Override public String toString() { return "ClientDraftView[redacted]"; }
}
