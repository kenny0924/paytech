package com.paytech.channel.connector.service.processor;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.domainservice.validator.channel.MidCapabilityDetailValidator;
import com.paytech.channel.connector.service.processor.request.MidCapabilityDetailItem;
import com.paytech.foundation.id.IdGenerator;

/** 能力组创建与整组替换共用：把请求明细按能力目录转为待写入对象并完成校验。 */
final class MidCapabilityDetailBuilder {

    /** 工具类不实例化。 */
    private MidCapabilityDetailBuilder() {
    }

    /**
     * 构建并校验同一能力组的全部明细，按能力代码升序
     *
     * @param midCapabilityId 所属能力组 ID
     * @param items 请求明细
     * @param catalog 能力目录；值类型以目录为准，未注册代码在校验时拒绝
     * @param ids 主键生成器
     * @param now 本次写入时间
     * @return 已校验的待写入明细
     */
    static List<MidCapabilityDetail> build(long midCapabilityId, List<MidCapabilityDetailItem> items,
        Map<String, CapabilityDefinition> catalog, IdGenerator ids, Instant now) {
        List<MidCapabilityDetail> details = items.stream()
            .map(item -> detail(midCapabilityId, item, catalog.get(item.getCapabilityCode()), ids, now))
            .sorted(Comparator.comparing(MidCapabilityDetail::getCapabilityCode)).toList();
        MidCapabilityDetailValidator.checkDetails(details, catalog);
        return details;
    }

    /** 单项明细；BOOLEAN 以 0/1 存储。 */
    static MidCapabilityDetail detail(long midCapabilityId, MidCapabilityDetailItem item,
        CapabilityDefinition definition, IdGenerator ids, Instant now) {
        MidCapabilityDetail detail = new MidCapabilityDetail();
        detail.setMidCapabilityDetailId(ids.nextId());
        detail.setMidCapabilityId(midCapabilityId);
        detail.setCapabilityCode(item.getCapabilityCode());
        detail.setValueType(definition == null ? null : definition.getValueType());
        detail.setBooleanValue(item.getBooleanValue() == null ? null : item.getBooleanValue() ? 1 : 0);
        detail.setDecimalValue(item.getDecimalValue());
        detail.setCodeValue(item.getCodeValue());
        detail.setCreateTime(now);
        detail.setUpdateTime(now);
        return detail;
    }
}
