package com.paytech.foundation.redis.test.fixture;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.tools.ToolProvider;

public final class NoParameterMetadataCompiler {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private NoParameterMetadataCompiler() {}

    public static Method compileWithoutParameters(String expression, String parameterType) throws Exception {
        return compile(expression, parameterType, false);
    }

    public static Method compileWithParameters(String expression, String parameterType) throws Exception {
        return compile(expression, parameterType, true);
    }

    private static Method compile(String expression, String parameterType, boolean parameters) throws Exception {
        String className = "DynamicResourceLockFixture" + SEQUENCE.incrementAndGet();
        Path output = Files.createTempDirectory("resource-lock-fixture-");
        Path source = output.resolve(className + ".java");
        Files.writeString(source, """
            import com.paytech.foundation.redis.api.ResourceLock;
            public class %s {
                @ResourceLock(prefix = \"operations:account\", key = \"%s\")
                public void invoke(%s accountId) {}
            }
            """.formatted(className, javaLiteral(expression), parameterType));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var options = new java.util.ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"), "-d",
            output.toString()));
        if (parameters) {
            options.add("-parameters");
        }
        int result = compiler.run(null, null, null, concat(options, source.toString()));
        if (result != 0) {
            throw new IOException("Dynamic fixture compilation failed: " + result);
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()},
            NoParameterMetadataCompiler.class.getClassLoader());
        Class<?> type = Class.forName(className, true, loader);
        return type.getDeclaredMethod("invoke", Class.forName(parameterType));
    }

    private static String[] concat(List<String> values, String source) {
        var all = new java.util.ArrayList<>(values);
        all.add(source);
        return all.toArray(String[]::new);
    }

    private static String javaLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
