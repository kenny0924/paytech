package com.paytech.client.master.domain.onboarding.requirement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.client.master.core.dal.dataobject.AddressFormatComponent;
import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import com.paytech.client.master.domain.enums.AttributeDataType;
import com.paytech.client.master.domain.enums.WidgetCode;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.ReferenceCatalogCodes;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.client.ReferenceCatalog;
import com.paytech.platform.support.referencedata.client.ReferenceCountry;
import com.paytech.platform.support.referencedata.client.ReferenceCurrency;
import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceItem;
import com.paytech.platform.support.referencedata.client.ReferenceSubdivision;

/**
 * 一次需求计算所用的 Platform 参考数据结论（技术方案 S-03、S-05）：只在内存中，用完即弃
 * <li>由提交准备步骤按本次申请实际填写的编码从进程内参考数据快照取出后构造；查找与计算都不访问外部服务。</li>
 * <li>编码不存在或不是 ACTIVE 一律视为不在目录；行政区还要求属于地址所在国家。</li>
 */
public class ReferenceCodeCheck {
    /** 地址国家字段键。 */
    public static final String ADDRESS_COUNTRY = "address.countryCode";
    private static final String ATTRIBUTES = "attributes.";

    private final Map<String, ReferenceCountry> countries;
    private final Map<String, ReferenceCurrency> currencies;
    private final Map<String, ReferenceSubdivision> subdivisions;

    /** 只持有查找结果。 */
    public ReferenceCodeCheck(Map<String, ReferenceCountry> countries, Map<String, ReferenceCurrency> currencies,
        Map<String, ReferenceSubdivision> subdivisions) {
        this.countries = Map.copyOf(countries);
        this.currencies = Map.copyOf(currencies);
        this.subdivisions = Map.copyOf(subdivisions);
    }

    /**
     * 从进程内快照取出本次编码
     * <li>快照中不存在的编码不出现在结果中；某类快照未配置或未就绪时抛 ReferenceDataUnavailableException，由调用方转换。</li>
     *
     * @param referenceData 进程内参考数据
     * @param codes 本次申请填写的编码
     * @return 参考数据结论
     */
    public static ReferenceCodeCheck lookup(ReferenceData referenceData, Codes codes) {
        return new ReferenceCodeCheck(found(referenceData.countries(), codes.countries()),
            found(referenceData.currencies(), codes.currencies()), found(referenceData.subdivisions(), codes.subdivisions()));
    }

    /** 快照中存在的编码。 */
    static <T extends ReferenceItem> Map<String, T> found(ReferenceCatalog<T> catalog, Set<String> codes) {
        return codes.stream().map(catalog::find).flatMap(Optional::stream)
            .collect(Collectors.toMap(ReferenceItem::code, Function.identity()));
    }

    /** 国家存在且 ACTIVE。 */
    public boolean countryActive(String code) {
        return active(countries, code);
    }

    /**
     * 取值是否在 REF_ 目录中
     *
     * @param catalogCode REF_ 保留目录码
     * @param value 取值
     * @param countryCode 行政区须属于的国家；不限制时为 null
     * @return 存在、ACTIVE 且（行政区）国家一致；未支持的 REF_ 目录一律为 false
     */
    public boolean contains(String catalogCode, String value, String countryCode) {
        if (ReferenceCatalogCodes.REF_CURRENCY.equals(catalogCode)) {
            return active(currencies, value);
        }
        if (ReferenceCatalogCodes.REF_COUNTRY_SUBDIVISION.equals(catalogCode)) {
            return active(subdivisions, value) && (countryCode == null || countryCode.equals(subdivisions.get(value).countryCode()));
        }
        return false;
    }

    /** 编码存在且 ACTIVE。 */
    static boolean active(Map<String, ? extends ReferenceItem> items, String code) {
        ReferenceItem item = code == null ? null : items.get(code);
        return item != null && item.status() == ReferenceDataStatus.ACTIVE;
    }

    /**
     * 字段是否取国家代码：COUNTRY 控件，或数据类型为 COUNTRY 的扩展属性
     *
     * @param pack 辖区包
     * @param field Schema 字段
     * @return 是否按国家校验
     */
    public static boolean countryField(OnboardingPack pack, OnboardingSchemaField field) {
        if (field.getWidgetCode() == WidgetCode.COUNTRY) {
            return true;
        }
        AttributeDefinition attribute = field.getFieldKey().startsWith(ATTRIBUTES)
            ? pack.attribute(field.getFieldKey().substring(ATTRIBUTES.length())) : null;
        return attribute != null && attribute.getDataType() == AttributeDataType.COUNTRY;
    }

    /**
     * 本次申请需要在参考数据中查找的编码
     * <li>国家：地址国家、证件签发国、国家字段与 COUNTRY 扩展属性；币种与行政区：引用 REF_ 目录的字段与地址组件。</li>
     *
     * @param pack 辖区包与 Schema
     * @param subjects 受审主体事实
     * @return 按类型去重的编码
     */
    public static Codes codes(OnboardingPack pack, List<RequirementSubject> subjects) {
        var codes = new Codes(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        for (RequirementSubject subject : subjects) {
            Map<String, String> values = subject.values();
            add(codes.countries(), values.get(ADDRESS_COUNTRY));
            add(codes.countries(), subject.identifier().issuingCountry());
            for (OnboardingSchemaField field : pack.getFields()) {
                if (countryField(pack, field)) {
                    add(codes.countries(), values.get(field.getFieldKey()));
                }
                collect(codes, field.getCatalogCode(), values.get(field.getFieldKey()));
            }
            subject.values().forEach((key, value) -> {
                AttributeDefinition attribute = key.startsWith(ATTRIBUTES) ? pack.attribute(key.substring(ATTRIBUTES.length())) : null;
                if (attribute != null && attribute.getDataType() == AttributeDataType.COUNTRY) {
                    add(codes.countries(), value);
                }
            });
            for (AddressFormatComponent component : pack.addressComponents(pack.addressFormatFor(values.get(ADDRESS_COUNTRY)))) {
                collect(codes, component.getCatalogCode(), values.get("address." + component.getComponent()));
            }
        }
        return codes;
    }

    /** 按 REF_ 目录归类。 */
    static void collect(Codes codes, String catalogCode, String value) {
        if (ReferenceCatalogCodes.REF_CURRENCY.equals(catalogCode)) {
            add(codes.currencies(), value);
        } else if (ReferenceCatalogCodes.REF_COUNTRY_SUBDIVISION.equals(catalogCode)) {
            add(codes.subdivisions(), value);
        }
    }

    /** 非空才加入。 */
    static void add(Set<String> target, String value) {
        if (value != null) {
            target.add(value);
        }
    }

    /**
     * 待查询编码
     *
     * @param countries 国家代码
     * @param currencies 币种代码
     * @param subdivisions 行政区代码
     */
    public record Codes(Set<String> countries, Set<String> currencies, Set<String> subdivisions) {
    }
}
