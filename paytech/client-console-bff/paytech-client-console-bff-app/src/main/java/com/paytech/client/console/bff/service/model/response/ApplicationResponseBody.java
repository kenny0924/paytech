package com.paytech.client.console.bff.service.model.response;

import java.util.List;
import java.util.Map;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PersonResponseBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.RelationResponseBody;

import lombok.AllArgsConstructor;
import com.paytech.client.master.api.onboarding.response.DeclarationResponseBody;
import com.paytech.client.master.api.onboarding.response.RequirementItemResponseBody;
import lombok.Getter;

/**
 * 入驻申请详情：当前表单、人员关系、材料与审核结果
 * <li>只读状态由 CCT 决定，不能据此推断可提交。</li>
 */
@Getter
@AllArgsConstructor
public class ApplicationResponseBody {

    /** 申请 ID */
    private final String applicationId;

    /** 申请编号 */
    private final String applicationNo;

    /** 申请对应的 Client ID */
    private final String clientId;

    /** 客户类型：INDIVIDUAL 或 ORGANIZATION */
    private final String clientType;

    /** 主主体 Party ID */
    private final String subjectPartyId;

    /** CCT 申请状态 */
    private final String status;

    /** 最近保存时间，UTC 六位微秒字符串 */
    private final String savedAt;

    /** 当前是否可编辑 */
    private final boolean editable;

    /** 表单值：分区 → 字段 → 值 */
    private final Map<String, Map<String, String>> values;

    /** 申请人员 */
    private final List<PersonResponseBody> people;

    /** 人员关系 */
    private final List<RelationResponseBody> relations;

    /** 材料引用及文件状态 */
    private final List<ApplicationDocument> documents;

    /**
     * 审核结果状态
     * <li>NONE 不适用；AVAILABLE 已读取 review；UNAVAILABLE 为 Compliance 不可用或与 CCT 状态不一致，不伪造结果。</li>
     */
    private final String reviewStatus;

    /**
     * 审核结果
     * <li>reviewStatus 不为 AVAILABLE 时为空。</li>
     */
    private final ReviewResult review;

    /** 辖区代码；与主体类型、组织形式一起决定采集 Schema */
    private final String jurisdictionCode;

    /** 组织形式代码，可空 */
    private final String organizationTypeCode;

    /** 申请冻结的采集 Schema ID */
    private final String schemaId;

    /** 退回补件截止时间，UTC；未退回为空 */
    private final String reviewDueTime;

    /**
     * 需求清单状态
     * <li>NONE 不适用（非 DRAFT / RETURNED）；UNAVAILABLE 不能解释为没有缺项。</li>
     */
    private final String requirementsStatus;

    /** 实时需求清单（CM-7），按 client-master 原样投影 */
    private final List<RequirementItemResponseBody> requirements;

    /** 责任 PayFac 的 Local Partner ID；平台直连为空 */
    private final String responsiblePartnerId;

    /** 责任 PayFac 的展示名，由 client-master 解析；平台直连为空，页面不显示审核方 */
    private final String responsiblePartnerName;

    /** 已记录的声明（每类型最新一条），页面据此回显勾选 */
    private final List<DeclarationResponseBody> declarations;

    /** 表单和文件展示名不打印到日志。 */
    @Override
    public String toString() {
        return "ApplicationResponseBody[REDACTED]";
    }
}
