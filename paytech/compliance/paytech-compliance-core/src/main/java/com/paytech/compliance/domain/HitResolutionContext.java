package com.paytech.compliance.domain;

import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次命中处置：追加的处置记录以及命中是否随之关闭。 */
@Getter
@AllArgsConstructor
public class HitResolutionContext {
    /** 命令公共事实。 */
    private final ReviewCommandContext command;
    /** 被处置的命中，读取时版本。 */
    private final ScreeningHit hit;
    /** 新处置记录。 */
    private final ScreeningResolution resolution;
    /** 处置是否关闭命中。 */
    private final boolean closesHit;
}
