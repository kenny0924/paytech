package com.paytech.client.contract.domain.onboarding;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.contract.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.contract.core.dal.dataobject.PartyIdentityKey;
import com.paytech.client.contract.core.dal.dataobject.PartyProfileIdentifier;
import com.paytech.risk.api.enums.MaterialQualificationType;

import lombok.Getter;
import lombok.Setter;

/** 一个受审主体在本次提交中的资料、证件行及待绑定身份。 */
@Getter
@Setter
public class ClientSubmissionSubject {
    /** 主主体角色。 */
    public static final String PRIMARY = "PRIMARY";
    /** 组织关联人员角色。 */
    public static final String RELATED_PERSON = "RELATED_PERSON";

    /** Party ID。 */
    private long partyId;
    /** 单份资料 ID。 */
    private long profileId;
    /** PRIMARY 或 RELATED_PERSON。 */
    private String subjectRole;
    /** 资格类型。 */
    private MaterialQualificationType qualificationType;
    /** 是否组织主体。 */
    private boolean organization;
    /** 当前资料字段。 */
    private ClientDraftFields fields;
    /** 联系方式；仅主主体需要。 */
    private List<ClientDraftContact> contacts;
    /** 资料证件行。 */
    private PartyProfileIdentifier identifier;
    /**
     * 本申请是否可为该主体绑定身份
     * <li>共享或已批准人员的身份应已绑定，不在本次提交改写。</li>
     */
    private boolean bindable;
    /** 规范化后的证件号；无法规范化时为空。 */
    private String normalizedIdentifierValue;
    /** 签发作用域；证件类型未知时为空。 */
    private String issuerScopeKey;
    /** 已存在的同一身份键；不存在为空。 */
    private PartyIdentityKey existingKey;

    /** 是否需要在本次提交新绑定身份。 */
    public boolean needsBinding() {
        return bindable && identifier != null && identifier.getIdentityKeyId() == null;
    }

    /** 已存在的身份键是否属于其他主体。 */
    public boolean identityOwnedByOther() {
        return existingKey != null && existingKey.getPartyId() != partyId;
    }

    /** 资料含号码，不进入日志。 */
    @Override
    public String toString() {
        return "ClientSubmissionSubject[redacted]";
    }
}
