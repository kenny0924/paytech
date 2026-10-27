package com.paytech.client.console.bff.service.model.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 最近一次审核案件的客户可见结果
 * <li>只在申请状态为 RETURNED / APPROVED / REJECTED / WITHDRAWN 时读取；不含内部理由、核验记录、风险评级或决定人。</li>
 */
@Getter
@AllArgsConstructor
public class ReviewResult {

    /** 案件当前状态，如 RETURNED、APPROVED、EXPIRED */
    private final String caseStatus;

    /** 已结束案件的结果：RETURNED、APPROVED、REJECTED、WITHDRAWN 或 EXPIRED */
    private final String outcome;

    /** 提交轮次，首次为 1 */
    private final Integer roundNo;

    /** 给客户的公开说明 */
    private final String publicNote;

    /** 决定时间，UTC 六位微秒字符串 */
    private final String decidedAt;

    /** 退回补件期限，UTC；未退回或未设期限为空 */
    private final String dueTime;

    /** 退回的需求级错误；非退回为空列表 */
    private final List<ReviewRequirementError> errors;
}
