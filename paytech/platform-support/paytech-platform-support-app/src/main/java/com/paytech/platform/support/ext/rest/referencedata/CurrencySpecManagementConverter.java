package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.request.ChangeCurrencySpecStatusRequestBody;
import com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody;
import com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.referencedata.service.processor.request.ChangeCurrencySpecStatusRequest;
import com.paytech.platform.support.referencedata.service.processor.request.CreateCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.request.UpdateCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecMutation;

/** 币种规则维护的协议转换；Code 按稳定编码解析，字段约束交由 Body 注解与公共 Processor。 */
@Component
public class CurrencySpecManagementConverter {

    /** 新建只接收代码、小数位、舍入方式与原因。 */
    public CreateCurrencySpecRequest create(String actor, String session, CreateCurrencySpecRequestBody body) {
        return new CreateCurrencySpecRequest(actor, session, body.getCurrency(), body.getMinorUnit(),
            EnumUtils.getEnumByCode(CurrencyRoundingMode.class, body.getRoundingMode()), text(body.getReason()));
    }

    /** 修改以 Path 币种为身份，携带原版本。 */
    public UpdateCurrencySpecRequest update(String actor, String session, String currency,
        UpdateCurrencySpecRequestBody body) {
        return new UpdateCurrencySpecRequest(actor, session, currency, body.getMinorUnit(),
            EnumUtils.getEnumByCode(CurrencyRoundingMode.class, body.getRoundingMode()), body.getExpectedVersion(),
            text(body.getReason()));
    }

    /** 启停只转换目标状态，转换规则在读取当前行后判断。 */
    public ChangeCurrencySpecStatusRequest status(String actor, String session, String currency,
        ChangeCurrencySpecStatusRequestBody body) {
        return new ChangeCurrencySpecStatusRequest(actor, session, currency,
            EnumUtils.getEnumByCode(CurrencySpecStatus.class, body.getStatus()), body.getExpectedVersion(),
            text(body.getReason()));
    }

    /** 主键输出十进制字符串，状态输出稳定 Code。 */
    public ConfigurationMutationResponseBody mutation(CurrencySpecMutation result) {
        return new ConfigurationMutationResponseBody(CurrencySpecAuditFacts.TARGET_TYPE, Long.toString(result.getId()),
            result.getCurrency(), result.getStatus().getCode(), result.getVersion());
    }

    /** 原因去除首尾空白；空值由内部请求注解拒绝。 */
    String text(String value) {
        return value == null ? null : value.strip();
    }
}
