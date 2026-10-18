package com.paytech.operations.console.bff.domain.enums;

import java.util.Arrays;

import com.paytech.foundation.common.enums.CodeEnum;

/** API Scope 详情页一次读取的引用类型；Code 是浏览器查询参数 referenceKind 的合同值。 */
public enum ApiScopeReferenceKind implements CodeEnum {
    /** 只读取 Scope 本身，不读取引用分页。 */
    NONE("NONE"),

    /** 读取引用该 Scope 的 Gateway 路由。 */
    ROUTE("ROUTE"),

    /** 读取绑定该 Scope 的 API 凭证。 */
    CREDENTIAL("CREDENTIAL");

    private final String code;

    ApiScopeReferenceKind(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按 Code 解析引用类型。
     *
     * @param code 查询参数值；缺省由调用方先补为 {@link #NONE}
     * @return 对应枚举；未知 Code 返回 null，由调用方按参数错误拒绝
     */
    public static ApiScopeReferenceKind fromCode(String code) {
        return Arrays.stream(values()).filter(kind -> kind.code.equals(code)).findFirst().orElse(null);
    }
}
