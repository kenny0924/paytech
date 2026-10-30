package com.paytech.acquiring.domain.customer;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 保存所需的本库对象及规范化、保护后的输入，不持有 HTTP 对象或协作者。 */
@Getter
@AllArgsConstructor
public class SaveConsumerContext {
    /** 已通过 Token 定位的原订单。 */
    private final CommerceOrder order;

    /** Token 实际绑定的会话。 */
    private final CheckoutSession session;

    /** 原资料对象；首次保存时只有预分配身份，version 为零。 */
    private final ConsumerContext consumer;

    /** 当前规范化资料，只用于本次处理，不写通用命令快照。 */
    private final ConsumerDetails details;

    /** 事务外取得的受保护引用。 */
    private final ProtectedConsumer protectedValues;

    /**
     * 按账单国家重算的新计算记录（最新 revision + 1，FINAL）
     * <li>税务服务上线前的历史订单不重算，为空。</li>
     */
    @Setter
    private OrderTaxCalculation quote;

    /**
     * 绑定新计算的合同行
     * <li>报价金额变化时为重新渲染的文件，否则沿用当前合同文件；与 quote 同时为空或非空。</li>
     */
    @Setter
    private FrozenCheckoutRecord contract;
}
