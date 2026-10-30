package com.paytech.operations.console.bff.service.model.risklimittier;

import lombok.Getter;
import lombok.Setter;

/** 页面勾选的上线复核人工项；不接受系统项。 */
@Getter
@Setter
public class TierChangeManualChecklistPageBody {
    /** 渠道报备已确认。 */
    private Boolean channelRegistrationConfirmed;

    /** 技术接入已确认。 */
    private Boolean integrationConfirmed;

    /** 生产交易验证已完成。 */
    private Boolean productionVerified;

    /** 未结事项已处理。 */
    private Boolean openIssuesCleared;
}
