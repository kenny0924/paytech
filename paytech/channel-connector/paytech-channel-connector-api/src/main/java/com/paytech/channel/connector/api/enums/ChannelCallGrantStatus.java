package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服务渠道调用授权状态；授权只停用、不删除。 */
@Getter
@AllArgsConstructor
public enum ChannelCallGrantStatus implements CodeEnum {
    /** 生效：调用方可经通用执行入口调用该渠道的该标准 API。 */
    ACTIVE("ACTIVE"),

    /** 停用：调用被拒绝，可再启用。 */
    DISABLED("DISABLED");

    /** 数据库、HTTP 与业务比较使用的稳定代码。 */
    private final String code;
}
