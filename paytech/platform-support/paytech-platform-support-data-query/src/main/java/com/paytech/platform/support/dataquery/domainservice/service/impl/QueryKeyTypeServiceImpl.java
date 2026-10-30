package com.paytech.platform.support.dataquery.domainservice.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryKeyType;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.ChangeQueryKeyTypeStatusCommand;
import com.paytech.platform.support.dataquery.domain.CreateQueryKeyTypeCommand;
import com.paytech.platform.support.dataquery.domain.QueryJointConfig;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDefinition;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeReorder;
import com.paytech.platform.support.dataquery.domain.QueryTemplateJson;
import com.paytech.platform.support.dataquery.domain.ReorderQueryKeyTypesCommand;
import com.paytech.platform.support.dataquery.domain.UpdateQueryKeyTypeCommand;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryKeyTypeRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateVersionRepository;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;

/** 键类型的本地规则与保存：版本核对、排他锁、ACTIVE 模板引用检查与整体重排。 */
@Service
public class QueryKeyTypeServiceImpl implements QueryKeyTypeService {

    /** 拒绝消息中列出的引用模板编码上限。 */
    static final int MAX_LISTED_TEMPLATES = 5;

    /** 重排后相邻顺序号的间隔。 */
    static final int SORT_STEP = 10;

    private final QueryKeyTypeRepository keyTypes;
    private final QueryTemplateRepository templates;
    private final QueryTemplateVersionRepository versions;
    private final TransactionTemplate transaction;

    /**
     * 写入使用默认传播：加入调用方事务，单独调用时自建事务。
     *
     * @param keyTypes 键类型仓储
     * @param templates 模板仓储
     * @param versions 版本仓储
     * @param transactionManager 本库事务管理器
     */
    public QueryKeyTypeServiceImpl(QueryKeyTypeRepository keyTypes, QueryTemplateRepository templates,
        QueryTemplateVersionRepository versions,
        @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.keyTypes = keyTypes;
        this.templates = templates;
        this.versions = versions;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setTimeout(15);
    }

