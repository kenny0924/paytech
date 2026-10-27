package com.paytech.client.master.domain.onboarding.requirement;

import java.util.Map;
import java.util.Set;

import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.RuleRole;
import com.paytech.client.master.domain.enums.SchemaSubject;

/**
 * 一个受审主体在需求计算中的事实
 *
 * @param partyId Party ID
 * @param role PRIMARY 或 RELATED_PERSON
 * @param partyType 主体类型
 * @param values 草稿字段值，键为 分区.字段（如 individual.givenName、address.city、attributes.X、contact.email）
 * @param identifier 资料标识事实；未填写时各项为空
 * @param roles 关联人员在关系中的规则角色；主主体为空集合
 */
public record RequirementSubject(long partyId, SchemaSubject role, ClientType partyType, Map<String, String> values,
    IdentifierFact identifier, Set<RuleRole> roles) {

    /** 资料含受审主体字段值，不输出到日志。 */
    @Override
    public String toString() {
        return "RequirementSubject[redacted]";
    }
}
