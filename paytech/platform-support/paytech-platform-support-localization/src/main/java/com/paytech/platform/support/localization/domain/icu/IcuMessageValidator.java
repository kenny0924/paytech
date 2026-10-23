package com.paytech.platform.support.localization.domain.icu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.icu.text.MessagePattern;
import com.ibm.icu.text.MessagePattern.ArgType;
import com.ibm.icu.text.MessagePattern.Part;

/**
 * ICU MessageFormat 校验（技术方案 3.4、需求 B5）：只用 ICU4J {@link MessagePattern} 解析，不在服务端格式化文案。
 * <li>语法必须可解析；plural、select、selectordinal 必须有 other 分支；翻译的参数名集合须与英文基准一致（plural 的 # 不算参数）。</li>
 * <li>前端运行时 intl-messageformat 只支持 number、date、time 三种简单参数类型且不支持 choice，其他类型同样拒绝，避免发布后无法格式化。</li>
 * <li>问题带字符位置与中文原因；纯 Java，无状态、线程安全。</li>
 */
public final class IcuMessageValidator {

    /** ICU4J 异常消息中的位置标记。 */
    private static final Pattern INDEX = Pattern.compile("\\[at pattern index (\\d+)]");

    /** 缺少 other 的选择类参数开头：{名称, 类型。 */
    private static final Pattern SELECT_ARGUMENT = Pattern.compile("\\{\\s*[^{}\\s,]+\\s*,\\s*(plural|selectordinal|select)\\b");

    /** 前端运行时支持的简单参数类型。 */
    private static final Set<String> SIMPLE_TYPES = Set.of("number", "date", "time");

    /** ICU4J 英文错误片段到中文原因；按顺序匹配第一个包含的片段。 */
    private static final Map<String, String> REASONS = reasons();

    private IcuMessageValidator() {
    }

    /**
     * 校验英文基准：语法与 other 分支。
     *
     * @param source 英文基准
     * @return 问题；没有问题为空列表
     */
    public static List<IcuProblem> validateSource(String source) {
        ParsedMessage parsed = parse(source);
        return parsed.problems();
    }

    /**
     * 校验翻译：语法、other 分支，以及参数名集合与基准一致。
     * <li>基准本身无法解析时只校验翻译自身，不比较参数。</li>
     *
     * @param source 英文基准
     * @param translation 翻译
     * @return 问题；没有问题为空列表
     */
    public static List<IcuProblem> validateTranslation(String source, String translation) {
        ParsedMessage target = parse(translation);
        if (!target.problems().isEmpty()) {
            return target.problems();
        }
        ParsedMessage base = parse(source);
        if (!base.problems().isEmpty()) {
            return List.of();
        }
        return parameterProblems(base.arguments(), target.arguments());
    }

    /**
     * 文案中的参数名，按首次出现排列；无法解析时为空列表。
     *
     * @param text 文案
     * @return 参数名
     */
    public static List<String> parameterNames(String text) {
        ParsedMessage parsed = parse(text);
        return parsed.problems().isEmpty() ? List.copyOf(parsed.arguments().keySet()) : List.of();
    }

