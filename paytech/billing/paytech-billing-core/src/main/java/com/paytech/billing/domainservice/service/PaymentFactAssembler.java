package com.paytech.billing.domainservice.service;

import java.time.Instant;

import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.PaymentFactInputs;

/**
 * 自动 PAYMENT 事实的纯内存组装（技术方案 4.5、4.6）：事实头、定价输入、引用与明细行
 * <li>只使用已读取并通过交叉校验的输入；不读库、不调用外部依赖、不开启事务。</li>
 */
public interface PaymentFactAssembler {

    /**
     * 组装一条待登记的自动 PAYMENT 事实
     * <li>行顺序：CLIENT_FEE（按 componentNo）→ CLIENT_PRINCIPAL → PARTNER_SHARE → PLATFORM_SERVICE_CHARGE → CHANNEL_COST，金额为零的组件
     * 不生成行（S7）；组装后按 {@code BillingFactInvariants.checkAutoPayment} 校验。</li>
     *
     * @param inputs 已验证的登记输入
     * @param now 登记时间（UTC 微秒），写入 approved_time 与创建时间
     * @return 携带定价输入、尚未落库的事实上下文
     * @throws com.paytech.foundation.common.error.BizCheckFailException 定价不可表达、Client 手续费合计为零（S14）、本金不为正、毛额超出币种精度或
     *         违反登记约束
     */
    BillingFactContext assemble(PaymentFactInputs inputs, Instant now);
}
