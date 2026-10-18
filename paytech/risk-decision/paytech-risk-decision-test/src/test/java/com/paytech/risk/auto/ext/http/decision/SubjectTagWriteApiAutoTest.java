package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CHANGED;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CODE_EXISTS;
import static com.paytech.risk.decision.domain.enums.DictionaryStatus.ACTIVE;
import static com.paytech.risk.decision.domain.enums.DictionaryStatus.DISABLED;
import static com.paytech.risk.decision.domain.enums.DictionaryType.SUBJECT_TAG;
import static com.paytech.risk.decision.domain.enums.DictionaryType.REJECTION_REASON;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.DictionaryEntryBuilder.reason;
import static com.paytech.risk.test.builder.decision.DictionaryEntryBuilder.tag;
import static com.paytech.risk.test.builder.decision.DictionaryRequestBuilder.NAME;
import static com.paytech.risk.test.builder.decision.DictionaryRequestBuilder.createTag;
import static com.paytech.risk.test.builder.decision.DictionaryRequestBuilder.status;
import static com.paytech.risk.test.builder.decision.DictionaryRequestBuilder.updateTag;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.paytech.risk.decision.api.DecisionDictionaryApi;
import com.paytech.risk.decision.api.request.ChangeDictionaryStatusRequestBody;
import com.paytech.risk.decision.api.request.CreateSubjectTagRequestBody;
import com.paytech.risk.decision.api.request.UpdateSubjectTagRequestBody;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.test.base.AbstractDecisionDictionaryApiTest;
import com.paytech.risk.test.builder.decision.DictionaryEntryBuilder;
import com.paytech.risk.test.builder.decision.DictionaryRequestBuilder;
import com.paytech.risk.test.checker.decision.DictionaryRecordChecker;
import com.paytech.risk.test.checker.decision.DictionaryWriteResponseChecker;
import com.paytech.risk.test.mapper.decision.DictionaryTestMapper;
import com.paytech.risk.test.mocker.decision.DictionaryMocker;
import com.paytech.risk.test.mocker.decision.DictionaryMocker.MockedDictionary;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.ReferencedRuleSet;

import tools.jackson.databind.node.ObjectNode;

/**
 * 主体标签创建、完整更新与启停
 * <li>正常流程：创建为 ACTIVE/修订号 1；更新保留编码与状态；启停保留内容；每次成功递增修订号并记录可信操作者与时间，空说明保存为 null。</li>
 * <li>业务规则：编码永久唯一（含停用）、期望修订号过期返回 409 且不改变记录；同编码并发只有一个成功；损坏记录或修订号已达上限失败关闭（500）。</li>
 * <li>锁顺序：同类型同编码的资源锁被占用时返回 409 CONCURRENT_REQUEST；规则保存持有的共享选择锁使启停等待至其提交。</li>
 * <li>参数：字段格式由 Bean Validation 拒绝（422）；缺键、多键、重复键、非字符串值与非法 JSON 返回 400；超过 16 KiB 返回 413；非 JSON 返回 415；写接口不接受
 * Query。</li>
 *
 * @see DecisionDictionaryApi#createSubjectTag(String, String, CreateSubjectTagRequestBody)
 * @see DecisionDictionaryApi#updateSubjectTag(String, String, String, UpdateSubjectTagRequestBody)
 * @see DecisionDictionaryApi#changeSubjectTagStatus(String, String, String, ChangeDictionaryStatusRequestBody)
 */
class SubjectTagWriteApiAutoTest extends AbstractDecisionDictionaryApiTest {
    /** 与 ACTOR 不同的第二个可信操作者，用于区分创建人与更新人。 */
    private static final long EDITOR = 70002L;

    @Autowired
    private DictionaryMocker dictionaries;

    @Autowired
    private DictionaryReferenceMocker references;

    @Autowired
    private DictionaryRecordChecker records;

    @Autowired
    private DictionaryTestMapper dictionaryMapper;

    @Autowired
    private RedissonClient redisson;

