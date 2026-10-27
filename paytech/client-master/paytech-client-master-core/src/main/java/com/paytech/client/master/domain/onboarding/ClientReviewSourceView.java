package com.paytech.client.master.domain.onboarding;

import java.util.List;
import java.util.Map;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.core.dal.dataobject.PartyProfileIdentifier;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核登记所需的提交事实，组合申请、主体资料、关系、材料和证件绑定多张表。
 * <li>SUBMITTED 后资料只读，读取到的当前事实即提交时事实。</li>
 */
@Getter
@AllArgsConstructor
public class ClientReviewSourceView {
    /** 提交坐标。 */
    private final ClientApplicationSubmission application;
    /** 申请资料归属。 */
    private final ClientDraftIdentity identity;
    /** 主主体资料。 */
    private final ClientDraftFields primary;
    /** 提交范围内的关联人员。 */
    private final List<ClientPersonView> people;
    /** 提交范围内的关系。 */
    private final List<ClientPartyRelation> relations;
    /** 材料引用。 */
    private final List<ClientApplicationDocument> documents;
    /** 各资料的证件绑定，按 profileId。 */
    private final Map<Long, PartyProfileIdentifier> identifiers;
    /** 主主体与关联人员的扩展属性（全部为非 Vault 级）。 */
    private final List<com.paytech.client.master.core.dal.dataobject.PartyAttribute> attributes;
    /** 申请声明（只追加，同类型以最后一行为准）。 */
    private final List<com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration> declarations;

    /** 资料含个人信息，不进入日志。 */
    @Override
    public String toString() {
        return "ClientReviewSourceView[redacted]";
    }
}
