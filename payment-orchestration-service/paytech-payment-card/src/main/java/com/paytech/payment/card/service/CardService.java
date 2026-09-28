package com.paytech.payment.card.service;

import com.paytech.payment.card.domain.CardInstrumentContext;

/** Card 受控用卡能力 */
public interface CardService {
    /**
     * 本地申请卡工具，填写引用与支持期限
     * <li>参与调用方事务，与工具 Token 一起提交，不独立提交或访问外部依赖</li>
     * <li>需要外部准备时，由调用方先在事务外调用 Card 的 Processor</li>
     */
    void applyInstrument(CardInstrumentContext context);

    /** 核对原工具身份，不重新准备材料或延长期限 */
    void checkInstrument(CardInstrumentContext context);
}
