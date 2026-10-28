package com.paytech.platform.support.referencedata.client.test;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;

/**
 * Platform 参考数据响应 DTO 的测试 Builder：{@code template()} 给出合法的 ACTIVE 默认值，用例只覆盖与场景有关的字段。
 * <li>允许设置 null、非法格式或未知状态 Code 等负例值，{@code build()} 不修复；每次 build 返回新的 DTO。</li>
 * <li>例：{@code ReferenceDataBuilders.currency("USD").minorUnit(2).status(ReferenceDataStatus.ACTIVE)}。</li>
 */
public final class ReferenceDataBuilders {

    private ReferenceDataBuilders() {
    }

    /** 指定代码的币种，其余字段取 {@link CurrencyBuilder#template()}。 */
    public static CurrencyBuilder currency(String code) {
        return CurrencyBuilder.template().code(code);
    }

    /** 指定代码的国家，其余字段取 {@link CountryBuilder#template()}。 */
    public static CountryBuilder country(String code) {
        return CountryBuilder.template().code(code);
    }

    /** 指定代码的行政区，所属国家取代码前两位，其余字段取 {@link SubdivisionBuilder#template()}。 */
    public static SubdivisionBuilder subdivision(String code) {
        SubdivisionBuilder builder = SubdivisionBuilder.template().code(code);
        return code != null && code.length() > 2 ? builder.countryCode(code.substring(0, 2)) : builder;
    }

    /** 指定代码的行业大类（层级 1），其余字段取 {@link IndustryBuilder#template()}。 */
    public static IndustryBuilder industry(String code) {
        return IndustryBuilder.template().code(code);
    }

    /** 指定代码与上级大类的行业细类（层级 2）。 */
    public static IndustryBuilder industry(String code, String parentCode) {
        return IndustryBuilder.template().code(code).parentCode(parentCode).level(2);
    }

    /** 指定代码的 MCC，其余字段取 {@link MccBuilder#template()}。 */
    public static MccBuilder mcc(String code) {
        return MccBuilder.template().code(code);
    }

    /** 币种响应 DTO。 */
    public static final class CurrencyBuilder {
        private String code;

        private String numericCode;

        private Integer minorUnit;

        private String roundingMode;

        private String status;

        private CurrencyBuilder() {
        }

        /** USD：数字码 840、2 位小数、HALF_UP、ACTIVE。 */
        public static CurrencyBuilder template() {
            return new CurrencyBuilder().code("USD").numericCode("840").minorUnit(2)
                .roundingMode(CurrencyRoundingMode.HALF_UP.getCode()).status(ReferenceDataStatus.ACTIVE);
        }

        /** ISO 4217 代码。 */
        public CurrencyBuilder code(String code) {
            this.code = code;
            return this;
        }

        /** ISO 4217 数字码。 */
        public CurrencyBuilder numericCode(String numericCode) {
            this.numericCode = numericCode;
            return this;
        }

        /** 小数位；允许越界或 null 负例。 */
        public CurrencyBuilder minorUnit(Integer minorUnit) {
            this.minorUnit = minorUnit;
            return this;
        }

        /** 舍入方式 Code；允许未知值负例。 */
        public CurrencyBuilder roundingMode(String roundingMode) {
            this.roundingMode = roundingMode;
            return this;
        }

        /** 状态。 */
        public CurrencyBuilder status(ReferenceDataStatus status) {
            return statusCode(status == null ? null : status.getCode());
        }

        /** 原始状态 Code；允许未知值负例。 */
        public CurrencyBuilder statusCode(String status) {
            this.status = status;
            return this;
        }

        /** 新的响应 DTO；名称、符号与版本取固定值。 */
        public ReferenceCurrencyResponseBody build() {
            ReferenceCurrencyResponseBody body = new ReferenceCurrencyResponseBody();
            body.setCurrencyCode(code);
            body.setNumericCode(numericCode);
            body.setName("Test Currency " + code);
            body.setSymbol(code);
            body.setMinorUnit(minorUnit);
            body.setRoundingMode(roundingMode);
            body.setStatus(status);
            body.setVersion(1);
            return body;
        }
    }

