package com.paytech.acquiring.core.dal.dataobject.checkout;

import java.time.Instant;

import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.foundation.common.error.FailException;

import lombok.*;

/** Hosted Checkout Session 的完整持久化映射。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSession {
    /** 本库预生成的 Checkout Session 主键。 */
    private long checkoutSessionId;

    /** 所属 Commerce Order 主键。 */
    private long orderId;

    /** 大小写敏感的 34 字符公开能力 Token。 */
    private String publicToken;

    /** 创建时从 allowlist 冻结的 HTTPS Origin。 */
    private String publicOrigin;

    /** Session 当前闭合生命周期状态。 */
    private CheckoutSessionStatus sessionStatus;

    /** 首次进入 OPEN 的 UTC 时刻；尚未打开时为空。 */
    private Instant openedAt;

    /** 进入 COMPLETED 的 UTC 时刻；未完成时为空。 */
    private Instant completedAt;

    /** 进入 CANCELLED 的 UTC 时刻；未取消时为空。 */
    private Instant cancelledAt;

    /** Session 条件更新使用的单调版本。 */
    private long version;

    /** 本记录创建 UTC时刻。 */
    private Instant createTime;

    /** 本记录最后更新 UTC 时刻。 */
    private Instant updateTime;

    /** 支付处理中及已关闭会话不再接受新的资料替换。 */
    public void checkConsumerEditable() {
        if (!statusIn(CheckoutSessionStatus.OPEN, CheckoutSessionStatus.PREPARING)) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_CLOSED);
        }
    }

    /** 尚未关闭的会话可参与支付准备与受理，实际就绪仍由订单和合同决定。 */
    public boolean allowsPayment() {
        return statusIn(CheckoutSessionStatus.PREPARING, CheckoutSessionStatus.OPEN);
    }

    /** 比较本会话的闭合状态。 */
    public boolean statusIn(CheckoutSessionStatus... statuses) {
        for (CheckoutSessionStatus status : statuses) {
            if (this.sessionStatus == status) {
                return true;
            }
        }
        return false;
    }

}