    /** 创建启用标签，说明为 null，创建与更新审计一致。 */
    @Test
    void test_normalFlow_create() {
        String code = dictionaries.newCode(SUBJECT_TAG);
        Instant before = now();

        DictionaryWriteResponseChecker response = create(createTag(code).build());

        response.assertWritten(SUBJECT_TAG, code, 1, ACTIVE, ACTOR).assertChangedBetween(before, now());
        records.assertCreated(MockedDictionary.created(SUBJECT_TAG, code, NAME, null, null, ACTOR,
            response.changedTime()));
    }

    /** 空串说明归一保存为 null。 */
    @Test
    void test_normalFlow_createEmptyDescription() {
        String code = dictionaries.newCode(SUBJECT_TAG);

        DictionaryWriteResponseChecker response = create(createTag(code).description("").build());

        response.assertWritten(SUBJECT_TAG, code, 1, ACTIVE, ACTOR);
        records.assertCreated(MockedDictionary.created(SUBJECT_TAG, code, NAME, null, null, ACTOR,
            response.changedTime()));
    }

    /** 64 位编码、100 码点名称（含补充平面字符）、500 码点多行说明均原样保存。 */
    @Test
    void test_edgeCase_createLengthLimits() {
        String prefix = dictionaries.newCode(SUBJECT_TAG);
        String code = dictionaries.own(SUBJECT_TAG, prefix + "Z".repeat(64 - prefix.length()));
        String name = "名".repeat(99) + "😀";
        String description = "\t" + "说".repeat(497) + "\r\n";

        DictionaryWriteResponseChecker response = create(createTag(code).name(name).description(description).build());

        response.assertWritten(SUBJECT_TAG, code, 1, ACTIVE, ACTOR);
        records.assertCreated(
            MockedDictionary.created(SUBJECT_TAG, code, name, description, null, ACTOR, response.changedTime()));
    }

    /** 原因目录中已有同编码不影响标签创建，两类字典编码互不占用。 */
    @Test
    void test_edgeCase_createSameCodeAsRejectionReason() {
        String code = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary existingReason = dictionaries.mock(reason().code(code));

        DictionaryWriteResponseChecker response = create(createTag(code).build());

        response.assertWritten(SUBJECT_TAG, code, 1, ACTIVE, ACTOR);
        records.assertCreated(MockedDictionary.created(SUBJECT_TAG, code, NAME, null, null, ACTOR,
            response.changedTime()));
        records.assertStored(existingReason);
    }

    /** 编码已登记（含停用）时拒绝复用，原记录不变。 */
    @ParameterizedTest(name = "已有记录 {0}")
    @EnumSource(DictionaryStatus.class)
    void test_businessRuleViolation_createCodeExists(DictionaryStatus existingStatus) {
        MockedDictionary existing = dictionaries.mock(tag().status(existingStatus).version(4));

        create(createTag(existing.code()).name("新名称").build()).assertProblem(409,
            RISK_DICTIONARY_CODE_EXISTS.getCode());

        records.assertStored(existing);
    }

    /** 同编码并发创建只有一个成功，另一请求以编码已存在或资源锁冲突拒绝，只落一行。 */
    @Test
    void test_businessRuleViolation_createConcurrently() throws Exception {
        String code = dictionaries.newCode(SUBJECT_TAG);

        List<DictionaryWriteResponseChecker> results = concurrently(() -> create(createTag(code).build()),
            () -> create(createTag(code).build()));

        DictionaryWriteResponseChecker winner = results.get(0).status() == 200 ? results.get(0) : results.get(1);
        DictionaryWriteResponseChecker loser = winner == results.get(0) ? results.get(1) : results.get(0);
        winner.assertWritten(SUBJECT_TAG, code, 1, ACTIVE, ACTOR);
        loser.assertConflictIn(RISK_DICTIONARY_CODE_EXISTS.getCode(), CONCURRENT_REQUEST.getCode());
        records.assertCreated(MockedDictionary.created(SUBJECT_TAG, code, NAME, null, null, ACTOR,
            winner.changedTime()));
    }

