package com.paytech.card.service;

import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardCredential;
import com.paytech.card.domain.CardInstrumentRecord;

/**
 * 卡与卡凭证的本地存取，只在 Card 模块内使用
 * <li>只访问 Card 自己的表，不访问 Vault、Redis 等外部依赖。</li>
 * <li>卡和卡凭证是可复用的事实，各自单条插入、自动提交，不参与工具申请的主事务。</li>
 */
public interface CardStorageService {
    /**
     * 用明文卡号定位卡
     *
     * @param pan 卡号
     * @return 卡；未保存过该卡号时为 null
     */
    PaymentCard findCard(String pan);

    /**
     * 保存新卡
     * <li>并发首用同一卡号时只留一条：撞卡指纹唯一索引则返回已有的卡。</li>
     *
     * @param card 已加密、已托管 DEK 的新卡
     * @return 实际保存的卡；与入参不是同一对象时，入参的 DEK 未被采用
     */
    PaymentCard saveCard(PaymentCard card);

    /**
     * 保存新卡凭证
     * <li>撞（卡、凭证摘要）唯一索引则返回已有的凭证。</li>
     *
     * @param credential 已加密的新凭证
     * @return 实际保存的凭证
     */
    PaymentCardCredential saveCredential(PaymentCardCredential credential);

    /**
     * 读取卡交易工具及其凭证与卡
     *
     * @param instrumentRef Card 工具引用
     * @return 三者齐全的记录；工具不存在时为 null
     */
    CardInstrumentRecord findInstrument(String instrumentRef);
}
