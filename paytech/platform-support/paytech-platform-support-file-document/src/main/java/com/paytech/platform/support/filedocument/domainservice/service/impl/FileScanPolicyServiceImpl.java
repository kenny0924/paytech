package com.paytech.platform.support.filedocument.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRequirement;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRevision;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.domain.storage.ChangeScanPolicyRevisionCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyDetailView;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyView;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageField;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageFieldChange;
import com.paytech.platform.support.filedocument.domain.storage.SaveScanPolicyRevisionCommand;
import com.paytech.platform.support.filedocument.domainservice.repository.FileScanPolicyRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileScanRequirementRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageBucketRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageInstanceRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageRouteRepository;
import com.paytech.platform.support.filedocument.domainservice.service.FileScanPolicyService;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 检查策略的本库规则与保存；不访问外部依赖或其他 Service
 * <li>写方法先锁定该代码的全部修订，再按修订状态与版本条件更新；停用在同一事务内重查路由引用。</li>
 */
@Service
@AllArgsConstructor
public class FileScanPolicyServiceImpl implements FileScanPolicyService {

    /** 修订状态：草稿。 */
    static final String DRAFT = "DRAFT";

    /** 修订状态：生效。 */
    static final String ACTIVE = "ACTIVE";

    /** 修订状态：停用。 */
    static final String RETIRED = "RETIRED";

    /** 允许媒体类型规则的结构版本。 */
    static final int RULE_SCHEMA_VERSION = 1;

    /** 生效修订的活动占位。 */
    static final int ACTIVE_SLOT = 1;

    private final ObjectMapper json;
    private final FileStorageRouteRepository routes;
    private final FileScanPolicyRepository policies;
    private final FileStorageBucketRepository buckets;
    private final FileStorageInstanceRepository instances;
    private final FileScanRequirementRepository requirements;

    /** 修订已按代码升序、修订号倒序读取；路由数按策略代码统计。 */
    @Override
    public List<FileScanPolicyView> listPolicies() {
        Map<String, Long> routeCounts = routes.findAll().stream()
            .collect(Collectors.groupingBy(FileStorageRoute::getScanPolicyCode, Collectors.counting()));
        Map<String, List<FileScanPolicyRevision>> byCode = policies.findAll().stream()
            .collect(Collectors.groupingBy(FileScanPolicyRevision::getPolicyCode, LinkedHashMap::new,
                Collectors.toList()));
        return byCode.entrySet().stream()
            .map(entry -> summary(entry.getKey(), entry.getValue(),
                Math.toIntExact(routeCounts.getOrDefault(entry.getKey(), 0L))))
            .toList();
    }

    /** 修订按修订号倒序，路由目标缺失时按空编码返回。 */
    @Override
    public FileScanPolicyDetailView policy(String policyCode) {
        List<FileScanPolicyRevision> revisions = policies.findByCode(policyCode);
        if (revisions.isEmpty()) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        Map<Long, List<FileScanPolicyRequirement>> rules = requirements
            .findByPolicyIds(revisions.stream().map(FileScanPolicyRevision::getPolicyId).toList()).stream()
            .collect(Collectors.groupingBy(FileScanPolicyRequirement::getPolicyId));
        return new FileScanPolicyDetailView(policyCode, routeRefs(policyCode), revisions.stream()
            .map(revision -> revisionView(revision, rules.getOrDefault(revision.getPolicyId(), List.of())))
            .toList());
    }

    /** 代码已存在时拒绝；修订 1 为 DRAFT，并写一条标准格式检查要求。 */
    @Override
    public FileStorageChange createPolicy(SaveScanPolicyRevisionCommand command) {
        if (!policies.lockByCode(command.getPolicyCode()).isEmpty()) {
            throw new BizCheckFailException("检查策略已存在: " + command.getPolicyCode());
        }
        FileScanPolicyRevision revision = draft(command, 1, command.getAllowedMimeTypes(), command.getMaxSizeBytes());
        insertDraft(revision, command.getRequirementId());
        return change(revision, 0, List.of(
            new FileStorageFieldChange(FileStorageField.CODE, null, revision.getPolicyCode()),
            new FileStorageFieldChange(FileStorageField.REVISION_NO, null, "1"),
            new FileStorageFieldChange(FileStorageField.ALLOWED_MIME_TYPES, null, joined(command.getAllowedMimeTypes())),
            new FileStorageFieldChange(FileStorageField.MAX_SIZE_BYTES, null, Long.toString(command.getMaxSizeBytes())),
            new FileStorageFieldChange(FileStorageField.STATUS, null, DRAFT)));
    }