    /** 资源锁按字典类型与编码隔离：标签锁被占用时创建等锁超时，同编码原因与其他标签不受影响。 */
    @Test
    void test_businessRuleViolation_createWhileResourceLocked() {
        String code = dictionaries.newCode(SUBJECT_TAG);
        String other = dictionaries.newCode(SUBJECT_TAG);
        dictionaries.own(REJECTION_REASON, code);
        RLock lock = redisson.getLock("decision:dictionary:SUBJECT_TAG:" + code);
        lock.lock(10, TimeUnit.SECONDS);
        try {
            create(createTag(code).build()).assertProblem(409, CONCURRENT_REQUEST.getCode());
            records.assertAbsent(SUBJECT_TAG, code);

            create(createTag(other).build()).assertWritten(SUBJECT_TAG, other, 1, ACTIVE, ACTOR);
            send(HttpMethod.POST, REASONS, ACTOR, DictionaryRequestBuilder.createReason(code).build(),
                DictionaryWriteResponseChecker::new).assertWritten(REJECTION_REASON, code, 1, ACTIVE, ACTOR);
        } finally {
            lock.unlock();
        }

        create(createTag(code).build()).assertWritten(SUBJECT_TAG, code, 1, ACTIVE, ACTOR);
    }

    /** 字段格式非法时由入口校验拒绝并定位字段，不落库。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidCreateFields")
    void test_invalidParameter_createFields(String field, String scenario,
        Function<String, DictionaryRequestBuilder.CreateTag> request) {
        String code = dictionaries.newCode(SUBJECT_TAG);
        CreateSubjectTagRequestBody body = request.apply(code).build();
        dictionaries.own(SUBJECT_TAG, body.getTagCode());

        create(body).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        records.assertAbsent(SUBJECT_TAG, code);
        records.assertAbsent(SUBJECT_TAG, body.getTagCode());
    }

    /** JSON 转义的孤立代理项可被解析，但不是合法 Unicode 文本，按字段校验拒绝。 */
    @Test
    void test_invalidParameter_createLoneSurrogate() {
        String code = dictionaries.newCode(SUBJECT_TAG);
        String body = objectMapper.writeValueAsString(createTag(code).description("PLACEHOLDER").build())
            .replace("PLACEHOLDER", "\\ud800");

        create(body).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("description");

        records.assertAbsent(SUBJECT_TAG, code);
    }

    /** 报文形状不符合精确 JSON 合同时返回 400，不落库。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedCreateBodies")
    void test_invalidParameter_createMalformedBody(String scenario, Function<ObjectNode, String> mutation) {
        String code = dictionaries.newCode(SUBJECT_TAG);

        create(mutation.apply(tree(createTag(code).build()))).assertProblem(400, INVALID_REQUEST.getCode());

        records.assertAbsent(SUBJECT_TAG, code);
    }

    /** 报文恰为 16 KiB（尾部空白）可接受，超过 1 字节返回 413 且不落库。 */
    @Test
    void test_invalidParameter_createPayloadTooLarge() {
        String accepted = dictionaries.newCode(SUBJECT_TAG);
        String rejected = dictionaries.newCode(SUBJECT_TAG);

        create(padTo(createTag(accepted).build(), 16384)).assertWritten(SUBJECT_TAG, accepted, 1, ACTIVE,
            ACTOR);
        create(padTo(createTag(rejected).build(), 16385)).assertProblem(413, INVALID_REQUEST.getCode());

        records.assertAbsent(SUBJECT_TAG, rejected);
    }

    /** 非 JSON 媒体类型返回 415，不落库。 */
    @Test
    void test_invalidParameter_createUnsupportedMediaType() {
        String code = dictionaries.newCode(SUBJECT_TAG);
        HttpHeaders headers = headers(ACTOR);
        headers.setContentType(MediaType.TEXT_PLAIN);

        exchange(HttpMethod.POST, TAGS, headers, objectMapper.writeValueAsString(createTag(code).build()),
            DictionaryWriteResponseChecker::new).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        records.assertAbsent(SUBJECT_TAG, code);
    }

    /** 写接口携带任何 Query 均在入口拒绝，不落库。 */
    @Test
    void test_invalidParameter_createWithQuery() {
        String code = dictionaries.newCode(SUBJECT_TAG);

        send(HttpMethod.POST, TAGS + "?dryRun=true", ACTOR, createTag(code).build(),
            DictionaryWriteResponseChecker::new).assertProblem(400, INVALID_REQUEST.getCode());

        records.assertAbsent(SUBJECT_TAG, code);
    }

