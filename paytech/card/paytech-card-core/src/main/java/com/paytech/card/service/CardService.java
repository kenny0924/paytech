package com.paytech.card.service;

import com.paytech.card.domain.CardInstrumentContext;

/**
 * Card 本地能力：只访问 Card 自己的表，不访问 Vault、Redis 等外部依赖
 * <li>需要外部访问的准备、核对、签票与领取由 Card 的 Processor 在事务外完成。</li>
 */
public interface CardService {
    /**
     * 保存事务外已准备的卡交易工具
     * <li>上下文必须带 {@code PrepareCardInstrumentProcessor} 的准备结果；卡与卡凭证已在准备阶段保存，这里只插入交易工具（单条插入）。</li>
     * <li>同一 Client 同一来源提交已有工具时抛出 {@link org.springframework.dao.DuplicateKeyException}，由调用方改读原工具。</li>
     *
     * @param context 已准备的卡工具上下文
     */
    void applyInstrument(CardInstrumentContext context);

    /**
     * 卡支付成功后的本地处理（消费 PaymentSucceeded，design/05 §2.3）
     * <li>在消费事务内调用，只写 Card 自己的表；可重复调用：凭证已验证时不改，已存卡已存在时只更新凭证与同意时间。</li>
     * <li>本次凭证首次成功时标记已验证；交易工具同意保存且有 Customer 时，生成已存卡，或把该 Customer 已有的同卡已存卡更新为本次凭证。</li>
     *
     * @param instrumentRef Card 工具引用
     */
    void paymentSucceeded(String instrumentRef);
}
