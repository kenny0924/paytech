package com.paytech.operations.console.bff.service.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 参考数据页面模型：国家、行政区、行业、MCC 与币种的只读展示，以及审核分类页的行业树
 * <li>Platform 为唯一 Owner；字段与 Platform 参考数据合同同名，BFF 只核对编码与状态后投影，不返回本地化名称。</li>
 * <li>name 为英文基础名称；页面按 refdata 文案或 Intl 显示本地化名称，取不到时回退 name，再回退编码。</li>
 *
 * @author Claude
 */
public final class ReferenceDataModels {

    /** 只承载类型，禁止创建实例。 */
    private ReferenceDataModels() {
    }

    /** 一个国家或地区（ISO 3166-1）。 */
    @Getter
    @Setter
    public static class CountryResponseBody {
        /** 两位大写国家代码。 */
        private String countryCode;

        /** 三位字母代码。 */
        private String alpha3Code;

        /** 三位数字代码。 */
        private String numericCode;

        /** 英文短名。 */
        private String name;

        /** 平台区域代码。 */
        private String regionCode;

        /** 国际区号（含 +），没有时为空。 */
        private String callingCode;

        /** 法定货币代码，没有时为空。 */
        private String defaultCurrencyCode;

        /** 国旗文件地址 /files/v1/{fileId}，没有时为空。 */
        private String logoFileAddress;

        /** 国旗公开地址；文件不是公开文件时为空。 */
        private String logoPublicUrl;

        /** 状态：DRAFT、ACTIVE 或 INACTIVE。 */
        private String status;

        /** 版本。 */
        private Integer version;
    }

    /** 国家列表，按国家代码升序。 */
    @Getter
    @Setter
    public static class CountryListResponseBody {
        /** 国家。 */
        private List<CountryResponseBody> items;
    }

    /** 一个一级行政区（ISO 3166-2）。 */
    @Getter
    @Setter
    public static class CountrySubdivisionResponseBody {
        /** 行政区代码，例如 AE-DU。 */
        private String subdivisionCode;

        /** 所属国家代码。 */
        private String countryCode;

        /** 上级行政区代码，没有时为空。 */
        private String parentSubdivisionCode;

        /** ISO 类别，例如 EMIRATE、GOVERNORATE。 */
        private String category;

        /** 英文名。 */
        private String name;

        /** 本地官方名，没有时为空。 */
        private String localName;

        /** 状态。 */
        private String status;

        /** 版本。 */
        private Integer version;
    }

    /** 行政区列表，按行政区代码升序。 */
    @Getter
    @Setter
    public static class CountrySubdivisionListResponseBody {
        /** 行政区。 */
        private List<CountrySubdivisionResponseBody> items;
    }

    /** 一个行业（大类或细类）。 */
    @Getter
    @Setter
    public static class IndustryResponseBody {
        /** 行业编码。 */
        private String industryCode;

        /** 上级大类编码；大类为空。 */
        private String parentIndustryCode;

        /** 层级：1 为大类，2 为细类。 */
        private Integer level;

        /** 英文名。 */
        private String name;

        /** 英文选择说明，没有时为空。 */
        private String description;

        /** 细类的建议 MCC；大类为空。 */
        private String defaultMccCode;

        /** 图标文件地址，没有时为空。 */
        private String logoFileAddress;

        /** 图标公开地址，没有时为空。 */
        private String logoPublicUrl;

        /** 排序号。 */
        private Integer sortNo;

        /** 状态。 */
        private String status;

        /** 版本。 */
        private Integer version;
    }

    /** 行业列表：每个大类之后紧跟其细类。 */
    @Getter
    @Setter
    public static class IndustryListResponseBody {
        /** 行业。 */
        private List<IndustryResponseBody> items;
    }

    /** 审核分类页的行业大类，带其细类。 */
    @Getter
    @Setter
    public static class IndustryCategoryResponseBody {
        /** 大类编码。 */
        private String industryCode;

        /** 英文名。 */
        private String name;

        /** 图标公开地址，没有时为空。 */
        private String logoPublicUrl;

        /** 细类，按排序号升序；没有细类时为空列表。 */
        private List<IndustryResponseBody> children;
    }

    /** 一个 MCC（ISO 18245）。 */
    @Getter
    @Setter
    public static class MccResponseBody {
        /** 四位数字编码。 */
        private String mccCode;

        /** 英文名。 */
        private String name;

        /** 英文说明，没有时为空。 */
        private String description;

        /** 是否为卡组织公布的高风险类目。 */
        private Boolean networkHighRisk;

        /** 状态。 */
        private String status;

        /** 版本。 */
        private Integer version;
    }
}