    /** 国家响应 DTO。 */
    public static final class CountryBuilder {
        private String code;

        private String alpha3Code;

        private String numericCode;

        private String regionCode;

        private String callingCode;

        private String defaultCurrencyCode;

        private String status;

        private CountryBuilder() {
        }

        /** AE：ARE、784、GCC、971、AED、ACTIVE。 */
        public static CountryBuilder template() {
            return new CountryBuilder().code("AE").alpha3Code("ARE").numericCode("784").regionCode("GCC")
                .callingCode("971").defaultCurrencyCode("AED").status(ReferenceDataStatus.ACTIVE);
        }

        /** ISO 3166-1 alpha-2 代码。 */
        public CountryBuilder code(String code) {
            this.code = code;
            return this;
        }

        /** ISO 3166-1 alpha-3 代码。 */
        public CountryBuilder alpha3Code(String alpha3Code) {
            this.alpha3Code = alpha3Code;
            return this;
        }

        /** ISO 3166-1 数字码。 */
        public CountryBuilder numericCode(String numericCode) {
            this.numericCode = numericCode;
            return this;
        }

        /** 平台区域代码。 */
        public CountryBuilder regionCode(String regionCode) {
            this.regionCode = regionCode;
            return this;
        }

        /** 国际区号。 */
        public CountryBuilder callingCode(String callingCode) {
            this.callingCode = callingCode;
            return this;
        }

        /** 法定主币种。 */
        public CountryBuilder defaultCurrencyCode(String defaultCurrencyCode) {
            this.defaultCurrencyCode = defaultCurrencyCode;
            return this;
        }

        /** 状态。 */
        public CountryBuilder status(ReferenceDataStatus status) {
            return statusCode(status == null ? null : status.getCode());
        }

        /** 原始状态 Code；允许未知值负例。 */
        public CountryBuilder statusCode(String status) {
            this.status = status;
            return this;
        }

        /** 新的响应 DTO；名称与版本取固定值。 */
        public ReferenceCountryResponseBody build() {
            ReferenceCountryResponseBody body = new ReferenceCountryResponseBody();
            body.setCountryCode(code);
            body.setAlpha3Code(alpha3Code);
            body.setNumericCode(numericCode);
            body.setName("Test Country " + code);
            body.setRegionCode(regionCode);
            body.setCallingCode(callingCode);
            body.setDefaultCurrencyCode(defaultCurrencyCode);
            body.setStatus(status);
            body.setVersion(1);
            return body;
        }
    }

    /** 行政区响应 DTO。 */
    public static final class SubdivisionBuilder {
        private String code;

        private String countryCode;

        private String parentCode;

        private String category;

        private String status;

        private SubdivisionBuilder() {
        }

        /** AE-DU：国家 AE、类别 EMIRATE、ACTIVE。 */
        public static SubdivisionBuilder template() {
            return new SubdivisionBuilder().code("AE-DU").countryCode("AE").category("EMIRATE")
                .status(ReferenceDataStatus.ACTIVE);
        }

        /** ISO 3166-2 代码。 */
        public SubdivisionBuilder code(String code) {
            this.code = code;
            return this;
        }

        /** 所属国家。 */
        public SubdivisionBuilder countryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        /** 上级行政区。 */
        public SubdivisionBuilder parentCode(String parentCode) {
            this.parentCode = parentCode;
            return this;
        }

        /** 行政区类别。 */
        public SubdivisionBuilder category(String category) {
            this.category = category;
            return this;
        }

        /** 状态。 */
        public SubdivisionBuilder status(ReferenceDataStatus status) {
            return statusCode(status == null ? null : status.getCode());
        }

