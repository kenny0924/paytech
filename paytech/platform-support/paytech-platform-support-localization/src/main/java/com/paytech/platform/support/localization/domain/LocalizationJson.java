package com.paytech.platform.support.localization.domain;

import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.paytech.platform.support.localization.domain.release.LocaleCoverageFact;
import com.paytech.platform.support.localization.domain.release.ReleaseReportFact;

/** 多语言表中 JSON 列的读写：必须覆盖语言、发布覆盖率与发布报告。 */
public final class LocalizationJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private LocalizationJson() {
    }

    /** 必须覆盖语言写为 JSON 数组。 */
    public static String writeLocales(List<String> locales) {
        return JSON.writeValueAsString(locales);
    }

    /** 读取必须覆盖语言；库中值损坏时报系统错误。 */
    public static List<String> readLocales(String json) {
        return read(json, new TypeReference<List<String>>() {
        });
    }

    /** 覆盖率写为 JSON 数组。 */
    public static String writeCoverage(List<LocaleCoverageFact> coverage) {
        return JSON.writeValueAsString(coverage);
    }

    /** 读取覆盖率。 */
    public static List<LocaleCoverageFact> readCoverage(String json) {
        return read(json, new TypeReference<List<LocaleCoverageFact>>() {
        });
    }

    /** 发布报告写为 JSON 对象。 */
    public static String writeReport(ReleaseReportFact report) {
        return JSON.writeValueAsString(report);
    }

    /** 读取发布报告。 */
    public static ReleaseReportFact readReport(String json) {
        return read(json, new TypeReference<ReleaseReportFact>() {
        });
    }

    /** 读取 JSON 列；解析失败说明库中数据被破坏。 */
    static <T> T read(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JacksonException corrupted) {
            throw new IllegalStateException("多语言 JSON 列无法解析", corrupted);
        }
    }
}
