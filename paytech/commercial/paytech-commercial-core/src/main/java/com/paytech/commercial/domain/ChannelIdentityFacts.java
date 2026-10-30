package com.paytech.commercial.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ④ 渠道身份的外部事实：Client 主 MCC 与各 OE 的就绪结论（REQ-02 technique.md §10.2）
 * <li>Processor 在合同锁内、事务外查询 client-master 与 channel-connector 后一次构造；纯数据，不访问外部依赖。</li>
 * <li>client-master 失败时整体不可用；MCC 未确认时不查 channel-connector；某 OE 查询失败时只有该 OE 不可用。</li>
 */
public class ChannelIdentityFacts {
    /** 没有需要核对的开通项：不发起任何查询。 */
    public static final ChannelIdentityFacts NOT_QUERIED = new ChannelIdentityFacts(false, null, Map.of());

    /** client-master 经营分类查询失败。 */
    private final boolean classificationUnavailable;

    /** Client 已确认的主 MCC；未确认或没有主分类时为空。 */
    private final String mccCode;

    /** 查询成功的 OE → 组合 → 结论；查询失败的 OE 不出现。 */
    private final Map<String, Map<ChannelIdentityItem, ChannelIdentityReadiness>> readiness;

    /**
     * 构造事实
     *
     * @param classificationUnavailable client-master 查询失败
     * @param mccCode 已确认的主 MCC，可空
     * @param readiness 查询成功的 OE → 组合 → 结论
     */
    ChannelIdentityFacts(boolean classificationUnavailable, String mccCode,
        Map<String, Map<ChannelIdentityItem, ChannelIdentityReadiness>> readiness) {
        this.classificationUnavailable = classificationUnavailable;
        this.mccCode = mccCode;
        this.readiness = Map.copyOf(readiness);
    }

    /** client-master 查询失败。 */
    public static ChannelIdentityFacts classificationUnavailable() {
        return new ChannelIdentityFacts(true, null, Map.of());
    }

    /** Client 主 MCC 未确认：不再查询 channel-connector。 */
    public static ChannelIdentityFacts mccMissing() {
        return new ChannelIdentityFacts(false, null, Map.of());
    }

    /**
     * 已确认 MCC 与各 OE 的查询结论
     *
     * @param mccCode 已确认的主 MCC
     * @param readinessByEntity 查询成功的 OE → 结论列表；失败的 OE 不放入
     * @return 事实
     */
    public static ChannelIdentityFacts of(String mccCode, Map<String, List<ChannelIdentityReadiness>> readinessByEntity) {
        Map<String, Map<ChannelIdentityItem, ChannelIdentityReadiness>> indexed = new HashMap<>();
        readinessByEntity.forEach((entityNo, results) -> {
            Map<ChannelIdentityItem, ChannelIdentityReadiness> byItem = new HashMap<>();
            results.forEach(result -> byItem.put(result.item(), result));
            indexed.put(entityNo, Map.copyOf(byItem));
        });
        return new ChannelIdentityFacts(false, mccCode, indexed);
    }

    /** Client 已确认的主 MCC；未确认为 null。 */
    public String mccCode() {
        return mccCode;
    }

    /** 该 OE 的核对暂时无法完成：client-master 失败，或 MCC 已确认但该 OE 的就绪查询失败。 */
    public boolean unavailable(String entityNo) {
        return classificationUnavailable || mccCode != null && !readiness.containsKey(entityNo);
    }

    /**
     * 某 OE 下一组组合的结论
     *
     * @param entityNo OE 编号
     * @param paymentMethodCode 支付方式
     * @param currency 币种
     * @return 结论；该 OE 未查询或结论缺失时为 null
     */
    public ChannelIdentityReadiness readiness(String entityNo, String paymentMethodCode, String currency) {
        return readiness.getOrDefault(entityNo, Map.of()).get(new ChannelIdentityItem(paymentMethodCode, currency, mccCode));
    }
}
