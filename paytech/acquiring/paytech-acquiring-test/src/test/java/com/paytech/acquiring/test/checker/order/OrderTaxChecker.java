package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.acquiring.test.dal.OrderTaxTestMapper;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;

import lombok.AllArgsConstructor;

/** 只读核对订单税务计算记录、当前报价、合同绑定与上下文引用；期望值由用例独立给出。 */
@TestComponent
@AllArgsConstructor
public class OrderTaxChecker {
    private final OrderTaxTestMapper mapper;

    /** 订单全部计算记录，按 revision 升序。 */
    public List<Map<String, Object>> calculations(long orderId) {
        return mapper.calculations(orderId);
    }

    /** 第 revision 条计算的 tax 计算 ID。 */
    public long calculationId(long orderId, int revision) {
        return ((Number) calculation(orderId, revision).get("tax_calculation_id")).longValue();
    }

    /**
     * 核对一条计算记录
     *
     * @param tax 期望税额；BLOCKED 时为 null
     * @param total 期望总额；BLOCKED 时为 null
     * @param reason 期望阻断原因；非 BLOCKED 为 null
     */
    public OrderTaxChecker assertCalculation(long orderId, int revision, TaxCalculationStage stage,
        TaxCalculationStatus status, String country, String tax, String total, String reason) {
        var row = calculation(orderId, revision);
        assertAll(() -> assertEquals(stage.getCode(), row.get("calculation_stage"), "计算阶段"),
            () -> assertEquals(status.getCode(), row.get("tax_status"), "计算状态"),
            () -> assertEquals(country, row.get("basis_country_code"), "国家证据"),
            () -> assertAmount(tax, row.get("tax_amount"), "税额"),
            () -> assertAmount(total, row.get("total_amount"), "总额"),
            () -> assertEquals(reason, row.get("block_reason_code"), "阻断原因"),
            () -> assertEquals(status == TaxCalculationStatus.BLOCKED, row.get("lines_json") == null, "逐行税额"),
            () -> assertEquals(status == TaxCalculationStatus.BLOCKED, row.get("components_json") == null, "税组件"));
        return this;
    }

    /** 计算记录条数。 */
    public OrderTaxChecker assertRevisions(long orderId, int count) {
        assertEquals(count, mapper.calculations(orderId).size(), "计算记录条数");
        return this;
    }

    /** 订单当前报价。 */
    public OrderTaxChecker assertQuote(long orderId, OrderAmountStatus status, String tax, String total) {
        var quote = mapper.quote(orderId);
        assertAll(() -> assertEquals(status.getCode(), quote.get("amount_status"), "金额状态"),
            () -> assertAmount(tax, quote.get("tax_amount"), "当前税额"),
            () -> assertAmount(total, quote.get("total_amount"), "当前总额"));
        return this;
    }

    /** 订单全部合同按呈现先后绑定的计算 ID。 */
    public OrderTaxChecker assertContracts(long orderId, List<Long> calculationIds) {
        var contracts = mapper.contracts(orderId);
        assertEquals(calculationIds,
            contracts.stream().map(row -> ((Number) row.get("tax_calculation_id")).longValue()).toList(), "合同绑定的计算");
        return this;
    }

    /** 订单全部合同，按呈现先后。 */
    public List<Map<String, Object>> contracts(long orderId) {
        return mapper.contracts(orderId);
    }

    /** 新订单上下文不写旧零税快照，记录建单计算。 */
    public OrderTaxChecker assertContext(long orderId, long initialCalculationId) {
        var context = mapper.context(orderId);
        assertAll(() -> assertNull(context.get("finance_tax_snapshot_id"), "旧税务快照"),
            () -> assertNull(context.get("finance_tax_snapshot_hash"), "旧税务快照摘要"),
            () -> assertEquals(initialCalculationId, ((Number) context.get("initial_tax_calculation_id")).longValue(),
                "建单计算"));
        return this;
    }

    /** 第 revision 条计算记录，必须存在。 */
    private Map<String, Object> calculation(long orderId, int revision) {
        var row = mapper.calculations(orderId).stream()
            .filter(candidate -> ((Number) candidate.get("revision")).intValue() == revision).findFirst().orElse(null);
        assertNotNull(row, "计算记录 revision " + revision);
        return row;
    }

    /** 金额按数值比较；期望为 null 时实际也必须为空。 */
    private static void assertAmount(String expected, Object actual, String label) {
        if (expected == null) {
            assertNull(actual, label);
            return;
        }
        assertNotNull(actual, label);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual), label + "：" + actual);
    }
}
