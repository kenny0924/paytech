package com.paytech.platform.support.localization.domain;

import java.util.List;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次发布登记的上下文：生成语言包时的应用与修订号、新发布、已上传的语言包与上线任务。 */
@Getter
@AllArgsConstructor
public class PublishContext {

    /** 生成语言包前读取的应用；其文案修订号即发布依据的修订号。 */
    private final I18nApplication application;

    /** 待登记的新发布，状态 PENDING_CHECK。 */
    private final I18nRelease release;

    /** 已上传的语言包。 */
    private final List<I18nReleaseBundle> bundles;

    /** 与发布同事务登记的上线补偿任务。 */
    private final CompensationEventRequest activationEvent;
}
