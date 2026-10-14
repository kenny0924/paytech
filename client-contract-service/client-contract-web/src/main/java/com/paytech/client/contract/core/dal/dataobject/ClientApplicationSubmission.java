package com.paytech.client.contract.core.dal.dataobject;

import java.time.Instant;

import com.paytech.client.contract.domain.enums.ClientApplicationStatus;

import lombok.Getter;
import lombok.Setter;

/** Client 申请的提交与审核登记坐标（t_client_application 的提交相关列）。 */
@Getter
@Setter
public class ClientApplicationSubmission {
    /** 申请 ID。 */
    private long applicationId;
    /** 申请编号。 */
    private String applicationNo;
    /** Client ID。 */
    private long clientId;
    /** 发起申请的 Platform CUSTOMER Account ID。 */
    private long accountId;
    /** 申请状态。 */
    private ClientApplicationStatus status;
    /**
     * 当前提交编号
     * <li>DRAFT 首次提交前为空；RETURNED 时保留上一次提交，重提时更换。</li>
     */
    private String currentSubmissionId;
    /** Risk 主审核任务 ID；登记成功前为空。 */
    private Long dueDiligenceCaseId;
    /** 当前主审核任务的最终资格决定 ID；通过或拒绝后写入。 */
    private Long qualificationDecisionId;
    /** 最近一次正式提交的 UTC 时间。 */
    private Instant submittedTime;
    /** 最近一次更新的 UTC 时间。 */
    private Instant updateTime;
}
