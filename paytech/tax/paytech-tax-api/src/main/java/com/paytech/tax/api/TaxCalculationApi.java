package com.paytech.tax.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 税务计算
 * <li>内部接口，调用方为 acquiring（结账报价）；不经 Gateway。计算一经保存不可变，地址、商品、金额、卖方任一变化由调用方换新
 * sourceRevision 重新计算。</li>
 */
@HttpExchange(url = "/tax/v1/calculations", accept = "application/json")
public interface TaxCalculationApi {

    /**
     * 计算并保存一次税务报价
     * <li>业务身份为 sourceSystem + sourceObjectType + sourceObjectId + sourceRevision + taxScopeKey：同身份同内容返回原计算
     * （200，不重算）；同身份不同内容 409 TAX_CALCULATION_CONFLICT。</li>
     * <li>READY、BLOCKED、NOT_MANAGED 均为 200 正常结果；BLOCKED 的原因见 blockReasons，订单不可支付。</li>
     *
     * @param body 计算请求
     * @return 计算结果：头汇总、逐行金额与组件、按辖区 + 注册汇总的组件；字段注解不符 422 VALIDATION_ERROR；金额精度、行合计或
     *         行号 / 证据类型重复 400 INVALID_REQUEST；409 同身份不同内容
     */
    @PostExchange
    TaxCalculationResponseBody calculateTax(@Valid @RequestBody CalculateTaxRequestBody body);

    /**
     * 按 ID 读取税务计算（内部诊断、联调）
     *
     * @param calculationId 计算 ID
     * @return 与 calculateTax 相同的结构；不存在 404 TAX_CALCULATION_NOT_FOUND
     */
    @GetExchange("/{calculationId}")
    TaxCalculationResponseBody getTaxCalculation(
        @PathVariable("calculationId") @Pattern(regexp = "[1-9][0-9]{0,18}") String calculationId);
}
