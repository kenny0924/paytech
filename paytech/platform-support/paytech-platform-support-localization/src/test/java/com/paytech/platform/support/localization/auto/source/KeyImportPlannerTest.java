package com.paytech.platform.support.localization.auto.source;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.source.KeyChange;
import com.paytech.platform.support.localization.domain.source.KeyImportPlan;
import com.paytech.platform.support.localization.domain.source.KeyImportPlanner;
import com.paytech.platform.support.localization.domain.source.NewKey;
import com.paytech.platform.support.localization.domain.source.SourceFile;
import com.paytech.platform.support.localization.test.builder.LocalizationFixtures;

/**
 * key 导入差异计算（技术方案 3.5、需求 B1）：新增、基准变更、将废弃、恢复在用四类，未创建的命名空间、key 前缀、格式与 ICU 问题，以及预览摘要。
 *
 * @see SourceFile#parse(byte[])
 * @see KeyImportPlanner#plan(SourceFile, String, java.util.Collection, List)
 * @see KeyImportPlan#digest(String, String, int)
 */
class KeyImportPlannerTest {

    private static final String APPLICATION = "client-console";

    /** 四类差异与说明变化；文件里没有的已废弃 key 保持不变，不计入任何一类。 */
    @Test
    void test_fourCategories() {
        List<I18nKey> existing = List.of(
            key(1, "team.invite.submit", "Send invitation", KeyStatus.ACTIVE),
            key(2, "team.invite.cancel", "Cancel", KeyStatus.ACTIVE),
            key(3, "team.member.remove", "Remove", KeyStatus.ACTIVE),
            key(4, "team.member.count", "{count} members", KeyStatus.DEPRECATED),
            key(5, "team.legacy.title", "Old", KeyStatus.DEPRECATED),
            key(6, "team.invite.hint", "Hint", KeyStatus.ACTIVE));
        SourceFile file = parse("""
            {"format":"code-mor-i18n-source/1","application":"client-console","namespaces":{"team":{
              "team.invite.submit":"Send invitation",
              "team.invite.cancel":"Cancel invitation",
              "team.member.count":{"text":"{count, plural, one {# member} other {# members}}","description":"Header"},
              "team.invite.hint":{"text":"Hint","description":"Shown under the form"},
              "team.invite.email":"Email"}}}
            """);

        KeyImportPlan plan = KeyImportPlanner.plan(file, APPLICATION, List.of("team"), existing);

        assertAll(() -> assertTrue(plan.importable()), () -> assertTrue(plan.hasChanges()),
            () -> assertEquals(List.of("team.invite.email"), plan.getAdded().stream().map(NewKey::keyCode).toList()),
            () -> assertEquals(List.of("team.invite.cancel"), codes(plan.getSourceChanged())),
            () -> assertEquals(List.of("team.member.remove"), plan.getDeprecated().stream().map(I18nKey::getKeyCode)
                .toList()),
            () -> assertEquals(List.of("team.member.count"), codes(plan.getRestored())),
            () -> assertTrue(plan.getRestored().getFirst().sourceChanged()),
            () -> assertEquals(List.of("team.invite.hint"), codes(plan.getDescriptionChanged())),
            () -> assertEquals(2, plan.getUnchangedCount()));
    }

    /** 未创建的命名空间使导入不可确认；key 前缀不符、格式错误与 ICU 问题按文件路径列出。 */
    @Test
    void test_problems() {
        SourceFile file = parse("""
            {"format":"code-mor-i18n-source/1","application":"client-console","namespaces":{
              "team":{"common.ok":"OK","team.bad":"{count, plural, one {#}}","team.blank":"  ","team.ok":"Fine"},
              "billing":{"billing.title":"Billing"},
              "Bad_NS":{"x.y":"z"}}}
            """);

        KeyImportPlan plan = KeyImportPlanner.plan(file, APPLICATION, List.of("team"), List.of());

        assertAll(() -> assertFalse(plan.importable()),
            () -> assertEquals(List.of("billing"), plan.getMissingNamespaces()),
            () -> assertEquals(List.of("namespaces.team.common.ok", "namespaces.Bad_NS", "namespaces.team.team.blank",
                "namespaces.team.team.bad"), plan.getProblems().stream().map(LocalizationProblem::field).sorted(
                    (left, right) -> order(left) - order(right)).toList()),
            () -> assertTrue(plan.getProblems().stream().anyMatch(problem -> problem.message()
                .contains("plural 参数缺少 other 分支"))),
            () -> assertEquals(List.of("team.bad", "team.ok", "billing.title"), plan.getAdded().stream()
                .map(NewKey::keyCode).toList()));
    }

