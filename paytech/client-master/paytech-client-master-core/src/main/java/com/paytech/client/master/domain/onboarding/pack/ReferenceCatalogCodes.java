package com.paytech.client.master.domain.onboarding.pack;

/**
 * 引用 Platform 参考数据目录的保留目录码（需求 R5、技术方案 S-04）
 * <li>以 REF_ 开头的 catalog_code 只出现在 Schema 字段与地址组件上，表示取值来自 Platform 参考数据；
 * 不从 t_catalog_item 加载，t_catalog_item 也不得出现这些码（守卫测试）。</li>
 * <li>form-schema 的 catalogs 不含 REF_ 目录，由 client-console-bff 向 Platform 取选项补全；提交时向 Platform 校验存在且 ACTIVE。</li>
 */
public final class ReferenceCatalogCodes {
    /** 保留前缀。 */
    public static final String PREFIX = "REF_";

    /** Platform 币种（ISO 4217）。 */
    public static final String REF_CURRENCY = "REF_CURRENCY";

    /** Platform 一级行政区（ISO 3166-2，如 AE-DU）；地址组件使用时须与地址国家一致。 */
    public static final String REF_COUNTRY_SUBDIVISION = "REF_COUNTRY_SUBDIVISION";

    /** 仅常量。 */
    private ReferenceCatalogCodes() {
    }

    /** 是否为引用 Platform 参考数据的保留目录码。 */
    public static boolean isReference(String catalogCode) {
        return catalogCode != null && catalogCode.startsWith(PREFIX);
    }
}
