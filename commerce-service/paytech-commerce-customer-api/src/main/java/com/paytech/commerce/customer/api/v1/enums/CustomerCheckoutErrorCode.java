package com.paytech.commerce.customer.api.v1.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Customer与Payment内部调用的稳定业务错误目录。 */
public enum CustomerCheckoutErrorCode implements CodeMessageEnum {
    /** Checkout不存在。 */
    CHECKOUT_NOT_FOUND("Checkout不存在", 404),

    /** Checkout已到期。 */
    CHECKOUT_EXPIRED("Checkout已到期", 410),

    /** Checkout已关闭。 */
    CHECKOUT_CLOSED("Checkout已关闭", 409),

    /** Checkout支付条件尚未满足。 */
    CHECKOUT_NOT_READY("Checkout支付条件尚未满足", 409),

    /** 请先保存消费者资料。 */
    CONSUMER_DETAILS_REQUIRED("请先保存消费者资料", 409),

    /** 首次法律接受后不能修改资料。 */
    CONSUMER_DETAILS_LOCKED("首次法律接受后不能修改资料", 409),

    /** 合同与当前展示内容不一致。 */
    LEGAL_DOCUMENT_CHANGED("合同与当前展示内容不一致", 409),

    /** 缺少完整的法律接受记录。 */
    LEGAL_ACCEPTANCE_REQUIRED("缺少完整的法律接受记录", 409),

    /** 原支付提交不存在或不属于当前 Checkout。 */
    PAYMENT_SUBMISSION_NOT_FOUND("支付提交不存在", 404),

    /** 当前 Intent 已有在途或成功支付。 */
    PAYMENT_ALREADY_IN_PROGRESS("当前订单已有支付，请查询原结果", 409),

    /** 调用服务无权访问。 */
    PAYMENT_ACCESS_DENIED("调用服务无权访问", 403);
    private final String message;
    private final int httpStatus;

    /** 冻结该错误的安全消息与HTTP映射。 */
    CustomerCheckoutErrorCode(String message, int httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return name();
    }

    /** 返回安全消息。 */
    @Override
    public String getMessage() {
        return message;
    }

    /** 返回公开HTTP状态。 */
    public int httpStatus() {
        return httpStatus;
    }
}
