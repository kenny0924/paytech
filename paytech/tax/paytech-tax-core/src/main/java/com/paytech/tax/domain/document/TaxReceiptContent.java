package com.paytech.tax.domain.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import com.paytech.foundation.money.Money;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.domain.policy.TaxReceiptRule;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 简化税票票面快照（document_content_json，内容结构版本 1；technique 9.3）
 * <li>只含卖方名称与公开税号、号码、日期、按税类描述的行（数量、净额、税率、税额、含税额）、合计、币种与法定文字键；不含消费者任何资料。</li>
 * <li>金额取冻结计算的行与组件（不再计算），按币种精度输出十进制字符串；纯函数。</li>
 */
public final class TaxReceiptContent {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯静态构造。 */
    private TaxReceiptContent() {
    }

    /**
     * 构造票面 JSON
     *
     * @param context 已登记事实
     * @param rule 地区包简化税票规则
     * @param documentNumber 已分配的号码
     * @param issuedAt 分号时间
     * @return document_content_json
     */
    public static String build(TaxDocumentIssueContext context, TaxReceiptRule rule, String documentNumber,
        Instant issuedAt) {
        TaxCalculation calculation = context.getCalculation().getCalculation();
        TaxRegistration registration = context.getRegistration();
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("documentKind", "RECEIPT");
        root.put("titleKey", rule.getTitleKey());
        root.put("templateCode", rule.getTemplateCode());
        root.put("documentNumber", documentNumber);
        root.put("issuedAt", issuedAt.toString());
        root.put("issueDate", issuedAt.atZone(ZoneId.of(registration.getLegalTimezone())).toLocalDate().toString());
        root.put("orderReference", calculation.getSourceObjectNo());
        ObjectNode seller = root.putObject("seller");
        seller.put("name", registration.getRegisteredName());
        seller.put("taxRegistrationNo", registration.getPublicRegistrationNo());
        seller.put("jurisdictionCode", registration.getJurisdictionCode());
        root.put("currency", calculation.getCurrency());
        lines(root.putArray("lines"), context, calculation.getCurrency());
        ObjectNode totals = root.putObject("totals");
        totals.put("netAmount", amount(calculation.getCurrency(), calculation.getNetAmount()));
        totals.put("taxAmount", amount(calculation.getCurrency(), calculation.getChargedTaxAmount()));
        totals.put("grossAmount", amount(calculation.getCurrency(), calculation.getGrossAmount()));
        ArrayNode legal = root.putArray("legalTextKeys");
        rule.getLegalTextKeys().forEach(legal::add);
        return JSON.writeValueAsString(root);
    }

    /** 行：描述取税类名称，税率取该行组件（无税率为空）。 */
    static void lines(ArrayNode array, TaxDocumentIssueContext context, String currency) {
        for (TaxCalculationLine line : context.getCalculation().getLines()) {
            BigDecimal rate = context.getCalculation().componentsOf(line.getId()).stream()
                .map(TaxCalculationComponent::getRate).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            ObjectNode item = array.addObject();
            item.put("lineNo", line.getLineNo());
            item.put("description", context.getCategoryNames().getOrDefault(line.getCategoryCode(), line.getCategoryCode()));
            item.put("quantity", line.getQuantity().stripTrailingZeros().toPlainString());
            item.put("netAmount", amount(currency, line.getNetAmount()));
            item.put("rate", rate == null ? null : rate.stripTrailingZeros().toPlainString());
            item.put("taxAmount", amount(currency, line.getChargedTaxAmount()));
            item.put("grossAmount", amount(currency, line.getGrossAmount()));
        }
    }

    /** 按币种精度输出金额。 */
    static String amount(String currency, BigDecimal value) {
        return Money.of(currency, value.stripTrailingZeros()).toAmountString();
    }
}
