package com.paytech.channel.connector.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本方案及其全部版本摘要的组合查询结果。 */
@Getter
@Setter
public class ChannelCostPlanView {
    /** 方案代码。 */
    private String planCode;

    /** Payment Channel 主键。 */
    private Long channelId;

    /** 方案状态。 */
    private ChannelCostPlanStatus status;

    /**
     * 当前版本号
     * <li>尚无当前版本时为空。</li>
     */
    private Integer currentCostVer;

    /** 全部版本摘要，按版本号降序。 */
    private List<Version> versions;

    /** 由方案与其版本（版本号降序）组装。 */
    public static ChannelCostPlanView of(ChannelCostPlan plan, List<ChannelCostPlanVer> versions) {
        ChannelCostPlanView view = new ChannelCostPlanView();
        view.setPlanCode(plan.getPlanCode());
        view.setChannelId(plan.getChannelId());
        view.setStatus(plan.getStatus());
        view.setCurrentCostVer(versions.stream().filter(ver -> ver.getId().equals(plan.getCurrentVerId()))
            .map(ChannelCostPlanVer::getCostVer).findFirst().orElse(null));
        view.setVersions(versions.stream().map(Version::of).toList());
        return view;
    }

    /** 版本摘要。 */
    @Getter
    @Setter
    public static class Version {
        /** 版本号。 */
        private Integer costVer;

        /** 版本状态。 */
        private ChannelCostVersionStatus status;

        /** 开始参与命中的时间。 */
        private Instant effectiveFrom;

        /**
         * 停止参与命中的时间
         * <li>长期有效时为空。</li>
         */
        private Instant effectiveTo;

        /** 版本内容摘要。 */
        private String contentHash;

        /** 编制人。 */
        private Long makerId;

        /**
         * 复核人
         * <li>未复核时为空。</li>
         */
        private Long checkerId;

        /** 由版本组装摘要。 */
        static Version of(ChannelCostPlanVer ver) {
            Version version = new Version();
            version.setCostVer(ver.getCostVer());
            version.setStatus(ver.getStatus());
            version.setEffectiveFrom(ver.getEffectiveFrom());
            version.setEffectiveTo(ver.getEffectiveTo());
            version.setContentHash(ver.getContentHash());
            version.setMakerId(ver.getMakerId());
            version.setCheckerId(ver.getCheckerId());
            return version;
        }
    }
}
