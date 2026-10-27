package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** C05 审核结果投影：客户可见的最近一次审核结果；不含内部理由、核验记录、风险评级和操作人。 */
@Getter
@Setter
public class ClientReviewResultResponseBody {

    /** Client 申请 ID。 */
    private String applicationId;

    /** 最近一次主审核案件 ID。 */
    private String caseId;

    /**
     * 主审核案件当前状态
     * <li>SUBMITTED、IN_REVIEW、PENDING_DEPENDENCY、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。</li>
     */
    private String status;

    /** 已结束案件的结果：RETURNED、APPROVED、REJECTED、WITHDRAWN 或 EXPIRED；进行中为空。 */
    private String outcome;

    /** 提交轮次，首次提交为 1，每次退回重提加 1。 */
    private Integer roundNo;

    /** 兼容字段：退回时客户需要修改的分区（BASIC、BUSINESS、PEOPLE、MATERIALS）；新退回可为空。 */
    private String returnSection;

    /** 运营填写的客户可见说明；未填写为空。 */
    private String publicNote;

    /** 退回的需求级错误；非退回为空列表。 */
    private List<ClientReviewRequirementErrorBody> errors;

    /** 退回补件期限，UTC 六位微秒；未退回或未设期限为空。 */
    private String dueTime;

    /** 结束时间，UTC 六位微秒；进行中为空。 */
    private String decidedAt;
}
