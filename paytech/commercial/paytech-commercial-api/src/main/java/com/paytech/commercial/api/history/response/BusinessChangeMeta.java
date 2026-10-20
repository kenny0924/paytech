package com.paytech.commercial.api.history.response;

import lombok.Getter;
import lombok.Setter;

/** 快照触发信息（快照 _meta 的对外子集，不含完整性哈希）。 */
@Getter
@Setter
public class BusinessChangeMeta {
    /**
     * 触发类型
     * <li>COMMAND：运营写命令；SYSTEM：系统命令（定时激活等）。</li>
     */
    private String triggerType;

    /** 同一命令所有快照共享的触发标识（十进制） */
    private String triggerId;

    /** 业务原因代码，例如 AGREEMENT_ACTIVATED、TERM_VERSION_SUBMITTED */
    private String reasonCode;
}