    /** 内容取 ACTIVE 修订（可能含格式检查器无法识别的存量类型），修订号为当前最大值加 1。 */
    @Override
    public FileStorageChange createRevision(SaveScanPolicyRevisionCommand command) {
        List<FileScanPolicyRevision> revisions = lockExisting(command.getPolicyCode());
        if (revisions.stream().anyMatch(revision -> DRAFT.equals(revision.getStatus()))) {
            throw new ConcurrentRequestException("检查策略已有草稿修订，请先编辑或废弃: " + command.getPolicyCode());
        }
        FileScanPolicyRevision active = revisions.stream().filter(revision -> ACTIVE.equals(revision.getStatus()))
            .findFirst().orElseThrow(() -> new BizCheckFailException(
                "检查策略没有生效修订，不能以其为模板新建修订: " + command.getPolicyCode()));
        int revisionNo = revisions.stream().mapToInt(FileScanPolicyRevision::getRevisionNo).max().orElse(0) + 1;
        List<String> types = mimeTypes(active.getAllowedMimeRules());
        FileScanPolicyRevision revision = draft(command, revisionNo, types, active.getMaxSizeBytes());
        insertDraft(revision, command.getRequirementId());
        return change(revision, 0, List.of(
            new FileStorageFieldChange(FileStorageField.REVISION_NO, null, Integer.toString(revisionNo)),
            new FileStorageFieldChange(FileStorageField.ALLOWED_MIME_TYPES, null, joined(types)),
            new FileStorageFieldChange(FileStorageField.MAX_SIZE_BYTES, null, Long.toString(active.getMaxSizeBytes())),
            new FileStorageFieldChange(FileStorageField.STATUS, null, DRAFT)));
    }

    /** 只允许 DRAFT；无变化时不递增版本。 */
    @Override
    public FileStorageChange updateRevision(SaveScanPolicyRevisionCommand command) {
        FileScanPolicyRevision revision = lockRevision(command.getPolicyCode(), command.getRevisionNo(),
            command.getExpectedVersion());
        requireStatus(revision, DRAFT, "只能修改草稿修订");
        var changes = new ArrayList<FileStorageFieldChange>();
        diff(changes, FileStorageField.ALLOWED_MIME_TYPES, joined(mimeTypes(revision.getAllowedMimeRules())),
            joined(command.getAllowedMimeTypes()));
        diff(changes, FileStorageField.MAX_SIZE_BYTES, Long.toString(revision.getMaxSizeBytes()),
            Long.toString(command.getMaxSizeBytes()));
        int before = revision.getVersion();
        if (!changes.isEmpty()) {
            policies.updateDraft(revision, rules(command.getAllowedMimeTypes()), command.getMaxSizeBytes(),
                command.getNow());
        }
        return change(revision, before, changes);
    }

    /** 草稿从未被上传冻结，物理删除修订及其检查要求。 */
    @Override
    public FileStorageChange discardRevision(ChangeScanPolicyRevisionCommand command) {
        FileScanPolicyRevision revision = lockRevision(command.getPolicyCode(), command.getRevisionNo(),
            command.getExpectedVersion());
        requireStatus(revision, DRAFT, "只能废弃草稿修订");
        requirements.deleteByPolicy(revision.getPolicyId());
        policies.deleteDraft(revision);
        return new FileStorageChange(FileStorageChange.SCAN_POLICY, revision.getPolicyId(), code(revision), null,
            revision.getVersion(), revision.getVersion(),
            List.of(new FileStorageFieldChange(FileStorageField.STATUS, DRAFT, null)));
    }

    /** 先把原 ACTIVE 修订改为 RETIRED（释放活动占位），再把草稿改为 ACTIVE；已开始的上传冻结了原修订不受影响。 */
    @Override
    public FileStorageChange activateRevision(ChangeScanPolicyRevisionCommand command) {
        List<FileScanPolicyRevision> revisions = lockExisting(command.getPolicyCode());
        FileScanPolicyRevision draft = revision(revisions, command.getRevisionNo());
        requireVersion(draft, command.getExpectedVersion());
        requireStatus(draft, DRAFT, "只能激活草稿修订");
        var changes = new ArrayList<FileStorageFieldChange>();
        revisions.stream().filter(revision -> ACTIVE.equals(revision.getStatus())).findFirst().ifPresent(active -> {
            policies.updateStatus(active, RETIRED, null, command.getNow());
            changes.add(new FileStorageFieldChange(FileStorageField.REVISION_NO, active.getRevisionNo().toString(),
                draft.getRevisionNo().toString()));
        });
        int before = draft.getVersion();
        policies.updateStatus(draft, ACTIVE, ACTIVE_SLOT, command.getNow());
        changes.add(new FileStorageFieldChange(FileStorageField.STATUS, DRAFT, ACTIVE));
        return change(draft, before, changes);
    }

