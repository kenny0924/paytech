package com.paytech.arch.support;

import com.paytech.arch.rule.RuleViolation;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 已登记的既有偏差清单（{@value #DEFAULT_RESOURCE}）：规则照常报告，测试据此区分新增违规与已登记偏差。
 * <li>{@link #unregistered(Collection)}：未登记的违规，必须为空。</li>
 * <li>{@link #missing(Collection, Predicate)}：属于被检查范围却未再出现的登记，必须为空（偏差消除后删除登记）。</li>
 *
 * @author Claude
 */
public final class KnownDeviations {

    /**
     * 打包在本 JAR 中的默认清单资源。
     */
    public static final String DEFAULT_RESOURCE = "/known-deviations.yaml";

    private final Set<RuleViolation> entries;

    private KnownDeviations(Set<RuleViolation> entries) {
        this.entries = Set.copyOf(entries);
    }

    /**
     * 读取本 JAR 内的默认清单。
     *
     * @return 偏差清单
     */
    public static KnownDeviations loadDefault() {
        try (InputStream input = KnownDeviations.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("类路径缺少偏差清单: " + DEFAULT_RESOURCE);
            }
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("偏差清单读取失败: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * 解析清单 YAML：{@code deviations: [{rule, subject, message}]}。
     *
     * @param yaml YAML 文本
     * @return 偏差清单
     */
    public static KnownDeviations parse(String yaml) {
        JsonNode root = YAMLMapper.builder().build().readTree(yaml);
        Set<RuleViolation> entries = new LinkedHashSet<>();
        for (JsonNode entry : root.path("deviations").values()) {
            String rule = entry.path("rule").asString();
            String subject = entry.path("subject").asString();
            String message = entry.path("message").asString();
            if (rule.isBlank() || subject.isBlank() || message.isBlank()) {
                throw new IllegalStateException("偏差清单条目缺少 rule / subject / message: " + entry);
            }
            if (!entries.add(new RuleViolation(rule, subject, message))) {
                throw new IllegalStateException("偏差清单条目重复: " + entry);
            }
        }
        return new KnownDeviations(entries);
    }

    /**
     * 全部登记条目。
     *
     * @return 条目
     */
    public Set<RuleViolation> entries() {
        return entries;
    }

    /**
     * 未登记的违规。
     *
     * @param violations 实际违规
     * @return 不在清单中的违规，保持原顺序
     */
    public List<RuleViolation> unregistered(Collection<RuleViolation> violations) {
        return violations.stream().filter(violation -> !entries.contains(violation)).toList();
    }

    /**
     * 属于被检查范围、却没有出现在实际违规中的登记条目。
     *
     * @param violations 实际违规
     * @param inScope 条目是否属于本次检查范围
     * @return 已过期的登记
     */
    public List<RuleViolation> missing(Collection<RuleViolation> violations, Predicate<RuleViolation> inScope) {
        List<RuleViolation> missing = new ArrayList<>();
        for (RuleViolation entry : entries) {
            if (inScope.test(entry) && !violations.contains(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }
}
