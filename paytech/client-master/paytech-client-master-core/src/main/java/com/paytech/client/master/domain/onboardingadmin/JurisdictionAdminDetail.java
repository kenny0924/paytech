package com.paytech.client.master.domain.onboardingadmin;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import com.paytech.client.master.core.dal.dataobject.RelationType;
import com.paytech.client.master.core.dal.dataobject.RuleSetting;

/**
 * 运营只读查看的一个辖区包（全部状态）
 *
 * @param jurisdiction 辖区
 * @param rules 辖区、组织类型与标识类型作用域及 * 默认的规则
 * @param organizationTypes 组织类型
 * @param identifierTypes 辖区与全局标识类型
 * @param relationTypes 辖区与全局关系类型
 */
public record JurisdictionAdminDetail(Jurisdiction jurisdiction, List<RuleSetting> rules, List<OrganizationType> organizationTypes,
    List<IdentifierType> identifierTypes, List<RelationType> relationTypes) {
}
