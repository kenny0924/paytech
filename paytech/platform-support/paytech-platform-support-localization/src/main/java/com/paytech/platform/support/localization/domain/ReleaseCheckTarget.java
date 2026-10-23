package com.paytech.platform.support.localization.domain;

import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 上线检查的对象：发布及其全部语言包。 */
@Getter
@AllArgsConstructor
public class ReleaseCheckTarget {

    /** 发布。 */
    private final I18nRelease release;

    /** 语言包，按语言、命名空间排列。 */
    private final List<I18nReleaseBundle> bundles;
}
