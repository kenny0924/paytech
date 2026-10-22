package com.paytech.card.api.binlibrary.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 一次激活或回切记录 */
@Getter
@Setter
public class BinLibraryActivationResponseBody {
    /** 激活类型，{@code BinLibraryActivationType} Code：ACTIVATE、ROLLBACK */
    private String activationType;

    /** 本次生效的版本号 */
    private int versionNo;

    /** 被替换（变为 RETIRED）的原生效版本号；此前没有生效版本时为 null */
    private Integer previousVersionNo;

    /** 操作人 Operations Account ID，十进制字符串；初始化种子激活为 null */
    private String operatorAccountId;

    /** 激活时间（UTC） */
    private Instant activatedAt;
}
