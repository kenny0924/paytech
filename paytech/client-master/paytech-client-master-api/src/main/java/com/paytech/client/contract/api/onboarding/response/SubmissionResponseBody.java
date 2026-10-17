package com.paytech.client.contract.api.onboarding.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** C14 提交结果；状态最新值仍以 C05 查询为准。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponseBody {
    /** 申请 ID，正 BIGINT 十进制字符串。 */
    private String applicationId;
    /** 当前提交编号，服务端生成的 UUID。 */
    private String submissionId;
    /**
     * 申请状态
     * <li>SUBMITTED：已提交，正在登记审核任务；UNDER_REVIEW：审核中。</li>
     */
    private String status;
}
