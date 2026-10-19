package com.paytech.commercial.domain;

import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;

/**
 * 方案与版本的组合结果：方案行、版本行（创建方案时为空）与方案 BCR 根当前版本
 * <li>写入命令由 Context 构造，版本详情查询由 Service 读取后构造。</li>
 */
public class CommercialTermVersionView {
    private final CommercialTermPlan plan;

    private final CommercialTermVersion version;

    private final long changeVersion;

    /**
     * 组合方案、版本与根版本。
     *
     * @param plan 方案
     * @param version 版本，创建方案时为空
     * @param changeVersion 方案 BCR 根当前版本
     */
    public CommercialTermVersionView(CommercialTermPlan plan, CommercialTermVersion version, long changeVersion) {
        this.plan = plan;
        this.version = version;
        this.changeVersion = changeVersion;
    }

    /** 方案。 */
    public CommercialTermPlan plan() {
        return plan;
    }

    /** 版本，可空。 */
    public CommercialTermVersion version() {
        return version;
    }

    /** 方案 BCR 根当前版本。 */
    public long changeVersion() {
        return changeVersion;
    }
}
