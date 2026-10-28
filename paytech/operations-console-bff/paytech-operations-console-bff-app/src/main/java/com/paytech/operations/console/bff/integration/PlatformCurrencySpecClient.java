package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.ChangeCurrencySpecStatusRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CreateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CurrencySpecListResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.UpdateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CurrencyListResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * Platform 币种规则的本地端口；币种事实、启停规则与 CURRENCY_SPEC_MANAGE 再次鉴权归 Platform
 * <li>写操作以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface PlatformCurrencySpecClient {

    /**
     * 币种页列表：全部状态，含数字代码、名称、符号、图标与版本
     *
     * @return 按币种代码升序的全部币种规则
     */
    CurrencySpecListResponseBody listCurrencySpecs();

    /**
     * 财务页币种下拉：由 Platform 按状态筛选，不含版本与更新时间
     *
     * @param status 可选状态；省略时返回全部
     * @return 按币种代码升序的币种
     */
    CurrencyListResponseBody listCurrencies(String status);

    /**
     * 新建 DRAFT 币种规则
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 保存回执，objectCode 为币种代码
     */
    ConfigurationMutationResponseBody createCurrencySpec(OperatorContext context, CreateCurrencySpecRequestBody request);

    /**
     * 修改币种规则（DRAFT 全部属性；其他状态只换图标）
     *
     * @param context 操作人
     * @param currency 币种代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateCurrencySpec(OperatorContext context, String currency,
        UpdateCurrencySpecRequestBody request);

    /**
     * 启用或停用币种规则
     *
     * @param context 操作人
     * @param currency 币种代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody changeCurrencySpecStatus(OperatorContext context, String currency,
        ChangeCurrencySpecStatusRequestBody request);
}
