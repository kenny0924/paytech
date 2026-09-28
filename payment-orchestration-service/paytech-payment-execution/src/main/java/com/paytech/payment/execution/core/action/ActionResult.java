package com.paytech.payment.execution.core.action;

import java.time.Instant;

import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.RiskResult;

import lombok.Getter;
import lombok.Setter;

/**
 * 动作执行结果
 * <li>承载本次完整结果，数据库更新成功后同步到原动作；可空字段为 null 时写入 NULL。</li>
 * @author lzb
 */
@Getter
@Setter
public class ActionResult {

    /** 执行结果 */
    private ActionStatus status;

    /** 交易风控评估标识 */
    private Long transactionAssessmentId;

    /** 交易风控结论 */
    private RiskResult riskResult;

    /** 认证要求 */
    private AuthenticationRequirement authenticationRequirement;

    /** 交互类型 */
    private InteractionType interactionType;

    /** 交互截止时间 */
    private Instant interactionExpiresAt;

    /** 渠道响应代码 */
    private String channelResponseCode;

    /** 渠道响应说明 */
    private String channelResponseMsg;

    /** 渠道结果时间 */
    private Instant channelResultTime;

    /** 构造未取得确定结果的动作回执。 */
    public static ActionResult unknown() {
        ActionResult result = new ActionResult();
        result.setStatus(ActionStatus.UNKNOWN);
        return result;
    }
}
