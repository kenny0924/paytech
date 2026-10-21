package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.ChangeCurrencySpecStatusRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CreateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CurrencySpecListResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CurrencySpecResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.UpdateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CurrencyListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CurrencyResponseBody;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * Platform 币种合同与 BFF 页面模型之间的显式投影和回执核对
 * <li>币种代码、小数位、舍入方式、状态、版本与更新时间任一不符合合同即按上游无效响应 502 处理，未知 Code 不放行。</li>
 * <li>写回执的对象类型、币种与状态必须与本次操作一致。</li>
 *
 * @author Claude
 */
final class CurrencySpecContractConverter {
    /** Platform 写回执中币种规则的对象类型。 */
    static final String OBJECT_TYPE = "CURRENCY_SPEC";

    private static final String CURRENCY = "[A-Z]{3}";
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final int MAX_MINOR_UNIT = 8;

    /** 只承载转换，禁止创建实例。 */
    private CurrencySpecContractConverter() {
    }

    /** 核对并投影全部币种规则；币种代码重复视为上游无效响应。 */
    static CurrencySpecListResponseBody toBff(
        com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody value) {
        require(value != null && value.getItems() != null);
        List<CurrencySpecResponseBody> items = value.getItems().stream().map(CurrencySpecContractConverter::toBff)
            .toList();
        require(new HashSet<>(items.stream().map(CurrencySpecResponseBody::getCurrency).toList()).size() == items.size());
        var result = new CurrencySpecListResponseBody();
        result.setItems(items);
        return result;
    }

    /** 财务页币种下拉：按状态筛选并去掉版本与更新时间；status 为空时返回全部。 */
    static CurrencyListResponseBody toFinance(CurrencySpecListResponseBody specs, String status) {
        var result = new CurrencyListResponseBody();
        result.setItems(specs.getItems().stream().filter(spec -> status == null || status.equals(spec.getStatus()))
            .map(CurrencySpecContractConverter::toFinance).toList());
        return result;
    }

    /** 新建请求逐字段转换，不补默认值。 */
    static com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody toPlatform(
        CreateCurrencySpecRequestBody request) {
        return new com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody(
            request.getCurrency(), request.getMinorUnit(), request.getRoundingMode(), request.getReason());
    }

    /** 修改请求逐字段转换；expectedVersion 已由入站校验保证非空。 */
    static com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody toPlatform(
        UpdateCurrencySpecRequestBody request) {
        return new com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody(
            request.getMinorUnit(), request.getRoundingMode(), request.getExpectedVersion(), request.getReason());
    }

    /** 启停请求逐字段转换；expectedVersion 已由入站校验保证非空。 */
    static com.paytech.platform.support.api.referencedata.request.ChangeCurrencySpecStatusRequestBody toPlatform(
        ChangeCurrencySpecStatusRequestBody request) {
        return new com.paytech.platform.support.api.referencedata.request.ChangeCurrencySpecStatusRequestBody(
            request.getStatus(), request.getExpectedVersion(), request.getReason());
    }

    /**
     * 核对写回执
     *
     * @param value Platform 回执
     * @param currency 本次操作的币种代码
     * @param expectedStatus 本次操作后应有的状态；修改草稿为 DRAFT，启停为请求状态
     * @return 浏览器回执
     */
    static ConfigurationMutationResponseBody toBff(
        com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody value,
        String currency, String expectedStatus) {
        require(value != null && OBJECT_TYPE.equals(value.getObjectType()) && value.getObjectId() != null
            && value.getObjectId().matches(ID) && Objects.equals(currency, value.getObjectCode())
            && Objects.equals(expectedStatus, value.getStatus()) && value.getVersion() > 0);
        return new ConfigurationMutationResponseBody(value.getObjectType(), value.getObjectId(), value.getObjectCode(),
            value.getStatus(), value.getVersion());
    }

    /** 单条币种规则的字段核对与投影。 */
    private static CurrencySpecResponseBody toBff(
        com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody value) {
        require(value != null && value.getCurrency() != null && value.getCurrency().matches(CURRENCY)
            && value.getMinorUnit() >= 0 && value.getMinorUnit() <= MAX_MINOR_UNIT
            && known(CurrencyRoundingMode.values(), value.getRoundingMode())
            && known(CurrencySpecStatus.values(), value.getStatus()) && value.getVersion() > 0);
        var result = new CurrencySpecResponseBody();
        result.setCurrency(value.getCurrency());
        result.setMinorUnit(value.getMinorUnit());
        result.setRoundingMode(value.getRoundingMode());
        result.setStatus(value.getStatus());
        result.setVersion(value.getVersion());
        result.setUpdateTime(instant(value.getUpdateTime()));
        return result;
    }

    /** 财务页只需要代码、小数位、舍入方式与状态。 */
    private static CurrencyResponseBody toFinance(CurrencySpecResponseBody spec) {
        var result = new CurrencyResponseBody();
        result.setCurrency(spec.getCurrency());
        result.setMinorUnit(spec.getMinorUnit());
        result.setRoundingMode(spec.getRoundingMode());
        result.setStatus(spec.getStatus());
        return result;
    }

    /** Code 必须属于已发布的枚举。 */
    private static boolean known(CodeEnum[] values, String code) {
        return code != null && Arrays.stream(values).anyMatch(value -> value.getCode().equals(code));
    }

    /** UTC ISO-8601 时间；缺失或格式不符视为上游无效响应。 */
    private static Instant instant(String value) {
        require(value != null);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }
}
