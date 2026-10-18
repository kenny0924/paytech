package com.paytech.acquiring.integration.payment;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.IntentRequest;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.IntentResult;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.CardChoice;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.CardInstrument;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentReference;

/** Payment 创建 Intent 和申请卡工具的出站边界。 */
public interface PaymentCheckoutClient {
    /** 同一来源订单复用唯一 Intent，由 Payment 核对全部冻结事实。 */
    IntentResult createIntent(IntentRequest request);

    /** 为本次 Client、Intent、来源提交申请工具，不持久化卡输入或工具 Token；卡来源为手输卡或已存卡。 */
    CardInstrument applyCardInstrument(long clientId, PaymentReference reference, CardChoice card);
}