    /** 作用于当前 ACTIVE 修订；锁定修订后在同一事务内重查路由引用，与路由保存的策略行锁互斥。 */
    @Override
    public FileStorageChange retire(ChangeScanPolicyRevisionCommand command) {
        FileScanPolicyRevision active = lockExisting(command.getPolicyCode()).stream()
            .filter(revision -> ACTIVE.equals(revision.getStatus())).findFirst()
            .orElseThrow(() -> new BizCheckFailException("检查策略没有生效修订: " + command.getPolicyCode()));
        requireVersion(active, command.getExpectedVersion());
        int routeCount = routes.countByPolicyCode(command.getPolicyCode());
        if (routeCount > 0) {
            throw new BizCheckFailException(
                "检查策略仍被 " + routeCount + " 条写入路由引用，不能停用: " + command.getPolicyCode());
        }
        int before = active.getVersion();
        policies.updateStatus(active, RETIRED, null, command.getNow());
        return change(active, before, List.of(new FileStorageFieldChange(FileStorageField.STATUS, ACTIVE, RETIRED)));
    }

    /** 锁定代码的全部修订；不存在返回 404。 */
    List<FileScanPolicyRevision> lockExisting(String policyCode) {
        List<FileScanPolicyRevision> revisions = policies.lockByCode(policyCode);
        if (revisions.isEmpty()) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return revisions;
    }

    /** 锁定代码的全部修订后取目标修订并核对版本。 */
    FileScanPolicyRevision lockRevision(String policyCode, Integer revisionNo, int expectedVersion) {
        FileScanPolicyRevision revision = revision(lockExisting(policyCode), revisionNo);
        requireVersion(revision, expectedVersion);
        return revision;
    }

    /** 按修订号取修订；不存在返回 404。 */
    FileScanPolicyRevision revision(List<FileScanPolicyRevision> revisions, Integer revisionNo) {
        return revisions.stream().filter(revision -> revision.getRevisionNo().equals(revisionNo)).findFirst()
            .orElseThrow(() -> new FailException(CommonErrorCode.NOT_FOUND));
    }

    /** 版本不符返回并发冲突。 */
    void requireVersion(FileScanPolicyRevision revision, int expectedVersion) {
        if (revision.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("检查策略修订已被其他操作修改，请刷新后重试: " + code(revision)
                + "，当前版本 " + revision.getVersion() + "，请求版本 " + expectedVersion);
        }
    }

    /** 状态不符返回业务拒绝。 */
    void requireStatus(FileScanPolicyRevision revision, String status, String message) {
        if (!status.equals(revision.getStatus())) {
            throw new BizCheckFailException(message + ": " + code(revision) + " 为 " + revision.getStatus());
        }
    }

    /** 新建草稿修订对象。 */
    FileScanPolicyRevision draft(SaveScanPolicyRevisionCommand command, int revisionNo, List<String> types,
        long maxSizeBytes) {
        FileScanPolicyRevision revision = new FileScanPolicyRevision();
        revision.setPolicyId(command.getPolicyId());
        revision.setPolicyCode(command.getPolicyCode());
        revision.setRevisionNo(revisionNo);
        revision.setRuleSchemaVersion(RULE_SCHEMA_VERSION);
        revision.setAllowedMimeRules(rules(types));
        revision.setMaxSizeBytes(maxSizeBytes);
        revision.setStatus(DRAFT);
        revision.setVersion(1);
        revision.setCreateTime(command.getNow());
        revision.setUpdateTime(command.getNow());
        return revision;
    }

    /** 插入草稿及标准格式检查要求：PLATFORM_FORMAT / FORMAT / 最低版本 1 / 接受 FORMAT_VALID。 */
    void insertDraft(FileScanPolicyRevision revision, long requirementId) {
        policies.insert(revision);
        FileScanPolicyRequirement requirement = new FileScanPolicyRequirement();
        requirement.setRequirementId(requirementId);
        requirement.setPolicyId(revision.getPolicyId());
        requirement.setRequirementNo(1);
        requirement.setScannerCode(FormatFileScanner.SCANNER_CODE);
        requirement.setScanType(FileScanType.FORMAT);
        requirement.setMinimumScannerVersion(FormatFileScanner.MINIMUM_RUNTIME_VERSION);
        requirement.setAcceptedResultCodes(json.writeValueAsString(List.of(FormatFileScanner.VALID)));
        requirement.setVersion(1);
        requirement.setCreateTime(revision.getCreateTime());
        requirement.setUpdateTime(revision.getCreateTime());
        requirements.insert(requirement);
    }

