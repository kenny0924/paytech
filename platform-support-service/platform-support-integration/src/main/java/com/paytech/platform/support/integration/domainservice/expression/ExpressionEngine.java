package com.paytech.platform.support.integration.domainservice.expression;

import java.time.Clock;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.googlecode.aviator.*;
import com.googlecode.aviator.exception.UnsupportedFeatureException;
import com.paytech.platform.support.integration.domainservice.function.ScriptFunctions;

/**
 * 外部 API 配置的 Aviator 表达式引擎：每个剖面一个独立实例，解释执行，编译结果按源码做有界缓存。
 *
 * <p>
 * 编译前先做静态检查：只允许调用剖面白名单内的函数，禁止正则匹配 {@code =~}（防回溯失控）。 编译阶段由 Aviator 按剖面的语法能力拒绝赋值、new、静态调用等；运行时实例中已移除白名单以外的具名函数。
 * 脚本剖面另外注册脚本函数；白名单函数缺失（如升级 Aviator 后改名、目录登记了未实现的脚本函数）时启动即失败。 Aviator 5.3.3 没有求值超时，耗时通过禁用 while/递归/正则、限制循环次数与源码长度来约束。
 */
@Component
public class ExpressionEngine {

    /** 公共脚本单个 for 循环的最大次数。 */
    public static final int MAX_LOOP_COUNT = 1000;

    /** 每个剖面缓存的编译结果上限。 */
    static final int CACHE_SIZE = 1024;

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern COMMENT = Pattern.compile("##[^\\n]*");
    private static final Pattern CALL = Pattern.compile("(?<![A-Za-z0-9_$.])([A-Za-z_$][A-Za-z0-9_$.]*)\\s*\\(");
    private static final Pattern NAMED_FUNCTION = Pattern.compile("[A-Za-z][A-Za-z0-9_.]*");
    private static final Set<String> KEYWORDS =
        Set.of("if", "elsif", "else", "for", "in", "while", "return", "let", "fn", "lambda", "new");
    /** Aviator 编译 for 等语句时生成的内部调用，保留在实例中但不对配置开放。 */
    private static final Set<String> COMPILER_FUNCTIONS = Set.of("with_meta");
    private static final int MAX_MESSAGE = 160;

    private final Map<ExpressionProfile, AviatorEvaluatorInstance> instances = new EnumMap<>(ExpressionProfile.class);

    /** 只做编译校验或不依赖时间的场景：脚本函数使用系统 UTC 时钟。 */
    public ExpressionEngine() {
        this(Clock.systemUTC());
    }

    /**
     * 按应用时钟创建引擎。
     *
     * @param clock 脚本函数 nowRfc3339 使用的时钟
     */
    @Autowired
    public ExpressionEngine(Clock clock) {
        for (ExpressionProfile profile : ExpressionProfile.values()) {
            instances.put(profile, newInstance(profile, clock));
        }
    }

    /**
     * 按剖面编译表达式或脚本；相同源码复用缓存的编译结果。
     *
     * @param profile
     *            白名单剖面
     * @param source
     *            源码
     * @return 编译结果，可在执行阶段以上下文求值
     * @throws ExpressionInvalidException
     *             不符合剖面或无法编译
     */
    public Expression compile(ExpressionProfile profile, String source) {
        checkCalls(profile, source);
        try {
            return instances.get(profile).compile(source, true);
        } catch (UnsupportedFeatureException exception) {
            throw new ExpressionInvalidException("不支持的语法：" + firstLine(exception));
        } catch (RuntimeException exception) {
            throw new ExpressionInvalidException("表达式无法编译：" + firstLine(exception));
        }
    }

    /** 静态检查函数调用与正则匹配；字符串字面量与注释内的文字不计入。 */
    void checkCalls(ExpressionProfile profile, String source) {
        String code = COMMENT.matcher(STRING_LITERAL.matcher(source).replaceAll("''")).replaceAll("");
        if (code.contains("=~")) {
            throw new ExpressionInvalidException("不支持正则匹配 =~");
        }
        Matcher matcher = CALL.matcher(code);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!KEYWORDS.contains(name) && !profile.getFunctions().contains(name)) {
                throw new ExpressionInvalidException("函数未开放：" + name);
            }
        }
    }

    /** 新建剖面实例：解释执行、无可用 Java 类、浮点字面量按十进制解析，注册脚本函数并移除白名单外的具名函数。 */
    static AviatorEvaluatorInstance newInstance(ExpressionProfile profile, Clock clock) {
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance(EvalMode.INTERPRETER);
        instance.setOption(Options.FEATURE_SET, profile.getFeatures());
        instance.setOption(Options.ALLOWED_CLASS_SET, Collections.emptySet());
        instance.setOption(Options.ASSIGNABLE_ALLOWED_CLASS_SET, Collections.emptySet());
        instance.setOption(Options.MAX_LOOP_COUNT, MAX_LOOP_COUNT);
        instance.setOption(Options.PUT_CAPTURING_GROUPS_INTO_ENV, false);
        instance.setOption(Options.CAPTURE_FUNCTION_ARGS, false);
        instance.setOption(Options.TRACE_EVAL, false);
        instance.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
        instance.useLRUExpressionCache(CACHE_SIZE);
        if (profile == ExpressionProfile.SCRIPT) {
            ScriptFunctions.all(clock).forEach(instance::addFunction);
        }
        List.copyOf(instance.getFuncMap().keySet()).stream().filter(name -> NAMED_FUNCTION.matcher(name).matches()
            && !profile.getFunctions().contains(name) && !COMPILER_FUNCTIONS.contains(name))
            .forEach(instance::removeFunction);
        var missing =
            profile.getFunctions().stream().filter(name -> !instance.containsFunction(name)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Aviator 缺少白名单函数: " + missing + ", 剖面: " + profile);
        }
        return instance;
    }

    /** 异常消息首行，限制长度，避免把整段源码带入问题说明。 */
    static String firstLine(RuntimeException exception) {
        String message = String.valueOf(exception.getMessage()).strip().split("\\R", 2)[0];
        return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + "…" : message;
    }
}
