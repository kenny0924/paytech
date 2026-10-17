package com.paytech.acquiring.ext.rest.external.customerapi.v1.checkout;

import com.paytech.acquiring.api.checkout.v1.checkout.request.SaveConsumerRequestBody;
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

    /** 只输出公开编号和保存状态。 */
    public static SaveConsumerResponseBody response(SaveConsumerResponse response) {
        return new SaveConsumerResponseBody(response.getOrderNo(), response.getConsumerStatus());
    }
}
