package com.paytech.client.master.domain.onboarding.requirement;

import java.time.LocalDate;

/**
 * 资料法律标识在需求计算中的事实；不含号码原值
 *
 * @param type 标识类型代码，未选为 null
 * @param valuePresent 是否已保存号码（Token 或公开登记号）
 * @param checkResult 保存时的格式与校验位结论；通过为 null
 * @param issuingCountry 签发国，可空
 * @param expiryDate 到期日，可空
 * @param scopeMissing 作用域依赖的字段（签发国或登记机关）缺失，无法计算唯一性作用域
 * @param conflict 号码已绑定其他主体，或同一申请内两个主体重复
 */
public record IdentifierFact(String type, boolean valuePresent, String checkResult, String issuingCountry,
    LocalDate expiryDate, boolean scopeMissing, boolean conflict) {
}
