package com.paytech.client.console.bff.service.model.response;

import java.util.List;
import java.util.Map;

import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;

import lombok.Getter;
import lombok.Setter;

/**
 * 入驻表单页面模型（D-04）：client-master 的采集 Schema 加 Platform 参考数据补全
 * <li>分区、地址格式、标识类型、关系、材料与声明沿用 client-master 合同类型原样返回。</li>
 * <li>{@code catalogs} 中 client-master 自有目录保持 {@code code + labelKey}；{@code REF_} 开头的目录由 BFF 填入 Platform 的 ACTIVE 条目。</li>
 * <li>只返回编码、英文名与图标地址，不返回本地化名称；页面按界面语言显示（国家、币种用 Intl，行政区用 refdata 文案）。</li>
 */
@Getter
@Setter
public class ClientFormSchemaResponseBody {
    /** Schema 版本 ID，十进制字符串。 */
    private String schemaId;

    /** Schema 版本号。 */
    private int versionNo;

    /** 辖区代码。 */
    private String jurisdictionCode;

    /** 主主体类型 INDIVIDUAL 或 ORGANIZATION。 */
    private String partyType;

    /** Schema 的组织类型键；* 为该辖区该主体类型的默认 Schema。 */
    private String organizationTypeCode;

    /** 分区与字段，按展示顺序。 */
    private List<FormSchemaResponseBody.Section> sections;

    /** 目录代码到可选项；含 client-master 目录与补全后的参考数据目录（REF_CURRENCY、REF_COUNTRY_SUBDIVISION）。 */
    private Map<String, List<CatalogItem>> catalogs;

    /**
     * 可选国家与地区
     * <li>Platform 的 ACTIVE 国家，按国家代码升序；国家类字段与地址国家都从这里取选项。</li>
     */
    private List<Country> countries;

    /** 当前主体类型可选的组织类型。 */
    private List<FormSchemaResponseBody.CatalogOption> organizationTypes;

    /** Schema 引用的标识类型规则。 */
    private List<FormSchemaResponseBody.IdentifierTypeOption> identifierTypes;

    /** 辖区本地地址格式。 */
    private FormSchemaResponseBody.AddressFormat addressFormat;

    /** 境外地址使用的通用格式 GENERIC。 */
    private FormSchemaResponseBody.AddressFormat fallbackAddressFormat;

    /** 可用的关系类型。 */
    private List<FormSchemaResponseBody.RelationTypeOption> relationTypes;

    /** 组织关系人门槛；个人申请为空。 */
    private FormSchemaResponseBody.RelationRules relationRules;

    /** 材料槽。 */
    private List<FormSchemaResponseBody.Material> materials;

    /** Schema 要求的声明。 */
    private List<FormSchemaResponseBody.Declaration> declarations;

    /** 目录项：client-master 目录只有代码与文案键；参考数据目录带英文名、图标与所属国家。 */
    @Getter
    @Setter
    public static class CatalogItem {
        /** 代码；币种为 ISO 4217，行政区为 ISO 3166-2。 */
        private String code;

        /** 展示文案键；参考数据目录为空。 */
        private String labelKey;

        /** 英文名称；只有参考数据目录有值，作为翻译缺失时的回退。 */
        private String name;

        /** 图标公开地址；没有图标时为空，页面显示占位。 */
        private String logoPublicUrl;

        /** 所属国家代码；只有行政区有值，页面按地址国家筛选。 */
        private String countryCode;
    }

    /** 国家与地区选项。 */
    @Getter
    @Setter
    public static class Country {
        /** ISO 3166-1 alpha-2 国家代码。 */
        private String code;

        /** 英文名称，Intl 无法给出名称时的回退。 */
        private String name;

        /** 国旗公开地址；没有国旗时为空，页面显示占位。 */
        private String logoPublicUrl;

        /** 国际电话区号，不含加号；没有区号时为空。 */
        private String callingCode;
    }
}
