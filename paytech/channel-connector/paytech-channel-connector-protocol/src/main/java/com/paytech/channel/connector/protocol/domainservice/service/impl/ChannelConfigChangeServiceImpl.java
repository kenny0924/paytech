package com.paytech.channel.connector.protocol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 变更记录在领域对象与持久化对象之间逐字段转换后读写；不开启事务。 */
@Service
@AllArgsConstructor
public class ChannelConfigChangeServiceImpl implements ChannelConfigChangeService {
    private final ChannelConfigChangeRepository channelConfigChangeRepository;

    /** 转为持久化对象后写入；行数检查由仓储完成。 */
    @Override
    public void record(ChannelConfigChangeRecord record) {
        channelConfigChangeRepository.insert(toRow(record));
    }

    /** 分页读取并转为领域对象，保留页码、条数与总数。 */
    @Override
    public PagingResponseBody<ChannelConfigChangeRecord> listChanges(ChangeTargetType targetType, long targetId,
        PagingRequestBody paging) {
        return channelConfigChangeRepository.findPage(targetType, targetId, paging).map(this::toRecord);
    }

    /** 领域对象到持久化对象。 */
    ChannelConfigChange toRow(ChannelConfigChangeRecord record) {
        ChannelConfigChange row = new ChannelConfigChange();
        row.setId(record.getId());
        row.setTargetType(record.getTargetType());
        row.setTargetId(record.getTargetId());
        row.setChangeType(record.getChangeType());
        row.setFromStatus(record.getFromStatus());
        row.setToStatus(record.getToStatus());
        row.setOperatorId(record.getOperatorId());
        row.setReason(record.getReason());
        row.setChangeDetail(record.getChangeDetail());
        row.setCreateTime(record.getCreateTime());
        return row;
    }

    /** 持久化对象到领域对象。 */
    ChannelConfigChangeRecord toRecord(ChannelConfigChange row) {
        ChannelConfigChangeRecord record = new ChannelConfigChangeRecord();
        record.setId(row.getId());
        record.setTargetType(row.getTargetType());
        record.setTargetId(row.getTargetId());
        record.setChangeType(row.getChangeType());
        record.setFromStatus(row.getFromStatus());
        record.setToStatus(row.getToStatus());
        record.setOperatorId(row.getOperatorId());
        record.setReason(row.getReason());
        record.setChangeDetail(row.getChangeDetail());
        record.setCreateTime(row.getCreateTime());
        return record;
    }
}
