package com.paytech.client.master.domain.onboarding;

import java.util.List;
import java.util.Map;

import com.paytech.client.master.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
import com.paytech.client.master.core.dal.dataobject.PartyIdentityKey;
import com.paytech.client.master.core.dal.dataobject.PartyProfileIdentifier;
import com.paytech.compliance.api.enums.MaterialQualificationType;

import lombok.Getter;
import lombok.Setter;

/** 一个受审主体在本次提交中的资料、证件行、扩展属性及待绑定身份；不含证件号原值。 */
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
    /** 扩展属性。 */
    private List<PartyAttribute> attributes;
    /** 资料证件行。 */
    private PartyProfileIdentifier identifier;
    /**
     * 本申请是否可为该主体绑定身份
     * <li>共享或已批准人员的身份应已绑定，不在本次提交改写。</li>
     */
    private boolean bindable;
    /** 唯一性作用域键；标识类型未知或作用域依赖字段缺失时为空。 */
    private String issuerScopeKey;
    /** 已存在的同一身份键；不存在为空。 */
    private PartyIdentityKey existingKey;
    /** 与本申请其他主体重复了同一号码。 */
    private boolean duplicateInApplication;

    /** 唯一性比较值：Token 或公开登记号；未填写为空。 */
    public String identifierKeyValue() {
        return fields.identifierKeyValue();
    }

    /** 是否需要在本次提交新绑定身份。 */
    public boolean needsBinding() {
        return bindable && identifier != null && identifier.getIdentityKeyId() == null;
    }

    /** 已存在的身份键是否属于其他主体，或与本申请其他主体重复。 */
    public boolean identityConflict() {
        return duplicateInApplication || (existingKey != null && existingKey.getPartyId() != partyId);
    }

    /** 扩展属性按代码取值，供 I01 与需求计算。 */
    public Map<String, PartyAttribute> attributesByCode() {
        return attributes.stream().collect(java.util.stream.Collectors.toMap(PartyAttribute::getAttributeCode, row -> row));
    }

    /** 资料含受审主体字段，不进入日志。 */
    @Override
    public String toString() {
        return "ClientSubmissionSubject[redacted]";
    }
}
