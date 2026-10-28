package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.paytech.foundation.common.enums.CodeEnum;
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
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;

/**
 * Platform 币种合同与 BFF 页面模型之间的显式投影和回执核对
 * <li>币种页列表读参考数据币种接口：币种代码、小数位、舍入方式、状态、版本任一不符合合同即按上游无效响应 502 处理，未知 Code 不放行；
 * 数字代码、名称、符号与图标可空（历史数据未补齐时页面显示占位）。</li>
 * <li>财务页下拉同样读参考数据币种接口：Platform 按状态筛选，BFF 逐条核对代码、小数位、舍入方式、状态与版本，
 * 并要求返回状态与本次筛选一致，只投影代码、小数位、舍入方式与状态。</li>
 * <li>写回执的对象类型、币种与状态必须与本次操作一致；修改不改变状态，只要求为已知状态。</li>
 *
 * @author Claude
 */
final class CurrencySpecContractConverter {
    /** Platform 写回执中币种规则的对象类型。 */
    static final String OBJECT_TYPE = "CURRENCY_SPEC";

    private static final String CURRENCY = "[A-Z]{3}";
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String NUMERIC = "[0-9]{3}";
    private static final String FILE_ADDRESS = "/files/v1/[1-9][0-9]{0,18}";
    private static final int MAX_MINOR_UNIT = 8;

    /** 只承载转换，禁止创建实例。 */
    private CurrencySpecContractConverter() {
    }

    /** 核对并投影币种页列表；币种代码重复视为上游无效响应。 */
    static CurrencySpecListResponseBody toBff(ReferenceCurrencyListResponseBody value) {
        require(value != null && value.getItems() != null);
        List<CurrencySpecResponseBody> items = value.getItems().stream().map(CurrencySpecContractConverter::toBff)
            .toList();
        require(new HashSet<>(items.stream().map(CurrencySpecResponseBody::getCurrency).toList()).size() == items.size());
        var result = new CurrencySpecListResponseBody();
        result.setItems(items);
        return result;
    }

    /** 财务页币种下拉：逐条核对后只保留代码、小数位、舍入方式与状态；status 为空表示全部状态，否则每条都须为该状态。 */
    static CurrencyListResponseBody toFinance(ReferenceCurrencyListResponseBody value, String status) {
        require(value != null && value.getItems() != null);
        List<CurrencyResponseBody> items = value.getItems().stream().map(CurrencySpecContractConverter::toFinance).toList();
        require(new HashSet<>(items.stream().map(CurrencyResponseBody::getCurrency).toList()).size() == items.size());
        require(status == null || items.stream().allMatch(item -> status.equals(item.getStatus())));
        var result = new CurrencyListResponseBody();
        result.setItems(items);
        return result;
    }

    /** 新建请求逐字段转换，不补默认值。 */
    static com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody toPlatform(
        CreateCurrencySpecRequestBody request) {
        return new com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody(
            request.getCurrency(), request.getMinorUnit(), request.getRoundingMode(), request.getReason(),
            request.getNumericCode(), request.getName(), request.getSymbol(), request.getLogoFileAddress());
    }

    /** 修改请求逐字段转换；expectedVersion 已由入站校验保证非空。 */
    static com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody toPlatform(
        UpdateCurrencySpecRequestBody request) {
        return new com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody(
            request.getMinorUnit(), request.getRoundingMode(), request.getExpectedVersion(), request.getReason(),
            request.getNumericCode(), request.getName(), request.getSymbol(), request.getLogoFileAddress());
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
     * @param expectedStatus 本次操作后应有的状态；新建为 DRAFT，启停为请求状态，修改为 null（任一已知状态）
     * @return 浏览器回执
     */
    static ConfigurationMutationResponseBody toBff(
        com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody value,
        String currency, String expectedStatus) {
        require(value != null && OBJECT_TYPE.equals(value.getObjectType()) && value.getObjectId() != null
            && value.getObjectId().matches(ID) && Objects.equals(currency, value.getObjectCode())
            && (expectedStatus == null ? known(CurrencySpecStatus.values(), value.getStatus())
                : Objects.equals(expectedStatus, value.getStatus())) && value.getVersion() > 0);
        return new ConfigurationMutationResponseBody(value.getObjectType(), value.getObjectId(), value.getObjectCode(),
            value.getStatus(), value.getVersion());
    }

    /** 单条币种的字段核对与投影；数字代码、名称、符号与图标原样投影（可空）。 */
    private static CurrencySpecResponseBody toBff(ReferenceCurrencyResponseBody value) {
        require(value != null && value.getCurrencyCode() != null && value.getCurrencyCode().matches(CURRENCY)
            && value.getMinorUnit() != null && value.getMinorUnit() >= 0 && value.getMinorUnit() <= MAX_MINOR_UNIT
            && known(CurrencyRoundingMode.values(), value.getRoundingMode())
            && known(CurrencySpecStatus.values(), value.getStatus()) && value.getVersion() != null && value.getVersion() > 0
            && (value.getNumericCode() == null || value.getNumericCode().matches(NUMERIC))
            && (value.getLogoFileAddress() == null || value.getLogoFileAddress().matches(FILE_ADDRESS)));
        var result = new CurrencySpecResponseBody();
        result.setCurrency(value.getCurrencyCode());
        result.setNumericCode(value.getNumericCode());
        result.setName(value.getName());
        result.setSymbol(value.getSymbol());
        result.setMinorUnit(value.getMinorUnit());
        result.setRoundingMode(value.getRoundingMode());
        result.setLogoFileAddress(value.getLogoFileAddress());
        result.setLogoPublicUrl(value.getLogoPublicUrl());
        result.setStatus(value.getStatus());
        result.setVersion(value.getVersion());
        return result;
    }

    /** 财务页只需要代码、小数位、舍入方式与状态；版本同样须为正数。 */
    private static CurrencyResponseBody toFinance(ReferenceCurrencyResponseBody value) {
        require(value != null && value.getCurrencyCode() != null && value.getCurrencyCode().matches(CURRENCY)
            && value.getMinorUnit() != null && value.getMinorUnit() >= 0 && value.getMinorUnit() <= MAX_MINOR_UNIT
            && known(CurrencyRoundingMode.values(), value.getRoundingMode())
            && known(CurrencySpecStatus.values(), value.getStatus()) && value.getVersion() != null && value.getVersion() > 0);
        var result = new CurrencyResponseBody();
        result.setCurrency(value.getCurrencyCode());
        result.setMinorUnit(value.getMinorUnit());
        result.setRoundingMode(value.getRoundingMode());
        result.setStatus(value.getStatus());
        return result;
    }

    /** Code 必须属于已发布的枚举。 */
    private static boolean known(CodeEnum[] values, String code) {
        return code != null && Arrays.stream(values).anyMatch(value -> value.getCode().equals(code));
    }

}
