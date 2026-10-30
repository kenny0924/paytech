package com.paytech.channel.connector.domainservice.service;

import com.paytech.channel.connector.domain.OperatingEntityMids;

/** 某 OE 渠道账号、MID 与能力的只读加载；只读本模块仓储，不创建事务，不调用其他 Service 或外部依赖。 */
public interface OperatingEntityMidService {

    /**
     * 读取 OE 的渠道账号、这些账号下的全部 MID 及其能力条件组与明细
     * <li>多表读取须由调用方放在同一个只读一致性快照（可重复读事务）内，防止账号换绑或能力主从更新被分次读取后混用。</li>
     * <li>不读渠道、不做可用筛选：调用方读取渠道身份后用 {@link OperatingEntityMids#usable} 筛选。</li>
     *
     * @param entityNo OE 编号，区分大小写
     * @return 读取的事实；没有账号或 MID 时列表为空
     */
    OperatingEntityMids loadMids(String entityNo);
}
