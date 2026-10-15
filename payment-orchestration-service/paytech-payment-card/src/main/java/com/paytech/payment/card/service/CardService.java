package com.paytech.payment.card.service;

import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.domain.CardRiskAttributes;
import com.paytech.payment.card.domain.ChannelCardMaterial;

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

    /**
     * 为渠道资金动作领取卡材料（PayerMax 卡直付对接技术方案第 2 节）
     * <li>调用方已核对工具与支付尝试的绑定及当前允许用卡；本方法不访问数据库或外部依赖。</li>
     * <li>CVV 只能领取一次，领取后清除；已领取、过期或不存在时拒绝，不返回部分材料。</li>
     *
     * @param instrumentRef Card 工具引用
     * @return 卡材料
     */
    ChannelCardMaterial claimChannelMaterial(String instrumentRef);

    /**
     * 读取支付风控评估所需的卡属性
     * <li>不访问数据库或外部依赖，不领取 CVV，不改变卡材料。</li>
     * <li>卡材料不存在或已过期时返回 null，由调用方省略这两个可选输入；后续资金动作领取材料时按原规则失败。</li>
     *
     * @param instrumentRef Card 工具引用
     * @return BIN 与卡指纹，或 null
     */
    CardRiskAttributes riskAttributes(String instrumentRef);
}
