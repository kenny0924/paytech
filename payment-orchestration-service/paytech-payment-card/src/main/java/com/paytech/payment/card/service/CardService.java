package com.paytech.payment.card.service;

import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.domain.CardRiskAttributes;

/**
 * Card 本地能力：只访问 Card 自己的表，不访问 Vault、Redis 等外部依赖
 * <li>需要外部访问的准备、核对与领取由 Card 的 Processor 在事务外完成。</li>
 */
public interface CardService {
    /**
     * 保存事务外已准备的卡交易工具
     * <li>参与调用方事务，与工具 Token 一起提交；上下文必须带 {@code PrepareCardInstrumentProcessor} 的准备结果。</li>
     * <li>卡与卡凭证已在准备阶段保存，这里只插入交易工具。</li>
     *
     * @param context 已准备的卡工具上下文
     */
    void applyInstrument(CardInstrumentContext context);

    /**
     * 读取支付风控评估所需的卡属性
     * <li>经交易工具关联到卡，只读 BIN 与指纹，不解密、不访问 Vault，不领取 CVV。</li>
     * <li>交易工具不存在时返回 null，由调用方省略这两个可选输入。</li>
     *
     * @param instrumentRef Card 工具引用
     * @return BIN 与卡指纹，或 null
     */
    CardRiskAttributes riskAttributes(String instrumentRef);
}
