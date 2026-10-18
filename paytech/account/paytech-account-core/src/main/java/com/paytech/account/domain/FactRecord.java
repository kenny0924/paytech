package com.paytech.account.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户服务内部的经济事实：由 EconomicFactRecorded Payload 或 billing 重放读取结果转换，只保留余额映射需要的字段（design/04 §5）
 *
 * @param factId 经济事实 ID
 * @param factVersion 经济事实版本
 * @param supersedesFactId 被替代的前一事实，可空
 * @param factType 事实类型代码（billing EconomicsType）
 * @param clientId Client，规范十进制
 * @param entityNo Operating Entity 编号，决定余额所在的 OE 账簿
 * @param localPartnerId Local Partner，可空
 * @param financeEntityId 平台服务费 Finance Entity，可空
 * @param currency 币种
 * @param grossAmount 总额
 * @param channelAccountId 渠道清算账户（CHANNEL_ACCOUNT Owner），可空
 * @param lines 全部明细行，按行号升序
 */
public record FactRecord(long factId, long factVersion, Long supersedesFactId, String factType, Long clientId,
    String entityNo, Long localPartnerId, Long financeEntityId, String currency, BigDecimal grossAmount,
    Long channelAccountId, List<Line> lines) {

    /** 余额所在的经营主体账簿身份。 */
    public BookIdentity book() {
        return BookIdentity.operatingEntity(entityNo, currency);
    }

    /**
     * 经济事实明细行
     *
     * @param lineNo 行号，从 1 起
     * @param feeTypeCode 费用类型（AmountSource 代码）
     * @param payerRole 付方角色（FactPartyRole 代码），交易事实行可空
     * @param payerId 付方标识
     * @param payeeRole 收方角色，交易事实行可空
     * @param payeeId 收方标识
     * @param direction 影响方向（CHARGE、REVERSAL、ADJUSTMENT），可空
     * @param amount 已按币种精度归整的金额
     */
    public record Line(int lineNo, String feeTypeCode, String payerRole, String payerId, String payeeRole,
        String payeeId, String direction, BigDecimal amount) {
    }
}
