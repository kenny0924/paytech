package com.paytech.account.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 账户服务内部的计费事实：由 BillingFactRecorded Payload 转换，只保留明细账入账需要的字段
 *
 * @param factId 计费事实 ID
 * @param factVersion 计费事实版本
 * @param supersedesFactId 被替代的前一事实，可空
 * @param factType 事实类型代码（billing BillingFactType）
 * @param clientId Client，规范十进制
 * @param entityNo Operating Entity 编号，决定入账流
 * @param localPartnerId Local Partner，可空
 * @param financeEntityId 平台服务费 Finance Entity，可空
 * @param currency 币种
 * @param balanceAvailableTime 应付余额到期转可用时间，可空（设计 8.5）
 * @param lines 全部明细行，按行号升序
 */
public record FactRecord(long factId, long factVersion, Long supersedesFactId, String factType, Long clientId,
    String entityNo, Long localPartnerId, Long financeEntityId, String currency, Instant balanceAvailableTime,
    List<Line> lines) {

    /**
     * 计费事实明细行
     *
     * @param lineNo 行号，从 1 起
     * @param feeTypeCode 金额组件（billing FactComponentType 代码）
     * @param payerRole 付方角色（FactPartyRole 代码），映射规则要求必填
     * @param payerId 付方标识
     * @param payeeRole 收方角色，映射规则要求必填
     * @param payeeId 收方标识
     * @param direction 影响方向（CHARGE、REVERSAL、ADJUSTMENT），可空
     * @param amount 已按币种精度归整的金额
     * @param targetBucket 收方余额桶（PENDING / AVAILABLE），可空；本期不参与映射，account 继续按现行规则记 PENDING
     */
    public record Line(int lineNo, String feeTypeCode, String payerRole, String payerId, String payeeRole,
        String payeeId, String direction, BigDecimal amount, String targetBucket) {
    }
}
