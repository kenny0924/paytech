package com.paytech.channel.connector.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 渠道成本版本内容摘要 {@value #SCHEMA}
 * <li>覆盖方案代码、Channel、版本号、生效时间、编制人，以及按 scopeNo 升序的全部适用范围和范围内按 componentCode 升序的
 * 全部成本组成；不含任何生成的主键，同样内容在不同环境得到同一摘要。</li>
 * <li>ID 与金额都以字符串表达：费率与金额去掉末尾零后以普通十进制输出（本库无币种精度表，金额按币种精度归整由 billing
 * 计算时完成）；effectiveFrom 为 UTC 微秒 ISO-8601；空值输出 JSON null。</li>
 * <li>先写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class ChannelCostContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1";

    private static final JsonFactory JSON = new JsonFactory();

    private static final DateTimeFormatter MICROS =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private ChannelCostContentHash() {
    }

    /**
     * 计算版本内容摘要
     *
     * @param plan 所属方案
     * @param ver 已填好版本号、生效时间与编制人的版本
     * @param scopes 版本全部适用范围，顺序不影响结果
     * @param components 全部适用范围的成本组成，以 scopeId 关联，顺序不影响结果
     * @return SHA-256 小写十六进制
     */
    public static String compute(ChannelCostPlan plan, ChannelCostPlanVer ver, List<ChannelCostScope> scopes,
        List<ChannelCostComponent> components) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            writeHeader(generator, plan, ver);
            generator.writeName("scopes");
            generator.writeStartArray();
            List<ChannelCostScope> sortedScopes =
                scopes.stream().sorted(Comparator.comparing(ChannelCostScope::getScopeNo)).toList();
            for (ChannelCostScope scope : sortedScopes) {
                writeScope(generator, scope, componentsOf(scope, components));
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        return sha256(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 写出版本头字段并保持对象处于打开状态。 */
    static void writeHeader(JsonGenerator generator, ChannelCostPlan plan, ChannelCostPlanVer ver) {
        generator.writeStartObject();
        generator.writeStringProperty("schema", SCHEMA);
        generator.writeStringProperty("planCode", plan.getPlanCode());
        generator.writeStringProperty("channelId", String.valueOf(plan.getChannelId()));
        generator.writeNumberProperty("costVer", ver.getCostVer());
        generator.writeStringProperty("effectiveFrom", micros(ver.getEffectiveFrom()));
        generator.writeStringProperty("makerId", String.valueOf(ver.getMakerId()));
    }

    /** 写出一个适用范围及其按组成代码升序的成本组成。 */
    static void writeScope(JsonGenerator generator, ChannelCostScope scope, List<ChannelCostComponent> components) {
        generator.writeStartObject();
        generator.writeNumberProperty("scopeNo", scope.getScopeNo());
        generator.writeStringProperty("midId", String.valueOf(scope.getMidId()));
        generator.writeStringProperty("entityNo", scope.getEntityNo());
        generator.writeStringProperty("channelAgreementRef", scope.getChannelAgreementRef());
        generator.writeStringProperty("countryCode", scope.getCountryCode());
        generator.writeStringProperty("currency", scope.getCurrency());
        generator.writeStringProperty("paymentMethodCode", scope.getPaymentMethodCode());
        generator.writeStringProperty("actionType", scope.getActionType());
        generator.writeName("components");
        generator.writeStartArray();
        for (ChannelCostComponent component : components) {
            writeComponent(generator, component);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /** 写出一个成本组成。 */
    static void writeComponent(JsonGenerator generator, ChannelCostComponent component) {
        generator.writeStartObject();
        generator.writeStringProperty("componentCode", component.getComponentCode());
        generator.writeStringProperty("componentType", component.getComponentType().getCode());
        generator.writeStringProperty("calculationBase", component.getCalculationBase().getCode());
        generator.writeStringProperty("rateValue", decimal(component.getRateValue()));
        generator.writeStringProperty("fixedAmount", decimal(component.getFixedAmount()));
        generator.writeStringProperty("currency", component.getCurrency());
        generator.writeStringProperty("minAmount", decimal(component.getMinAmount()));
        generator.writeStringProperty("maxAmount", decimal(component.getMaxAmount()));
        generator.writeStringProperty("roundingMode", component.getRoundingMode().getCode());
        generator.writeEndObject();
    }

    /** 适用范围的成本组成，按组成代码升序复制，不改变调用方列表。 */
    static List<ChannelCostComponent> componentsOf(ChannelCostScope scope, List<ChannelCostComponent> components) {
        return components.stream().filter(component -> component.getScopeId().equals(scope.getId()))
            .sorted(Comparator.comparing(ChannelCostComponent::getComponentCode)).toList();
    }

    /** 费率或金额去掉末尾零后以普通十进制输出；为空时输出 null。 */
    public static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** UTC 微秒 ISO-8601 时间。 */
    static String micros(Instant time) {
        return MICROS.format(time);
    }

    /** 计算 SHA-256 小写十六进制。 */
    static String sha256(byte[] canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new ErrorException("运行环境缺少 SHA-256", e);
        }
    }
}
