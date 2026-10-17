package com.paytech.card.domain;

import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardCredential;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;

/**
 * 一个卡交易工具及其使用的卡凭证与卡；只在 Card 模块内使用
 *
 * @param instrument 卡交易工具
 * @param credential 本次使用的卡凭证
 * @param card 凭证所属的卡
 */
public record CardInstrumentRecord(PaymentCardInstrument instrument, PaymentCardCredential credential,
    PaymentCard card) {}
