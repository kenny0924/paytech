package com.paytech.acquiring.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 订单一次税务计算：按 revision 追加、写入后不可变；同订单最大 revision 即当前有效报价。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTaxCalculation {
    /** 本库预生成的主键。 */
    private long orderTaxCalculationId;

    /** 所属订单主键。 */
    private long orderId;

    /**
     * 订单内计算序号
     * <li>建单为 1，每次消费者资料提交重算加 1；即 tax sourceRevision。</li>
     */
    private int revision;

    /** tax 返回的不可变计算 ID。 */
    private long taxCalculationId;

    /** 计算阶段：PROVISIONAL 暂估不可支付，FINAL 最终。 */
    private TaxCalculationStage calculationStage;

    /** 计算状态：READY、BLOCKED 或 NOT_MANAGED。 */
    private TaxCalculationStatus taxStatus;

    /** 本次计算采用的国家证据（PROVISIONAL 为 Client 声明，FINAL 为账单国家）。 */
    private String basisCountryCode;

    /**
     * 税额汇总，订单币种主单位
     * <li>BLOCKED 时为空。</li>
     */
    private BigDecimal taxAmount;

    /**
     * 头部舍入差额
     * <li>BLOCKED 时为空。</li>
     */
    private BigDecimal roundingAmount;

    /**
     * 应付总额 = 订单净额 + 税额 + 舍入差额
     * <li>BLOCKED 时为空。</li>
     */
    private BigDecimal totalAmount;

    /**
     * BLOCKED 的第一条原因代码
     * <li>tax TaxBlockReason Code；其他状态为空。</li>
     */
    private String blockReasonCode;

    /**
     * 报价有效截止时刻
     * <li>只有 FINAL + READY 非空；过期后不可支付。</li>
     */
    private Instant expiresAt;

    /**
     * 逐行税额 JSON 数组
     * <li>元素 clientItemId、netAmount、taxAmount、totalAmount（十进制字符串）；BLOCKED 时为空。</li>
     */
    private String linesJson;

    /**
     * 按辖区与注册汇总的税组件 JSON 数组
     * <li>元素 jurisdictionCode、registrationId、taxSubjectPartyId、taxAmount（十进制字符串）；NOT_MANAGED 为 []，BLOCKED 时为空。</li>
     */
    private String componentsJson;

    /** 创建时间。 */
    private Instant createTime;

    /** 订单金额状态：BLOCKED 阻断；NOT_MANAGED 或 FINAL 阶段为最终；建单暂估为 ESTIMATED。 */
    public OrderAmountStatus amountStatus() {
        if (taxStatus == TaxCalculationStatus.BLOCKED) {
            return OrderAmountStatus.BLOCKED;
        }
        if (taxStatus == TaxCalculationStatus.NOT_MANAGED || calculationStage == TaxCalculationStage.FINAL) {
            return OrderAmountStatus.FINAL;
        }
        return OrderAmountStatus.ESTIMATED;
    }

    /**
     * 当前时刻能否据此支付
     * <li>必须是 FINAL 阶段、READY 或 NOT_MANAGED，且有效期未过；暂估、阻断或过期都不可支付。</li>
     *
     * @param now 当前时刻
     * @return 可支付为 true
     */
    public boolean payableAt(Instant now) {
        if (calculationStage != TaxCalculationStage.FINAL || taxStatus == TaxCalculationStatus.BLOCKED) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
