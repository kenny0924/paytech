package com.paytech.compliance.domain;

import java.time.Instant;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次人工检查结论录入。 */
@Getter
@AllArgsConstructor
public class CheckResultContext {
    /** 命令公共事实。 */
    private final ReviewCommandContext command;
    /** 被录入的检查，读取时版本。 */
    private final CaseCheck check;
    /** PASS 或 FAIL。 */
    private final String outcome;
    /** 结论原因码。 */
    private final String reasonCode;
    /** 名单版本或查询编号。 */
    private final String evidenceRef;
    /** 内部备注。 */
    private final String note;
    /** 结论有效截止；模板未设有效天数为空。 */
    private final Instant validUntil;
}
