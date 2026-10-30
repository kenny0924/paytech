package com.paytech.acquiring.integration.tax.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxCalculation;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxComponent;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxLine;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxParties;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxQuoteRequest;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.request.TaxCalculationLineRequestBody;
import com.paytech.tax.api.request.TaxEvidenceRequestBody;
import com.paytech.tax.api.response.TaxCalculationLineResponseBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.api.response.TaxComponentSummaryResponseBody;

/**
 * acquiring 报价与 tax 计算合同之间的纯转换
 * <li>请求：sourceSystem=acquiring、ORDER、SALE、恒为未税价（technique S-06）；PROVISIONAL 带 DECLARED_COUNTRY，FINAL 带
 * BILLING_COUNTRY；Client API 没有自报税额字段，declaredTaxAmount 不传（NOT_MANAGED 时税额为 0）；没有商业关系快照时不传责任方；
 * 行 description 取商品名称（票据打印内容），名称为空时取商品说明，截断到 255。</li>
 * <li>响应：核对计算 ID、订单、revision、阶段与币种回显，状态必须是已发布 Code；不符合即上游协议错误。</li>
 */
public final class CheckoutTaxConverter {
    /** tax 记录的来源系统。 */
    static final String SOURCE_SYSTEM = "acquiring";

    /** 来源对象类型：订单。 */
    static final String SOURCE_OBJECT_TYPE = "ORDER";

    /** 本期唯一的税务范围与供应类型：销售。 */
    public static final String SALE = "SALE";

    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** tax 行说明最大长度。 */
    static final int DESCRIPTION_LIMIT = 255;

    /** 纯静态转换。 */
    private CheckoutTaxConverter() {
    }

    /** 构造计算请求；金额以普通十进制字符串传递，不做舍入。 */
    static CalculateTaxRequestBody toRequest(TaxQuoteRequest request) {
        var body = new CalculateTaxRequestBody();
        body.setSourceSystem(SOURCE_SYSTEM);
        body.setSourceObjectType(SOURCE_OBJECT_TYPE);
        body.setSourceObjectId(Long.toString(request.getOrderId()));
        body.setSourceObjectNo(request.getOrderNo());
        body.setSourceRevision(request.getRevision());
        body.setTaxScopeKey(SALE);
        body.setStage(request.getStage().getCode());
        body.setSupplyKind(SALE);
        body.setBusinessMode(request.getParties().businessMode());
        body.setSellerPartyId(text(request.getParties().sellerPartyId()));
        body.setTaxSubjectPartyId(text(request.getParties().taxSubjectPartyId()));
        body.setBuyerClientId(Long.toString(request.getClientId()));
        body.setEntityNo(request.getEntityNo());
        body.setCurrency(request.getCurrency());
        body.setPriceMode(TaxPriceMode.EXCLUSIVE.getCode());
        body.setTaxPointDate(request.getTaxPointDate().toString());
        body.setEvidence(List.of(evidence(request)));
        body.setLines(lines(request.getItems()));
        return body;
    }

    /** 本阶段唯一的国家证据：暂估用 Client 声明国家，最终用账单国家。 */
    static TaxEvidenceRequestBody evidence(TaxQuoteRequest request) {
        var evidence = new TaxEvidenceRequestBody();
        evidence.setType(request.getStage() == TaxCalculationStage.FINAL ? TaxEvidenceType.BILLING_COUNTRY.getCode()
            : TaxEvidenceType.DECLARED_COUNTRY.getCode());
        evidence.setCountryCode(request.getCountryCode());
        return evidence;
    }

