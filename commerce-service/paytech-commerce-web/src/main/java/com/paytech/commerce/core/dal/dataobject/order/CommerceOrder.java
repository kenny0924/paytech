package com.paytech.commerce.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commerce.client.api.v1.enums.OrderCreationMode;
import com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.foundation.common.error.FailException;

import lombok.*;

/** Commerce 订单表对象；创建阶段尚未确定的支付准备事实为空。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommerceOrder {
    /** 本库 Order 主键。 */
    private long orderId;

    /** 对外不透明订单编号。 */
    private String orderNo;

    /** 可信 Client 主键。 */
    private long clientId;

    /** Client 内永久唯一订单号。 */
    private String clientOrderId;

    /** 服务端根据创建入口固定的接入方式，不接受 Client 指定或后续修改。 */
    private OrderCreationMode creationMode;

    /** 创建时固定的消费者国家。 */
    private String consumerCountryCode;

    /** 在途或已完成的支付准备命令；尚未开始准备时为空。 */
    private Long preparationCommandId;

    /** 首次请求终止的 UTC 时刻，不代表支付已经终止。 */
    private Instant terminationRequestedAt;

    /** 支付准备固定的 OE 编号；尚未选择时为空。 */
    private String entityNo;

    /** 冻结的 OE 来源类型。 */
    private String entitySourceType;
    /** PARTNER 来源所属合作方，其他来源为空。 */
    private Long localPartnerId;
    /** 冻结的 Client 行业分类。 */
    private String industryCode;
    /** 冻结的四位 MCC。 */
    private String mccCode;

    /** 优惠前商品总额，以订单币种主单位表示，等于各行单价乘数量之和。 */
    private BigDecimal merchandiseAmount;

    /** 商品优惠总额，以订单币种主单位表示，等于各行优惠金额之和。 */
    private BigDecimal discountAmount;

    /** 冻结商品净额，以订单币种主单位表示，等于商品总额减优惠总额。 */
    private BigDecimal netAmount;

    /** 支付准备确定的税额，以订单币种主单位表示。 */
    private BigDecimal taxAmount;

    /** 支付准备确定的舍入差额，以订单币种主单位表示。 */
    private BigDecimal roundingAmount;

    /** 支付准备确定的最终应付金额，以订单币种主单位表示。 */
    private BigDecimal totalAmount;

    /** 金额币种。 */
    private String currency;

    /** 订单当前生命周期状态。 */
    private OrderStatus orderStatus;

    /** 创建请求确定的到期时间。 */
    private Instant expiresAt;

    /** 确认支付成功的 UTC 时刻；未支付时为空。 */
    private Instant paidAt;

    /** 订单进入取消终态的 UTC 时刻，不依赖 Checkout Session。 */
    private Instant cancelledAt;

    /** 条件更新使用的单调版本。 */
    private long version;

    /** 创建请求时间。 */
    private Instant createTime;

    /** 本记录最后更新 UTC 时刻。 */
    private Instant updateTime;

    /** 准备一旦受理，后续资料不能覆盖其固定的消费者事实。 */
    public void checkConsumerEditable() {
        if (terminationRequestedAt != null || paidAt != null || (!statusIn(OrderStatus.CREATED, OrderStatus.OPEN))) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_CLOSED);
        }
        if (preparationCommandId != null) {
            throw new FailException(CustomerCheckoutErrorCode.CONSUMER_DETAILS_LOCKED);
        }
    }

    /** 按原始期限与已持久化过期状态校验 Checkout 访问，不因查询延长期限。 */
    public void checkExpiry(Instant now) {
        if (!expiresAt.isAfter(now) || orderStatus == OrderStatus.EXPIRED) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_EXPIRED);
        }
    }

    /** 已开放、未支付且未开始终止的订单才允许新的支付执行。 */
    public boolean allowsPayment() {
        return orderStatus == OrderStatus.OPEN && terminationRequestedAt == null && paidAt == null;
    }

    /** 同一对象的闭合状态比较，不依赖枚举名称或声明顺序。 */
    public boolean statusIn(OrderStatus... statuses) {
        for (OrderStatus status : statuses) {
            if (this.orderStatus == status) {
                return true;
            }
        }
        return false;
    }

}
