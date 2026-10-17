package com.paytech.acquiring.api.client.v1.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** acquiring Client API 对外稳定错误码目录；Customer 相关的三个错误码已归 consumer-api 的 ConsumerErrorCode（Code 值不变）。 */
public enum CommerceCheckoutErrorCode implements CodeMessageEnum {
    /** Original command for this idempotency key has not completed yet. */
    IDEMPOTENCY_REQUEST_IN_PROGRESS("IDEMPOTENCY_REQUEST_IN_PROGRESS", "相同命令正在处理，请稍后重试"),

    /** Another order already owns the Client-scoped {@code clientOrderId}. */
    CLIENT_ORDER_ID_ALREADY_EXISTS("CLIENT_ORDER_ID_ALREADY_EXISTS", "Client Order ID 已存在"),

    /** Submitted data cannot produce an eligible hosted checkout. */
    CHECKOUT_NOT_ELIGIBLE("CHECKOUT_NOT_ELIGIBLE", "Checkout 不符合创建条件"),

    /** Required Client configuration is not ready for checkout creation. */
    CLIENT_CONFIGURATION_NOT_READY("CLIENT_CONFIGURATION_NOT_READY", "Client 配置尚未就绪"),

    /** Checkout creation is still being completed for the existing command. */
    CHECKOUT_CREATION_IN_PROGRESS("CHECKOUT_CREATION_IN_PROGRESS", "Checkout 仍在创建，请稍后重试"),

    /** A required downstream service is temporarily unavailable. */
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "依赖服务暂时不可用，请稍后重试"),

    /** A required downstream service did not respond before its deadline. */
    DEPENDENCY_TIMEOUT("DEPENDENCY_TIMEOUT", "依赖服务响应超时，请稍后重试"),

    /** A required downstream response violated the agreed internal protocol. */
    UPSTREAM_PROTOCOL_ERROR("UPSTREAM_PROTOCOL_ERROR", "依赖服务响应不符合协议"),

    /** Requested order does not exist in the authenticated Client namespace. */
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "Order 不存在"),

    /** 已发生成功支付，必须使用独立撤销或退款流程。 */
    REVERSAL_REQUIRED("REVERSAL_REQUIRED", "订单已有成功支付，不能取消"),

    /** 订单已到期或处于不可取消终态。 */
    ORDER_NOT_CANCELLABLE("ORDER_NOT_CANCELLABLE", "订单当前不可取消"),

    /** 退款不属于当前 Client 或不存在。 */
    REFUND_NOT_FOUND("REFUND_NOT_FOUND", "Refund 不存在"),

    /** 游标无效、版本未知或不属于当前订单。 */
    INVALID_CURSOR("INVALID_CURSOR", "退款分页游标无效"),

    /** Client 退款业务标识已绑定不同内容。 */
    CLIENT_REFUND_ID_CONFLICT("CLIENT_REFUND_ID_CONFLICT", "Client Refund ID 已用于不同退款"),

    /** 包含在途预占后的退款额度不足。 */
    REFUND_AMOUNT_EXCEEDS_REFUNDABLE("REFUND_AMOUNT_EXCEEDS_REFUNDABLE", "退款金额超过当前可退款金额");

    private final String code;
    private final String message;

    CommerceCheckoutErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
