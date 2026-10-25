package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * EntitySourceRelation SourceType 的数据库闭合值域。
 * <li>2026-10-24 取消 PLATFORM：平台自有主体迁到 PayFac Partner 名下，数据库中残留的 PLATFORM 读取时失败关闭。</li>
 */
public enum EntitySourceRelationSourceType implements CodeEnum {
    CLIENT("CLIENT"),

    PARTNER("PARTNER");
    private final String code;
    EntitySourceRelationSourceType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
