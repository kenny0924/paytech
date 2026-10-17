package com.paytech.commerce.test.builder.customer;

import com.paytech.commerce.customer.api.v1.checkout.request.SaveConsumerRequestBody;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.Address;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 只构造合成资料；原值与期望值由用例分别指定，不调用生产规范化逻辑。 */
@Setter
@Accessors(fluent = true)
public final class SaveConsumerRequestBuilder {
    private String fullName = "Test Consumer";
    private String email = "Buyer+checkout@example.invalid";
    private String country = "AE";
    private String line1 = "10 Test Street";
    private String line2 = "Unit 2";
    private String region = "DU";
    private String city = "Dubai";
    private String postalCode = "00000";

    /** 每次返回独立构造器，禁止共享可变请求。 */
    public static SaveConsumerRequestBuilder template() {
        return new SaveConsumerRequestBuilder();
    }

    /** 使用正式 RequestBody 和地址对象。 */
    public SaveConsumerRequestBody build() {
        var address = new SaveConsumerRequestBody.BillingAddressInfo();
        address.setCountryCode(country);
        address.setAddressLine1(line1);
        address.setAddressLine2(line2);
        address.setRegion(region);
        address.setCity(city);
        address.setPostalCode(postalCode);
        var body = new SaveConsumerRequestBody();
        body.setFullName(fullName);
        body.setEmail(email);
        body.setBillingAddress(address);
        return body;
    }

    /** 构造独立指定的预期外部 DTO；不替调用方修正输入。 */
    public ConsumerDetails details() {
        return new ConsumerDetails(fullName, email, new Address(country, line1, line2, region, city, postalCode));
    }
}
