package com.paytech.platform.support.api.clientapicredential.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 可授予的 Scope 目录。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientApiScopesResponseBody {

    /** 按编码排序的 Scope。 */
    private List<Item> items;

    /** 单个 Scope。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /** Scope 编码。 */
        private String scopeCode;

        /** 展示名称。 */
        private String displayName;

        /** 能力说明；可为空。 */
        private String description;
    }
}
