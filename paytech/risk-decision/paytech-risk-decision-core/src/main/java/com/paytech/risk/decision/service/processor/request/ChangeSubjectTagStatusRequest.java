package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.enums.DictionaryStatus;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字典启停的内部请求，公共模板在获取锁前校验。 */
@Getter
@AllArgsConstructor
public class ChangeSubjectTagStatusRequest {

    /**
     * 永久字典编码。
     * <li>格式合法后才构造资源锁。</li>
     */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String tagCode;

    /** 调用方期望的正整数修订号。 */
    @Positive
    private final long expectedVersion;

    /**
     * 本次目标字典资格。
     * <li>ACTIVE 或 DISABLED。</li>
     */
    @NotNull
    private final DictionaryStatus status;

    /** 可信操作者 Account ID。 */
    @Positive
    private final long actorAccountId;
}
