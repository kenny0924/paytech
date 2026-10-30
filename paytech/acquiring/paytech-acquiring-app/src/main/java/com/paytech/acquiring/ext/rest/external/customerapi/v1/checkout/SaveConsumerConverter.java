package com.paytech.acquiring.ext.rest.external.customerapi.v1.checkout;

import com.paytech.acquiring.api.checkout.v1.checkout.request.SaveConsumerRequestBody;
import com.paytech.acquiring.api.checkout.v1.checkout.response.GetCheckoutResponseBody.CheckoutAmountsInfo;
import com.paytech.acquiring.api.checkout.v1.checkout.response.SaveConsumerResponseBody;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Address;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.service.processor.customer.request.SaveConsumerRequest;
import com.paytech.acquiring.service.processor.customer.response.SaveConsumerResponse;

/** 保存接口只做 Path、Body 和内部模型之间的适配。 */
public final class SaveConsumerConverter {
    /** 禁止实例化无状态转换器。 */
    private SaveConsumerConverter() {}

    /** 不在入站转换器中执行规范化、资格判断或资料保护。 */
    public static SaveConsumerRequest request(String token, SaveConsumerRequestBody body) {
        var address = body.getBillingAddress();
        return new SaveConsumerRequest(token,
            new ConsumerDetails(body.getFullName(), body.getEmail(),
                new Address(address.getCountryCode(), address.getAddressLine1(), address.getAddressLine2(),
                    address.getRegion(), address.getCity(), address.getPostalCode())));
    }

    /** 输出公开编号、保存状态与重算后的报价；金额按币种精度，未知金额保持 null。 */
    public static SaveConsumerResponseBody response(SaveConsumerResponse response) {
        var currency = response.getCurrency();
        var amounts = new CheckoutAmountsInfo(GetCheckoutConverter.money(currency, response.getMerchandiseAmount()),
            GetCheckoutConverter.money(currency, response.getDiscountAmount()),
            GetCheckoutConverter.money(currency, response.getNetAmount()),
            GetCheckoutConverter.money(currency, response.getTaxAmount()),
            GetCheckoutConverter.money(currency, response.getRoundingAmount()),
            GetCheckoutConverter.money(currency, response.getTotalAmount()));
        return new SaveConsumerResponseBody(response.getOrderNo(), response.getConsumerStatus(),
            response.getAmountStatus().getCode(), response.getTaxBlockReason(), currency, amounts);
    }
}
