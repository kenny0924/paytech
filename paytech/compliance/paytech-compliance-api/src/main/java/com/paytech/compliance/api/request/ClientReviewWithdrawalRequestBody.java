package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** I03 撤回审核案件：只用于核对案件属于该申请。 */
@Getter
@Setter
public class ClientReviewWithdrawalRequestBody {

    /** Client 申请 ID，正 BIGINT 十进制字符串；必须与案件来源申请一致。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String applicationId;

    /** 发起撤回的 Platform CUSTOMER Account ID，可空。 */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String requestedByAccountId;
}
