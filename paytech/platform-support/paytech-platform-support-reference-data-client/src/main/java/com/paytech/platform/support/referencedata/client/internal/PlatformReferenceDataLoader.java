package com.paytech.platform.support.referencedata.client.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;
import com.paytech.platform.support.referencedata.client.ReferenceCountry;
import com.paytech.platform.support.referencedata.client.ReferenceCurrency;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceIndustry;
import com.paytech.platform.support.referencedata.client.ReferenceItem;
import com.paytech.platform.support.referencedata.client.ReferenceMcc;
import com.paytech.platform.support.referencedata.client.ReferenceSubdivision;

/**
 * 从 Platform {@code /platform/v1/reference-data/*} 全量拉取一个种类，逐条校验并转换为值对象。
 * <li>任一条不合法（编码为空或格式非法、编码重复、状态或舍入方式无法识别、币种小数位越界、MCC 翻页总数不符）时整个种类本次失败，
 * 避免把半份数据装入快照；失败抛 UPSTREAM_INVALID_RESPONSE 系统异常，由刷新入口记录并保留旧快照。</li>
 * <li>只读取本客户端定义的字段，Platform 追加字段由宽松 JSON 忽略。</li>
 */
public final class PlatformReferenceDataLoader {

    /** MCC 全量加载的每页条数（Platform 允许的最大值）。 */
    static final int MCC_PAGE_SIZE = 200;

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    private static final Pattern NUMERIC_CODE = Pattern.compile("[0-9]{3}");