    /** 完整更新内容，保留编码、状态、创建人与创建时间，修订号加一并记录本次操作者。 */
    @Test
    void test_normalFlow_update() {
        MockedDictionary existing = dictionaries.mock(tag().version(3));
        Instant before = now();

        DictionaryWriteResponseChecker response = update(existing.code(), EDITOR,
            updateTag(3).name("新的名称").description("新的说明\n第二行").build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 4, ACTIVE, EDITOR).assertChangedBetween(before,
            now());
        records.assertStored(
            existing.changed("新的名称", "新的说明\n第二行", null, ACTIVE, EDITOR, response.changedTime()));
    }

    /** 停用标签更新内容后仍为停用。 */
    @Test
    void test_normalFlow_updateKeepsDisabledStatus() {
        MockedDictionary existing = dictionaries.mock(tag().status(DISABLED));

        DictionaryWriteResponseChecker response = update(existing.code(), EDITOR, updateTag(1).build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 2, DISABLED, EDITOR);
        records.assertStored(existing.changed(NAME, null, null, DISABLED, EDITOR, response.changedTime()));
    }

    /** 空串说明清空为 null。 */
    @Test
    void test_normalFlow_updateEmptyDescription() {
        MockedDictionary existing = dictionaries.mock(tag());

        DictionaryWriteResponseChecker response =
            update(existing.code(), ACTOR, updateTag(1).name(existing.name()).description("").build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 2, ACTIVE, ACTOR);
        records.assertStored(
            existing.changed(existing.name(), null, null, ACTIVE, ACTOR, response.changedTime()));
    }

    /** 内容不变的完整提交同样递增修订号。 */
    @Test
    void test_normalFlow_updateSameContent() {
        MockedDictionary existing = dictionaries.mock(tag());

        DictionaryWriteResponseChecker response =
            update(existing.code(), ACTOR, updateTag(1).name(existing.name()).description(existing.description()).build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 2, ACTIVE, ACTOR);
        records.assertStored(existing.changed(existing.name(), existing.description(), null,
            ACTIVE, ACTOR, response.changedTime()));
    }

    /** 未登记的标签不能更新，也不会被创建。 */
    @Test
    void test_noDataFound_update() {
        String code = dictionaries.newCode(SUBJECT_TAG);

        update(code, ACTOR, updateTag(1).build()).assertNotFound();

        records.assertAbsent(SUBJECT_TAG, code);
    }

    /** 期望修订号落后或超前于当前修订号时拒绝，记录不变。 */
    @ParameterizedTest(name = "expectedVersion={0}")
    @MethodSource("staleVersions")
    void test_businessRuleViolation_updateStaleVersion(long expectedVersion) {
        MockedDictionary existing = dictionaries.mock(tag().version(3));

        update(existing.code(), ACTOR, updateTag(expectedVersion).name("新的名称").build()).assertProblem(409,
            RISK_DICTIONARY_CHANGED.getCode());

        records.assertStored(existing);
    }

    /** 同一修订号的两个并发更新只有一个成功，修订号只加一。 */
    @Test
    void test_businessRuleViolation_updateConcurrently() throws Exception {
        MockedDictionary existing = dictionaries.mock(tag());

        List<DictionaryWriteResponseChecker> results =
            concurrently(() -> update(existing.code(), ACTOR, updateTag(1).name("并发甲").build()),
                () -> update(existing.code(), EDITOR, updateTag(1).name("并发乙").build()));

        int winnerIndex = results.get(0).status() == 200 ? 0 : 1;
        long winnerActor = winnerIndex == 0 ? ACTOR : EDITOR;
        DictionaryWriteResponseChecker winner = results.get(winnerIndex);
        winner.assertWritten(SUBJECT_TAG, existing.code(), 2, ACTIVE, winnerActor);
        results.get(1 - winnerIndex).assertConflictIn(RISK_DICTIONARY_CHANGED.getCode(), CONCURRENT_REQUEST.getCode());
        records.assertStored(existing.changed(winnerIndex == 0 ? "并发甲" : "并发乙", null, null, ACTIVE,
            winnerActor, winner.changedTime()));
    }