    /** 一个代码的列表行：展示内容取 ACTIVE，没有时取 DRAFT，都没有时取最新修订。 */
    FileScanPolicyView summary(String policyCode, List<FileScanPolicyRevision> revisions, int routeCount) {
        FileScanPolicyRevision active = first(revisions, ACTIVE);
        FileScanPolicyRevision draft = first(revisions, DRAFT);
        FileScanPolicyRevision shown = active != null ? active : draft != null ? draft : revisions.getFirst();
        String status = active != null ? ACTIVE : draft != null ? DRAFT : RETIRED;
        Instant updateTime = revisions.stream().map(FileScanPolicyRevision::getUpdateTime)
            .max(Comparator.naturalOrder()).orElse(null);
        return new FileScanPolicyView(policyCode, active == null ? null : active.getRevisionNo(),
            draft == null ? null : draft.getRevisionNo(), mimeTypes(shown.getAllowedMimeRules()),
            shown.getMaxSizeBytes(), routeCount, status, updateTime);
    }

    /** 指定状态的第一个修订；没有为空。 */
    static FileScanPolicyRevision first(List<FileScanPolicyRevision> revisions, String status) {
        return revisions.stream().filter(revision -> status.equals(revision.getStatus())).findFirst().orElse(null);
    }

    /** 引用策略代码的路由及其目标实例与桶。 */
    List<FileScanPolicyDetailView.RouteRef> routeRefs(String policyCode) {
        Map<Long, FileStorageBucket> bucketsById = buckets.findAll().stream()
            .collect(Collectors.toMap(FileStorageBucket::getBucketId, Function.identity()));
        Map<Long, FileStorageInstance> instancesById = instances.findAll().stream()
            .collect(Collectors.toMap(FileStorageInstance::getInstanceId, Function.identity()));
        return routes.findByPolicyCode(policyCode).stream().map(route -> {
            FileStorageBucket bucket = bucketsById.get(route.getBucketId());
            FileStorageInstance instance = bucket == null ? null : instancesById.get(bucket.getInstanceId());
            return new FileScanPolicyDetailView.RouteRef(route.getSourceSystem(), route.getAccessLevel().getCode(),
                instance == null ? null : instance.getInstanceCode(), bucket == null ? null : bucket.getBucketName());
        }).toList();
    }

    /** 修订与其检查要求。 */
    FileScanPolicyDetailView.Revision revisionView(FileScanPolicyRevision revision,
        List<FileScanPolicyRequirement> rules) {
        return new FileScanPolicyDetailView.Revision(revision.getRevisionNo(), revision.getStatus(),
            mimeTypes(revision.getAllowedMimeRules()), revision.getMaxSizeBytes(),
            rules.stream().map(rule -> new FileScanPolicyDetailView.Requirement(rule.getScannerCode(),
                rule.getScanType().getCode(), rule.getMinimumScannerVersion(), codes(rule.getAcceptedResultCodes())))
                .toList(),
            revision.getVersion(), revision.getCreateTime(), revision.getUpdateTime());
    }

    /** 允许媒体类型规则 JSON；与上传时读取的结构一致。 */
    String rules(List<String> types) {
        return json.writeValueAsString(Map.of("mimeTypes", types));
    }

    /** 读取规则中的媒体类型；结构损坏时为空列表（上传时按失败关闭处理）。 */
    List<String> mimeTypes(String rules) {
        try {
            return texts(json.readTree(rules).path("mimeTypes"));
        } catch (RuntimeException invalid) {
            return List.of();
        }
    }

    /** 读取接受结果代码 JSON 数组；结构损坏时为空列表。 */
    List<String> codes(String value) {
        try {
            return texts(json.readTree(value));
        } catch (RuntimeException invalid) {
            return List.of();
        }
    }

    /** JSON 文本数组转列表；非数组为空列表。 */
    static List<String> texts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    /** 媒体类型的审计形式：逗号分隔。 */
    static String joined(List<String> types) {
        return String.join(",", types);
    }

    /** 值不同才记录。 */
    static void diff(List<FileStorageFieldChange> changes, FileStorageField field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FileStorageFieldChange(field, before, after));
        }
    }

    /** 修订的审计编码：{策略代码}/{修订号}。 */
    static String code(FileScanPolicyRevision revision) {
        return revision.getPolicyCode() + "/" + revision.getRevisionNo();
    }

    /** 检查策略变更结果。 */
    static FileStorageChange change(FileScanPolicyRevision revision, int before, List<FileStorageFieldChange> changes) {
        return new FileStorageChange(FileStorageChange.SCAN_POLICY, revision.getPolicyId(), code(revision),
            revision.getStatus(), before, revision.getVersion(), changes);
    }
}
