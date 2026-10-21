package com.paytech.channel.connector.protocol.domainservice.expression;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.googlecode.aviator.Feature;
import com.paytech.channel.connector.protocol.domain.RegisteredFunctionCatalog;

/**
 * Aviator 白名单剖面（研发计划第 3 步第 4 节）：只开放列出的语法与函数，其余全部拒绝。
 *
 * <p>两个剖面都不开放模块、use、new、静态字段与方法、Lambda、fn、while、异常处理与内部变量，也不允许任何 Java 类。
 */
public enum ExpressionProfile {

    /** 条件与取值表达式：只读，不允许赋值与语句。 */
    READ_ONLY(Feature.asSet(), Functions.READ_ONLY),

    /** 公共脚本：允许赋值、let、if、有界 for 与词法作用域，可调用脚本函数。 */
    SCRIPT(Feature.asSet(Feature.Assignment, Feature.Let, Feature.If, Feature.ForLoop, Feature.LexicalScope),
        Functions.SCRIPT);

    /** 开放的语法能力。 */
    private final Set<Feature> features;

    /** 允许调用的函数名。 */
    private final Set<String> functions;

    ExpressionProfile(Set<Feature> features, Set<String> functions) {
        this.features = Set.copyOf(features);
        this.functions = functions;
    }

    public Set<Feature> getFeatures() {
        return features;
    }

    public Set<String> getFunctions() {
        return functions;
    }

    /** 函数白名单；脚本函数由 ExpressionEngine 注册到脚本剖面实例。 */
    static final class Functions {

        /** 只读纯计算的内置函数；不含正则、随机数、时钟、打印、eval/load/require 与修改集合的函数。 */
        static final Set<String> READ_ONLY = Set.of("string.contains", "string.startsWith", "string.endsWith",
            "string.indexOf", "string.length", "string.substring", "string.join", "math.abs", "math.ceil", "math.floor",
            "math.round", "long", "double", "decimal", "bigint", "boolean", "str", "type", "count", "include",
            "is_empty", "is_def", "min", "max", "seq.get", "seq.contains_key");

        /** 公共脚本可用的注册函数，以注册函数目录为准。 */
        static final Set<String> SCRIPT_FUNCTIONS = RegisteredFunctionCatalog.codes(RegisteredFunctionCatalog.SCRIPT);

        /** 公共脚本：只读函数、有界循环所需的 range、构造与修改本地集合的函数及脚本函数。 */
        static final Set<String> SCRIPT = Stream.of(READ_ONLY,
                Set.of("range", "seq.map", "seq.list", "seq.put", "seq.add", "seq.remove"), SCRIPT_FUNCTIONS)
            .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

        private Functions() {
        }
    }
}
