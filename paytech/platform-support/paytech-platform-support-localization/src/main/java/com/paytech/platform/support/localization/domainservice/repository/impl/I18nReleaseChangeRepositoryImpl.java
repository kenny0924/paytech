package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseChange;
import com.paytech.platform.support.localization.core.dal.mapper.I18nReleaseChangeMapper;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseChangeRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的发布文案变化仓储。 */
@Repository
@AllArgsConstructor
public class I18nReleaseChangeRepositoryImpl implements I18nReleaseChangeRepository {

    /**
     * 每条插入语句的行数上限：控制语句大小；主键在每批写入前才分配，单批远低于 Snowflake 单毫秒 4096 个序列的上限，
     * 批与批之间隔着一次插入，首个发布的全量基线（上万行）也不会耗尽同一毫秒的序列。
     */
    static final int BATCH_SIZE = 1000;

    private final IdGenerator ids;
    private final I18nReleaseChangeMapper mapper;

    /** 每批最多 {@value #BATCH_SIZE} 行：先分配本批主键再写入，逐批核对写入行数。 */
    @Override
    public void insertAll(List<I18nReleaseChange> changes) {
        for (int from = 0; from < changes.size(); from += BATCH_SIZE) {
            List<I18nReleaseChange> batch = changes.subList(from, Math.min(from + BATCH_SIZE, changes.size()));
            batch.forEach(change -> change.setChangeId(ids.nextId()));
            RowChecks.requireInserted(mapper.insertBatch(batch), batch.size(), "发布文案变化");
        }
    }

    /** 按语言、key 组装还原结果。 */
    @Override
    public Map<String, Map<String, String>> findContent(long applicationId, int releaseNo) {
        Map<String, Map<String, String>> content = new TreeMap<>();
        for (I18nReleaseChange row : mapper.selectContent(applicationId, releaseNo)) {
            content.computeIfAbsent(row.getLocaleCode(), ignored -> new TreeMap<>()).put(row.getKeyCode(),
                row.getText());
        }
        return content;
    }
}