    private static final Pattern SUBDIVISION_CODE = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,3}");

    private static final Pattern INDUSTRY_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Pattern MCC_CODE = Pattern.compile("[0-9]{4}");

    /** 开放集合的文本编码（区域、行政区类别）：大写字母、数字与下划线。 */
    private static final Pattern OPEN_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** 币种小数位上限。 */
    private static final int MAX_MINOR_UNIT = 8;

    private final ReferenceCurrencyQueryApi currencyApi;

    private final ReferenceCountryQueryApi countryApi;

    private final CountrySubdivisionQueryApi subdivisionApi;

    private final IndustryQueryApi industryApi;

    private final MccQueryApi mccApi;

    /**
     * 使用 Platform 五个只读查询接口代理
     *
     * @param currencyApi 币种
     * @param countryApi 国家
     * @param subdivisionApi 行政区
     * @param industryApi 行业
     * @param mccApi MCC
     */
    public PlatformReferenceDataLoader(ReferenceCurrencyQueryApi currencyApi, ReferenceCountryQueryApi countryApi,
        CountrySubdivisionQueryApi subdivisionApi, IndustryQueryApi industryApi, MccQueryApi mccApi) {
        this.currencyApi = currencyApi;
        this.countryApi = countryApi;
        this.subdivisionApi = subdivisionApi;
        this.industryApi = industryApi;
        this.mccApi = mccApi;
    }

    /**
     * 全量拉取并校验一个种类（发起 HTTP，只在刷新入口调用）
     *
     * @param kind 种类
     * @return 该种类全部条目，编码唯一
     */
    public List<? extends ReferenceItem> load(ReferenceDataKind kind) {
        return switch (kind) {
            case CURRENCY -> convert(kind, requireItems(kind, currencyItems()), this::toCurrency);
            case COUNTRY -> convert(kind, requireItems(kind, countryItems()), this::toCountry);
            case SUBDIVISION -> convert(kind, requireItems(kind, subdivisionItems()), this::toSubdivision);
            case INDUSTRY -> convert(kind, requireItems(kind, industryItems()), this::toIndustry);
            case MCC -> convert(kind, loadAllMccs(), this::toMcc);
        };
    }

    /** 全部状态的币种列表。 */
    private List<ReferenceCurrencyResponseBody> currencyItems() {
        var body = currencyApi.listCurrencies(null, null);
        return body == null ? null : body.getItems();
    }

    /** 全部状态的国家列表。 */
    private List<ReferenceCountryResponseBody> countryItems() {
        var body = countryApi.listCountries(null, null);
        return body == null ? null : body.getItems();
    }

    /** 全部国家、全部状态的行政区列表。 */
    private List<CountrySubdivisionResponseBody> subdivisionItems() {
        var body = subdivisionApi.listSubdivisions(null, null, null);
        return body == null ? null : body.getItems();
    }

    /** 两级、全部状态的行业列表。 */
    private List<IndustryResponseBody> industryItems() {
        var body = industryApi.listIndustries(null, null, null, null);
        return body == null ? null : body.getItems();
    }

    /**
     * 按每页 200 条翻到最后一页，并核对总数
     * <li>翻页过程中 total 变化、空页提前结束或最终条数不等于 total 时本次失败。</li>
     */
    List<MccResponseBody> loadAllMccs() {
        List<MccResponseBody> items = new ArrayList<>();
        long total = -1;
        for (int page = 1; ; page++) {
            PagingResponseBody<MccResponseBody> body = mccApi.searchMccs(null, null, null, page, MCC_PAGE_SIZE);
            if (body == null) {
                throw invalid(ReferenceDataKind.MCC, "第 " + page + " 页缺少响应体");
            }
            if (total >= 0 && body.total() != total) {
                throw invalid(ReferenceDataKind.MCC, "翻页过程中总数变化 " + total + " -> " + body.total());
            }
            total = body.total();
            items.addAll(body.items());
            if (body.items().size() < MCC_PAGE_SIZE || items.size() >= total) {
                break;
            }
        }
        if (items.size() != total) {
            throw invalid(ReferenceDataKind.MCC, "条数 " + items.size() + " 与总数 " + total + " 不一致");
        }
        return items;
    }

    /** 逐条转换并拒绝重复编码。 */
    private static <D, T extends ReferenceItem> List<T> convert(ReferenceDataKind kind, List<D> items,
        Function<D, T> converter) {
        Set<String> codes = new HashSet<>();
        List<T> converted = new ArrayList<>(items.size());
        for (D item : items) {
            if (item == null) {
                throw invalid(kind, "存在空条目");
            }
            T value = converter.apply(item);
            if (!codes.add(value.code())) {
                throw invalid(kind, "编码重复 " + value.code());
            }
            converted.add(value);
        }
        return converted;
    }

    /** 列表响应必须有 items。 */
    private static <D> List<D> requireItems(ReferenceDataKind kind, List<D> items) {
        if (items == null) {
            throw invalid(kind, "缺少 items");
        }
        return items;
    }

    /** 币种：代码、数字码、小数位 0..8、舍入方式与状态。 */
    ReferenceCurrency toCurrency(ReferenceCurrencyResponseBody item) {
        String code = requireCode(ReferenceDataKind.CURRENCY, item.getCurrencyCode(), CURRENCY_CODE);
        Integer minorUnit = item.getMinorUnit();
        if (minorUnit == null || minorUnit < 0 || minorUnit > MAX_MINOR_UNIT) {
            throw invalid(ReferenceDataKind.CURRENCY, code + " 小数位越界 " + minorUnit);
        }
        return new ReferenceCurrency(code, requireField(ReferenceDataKind.CURRENCY, code, item.getNumericCode(),
            NUMERIC_CODE), minorUnit, parse(ReferenceDataKind.CURRENCY, code, CurrencyRoundingMode.class,
            item.getRoundingMode()), status(ReferenceDataKind.CURRENCY, code, item.getStatus()));
    }

    /** 国家：alpha-2 / alpha-3 / 数字码、区域、可选法定币种与状态。 */
    ReferenceCountry toCountry(ReferenceCountryResponseBody item) {
        ReferenceDataKind kind = ReferenceDataKind.COUNTRY;
        String code = requireCode(kind, item.getCountryCode(), COUNTRY_CODE);
        return new ReferenceCountry(code, requireField(kind, code, item.getAlpha3Code(), CURRENCY_CODE),
            requireField(kind, code, item.getNumericCode(), NUMERIC_CODE),
            requireField(kind, code, item.getRegionCode(), OPEN_CODE), item.getCallingCode(),
            optionalField(kind, code, item.getDefaultCurrencyCode(), CURRENCY_CODE), status(kind, code, item.getStatus()));
    }

    /** 行政区：代码须以所属国家开头，可选上级行政区、类别与状态。 */
    ReferenceSubdivision toSubdivision(CountrySubdivisionResponseBody item) {
        ReferenceDataKind kind = ReferenceDataKind.SUBDIVISION;
        String code = requireCode(kind, item.getSubdivisionCode(), SUBDIVISION_CODE);
        String countryCode = requireField(kind, code, item.getCountryCode(), COUNTRY_CODE);
        if (!code.startsWith(countryCode + "-")) {
            throw invalid(kind, code + " 与所属国家 " + countryCode + " 不一致");
        }
        return new ReferenceSubdivision(code, countryCode,
            optionalField(kind, code, item.getParentSubdivisionCode(), SUBDIVISION_CODE),
            requireField(kind, code, item.getCategory(), OPEN_CODE), status(kind, code, item.getStatus()));
    }

    /** 行业：层级 1 / 2，细类必须有上级大类，可选建议 MCC 与状态。 */
    ReferenceIndustry toIndustry(IndustryResponseBody item) {
        ReferenceDataKind kind = ReferenceDataKind.INDUSTRY;
        String code = requireCode(kind, item.getIndustryCode(), INDUSTRY_CODE);
        Integer level = item.getLevel();
        String parentCode = optionalField(kind, code, item.getParentIndustryCode(), INDUSTRY_CODE);
        if (level == null || (level != 1 && level != 2) || (level == 2) != (parentCode != null)) {
            throw invalid(kind, code + " 层级 " + level + " 与上级 " + parentCode + " 不一致");
        }
        return new ReferenceIndustry(code, parentCode, level,
            optionalField(kind, code, item.getDefaultMccCode(), MCC_CODE), status(kind, code, item.getStatus()));
    }

    /** MCC：四位数字、高风险标记必填与状态。 */
    ReferenceMcc toMcc(MccResponseBody item) {
        ReferenceDataKind kind = ReferenceDataKind.MCC;
        String code = requireCode(kind, item.getMccCode(), MCC_CODE);
        if (item.getNetworkHighRisk() == null) {
            throw invalid(kind, code + " 缺少 networkHighRisk");
        }
        return new ReferenceMcc(code, item.getNetworkHighRisk(), status(kind, code, item.getStatus()));
    }

    /** 编码必填且符合格式。 */
    private static String requireCode(ReferenceDataKind kind, String code, Pattern format) {
        if (code == null || !format.matcher(code).matches()) {
            throw invalid(kind, "编码非法 " + code);
        }
        return code;
    }

    /** 必填字段符合格式。 */
    private static String requireField(ReferenceDataKind kind, String code, String value, Pattern format) {
        if (value == null || !format.matcher(value).matches()) {
            throw invalid(kind, code + " 字段格式非法 " + value);
        }
        return value;
    }

    /** 可空字段有值时符合格式。 */
    private static String optionalField(ReferenceDataKind kind, String code, String value, Pattern format) {
        return value == null ? null : requireField(kind, code, value, format);
    }

    /** 解析 DRAFT / ACTIVE / INACTIVE。 */
    private static ReferenceDataStatus status(ReferenceDataKind kind, String code, String value) {
        return parse(kind, code, ReferenceDataStatus.class, value);
    }

    /** 按稳定 Code 解析封闭枚举；未知或缺失使该种类本次失败。 */
    private static <E extends Enum<E> & CodeEnum> E parse(ReferenceDataKind kind, String code, Class<E> type,
        String value) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.getCode().equals(value)) {
                return candidate;
            }
        }
        throw invalid(kind, code + " 的 " + type.getSimpleName() + " 无法识别 " + value);
    }

    /** 响应不合法：该种类本次失败。 */
    private static ErrorException invalid(ReferenceDataKind kind, String detail) {
        return new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE,
            "Platform 参考数据响应无效: " + kind.getCode() + " " + detail);
    }
}