        /** 原始状态 Code；允许未知值负例。 */
        public SubdivisionBuilder statusCode(String status) {
            this.status = status;
            return this;
        }

        /** 新的响应 DTO；名称与版本取固定值。 */
        public CountrySubdivisionResponseBody build() {
            CountrySubdivisionResponseBody body = new CountrySubdivisionResponseBody();
            body.setSubdivisionCode(code);
            body.setCountryCode(countryCode);
            body.setParentSubdivisionCode(parentCode);
            body.setCategory(category);
            body.setName("Test Subdivision " + code);
            body.setStatus(status);
            body.setVersion(1);
            return body;
        }
    }

    /** 行业响应 DTO。 */
    public static final class IndustryBuilder {
        private String code;

        private String parentCode;

        private Integer level;

        private String defaultMccCode;

        private String status;

        private IndustryBuilder() {
        }

        /** RETAIL 大类：层级 1、无上级、无建议 MCC、ACTIVE。 */
        public static IndustryBuilder template() {
            return new IndustryBuilder().code("RETAIL").level(1).status(ReferenceDataStatus.ACTIVE);
        }

        /** 行业编码。 */
        public IndustryBuilder code(String code) {
            this.code = code;
            return this;
        }

        /** 上级大类。 */
        public IndustryBuilder parentCode(String parentCode) {
            this.parentCode = parentCode;
            return this;
        }

        /** 层级。 */
        public IndustryBuilder level(Integer level) {
            this.level = level;
            return this;
        }

        /** 建议 MCC。 */
        public IndustryBuilder defaultMccCode(String defaultMccCode) {
            this.defaultMccCode = defaultMccCode;
            return this;
        }

        /** 状态。 */
        public IndustryBuilder status(ReferenceDataStatus status) {
            return statusCode(status == null ? null : status.getCode());
        }

        /** 原始状态 Code；允许未知值负例。 */
        public IndustryBuilder statusCode(String status) {
            this.status = status;
            return this;
        }

        /** 新的响应 DTO；名称、排序与版本取固定值。 */
        public IndustryResponseBody build() {
            IndustryResponseBody body = new IndustryResponseBody();
            body.setIndustryCode(code);
            body.setParentIndustryCode(parentCode);
            body.setLevel(level);
            body.setName("Test Industry " + code);
            body.setDefaultMccCode(defaultMccCode);
            body.setSortNo(10);
            body.setStatus(status);
            body.setVersion(1);
            return body;
        }
    }

    /** MCC 响应 DTO。 */
    public static final class MccBuilder {
        private String code;

        private Boolean networkHighRisk;

        private String status;

        private MccBuilder() {
        }

        /** 5734：非高风险、ACTIVE。 */
        public static MccBuilder template() {
            return new MccBuilder().code("5734").networkHighRisk(false).status(ReferenceDataStatus.ACTIVE);
        }

        /** 四位 MCC。 */
        public MccBuilder code(String code) {
            this.code = code;
            return this;
        }

        /** 卡组织高风险标记；允许 null 负例。 */
        public MccBuilder networkHighRisk(Boolean networkHighRisk) {
            this.networkHighRisk = networkHighRisk;
            return this;
        }

        /** 状态。 */
        public MccBuilder status(ReferenceDataStatus status) {
            return statusCode(status == null ? null : status.getCode());
        }

        /** 原始状态 Code；允许未知值负例。 */
        public MccBuilder statusCode(String status) {
            this.status = status;
            return this;
        }

        /** 新的响应 DTO；名称、描述与版本取固定值。 */
        public MccResponseBody build() {
            MccResponseBody body = new MccResponseBody();
            body.setMccCode(code);
            body.setName("Test MCC " + code);
            body.setDescription("Test MCC " + code);
            body.setNetworkHighRisk(networkHighRisk);
            body.setStatus(status);
            body.setVersion(1);
            return body;
        }
    }
}