    /**
     * 解析文案：语法错误转换为一条问题；成功时收集参数名及首次位置，并检查选择类参数的 other 分支。
     *
     * @param text 文案
     * @return 解析结果
     */
    static ParsedMessage parse(String text) {
        MessagePattern pattern = new MessagePattern(MessagePattern.ApostropheMode.DOUBLE_OPTIONAL);
        try {
            pattern.parse(text);
        } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
            return new ParsedMessage(new LinkedHashMap<>(), List.of(syntaxProblem(text, failure.getMessage())));
        }
        Map<String, Integer> arguments = new LinkedHashMap<>();
        List<IcuProblem> problems = new ArrayList<>();
        for (int index = 0; index < pattern.countParts(); index++) {
            Part part = pattern.getPart(index);
            if (part.getType() != Part.Type.ARG_START) {
                continue;
            }
            arguments.putIfAbsent(pattern.getSubstring(pattern.getPart(index + 1)), part.getIndex());
            IcuProblem problem = argumentProblem(pattern, index);
            if (problem != null) {
                problems.add(problem);
            }
        }
        return new ParsedMessage(arguments, problems);
    }

    /**
     * 单个参数的结构问题：choice 不支持、简单类型不在 number/date/time 之内、选择类参数缺少 other。
     *
     * @param pattern 已解析的模式
     * @param argStart ARG_START 的部件下标
     * @return 问题；没有问题为 null
     */
    static IcuProblem argumentProblem(MessagePattern pattern, int argStart) {
        Part part = pattern.getPart(argStart);
        ArgType type = part.getArgType();
        if (type == ArgType.CHOICE) {
            return new IcuProblem(part.getIndex(), "不支持 choice 参数，请改用 plural 或 select");
        }
        if (type == ArgType.SIMPLE) {
            String typeName = pattern.getSubstring(pattern.getPart(argStart + 2));
            return SIMPLE_TYPES.contains(typeName) ? null
                : new IcuProblem(part.getIndex(), "不支持的参数类型 " + typeName + "，只允许 number、date、time");
        }
        if (isSelectable(type) && !hasOtherBranch(pattern, argStart)) {
            return new IcuProblem(part.getIndex(), argumentLabel(type) + " 缺少 other 分支");
        }
        return null;
    }

    /** 是否为必须带 other 分支的选择类参数。 */
    static boolean isSelectable(ArgType type) {
        return type == ArgType.PLURAL || type == ArgType.SELECT || type == ArgType.SELECTORDINAL;
    }

    /**
     * 选择类参数在自身层级是否有 other 选择器；嵌套消息按其 MSG_LIMIT 整体跳过。
     *
     * @param pattern 已解析的模式
     * @param argStart ARG_START 的部件下标
     * @return 有 other 返回 true
     */
    static boolean hasOtherBranch(MessagePattern pattern, int argStart) {
        int limit = pattern.getLimitPartIndex(argStart);
        for (int index = argStart + 1; index < limit; index++) {
            Part part = pattern.getPart(index);
            if (part.getType() == Part.Type.MSG_START) {
                index = pattern.getLimitPartIndex(index);
                continue;
            }
            if (part.getType() == Part.Type.ARG_SELECTOR && "other".equals(pattern.getSubstring(part))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 参数名集合比较：翻译缺少的参数定位在开头，多出的参数定位在其首次出现处。
     *
     * @param base 基准参数及位置
     * @param target 翻译参数及位置
     * @return 问题
     */
    static List<IcuProblem> parameterProblems(Map<String, Integer> base, Map<String, Integer> target) {
        List<IcuProblem> problems = new ArrayList<>();
        for (String name : base.keySet()) {
            if (!target.containsKey(name)) {
                problems.add(new IcuProblem(-1, "缺少参数 {" + name + "}，英文基准包含该参数"));
            }
        }
        for (Map.Entry<String, Integer> entry : target.entrySet()) {
            if (!base.containsKey(entry.getKey())) {
                problems.add(new IcuProblem(entry.getValue(), "多出参数 {" + entry.getKey() + "}，英文基准没有该参数"));
            }
        }
        return problems;
    }

    /**
     * 把 ICU4J 的英文语法错误转换为带位置的中文问题；ICU4J 未给出位置时按错误类别自行定位。
     *
     * @param text 被解析的文案
     * @param message ICU4J 异常消息
     * @return 问题
     */
    static IcuProblem syntaxProblem(String text, String message) {
        String detail = message == null ? "" : message;
        Matcher matcher = INDEX.matcher(detail);
        int position = matcher.find() ? Integer.parseInt(matcher.group(1)) : locate(text, detail);
        for (Map.Entry<String, String> reason : REASONS.entrySet()) {
            if (detail.contains(reason.getKey())) {
                return new IcuProblem(position, reason.getValue());
            }
        }
        return new IcuProblem(position, "ICU MessageFormat 语法错误");
    }

    /**
     * ICU4J 不带位置的两类错误：未闭合的左花括号定位到最外层未闭合处，缺少 other 定位到第一个选择类参数。
     *
     * @param text 文案
     * @param detail ICU4J 异常消息
     * @return 位置；无法定位为 -1
     */
    static int locate(String text, String detail) {
        if (detail.startsWith("Unmatched '{'")) {
            return unmatchedOpenBrace(text);
        }
        if (detail.startsWith("Missing 'other'")) {
            Matcher matcher = SELECT_ARGUMENT.matcher(text);
            return matcher.find() ? matcher.start() : -1;
        }
        return -1;
    }

    /**
     * 最外层未闭合的左花括号位置；只按花括号计数，不处理撇号转义。
     *
     * @param text 文案
     * @return 位置；全部闭合时为 -1
     */
    static int unmatchedOpenBrace(String text) {
        Deque<Integer> open = new ArrayDeque<>();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '{') {
                open.push(index);
            } else if (current == '}' && !open.isEmpty()) {
                open.pop();
            }
        }
        return open.isEmpty() ? -1 : open.peekLast();
    }

    /** 参数类型的中文名称。 */
    static String argumentLabel(ArgType type) {
        return switch (type) {
            case PLURAL -> "plural 参数";
            case SELECTORDINAL -> "selectordinal 参数";
            default -> "select 参数";
        };
    }

    /** ICU4J 错误前缀与中文原因。 */
    private static Map<String, String> reasons() {
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put("Unmatched '{' braces", "花括号不匹配，缺少右花括号");
        reasons.put("Missing 'other' keyword in plural", "plural 参数缺少 other 分支");
        reasons.put("Missing 'other' keyword in selectordinal", "selectordinal 参数缺少 other 分支");
        reasons.put("Missing 'other' keyword in select", "select 参数缺少 other 分支");
        reasons.put("Bad argument syntax", "参数语法错误");
        reasons.put("too long", "名称过长");
        reasons.put("too large", "数值过大");
        reasons.put("No message fragment", "选择分支后缺少 {...} 文案");
        reasons.put("pattern syntax", "选择分支语法错误");
        reasons.put("numeric value", "数值写法不合法");
        return reasons;
    }

    /**
     * 解析结果。
     *
     * @param arguments 参数名到首次出现位置
     * @param problems 问题
     */
    record ParsedMessage(Map<String, Integer> arguments, List<IcuProblem> problems) {
    }
}
