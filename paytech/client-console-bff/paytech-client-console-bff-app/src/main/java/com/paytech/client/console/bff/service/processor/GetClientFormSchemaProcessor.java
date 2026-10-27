package com.paytech.client.console.bff.service.processor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.domain.enums.ReferenceCatalog;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.client.console.bff.service.model.response.ClientFormSchemaResponseBody;
import com.paytech.client.console.bff.service.model.response.ClientFormSchemaResponseBody.CatalogItem;
import com.paytech.client.console.bff.service.model.response.ClientFormSchemaResponseBody.Country;
import com.paytech.client.console.bff.service.processor.request.GetClientFormSchemaRequest;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.AddressFormat;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;

import lombok.AllArgsConstructor;

/**
 * 入驻采集 Schema 页面（D-04）：client-master 的 Schema 加 Platform 参考数据选项
 * <li>国家列表总是补全；币种与行政区只在 Schema 引用 REF_CURRENCY / REF_COUNTRY_SUBDIVISION 时读取。</li>
 * <li>行政区范围：只有辖区本地地址格式引用、且辖区代码是 ACTIVE 国家代码时，只读该国；其他情况（通用格式或普通字段也引用）读取全部，由页面按地址国家筛选。</li>
 * <li>任一 Platform 读取失败整页失败，不返回缺少选项的 Schema；全部为只读调用，不涉及事务。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class GetClientFormSchemaProcessor
    extends AbstractProcessor<GetClientFormSchemaRequest, ClientFormSchemaResponseBody> {
    private final CctOnboardingClient onboarding;
    private final PlatformReferenceDataClient referenceData;

    @Override
    protected ClientFormSchemaResponseBody processRequest(GetClientFormSchemaRequest request) {
        var schema = onboarding.formSchema(request.getActor(), request.getApplicationId(), request.getJurisdiction(),
            request.getPartyType(), request.getOrganizationType());
        var countries = referenceData.activeCountries();
        var catalogs = ownerCatalogs(schema);
        Set<String> referenced = referencedCatalogs(schema);
        if (referenced.contains(ReferenceCatalog.REF_CURRENCY.getCode())) {
            catalogs.put(ReferenceCatalog.REF_CURRENCY.getCode(),
                referenceData.activeCurrencies().stream().map(GetClientFormSchemaProcessor::currencyItem).toList());
        }
        if (referenced.contains(ReferenceCatalog.REF_COUNTRY_SUBDIVISION.getCode())) {
            catalogs.put(ReferenceCatalog.REF_COUNTRY_SUBDIVISION.getCode(),
                referenceData.activeSubdivisions(subdivisionCountry(schema, countries)).stream()
                    .map(GetClientFormSchemaProcessor::subdivisionItem).toList());
        }
        return assemble(schema, catalogs, countries.stream().map(GetClientFormSchemaProcessor::country).toList());
    }

    /** client-master 自有目录原样转为页面目录项（代码 + 文案键）。 */
    static Map<String, List<CatalogItem>> ownerCatalogs(FormSchemaResponseBody schema) {
        Map<String, List<CatalogItem>> catalogs = new LinkedHashMap<>();
        if (schema.getCatalogs() != null) {
            schema.getCatalogs().forEach((code, options) -> catalogs.put(code, stream(options).filter(Objects::nonNull)
                .map(option -> catalogItem(option.getCode(), option.getLabelKey(), null, null, null)).toList()));
        }
        return catalogs;
    }

    /** 字段与两种地址格式组件引用的全部目录码。 */
    static Set<String> referencedCatalogs(FormSchemaResponseBody schema) {
        return Stream.concat(fieldCatalogs(schema), Stream.of(schema.getAddressFormat(), schema.getFallbackAddressFormat())
                .flatMap(GetClientFormSchemaProcessor::componentCatalogs))
            .collect(Collectors.toSet());
    }

    /**
     * 行政区的读取范围
     *
     * @return 只需辖区国家时返回该国代码；否则为 null，表示读取全部 ACTIVE 行政区
     */
    static String subdivisionCountry(FormSchemaResponseBody schema, List<ReferenceCountryResponseBody> countries) {
        String code = ReferenceCatalog.REF_COUNTRY_SUBDIVISION.getCode();
        boolean beyondLocalAddress = fieldCatalogs(schema).anyMatch(code::equals)
            || componentCatalogs(schema.getFallbackAddressFormat()).anyMatch(code::equals);
        String jurisdiction = schema.getJurisdictionCode();
        if (beyondLocalAddress || jurisdiction == null
            || countries.stream().noneMatch(country -> jurisdiction.equals(country.getCountryCode()))) {
            return null;
        }
        return jurisdiction;
    }

    /** 分区、分组与字段上的目录码。 */
    static Stream<String> fieldCatalogs(FormSchemaResponseBody schema) {
        return stream(schema.getSections()).filter(Objects::nonNull).flatMap(section -> stream(section.getGroups()))
            .filter(Objects::nonNull).flatMap(group -> stream(group.getFields())).filter(Objects::nonNull)
            .map(FormSchemaResponseBody.Field::getCatalogCode).filter(Objects::nonNull);
    }

    /** 地址格式组件上的目录码；格式为空时没有。 */
    static Stream<String> componentCatalogs(AddressFormat format) {
        return format == null ? Stream.empty() : stream(format.getComponents()).filter(Objects::nonNull)
            .map(FormSchemaResponseBody.AddressComponent::getCatalogCode).filter(Objects::nonNull);
    }

    /** 币种目录项：代码、英文名与图标。 */
    static CatalogItem currencyItem(ReferenceCurrencyResponseBody currency) {
        return catalogItem(currency.getCurrencyCode(), null, currency.getName(), currency.getLogoPublicUrl(), null);
    }

    /** 行政区目录项：代码、英文名与所属国家。 */
    static CatalogItem subdivisionItem(CountrySubdivisionResponseBody subdivision) {
        return catalogItem(subdivision.getSubdivisionCode(), null, subdivision.getName(), null, subdivision.getCountryCode());
    }

    /** 国家选项：代码、英文名、国旗与区号。 */
    static Country country(ReferenceCountryResponseBody source) {
        var country = new Country();
        country.setCode(source.getCountryCode());
        country.setName(source.getName());
        country.setLogoPublicUrl(source.getLogoPublicUrl());
        country.setCallingCode(source.getCallingCode());
        return country;
    }

    static CatalogItem catalogItem(String code, String labelKey, String name, String logoPublicUrl, String countryCode) {
        var item = new CatalogItem();
        item.setCode(code);
        item.setLabelKey(labelKey);
        item.setName(name);
        item.setLogoPublicUrl(logoPublicUrl);
        item.setCountryCode(countryCode);
        return item;
    }

    /** 页面模型：client-master 的其余部分原样带出。 */
    static ClientFormSchemaResponseBody assemble(FormSchemaResponseBody schema, Map<String, List<CatalogItem>> catalogs,
        List<Country> countries) {
        var page = new ClientFormSchemaResponseBody();
        page.setSchemaId(schema.getSchemaId());
        page.setVersionNo(schema.getVersionNo());
        page.setJurisdictionCode(schema.getJurisdictionCode());
        page.setPartyType(schema.getPartyType());
        page.setOrganizationTypeCode(schema.getOrganizationTypeCode());
        page.setSections(schema.getSections());
        page.setCatalogs(catalogs);
        page.setCountries(countries);
        page.setOrganizationTypes(schema.getOrganizationTypes());
        page.setIdentifierTypes(schema.getIdentifierTypes());
        page.setAddressFormat(schema.getAddressFormat());
        page.setFallbackAddressFormat(schema.getFallbackAddressFormat());
        page.setRelationTypes(schema.getRelationTypes());
        page.setRelationRules(schema.getRelationRules());
        page.setMaterials(schema.getMaterials());
        page.setDeclarations(schema.getDeclarations());
        return page;
    }

    private static <T> Stream<T> stream(Collection<T> values) {
        return values == null ? Stream.empty() : values.stream();
    }
}
