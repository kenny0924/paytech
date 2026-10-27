package com.paytech.client.master.domain.onboarding;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.dataobject.*;
/**
 * 单次本库快照；Vault Reveal 只能在读取事务结束后进行。attributes 含主主体与关联人员全部扩展属性。
 * <li>declarations 为每种声明的最新一条；responsiblePartnerName 为责任 PayFac 的法定名称，平台自营或 Partner 无资料时为空。</li>
 */
public record ClientDraftView(ClientDraftIdentity identity,ClientDraftFields fields,List<ClientDraftContact> contacts,
    List<ClientPersonView> people,List<ClientPartyRelation> relations,List<ClientApplicationDocument> documents,
    List<PartyAttribute> attributes,List<ApplicationDeclaration> declarations,String responsiblePartnerName) {
    @Override public String toString() { return "ClientDraftView[redacted]"; }
}
