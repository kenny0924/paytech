package com.paytech.risk.decision.domain.enums;

import java.util.Arrays;
import java.util.List;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 实时累计事件编码
 * <li>每个事件只属于一个运行期风控点，变量只能统计本点事件；跨风控点读取事件不在本期。</li>
 * <li>“评估”事件每次成功评估都写，可计入本次；“被拦截”事件在评估得出结论后才知道，不可计入本次。</li>
 * <li>“放行”事件只在结论为放行时写（REQ-04 档位额度只累计放行金额）；计入本次表示“若本次放行后的累计”，供额度判断。</li>
 */
public enum VelocityEventCode implements CodeEnum {
    /** 一次成功完成的正式支付尝试评估。 */
    PAYMENT_ATTEMPT_ASSESSED("PAYMENT_ATTEMPT_ASSESSED", "支付尝试评估", "PAYMENT_ATTEMPT_RISK", true),

    /** 正式评估结论为审核或拒绝的支付尝试。 */
    PAYMENT_ATTEMPT_REJECTED("PAYMENT_ATTEMPT_REJECTED", "支付尝试被拦截（审核或拒绝）", "PAYMENT_ATTEMPT_RISK", false),

    /** 正式评估结论为放行的支付尝试。 */
    PAYMENT_ATTEMPT_ALLOWED("PAYMENT_ATTEMPT_ALLOWED", "支付尝试放行", "PAYMENT_ATTEMPT_RISK", true),

    /** 一次成功完成的正式结账评估。 */
    CHECKOUT_ASSESSED("CHECKOUT_ASSESSED", "结账评估", "CHECKOUT_PREPARE", true),

    /** 正式评估结论为审核或拒绝的结账。 */
    CHECKOUT_REJECTED("CHECKOUT_REJECTED", "结账被拦截（审核或拒绝）", "CHECKOUT_PREPARE", false),

    /** 正式评估结论为放行的结账。 */
    CHECKOUT_ALLOWED("CHECKOUT_ALLOWED", "结账放行", "CHECKOUT_PREPARE", true);

    private final String code;

    private final String eventName;

    private final String pointCode;

    private final boolean includeCurrentAllowed;

    VelocityEventCode(String code, String eventName, String pointCode, boolean includeCurrentAllowed) {
        this.code = code;
        this.eventName = eventName;
        this.pointCode = pointCode;
        this.includeCurrentAllowed = includeCurrentAllowed;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** 事件显示名称。 */
    public String eventName() {
        return eventName;
    }

    /** 事件所属风控点。 */
    public String pointCode() {
        return pointCode;
    }

    /** 可在累计中计入本次：评估事件每次都写；放行事件计入本次即“若本次放行后的累计”。 */
    public boolean includeCurrentAllowed() {
        return includeCurrentAllowed;
    }

    /**
     * 风控点的累计事件
     *
     * @param pointCode 风控点编码
     * @return 按声明顺序的本点事件；未登记的风控点为空
     */
    public static List<VelocityEventCode> of(String pointCode) {
        return Arrays.stream(values()).filter(event -> event.pointCode.equals(pointCode)).toList();
    }
}