    /** 资源锁被占用时更新等锁超时，记录不变。 */
    @Test
    void test_businessRuleViolation_updateWhileResourceLocked() {
        MockedDictionary existing = dictionaries.mock(tag());
        RLock lock = redisson.getLock("decision:dictionary:SUBJECT_TAG:" + existing.code());
        lock.lock(10, TimeUnit.SECONDS);
        try {
            update(existing.code(), ACTOR, updateTag(1).build()).assertProblem(409, CONCURRENT_REQUEST.getCode());
        } finally {
            lock.unlock();
        }

        records.assertStored(existing);
    }

    /** 当前记录损坏时失败关闭，不改写保存事实。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptions")
    void test_businessRuleViolation_updateCorruptedRecord(String scenario, Supplier<DictionaryEntryBuilder> entry) {
        MockedDictionary existing = dictionaries.mock(entry.get());

        update(existing.code(), ACTOR, updateTag(existing.version()).build()).assertProblem(500,
            INTERNAL_ERROR.getCode());

        records.assertStored(existing);
    }

    /** 修订号已达 BIGINT 上限时不能再递增，失败关闭且记录不变。 */
    @Test
    void test_edgeCase_updateVersionCeiling() {
        MockedDictionary existing = dictionaries.mock(tag().version(Long.MAX_VALUE));

        update(existing.code(), ACTOR, updateTag(Long.MAX_VALUE).build()).assertProblem(500,
            INTERNAL_ERROR.getCode());

        records.assertStored(existing);
    }

    /** 更新字段格式非法时拒绝并定位字段，记录不变。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidUpdateFields")
    void test_invalidParameter_updateFields(String field, String scenario, DictionaryRequestBuilder.UpdateTag request) {
        MockedDictionary existing = dictionaries.mock(tag());

        update(existing.code(), ACTOR, request.build()).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError(field);

        records.assertStored(existing);
    }

    /** 更新报文形状不符合精确合同时返回 400，记录不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedUpdateBodies")
    void test_invalidParameter_updateMalformedBody(String scenario, Function<ObjectNode, String> mutation) {
        MockedDictionary existing = dictionaries.mock(tag());

        update(existing.code(), ACTOR, mutation.apply(tree(updateTag(1).build()))).assertProblem(400,
            INVALID_REQUEST.getCode());

        records.assertStored(existing);
    }

    /** 路径编码格式非法、携带 Query 或非 JSON 时在入口拒绝。 */
    @Test
    void test_invalidParameter_updateRequestShape() {
        MockedDictionary existing = dictionaries.mock(tag());
        HttpHeaders textHeaders = headers(ACTOR);
        textHeaders.setContentType(MediaType.TEXT_PLAIN);

        update(existing.code().toLowerCase(), ACTOR, updateTag(1).build()).assertProblem(400,
            INVALID_REQUEST.getCode());
        send(HttpMethod.PUT, TAGS + "/" + existing.code() + "?force=true", ACTOR, updateTag(1).build(),
            DictionaryWriteResponseChecker::new).assertProblem(400, INVALID_REQUEST.getCode());
        exchange(HttpMethod.PUT, TAGS + "/" + existing.code(), textHeaders,
            objectMapper.writeValueAsString(updateTag(1).build()), DictionaryWriteResponseChecker::new)
            .assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        records.assertStored(existing);
    }

    /** 停用启用标签，内容保持不变。 */
    @Test
    void test_normalFlow_disable() {
        MockedDictionary existing = dictionaries.mock(tag().version(2));
        Instant before = now();

        DictionaryWriteResponseChecker response = changeStatus(existing.code(), EDITOR, status(2, DISABLED).build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 3, DISABLED, EDITOR).assertChangedBetween(before,
            now());
        records.assertStored(existing.statusChanged(DISABLED, EDITOR, response.changedTime()));
    }

    /** 重新启用停用标签，空说明保持为 null。 */
    @Test
    void test_normalFlow_enable() {
        MockedDictionary existing = dictionaries.mock(tag().status(DISABLED).description(null));

        DictionaryWriteResponseChecker response = changeStatus(existing.code(), ACTOR, status(1, ACTIVE).build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 2, ACTIVE, ACTOR);
        records.assertStored(existing.statusChanged(ACTIVE, ACTOR, response.changedTime()));
    }