    /** 当前页之外另读一次 ACTIVE 模板当前版本，统计引用数。 */
    @Override
    public PagingResponseBody<QueryKeyTypeDetail> listQueryKeyTypes(QueryKeyTypeStatus status, String keyword,
        PagingRequestBody paging) {
        List<QueryKeyType> rows = keyTypes.findPage(status, keyword, paging);
        Map<String, Set<String>> references = rows.isEmpty() ? Map.of() : references(false);
        List<QueryKeyTypeDetail> items = rows.stream().map(row -> detail(row,
            references.getOrDefault(row.getKeyTypeCode(), Set.of()).size())).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), keyTypes.count(status, keyword));
    }

    /** 不存在返回 null。 */
    @Override
    public QueryKeyTypeDetail findQueryKeyType(long keyTypeId) {
        QueryKeyType row = keyTypes.findById(keyTypeId);
        return row == null ? null : detail(row, 0);
    }

    /** 不加锁读取；写入时由模板保存事务内的共享锁重证。 */
    @Override
    public Map<String, QueryKeyTypeDetail> findQueryKeyTypes(Collection<String> codes) {
        return keyTypes.findByCodes(Set.copyOf(codes)).stream().map(row -> detail(row, 0))
            .collect(Collectors.toMap(QueryKeyTypeDetail::getKeyTypeCode, Function.identity()));
    }

    /** 插入 ACTIVE、版本 1；编码唯一索引为最终保护。 */
    @Override
    public QueryKeyTypeChange createQueryKeyType(CreateQueryKeyTypeCommand command) {
        return transaction.execute(status -> {
            QueryKeyType row = new QueryKeyType();
            row.setKeyTypeId(command.getKeyTypeId());
            row.setKeyTypeCode(command.getKeyTypeCode());
            apply(row, command.getDefinition());
            row.setStatus(QueryKeyTypeStatus.ACTIVE);
            row.setVersion(1);
            row.setCreatedBy(command.getActorAccountId());
            row.setUpdatedBy(command.getActorAccountId());
            row.setCreateTime(command.getNow());
            row.setUpdateTime(command.getNow());
            keyTypes.insert(row);
            return new QueryKeyTypeChange(null, detail(row, 0));
        });
    }

    /** 排他锁读取并核对版本；值类型变化时要求没有 ACTIVE 模板引用。 */
    @Override
    public QueryKeyTypeChange updateQueryKeyType(UpdateQueryKeyTypeCommand command) {
        return transaction.execute(status -> {
            QueryKeyType row = requireVersion(command.getKeyTypeId(), command.getExpectedVersion());
            QueryKeyTypeDetail before = detail(row, 0);
            QueryKeyTypeDefinition definition = command.getDefinition();
            if (definition.getValueType() != row.getValueType()) {
                requireNotReferenced(row, "修改值类型");
            }
            QueryKeyType next = new QueryKeyType();
            apply(next, definition);
            next.setUpdatedBy(command.getActorAccountId());
            next.setUpdateTime(command.getNow());
            keyTypes.updateDefinition(row, next);
            return new QueryKeyTypeChange(before, detail(row, 0));
        });
    }

    /** 目标与当前相同时不写入；停用时要求没有 ACTIVE 模板引用。 */
    @Override
    public QueryKeyTypeChange changeQueryKeyTypeStatus(ChangeQueryKeyTypeStatusCommand command) {
        return transaction.execute(status -> {
            QueryKeyType row = requireVersion(command.getKeyTypeId(), command.getExpectedVersion());
            QueryKeyTypeDetail before = detail(row, 0);
            if (row.getStatus() == command.getTargetStatus()) {
                return new QueryKeyTypeChange(before, before);
            }
            if (command.getTargetStatus() == QueryKeyTypeStatus.INACTIVE) {
                requireNotReferenced(row, "停用");
            }
            keyTypes.changeStatus(row, command.getTargetStatus(), command.getActorAccountId(), command.getNow());
            return new QueryKeyTypeChange(before, detail(row, 0));
        });
    }

    /** 排他锁读取全部键类型，核对覆盖范围与各项版本后按位置重写 sort_no。 */
    @Override
    public QueryKeyTypeReorder reorderQueryKeyTypes(ReorderQueryKeyTypesCommand command) {
        return transaction.execute(status -> {
            Map<Long, QueryKeyType> rows = keyTypes.findAllForUpdate().stream()
                .collect(Collectors.toMap(QueryKeyType::getKeyTypeId, Function.identity()));
            requireCompleteOrder(command.getItems(), rows.keySet());
            List<QueryKeyTypeDetail> before = rows.values().stream().map(row -> detail(row, 0))
                .sorted((left, right) -> left.getSortNo() != right.getSortNo()
                    ? Integer.compare(left.getSortNo(), right.getSortNo())
                    : left.getKeyTypeCode().compareTo(right.getKeyTypeCode())).toList();
            List<QueryKeyTypeDetail> after = new ArrayList<>();
            for (int index = 0; index < command.getItems().size(); index++) {
                ReorderQueryKeyTypesCommand.Item item = command.getItems().get(index);
                QueryKeyType row = rows.get(item.getKeyTypeId());
                requireExpectedVersion(row, item.getExpectedVersion());
                keyTypes.changeSortNo(row, Math.multiplyExact(index + 1, SORT_STEP), command.getActorAccountId(),
                    command.getNow());
                after.add(detail(row, 0));
            }
            return new QueryKeyTypeReorder(before, List.copyOf(after));
        });
    }

    /** 排他锁读取；不存在返回 404，版本不符返回 409。 */
    QueryKeyType requireVersion(long keyTypeId, int expectedVersion) {
        QueryKeyType row = keyTypes.findByIdForUpdate(keyTypeId);
        if (row == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        requireExpectedVersion(row, expectedVersion);
        return row;
    }

    /** 版本不符返回 409。 */
    static void requireExpectedVersion(QueryKeyType row, int expectedVersion) {
        if (row.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("键类型已被其他操作修改，请刷新后重试: " + row.getKeyTypeCode()
                + "，当前版本 " + row.getVersion() + "，请求版本 " + expectedVersion);
        }
    }

    /** 重排必须恰好覆盖全部键类型且不重复。 */
    static void requireCompleteOrder(List<ReorderQueryKeyTypesCommand.Item> items, Set<Long> allIds) {
        Set<Long> requested = new HashSet<>();
        items.forEach(item -> requested.add(item.getKeyTypeId()));
        if (requested.size() != items.size() || !requested.equals(allIds)) {
            throw new BizCheckFailException("排序必须恰好包含全部 " + allIds.size() + " 个键类型且不能重复，本次提交 "
                + items.size() + " 项");
        }
    }

    /** 被 ACTIVE 模板当前版本引用时拒绝，消息列出最多 5 个模板编码。 */
    void requireNotReferenced(QueryKeyType row, String action) {
        Set<String> codes = references(true).getOrDefault(row.getKeyTypeCode(), Set.of());
        if (codes.isEmpty()) {
            return;
        }
        List<String> listed = codes.stream().limit(MAX_LISTED_TEMPLATES).toList();
        throw new BizCheckFailException("键类型被启用的模板引用，不能" + action + ": " + row.getKeyTypeCode() + "，模板 "
            + String.join(", ", listed) + (codes.size() > listed.size() ? " 等 " + codes.size() + " 个" : ""));
    }

    /**
     * ACTIVE 模板当前版本对各键类型的引用。
     *
     * @param forShare 写事务内为 true：以共享锁读取最新提交，避免与并发保存交错
     * @return 键类型编码到引用它的模板编码（升序）
     */
    Map<String, Set<String>> references(boolean forShare) {
        List<QueryTemplate> active = forShare ? templates.findAllForShare(QueryTemplateStatus.ACTIVE)
            : templates.findAll(QueryTemplateStatus.ACTIVE);
        Map<Long, QueryTemplateVersion> current = forShare ? versions.findCurrentForShare(active)
            : versions.findCurrent(active);
        Map<String, Set<String>> references = new HashMap<>();
        for (QueryTemplate template : active) {
            QueryTemplateVersion version = current.get(template.getTemplateId());
            QueryJointConfig joint = version == null ? null : QueryTemplateJson.readJoint(version.getJointJson());
            if (joint == null) {
                continue;
            }
            joint.referencedKeyTypes().forEach(code -> references.computeIfAbsent(code, key -> new TreeSet<>())
                .add(template.getTemplateCode()));
        }
        return new TreeMap<>(references);
    }

    /** 把可维护内容写入行对象。 */
    static void apply(QueryKeyType row, QueryKeyTypeDefinition definition) {
        row.setKeyTypeName(definition.getKeyTypeName());
        row.setGroupName(definition.getGroupName());
        row.setValueType(definition.getValueType());
        row.setDefaultRole(definition.getDefaultRole());
        row.setEntryAllowed(definition.isEntryAllowed());
        row.setValuePattern(definition.getValuePattern());
        row.setInputHint(definition.getInputHint());
        row.setSortNo(definition.getSortNo());
        row.setDescription(definition.getDescription());
    }

    /**
     * 行对象到领域事实。
     *
     * @param row 键类型行
     * @param referencedTemplateCount 引用数
     * @return 事实
     */
    static QueryKeyTypeDetail detail(QueryKeyType row, long referencedTemplateCount) {
        return QueryKeyTypeDetail.builder().keyTypeId(row.getKeyTypeId()).keyTypeCode(row.getKeyTypeCode())
            .keyTypeName(row.getKeyTypeName()).groupName(row.getGroupName()).valueType(row.getValueType())
            .defaultRole(row.getDefaultRole()).entryAllowed(Boolean.TRUE.equals(row.getEntryAllowed()))
            .valuePattern(row.getValuePattern()).inputHint(row.getInputHint()).sortNo(row.getSortNo())
            .description(row.getDescription()).status(row.getStatus()).version(row.getVersion())
            .createdBy(row.getCreatedBy()).updatedBy(row.getUpdatedBy()).createTime(row.getCreateTime())
            .updateTime(row.getUpdateTime()).referencedTemplateCount(referencedTemplateCount).build();
    }
}
