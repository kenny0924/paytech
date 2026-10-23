package com.paytech.platform.support.localization.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;

/** t_i18n_release_bundle 仓储；语言包创建后不修改。 */
public interface I18nReleaseBundleRepository {

    /** 批量插入语言包。 */
    void insertAll(List<I18nReleaseBundle> bundles);

    /** 发布的全部语言包，按语言、命名空间排列。 */
    List<I18nReleaseBundle> findByRelease(long releaseId);
}