    /** 目标状态与当前相同也视为一次成功提交，修订号加一。 */
    @ParameterizedTest(name = "{0} -> {0}")
    @EnumSource(DictionaryStatus.class)
    void test_normalFlow_sameStatus(DictionaryStatus current) {
        MockedDictionary existing = dictionaries.mock(tag().status(current));

        DictionaryWriteResponseChecker response = changeStatus(existing.code(), ACTOR, status(1, current).build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 2, current, ACTOR);
        records.assertStored(existing.statusChanged(current, ACTOR, response.changedTime()));
    }

    /** 已被历史与当前规则版本的标签动作引用的标签仍可停用，已有引用不阻止停用。 */
    @Test
    void test_edgeCase_disableReferencedTag() {
        MockedDictionary existing = dictionaries.mock(tag());
        ReferencedRuleSet ruleSet = references.ruleSet(versions(FROZEN, DRAFT));
        references.addTag(references.rule(ruleSet, 1, null), DictionaryReferenceMocker.TARGET_BINDING, existing.code());
        references.addTag(references.rule(ruleSet, 2, null), DictionaryReferenceMocker.TARGET_BINDING, existing.code());

        DictionaryWriteResponseChecker response = changeStatus(existing.code(), ACTOR, status(1, DISABLED).build());

        response.assertWritten(SUBJECT_TAG, existing.code(), 2, DISABLED, ACTOR);
        records.assertStored(existing.statusChanged(DISABLED, ACTOR, response.changedTime()));
    }

    /** 规则保存持有标签的共享选择锁时，启停等待该事务提交后才写入。 */
    @Test
    void test_edgeCase_statusWaitsForSelectionShareLock() throws Exception {
        MockedDictionary existing = dictionaries.mock(tag());
        AtomicReference<CompletableFuture<DictionaryWriteResponseChecker>> pending = new AtomicReference<>();

        dictionaryMapper.holdShareLock(SUBJECT_TAG, existing.code(), () -> {
            pending.set(CompletableFuture
                .supplyAsync(() -> changeStatus(existing.code(), ACTOR, status(1, DISABLED).build())));
            assertThrows(TimeoutException.class, () -> pending.get().get(1, TimeUnit.SECONDS),
                "共享锁释放前启停不应完成");
        });

        DictionaryWriteResponseChecker response = pending.get().get(10, TimeUnit.SECONDS);
        response.assertWritten(SUBJECT_TAG, existing.code(), 2, DISABLED, ACTOR);
        records.assertStored(existing.statusChanged(DISABLED, ACTOR, response.changedTime()));
    }

    /** 未登记的标签不能启停。 */
    @Test
    void test_noDataFound_status() {
        String code = dictionaries.newCode(SUBJECT_TAG);

        changeStatus(code, ACTOR, status(1, DISABLED).build()).assertNotFound();

        records.assertAbsent(SUBJECT_TAG, code);
    }

    /** 期望修订号过期时拒绝启停，记录不变。 */
    @ParameterizedTest(name = "expectedVersion={0}")
    @MethodSource("staleVersions")
    void test_businessRuleViolation_statusStaleVersion(long expectedVersion) {
        MockedDictionary existing = dictionaries.mock(tag().version(3));

        changeStatus(existing.code(), ACTOR, status(expectedVersion, DISABLED).build()).assertProblem(409,
            RISK_DICTIONARY_CHANGED.getCode());

        records.assertStored(existing);
    }