    /** 按原呈现顺序逐行，lineRef 为 Client Item ID，lineAmount 为未税净额。 */
    static List<TaxCalculationLineRequestBody> lines(List<OrderItem> items) {
        List<TaxCalculationLineRequestBody> lines = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            var line = new TaxCalculationLineRequestBody();
            line.setLineRef(item.clientItemId());
            line.setLineNo(index + 1);
            line.setCategoryCode(item.taxCategoryCode());
            line.setQuantity(plain(item.quantity()));
            line.setUnitPrice(plain(item.unitAmount()));
            line.setDiscountAmount(plain(item.lineDiscountAmount()));
            line.setLineAmount(plain(item.lineNetAmount()));
            line.setDescription(description(item.itemName(), item.itemDescription()));
            lines.add(line);
        }
        return lines;
    }

    /**
     * 核对回显并转换为本库计算结果
     * <li>BLOCKED 时税额、总额与逐行税额为空，blockReasonCode 取第一条原因；其他状态头部与逐行金额必须完整。</li>
     */
    static TaxCalculation toCalculation(TaxQuoteRequest request, TaxCalculationResponseBody body) {
        if (body == null || body.getCalculationId() == null || !ID.matcher(body.getCalculationId()).matches()
            || !Long.toString(request.getOrderId()).equals(body.getSourceObjectId())
            || body.getSourceRevision() == null || body.getSourceRevision() != request.getRevision()
            || !request.getStage().getCode().equals(body.getStage())
            || !request.getCurrency().equals(body.getCurrency())) {
            throw new CheckoutUpstreamProtocolException("tax 计算回显与订单报价请求不一致");
        }
        var status = EnumUtils.getEnumByCode(TaxCalculationStatus.class, body.getStatus());
        if (status == null || body.getLines() == null || body.getComponents() == null) {
            throw new CheckoutUpstreamProtocolException("tax 计算状态或明细缺失");
        }
        boolean blocked = status == TaxCalculationStatus.BLOCKED;
        return TaxCalculation.builder().calculationId(Long.parseLong(body.getCalculationId())).stage(request.getStage())
            .status(status).taxAmount(amount(body.getTaxAmount(), !blocked))
            .roundingAmount(amount(body.getRoundingAmount(), !blocked)).totalAmount(amount(body.getGrossAmount(), !blocked))
            .blockReasonCode(blocked ? blockReason(body) : null).expiresAt(body.getExpiresAt())
            .lines(body.getLines().stream().map(line -> line(line, status)).toList())
            .components(body.getComponents().stream().map(CheckoutTaxConverter::component).toList()).build();
    }

    /** 某次计算冻结的计税主体；业务模式必须存在，卖方与责任方可空。 */
    static TaxParties toParties(long calculationId, TaxCalculationResponseBody body) {
        if (body == null || !Long.toString(calculationId).equals(body.getCalculationId())
            || body.getBusinessMode() == null) {
            throw new CheckoutUpstreamProtocolException("tax 计算读取回显不一致");
        }
        return new TaxParties(body.getBusinessMode(), id(body.getSellerPartyId()), id(body.getTaxSubjectPartyId()));
    }

    /** BLOCKED 必须给出至少一条原因。 */
    static String blockReason(TaxCalculationResponseBody body) {
        if (body.getBlockReasons() == null || body.getBlockReasons().isEmpty()
            || body.getBlockReasons().getFirst().getCode() == null) {
            throw new CheckoutUpstreamProtocolException("tax BLOCKED 计算缺少原因");
        }
        return body.getBlockReasons().getFirst().getCode();
    }

    /**
     * 一行回显
     * <li>READY 时行税额与含税额必须存在；BLOCKED 时必须为空。</li>
     * <li>NOT_MANAGED 时 tax 只在头部给自报税额、行金额为空：本库按行税额 0、含税额等于净额记录（acquiring 不自报税额，头税额恒为 0，
     * 合计校验仍成立）。</li>
     */
    static TaxLine line(TaxCalculationLineResponseBody line, TaxCalculationStatus status) {
        if (line == null || line.getLineRef() == null) {
            throw new CheckoutUpstreamProtocolException("tax 计算行缺少引用");
        }
        var net = amount(line.getNetAmount(), true);
        if (status == TaxCalculationStatus.NOT_MANAGED && line.getTaxAmount() == null && line.getGrossAmount() == null) {
            return new TaxLine(line.getLineRef(), net, BigDecimal.ZERO, net);
        }
        boolean required = status != TaxCalculationStatus.BLOCKED;
        return new TaxLine(line.getLineRef(), net, amount(line.getTaxAmount(), required),
            amount(line.getGrossAmount(), required));
    }

    /** 汇总组件的辖区、税种、注册、纳税主体与税额都必须存在。 */
    static TaxComponent component(TaxComponentSummaryResponseBody component) {
        if (component == null || component.getJurisdictionCode() == null || component.getTaxType() == null
            || component.getRegistrationId() == null || component.getTaxSubjectPartyId() == null
            || !ID.matcher(component.getRegistrationId()).matches()
            || !ID.matcher(component.getTaxSubjectPartyId()).matches()) {
            throw new CheckoutUpstreamProtocolException("tax 计算组件不完整");
        }
        return new TaxComponent(component.getJurisdictionCode(), component.getTaxType(), component.getRegistrationId(),
            component.getTaxSubjectPartyId(), amount(component.getTaxAmount(), true));
    }

    /** 十进制字符串金额；required 时不得为空，非 required 时必须为空。 */
    static BigDecimal amount(String value, boolean required) {
        if (required != (value != null)) {
            throw new CheckoutUpstreamProtocolException("tax 计算金额与状态不一致");
        }
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException invalid) {
            throw new CheckoutUpstreamProtocolException("tax 计算金额格式无效", invalid);
        }
    }

    /** 去掉无意义尾零的普通十进制字符串。 */
    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** 行说明：优先商品名称，名称为空时取商品说明，截断到 tax 允许的 255 字符；两者都为空不传。 */
    static String description(String name, String fallback) {
        String value = name == null || name.isBlank() ? fallback : name;
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= DESCRIPTION_LIMIT ? value : value.substring(0, DESCRIPTION_LIMIT);
    }

    /** 可空十进制 ID 字符串转 Long；格式非法为协议错误。 */
    static Long id(String value) {
        if (value == null) {
            return null;
        }
        if (!ID.matcher(value).matches()) {
            throw new CheckoutUpstreamProtocolException("tax 计算主体 ID 格式无效");
        }
        return Long.valueOf(value);
    }

    /** 可空 ID 转十进制字符串。 */
    static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
