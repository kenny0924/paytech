package com.paytech.platform.support.localization.auto.source;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.source.TranslationFile;
import com.paytech.platform.support.localization.test.builder.LocalizationFixtures;

/**
 * 翻译文件 code-mor-i18n-translation/1 的解析：导出结构原样可读，值不是字符串、格式不符时列出问题。
 *
 * @see TranslationFile#parse(byte[])
 */
class TranslationFileTest {

    /** 合法文件保留命名空间、key 与空字符串。 */
    @Test
    void test_normalFlow() {
        TranslationFile file = TranslationFile.parse(LocalizationFixtures.utf8("""
            {"format":"code-mor-i18n-translation/1","application":"client-console","locale":"zh-CN",
             "namespaces":{"team":{"team.invite.submit":"发送邀请","team.invite.cancel":""}}}
            """));
        assertAll(() -> assertTrue(file.getProblems().isEmpty()),
            () -> assertEquals("client-console", file.getApplication()),
            () -> assertEquals("zh-CN", file.getLocale()),
            () -> assertEquals(Map.of("team.invite.submit", "发送邀请", "team.invite.cancel", ""),
                file.getNamespaces().get("team")));
    }

    /** 格式不符、值不是字符串、namespaces 不是对象。 */
    @Test
    void test_invalidFile() {
        TranslationFile wrong = TranslationFile.parse(LocalizationFixtures.utf8("""
            {"format":"code-mor-i18n-source/1","namespaces":{"team":{"team.a":1,"team.b":"B"}}}
            """));
        TranslationFile noNamespaces = TranslationFile.parse(LocalizationFixtures.utf8(
            "{\"format\":\"code-mor-i18n-translation/1\",\"namespaces\":[]}"));
        assertAll(() -> assertEquals(List.of("format", "namespaces.team.team.a"), wrong.getProblems().stream()
                .map(LocalizationProblem::field).toList()),
            () -> assertEquals(Map.of("team.b", "B"), wrong.getNamespaces().get("team")),
            () -> assertEquals("namespaces", noNamespaces.getProblems().getFirst().field()));
    }
}