    /** 当前记录损坏或修订号已达上限时启停失败关闭，记录不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptionsAndCeiling")
    void test_businessRuleViolation_statusCorruptedRecord(String scenario, Supplier<DictionaryEntryBuilder> entry) {
        MockedDictionary existing = dictionaries.mock(entry.get());

        changeStatus(existing.code(), ACTOR, status(existing.version(), DISABLED).build()).assertProblem(500,
            INTERNAL_ERROR.getCode());

        records.assertStored(existing);
    }

    /** 启停字段格式非法时拒绝并定位字段，记录不变。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidStatusFields")
    void test_invalidParameter_statusFields(String field, String scenario, DictionaryRequestBuilder.Status request) {
        MockedDictionary existing = dictionaries.mock(tag());

        changeStatus(existing.code(), ACTOR, request.build()).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError(field);

        records.assertStored(existing);
    }

    /** 启停报文形状非法时返回 400，记录不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedStatusBodies")
    void test_invalidParameter_statusMalformedBody(String scenario, Function<ObjectNode, String> mutation) {
        MockedDictionary existing = dictionaries.mock(tag());

        changeStatus(existing.code(), ACTOR, mutation.apply(tree(status(1, DISABLED).build()))).assertProblem(400,
            INVALID_REQUEST.getCode());

        records.assertStored(existing);
    }

    /** 路径编码格式非法、携带 Query 或非 JSON 时在入口拒绝，记录不变。 */
    @Test
    void test_invalidParameter_statusRequestShape() {
        MockedDictionary existing = dictionaries.mock(tag());
        HttpHeaders textHeaders = headers(ACTOR);
        textHeaders.setContentType(MediaType.TEXT_PLAIN);

        changeStatus("M".repeat(65), ACTOR, status(1, DISABLED).build()).assertProblem(400, INVALID_REQUEST.getCode());
        send(HttpMethod.POST, TAGS + "/" + existing.code() + "/status?status=DISABLED", ACTOR,
            status(1, DISABLED).build(), DictionaryWriteResponseChecker::new).assertProblem(400,
                INVALID_REQUEST.getCode());
        exchange(HttpMethod.POST, TAGS + "/" + existing.code() + "/status", textHeaders,
            objectMapper.writeValueAsString(status(1, DISABLED).build()), DictionaryWriteResponseChecker::new)
            .assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        records.assertStored(existing);
    }

    static Stream<Long> staleVersions() {
        return Stream.of(2L, 4L);
    }

    static Stream<Arguments> invalidCreateFields() {
        return Stream.of(createField("tagCode", "小写", code -> createTag(code.toLowerCase())),
            createField("tagCode", "数字开头", code -> createTag("1" + code)),
            createField("tagCode", "含连字符", code -> createTag(code + "-X")),
            createField("tagCode", "空串", code -> createTag("")),
            createField("tagCode", "超过 64 位", code -> createTag(code + "X".repeat(65 - code.length()))),
            createField("name", "空串", code -> createTag(code).name("")),
            createField("name", "空白", code -> createTag(code).name("   ")),
            createField("name", "首部空白", code -> createTag(code).name(" 名称")),
            createField("name", "尾部空白", code -> createTag(code).name("名称\t")),
            createField("name", "含换行", code -> createTag(code).name("第一行\n第二行")),
            createField("name", "含控制字符", code -> createTag(code).name("名\u0007称")),
            createField("name", "超过 100 码点", code -> createTag(code).name("名".repeat(100) + "😀")),
            createField("description", "超过 500 码点", code -> createTag(code).description("说".repeat(501))),
            createField("description", "含控制字符", code -> createTag(code).description("说明\u0000")),
            createField("description", "含 ESC 控制字符", code -> createTag(code).description("说\u001B明")));
    }

    static Stream<Arguments> malformedCreateBodies() {
        return Stream.of(malformed("非法 JSON", node -> "{\"tagCode\":"), malformed("空报文", node -> ""),
            malformed("顶层为数组", node -> "[" + node + "]"), malformed("顶层为 null", node -> "null"),
            malformed("缺少 description 键", node -> without(node, "description")),
            malformed("携带 defaultMessage", node -> node.put("defaultMessage", "文案").toString()),
            malformed("未知字段", node -> node.put("status", "ACTIVE").toString()),
            malformed("重复字段", node -> node.toString().replaceFirst("\\{", "{\"name\":\"重复\",")),
            malformed("tagCode 为数字", node -> node.put("tagCode", 1).toString()),
            malformed("name 为 null", node -> node.putNull("name").toString()),
            malformed("tagCode 为 null", node -> node.putNull("tagCode").toString()),
            malformed("description 为数字", node -> node.put("description", 1).toString()),
            malformed("description 为对象", node -> node.set("description", node.objectNode()).toString()),
            malformed("对象后追加内容", node -> node + " {}"));
    }

