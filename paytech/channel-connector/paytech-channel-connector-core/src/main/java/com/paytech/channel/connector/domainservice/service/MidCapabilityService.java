package com.paytech.channel.connector.domainservice.service;

import java.util.SortedMap;

import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domain.MidCapabilityWriteContext;

/**
 * MID 能力组的本地保存与查询
 * <li>每个写方法在一个事务内完成能力组 / 明细写入与变更记录插入；只操作本服务数据库。</li>
 */
public interface MidCapabilityService {

    /** 已注册的能力目录，按能力代码升序。 */
    SortedMap<String, CapabilityDefinition> capabilityDefinitions();

    /** 读取能力组；不存在时业务拒绝。 */
    MidCapability loadCapability(long midCapabilityId);

    /** 能力组与其明细；不存在时业务拒绝。 */
    MidCapabilityView getCapability(long midCapabilityId);

    /** 一个事务内写入能力组、全部明细与 CREATE 变更记录；六维组合重复按并发冲突拒绝。 */
    void createCapability(MidCapabilityWriteContext context);

    /** 一个事务内删除原明细、写入上下文中的全部明细、刷新能力组修改时间，并写入 UPDATE 变更记录。 */
    void replaceDetails(MidCapabilityWriteContext context);

    /** 一个事务内以原状态为条件启停能力组，并写入 STATUS 变更记录。 */
    void changeCapabilityStatus(MidCapabilityWriteContext context, CapabilityStatus targetStatus);
}