    /** 文件不是 JSON、含重复键、格式或应用不符时列出问题，不抛出。 */
    @Test
    void test_invalidFile() {
        SourceFile notJson = SourceFile.parse(LocalizationFixtures.utf8("not json"));
        SourceFile duplicated = parse("{\"format\":\"code-mor-i18n-source/1\",\"application\":\"client-console\","
            + "\"namespaces\":{\"team\":{\"team.a\":\"A\",\"team.a\":\"B\"}}}");
        KeyImportPlan wrongApplication = KeyImportPlanner.plan(parse(
            "{\"format\":\"other/1\",\"application\":\"checkout\",\"namespaces\":{}}"), APPLICATION, List.of(),
            List.of());
        assertAll(() -> assertEquals("file", notJson.getProblems().getFirst().field()),
            () -> assertEquals("file", duplicated.getProblems().getFirst().field()),
            () -> assertEquals(List.of("format", "application"), wrongApplication.getProblems().stream()
                .map(LocalizationProblem::field).toList()));
    }

    /** 预览摘要随文件内容、key 集合修订号或应用变化，同一输入稳定。 */
    @Test
    void test_previewDigest() {
        String digest = KeyImportPlan.digest(APPLICATION, "a".repeat(64), 3);
        assertAll(() -> assertEquals(digest, KeyImportPlan.digest(APPLICATION, "a".repeat(64), 3)),
            () -> assertTrue(digest.matches("[0-9a-f]{64}")),
            () -> assertNotEquals(digest, KeyImportPlan.digest(APPLICATION, "a".repeat(64), 4)),
            () -> assertNotEquals(digest, KeyImportPlan.digest(APPLICATION, "b".repeat(64), 3)),
            () -> assertNotEquals(digest, KeyImportPlan.digest("checkout", "a".repeat(64), 3)));
    }

    /** 字符串文案的说明为 null；namespaceOf 取第一个点号前的部分。 */
    @Test
    void test_entryShapes() {
        SourceFile file = parse("""
            {"format":"code-mor-i18n-source/1","application":"client-console","namespaces":{"team":{
              "team.plain":"Plain","team.rich":{"text":"Rich","description":null}}}}
            """);
        assertAll(() -> assertNull(file.getNamespaces().get("team").get("team.plain").description()),
            () -> assertNull(file.getNamespaces().get("team").get("team.rich").description()),
            () -> assertEquals("team", LocalizationCodes.namespaceOf("team.invite.submit")));
    }

    /** 测试 key，命名空间固定为 team。 */
    static I18nKey key(long id, String code, String text, KeyStatus status) {
        return LocalizationFixtures.key(id, LocalizationFixtures.TEAM_NAMESPACE_ID, code, text, status);
    }

    /** 变化涉及的 key。 */
    static List<String> codes(List<KeyChange> changes) {
        return changes.stream().map(change -> change.key().getKeyCode()).toList();
    }

    /** 解析 UTF-8 文本。 */
    static SourceFile parse(String json) {
        return SourceFile.parse(LocalizationFixtures.utf8(json));
    }

    /** 断言用的稳定顺序。 */
    static int order(String field) {
        return List.of("namespaces.team.common.ok", "namespaces.Bad_NS", "namespaces.team.team.blank",
            "namespaces.team.team.bad").indexOf(field);
    }
}