    static Stream<Arguments> invalidUpdateFields() {
        return Stream.of(updateField("expectedVersion", "零", updateTag(1).expectedVersion("0")),
            updateField("expectedVersion", "负数", updateTag(1).expectedVersion("-1")),
            updateField("expectedVersion", "前导零", updateTag(1).expectedVersion("01")),
            updateField("expectedVersion", "非数字", updateTag(1).expectedVersion("one")),
            updateField("expectedVersion", "超出 BIGINT", updateTag(1).expectedVersion("9223372036854775808")),
            updateField("name", "空串", updateTag(1).name("")),
            updateField("name", "尾部空白", updateTag(1).name("名称 ")),
            updateField("description", "超过 500 码点", updateTag(1).description("说".repeat(501))),
            updateField("name", "空白", updateTag(1).name("   ")));
    }

    static Stream<Arguments> malformedUpdateBodies() {
        return Stream.of(malformed("携带 tagCode", node -> node.put("tagCode", "MOCK").toString()),
            malformed("缺少 expectedVersion", node -> without(node, "expectedVersion")),
            malformed("expectedVersion 为数字", node -> node.put("expectedVersion", 1).toString()),
            malformed("缺少 description 键", node -> without(node, "description")),
            malformed("携带 defaultMessage", node -> node.put("defaultMessage", "文案").toString()));
    }

    static Stream<Arguments> invalidStatusFields() {
        return Stream.of(statusField("status", "小写", status(1, DISABLED).status("disabled")),
            statusField("status", "未知状态", status(1, DISABLED).status("DELETED")),
            statusField("status", "空串", status(1, DISABLED).status("")),
            statusField("expectedVersion", "零", status(1, DISABLED).expectedVersion("0")),
            statusField("expectedVersion", "超出 BIGINT", status(1, DISABLED).expectedVersion("9223372036854775808")));
    }

    static Stream<Arguments> malformedStatusBodies() {
        return Stream.of(malformed("status 为 null", node -> node.putNull("status").toString()),
            malformed("缺少 status", node -> without(node, "status")),
            malformed("expectedVersion 为数字", node -> node.put("expectedVersion", 1).toString()),
            malformed("未知字段", node -> node.put("name", "x").toString()));
    }

    static Stream<Arguments> corruptions() {
        return Stream.of(corrupted("名称尾部空白", () -> tag().name("名称 ")),
            corrupted("名称为空串", () -> tag().name("")),
            corrupted("说明含控制字符", () -> tag().description("说明\u0001")),
            corrupted("更新人为 0", () -> tag().updatedBy(0)));
    }

    static Stream<Arguments> corruptionsAndCeiling() {
        return Stream.concat(corruptions(),
            Stream.of(corrupted("修订号已达上限", () -> tag().version(Long.MAX_VALUE))));
    }

    private static Arguments createField(String field, String scenario,
        Function<String, DictionaryRequestBuilder.CreateTag> request) {
        return Arguments.of(field, scenario, request);
    }

    private static Arguments updateField(String field, String scenario, DictionaryRequestBuilder.UpdateTag request) {
        return Arguments.of(field, scenario, request);
    }

    private static Arguments statusField(String field, String scenario, DictionaryRequestBuilder.Status request) {
        return Arguments.of(field, scenario, request);
    }

    private static Arguments malformed(String scenario, Function<ObjectNode, String> mutation) {
        return Arguments.of(scenario, mutation);
    }

    private static Arguments corrupted(String scenario, Supplier<DictionaryEntryBuilder> entry) {
        return Arguments.of(scenario, entry);
    }

    /** 序列化后以尾部空白补足到指定字节数。 */
    private String padTo(Object body, int bytes) {
        String json = objectMapper.writeValueAsString(body);
        return json + " ".repeat(bytes - json.getBytes(StandardCharsets.UTF_8).length);
    }

    private DictionaryWriteResponseChecker create(Object body) {
        return send(HttpMethod.POST, TAGS, ACTOR, body, DictionaryWriteResponseChecker::new);
    }

    private DictionaryWriteResponseChecker update(String code, long actor, Object body) {
        return send(HttpMethod.PUT, TAGS + "/" + code, actor, body, DictionaryWriteResponseChecker::new);
    }

    private DictionaryWriteResponseChecker changeStatus(String code, long actor, Object body) {
        return send(HttpMethod.POST, TAGS + "/" + code + "/status", actor, body,
            DictionaryWriteResponseChecker::new);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 删除一个键后输出报文。 */
    private static String without(ObjectNode node, String field) {
        node.remove(field);
        return node.toString();
    }
}
