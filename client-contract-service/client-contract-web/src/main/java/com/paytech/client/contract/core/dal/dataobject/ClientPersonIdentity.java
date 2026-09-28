package com.paytech.client.contract.core.dal.dataobject;
import java.time.Instant;
/** 查询已在申请可见范围内过滤；共享引用独立于资料本身的批准标记。 */
public record ClientPersonIdentity(long partyId,long profileId,Long draftApplicationId,Instant approvedTime,boolean shared) {}
