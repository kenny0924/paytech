package com.paytech.client.console.bff.service.model.response;

import java.util.List;
import java.util.Map;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PersonResponseBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.RelationResponseBody;

import lombok.AllArgsConstructor;
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

    /** 材料主体及其材料要求 */
    private final List<MaterialSubject> materialSubjects;

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

    /** 表单和文件展示名不打印到日志。 */
    @Override
    public String toString() {
        return "ApplicationResponseBody[REDACTED]";
    }
}
