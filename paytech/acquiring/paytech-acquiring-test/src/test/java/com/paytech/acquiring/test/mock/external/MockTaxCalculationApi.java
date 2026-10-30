package com.paytech.acquiring.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.acquiring.test.mocker.external.TaxCalculationMocker;
import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;

import lombok.AllArgsConstructor;

/** 独立 Mock 服务直接实现 tax 计算合同（tax 无 context-path），不复制接口方法与路径。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockTaxCalculationApi implements TaxCalculationApi {
    private final TaxCalculationMocker mocker;

    /** 计算由场景按阶段配置 DTO 或故障。 */
    @Override
    public TaxCalculationResponseBody calculateTax(CalculateTaxRequestBody body) {
        var request = (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        request.getResponse().setHeader("X-Request-Id", "tax-test");
        return mocker.answer(body);
    }

    /** 没有商业关系快照的订单重算时按 ID 读取原计算；返回已计算或已配置的计算。 */
    @Override
    public TaxCalculationResponseBody getTaxCalculation(String calculationId) {
        return mocker.answerGet(calculationId);
    }
}
