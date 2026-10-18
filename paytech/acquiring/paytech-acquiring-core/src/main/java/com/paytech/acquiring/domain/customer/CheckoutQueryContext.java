package com.paytech.acquiring.domain.customer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.CheckoutVersionView;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.FileLink;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.PaymentStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本次 Checkout 回读事实及纯展示判断，不执行查询、事务或外部调用。 */
@Getter
@AllArgsConstructor
public class CheckoutQueryContext {
    /** Token 实际绑定的会话。 */
    private final CheckoutSession session;
    /** 本库订单，不装配旧 OrderRecord。 */
    private final CommerceOrder order;
    /** 已保存的消费者引用；未保存时为空。 */
    private final ConsumerContext consumer;
    /** 原冻结法律展示；未冻结时为空。 */
    private final CheckoutLegalView legal;
    /** 本地已绑定的支付投影；尚无 Intent 时为空。 */
    private final OrderPaymentProjection paymentProjection;
    /** 本订单不可变商品行。 */
    private final List<OrderItem> items;
    /** 订单或原冻结上下文中的计税国家。 */
    private final String country;
    /** 显式要求回填后取得的原值，不持久化或记录日志。 */
    private final ConsumerDetails details;
    /** 原冻结文件的受控链接，未冻结时为空集合。 */
    private final List<FileLink> links;
    /** Payment 只读摘要；无 Intent 或依赖不可用时为空。 */
    private final PaymentStatus payment;
    /** 已保存接受事实后可能已向 Payment 创建 Intent，禁止再修改消费者。 */
    private final boolean paymentStarted;

    /** 最终合同与金额齐备即可以展示支付页。 */
    public boolean isReady() {
        return legal != null && legal.hasContract() && order.getTotalAmount() != null;
    }

    /** 取消意图或订单终态关闭全部展示操作。 */
    public boolean isOpen() {
        return order.getTerminationRequestedAt() == null
            && (order.getOrderStatus() == OrderStatus.CREATED || order.getOrderStatus() == OrderStatus.OPEN);
    }

    /** 版本稳定后才将法律关联缺失视为损坏，避免把并发冻结误判成错误。 */
    public void validateFrozen() {
        if (legal != null && !legal.hasContract()) {
            throw new ErrorException("Checkout 冻结事实不完整");
        }
    }

    /** 比较本次已读事实，不重新查询数据库或缓存比较结果。 */
    public boolean matchesVersions(CheckoutVersionView versions, Long paymentVersion) {
        return versions.getOrderVersion() == order.getVersion() && versions.getSessionVersion() == session.getVersion()
            && Objects.equals(versions.getConsumerVersion(), consumer == null ? null : consumer.getVersion())
            && Objects.equals(paymentVersion, paymentProjection == null ? null : paymentProjection.getVersion());
    }

    /** 根据当前事实计算提示操作，实际写入口仍需重新验证资格。 */
    public List<String> allowedActions(Instant now) {
        if (!isOpen()) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (!paymentStarted) {
            actions.add("SAVE_CONSUMER");
        }
        if (isReady() && consumer != null) {
            actions.addAll(paymentActions(now));
        }
        return List.copyOf(actions);
    }

    /** 已有支付待收敛时建议轮询，不持久化派生状态。 */
    public Integer pollAfterMs(List<String> actions) {
        boolean polling = isOpen() && actions.contains("CHECK_PAYMENT_STATUS");
        return polling ? 2000 : null;
    }

    /** Payment 不可用时仅轮询，已有认证动作到期后不再提示继续。 */
    List<String> paymentActions(Instant now) {
        if (paymentProjection == null) {
            return List.of("PAY");
        }
        if (payment == null) {
            return List.of("CHECK_PAYMENT_STATUS");
        }
        if (payment.status() == null
            || Set.of("FAILED", "CANCELLED", "VOIDED", "EXPIRED", "AWAITING_CARD").contains(payment.status())) {
            return List.of("PAY");
        }
        if ("REQUIRES_ACTION".equals(payment.status()) && payment.actionId() != null
            && (payment.actionExpiresAt() == null || payment.actionExpiresAt().isAfter(now))) {
            return List.of("CHECK_PAYMENT_STATUS", "CONTINUE_AUTHENTICATION");
        }
        return List.of("CHECK_PAYMENT_STATUS");
    }
}
