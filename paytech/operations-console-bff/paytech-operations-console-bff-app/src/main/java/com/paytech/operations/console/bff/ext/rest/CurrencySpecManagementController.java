package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformCurrencySpecClient;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.ChangeCurrencySpecStatusRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CreateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CurrencySpecListResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.UpdateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 币种页的浏览器入口，全部简单转发 Platform
 * <li>每个路由由 Gateway 绑定 CURRENCY_SPEC_READ（列表）或 CURRENCY_SPEC_MANAGE（新建、修改、启停），并经 PLT-I02 实时授权；Platform 再次校验管理权限。</li>
 * <li>Query 与 Body 按配置管理的严格边界解析：未知字段、重复或多余 Query、查询带 Body 均在访问 Platform 前拒绝。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/currency-admin/v1/currency-specs")
public class CurrencySpecManagementController {
    private static final String CURRENCY = "[A-Z]{3}";

    private final PlatformCurrencySpecClient client;

    /** 全部币种规则，按币种代码升序；页面据 version 提交修改与启停。 */
    @GetMapping
    public CurrencySpecListResponseBody listCurrencySpecs() {
        return client.listCurrencySpecs();
    }

    /** 新建 DRAFT 币种规则。 */
    @PostMapping
    public ConfigurationMutationResponseBody createCurrencySpec(OperatorContext context,
        @Valid @RequestBody CreateCurrencySpecRequestBody request) {
        return client.createCurrencySpec(context, request);
    }

    /** 修改 DRAFT 币种规则的小数位与舍入方式；已启用的规则由 Platform 拒绝。 */
    @PutMapping("/{currency}")
    public ConfigurationMutationResponseBody updateCurrencySpec(OperatorContext context,
        @PathVariable("currency") @Pattern(regexp = CURRENCY) String currency,
        @Valid @RequestBody UpdateCurrencySpecRequestBody request) {
        return client.updateCurrencySpec(context, currency, request);
    }

    /** 启用或停用币种规则；停用只拦截新用途，已有账簿与计费事实照常处理。 */
    @PatchMapping("/{currency}/status")
    public ConfigurationMutationResponseBody changeCurrencySpecStatus(OperatorContext context,
        @PathVariable("currency") @Pattern(regexp = CURRENCY) String currency,
        @Valid @RequestBody ChangeCurrencySpecStatusRequestBody request) {
        return client.changeCurrencySpecStatus(context, currency, request);
    }
}
