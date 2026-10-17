package com.paytech.foundation.jsoncanonicalization.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

class JsonCanonicalizationContractAutoTest {

    private static final String API_PACKAGE = "com.paytech.foundation.jsoncanonicalization.api";
    private static final String SAFE_MESSAGE = "JSON canonicalization failed";
    private static final List<String> FORBIDDEN_PACKAGE_PREFIXES = List.of(
        "tools.jackson.",
        "org.erdtman."
    );

    @Test
    void test_apiPackageContainsExactlyTheTwoApprovedPublicTypes() throws Exception {
        URI classesLocation = Rfc8785JsonCanonicalizer.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI();
        Path apiDirectory = Path.of(classesLocation).resolve(API_PACKAGE.replace('.', '/'));

        assertTrue(Files.isDirectory(apiDirectory));
        List<String> publicTypes;
        try (Stream<Path> classFiles = Files.list(apiDirectory)) {
            publicTypes = classFiles
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .map(path -> loadApiClass(path.getFileName().toString()))
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getName)
                .sorted()
                .toList();
        }

        assertEquals(
            List.of(
                "com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException",
                "com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer"
            ),
            publicTypes
        );
    }

    @Test
    void test_canonicalizerHasTheExactApprovedPublicSurface() throws Exception {
        Class<Rfc8785JsonCanonicalizer> type = Rfc8785JsonCanonicalizer.class;

        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(0, type.getConstructors().length);
        assertEquals(0, publicDeclaredFields(type).size());
        assertEquals(0, publicOrProtectedDeclaredNestedTypes(type).size());

        Method canonicalize = type.getDeclaredMethod("canonicalizeObject", byte[].class);
        assertEquals(List.of(canonicalize), publicDeclaredMethods(type));
        assertTrue(Modifier.isStatic(canonicalize.getModifiers()));
        assertEquals(byte[].class, canonicalize.getReturnType());
        assertEquals(List.of(byte[].class), Arrays.asList(canonicalize.getParameterTypes()));
        assertEquals(0, canonicalize.getExceptionTypes().length);
    }

    @Test
    void test_exceptionHasTheExactApprovedPublicSurface() {
        Class<JsonCanonicalizationException> type = JsonCanonicalizationException.class;

        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(RuntimeException.class, type.getSuperclass());
        assertEquals(0, publicDeclaredFields(type).size());
        assertEquals(0, publicDeclaredMethods(type).size());
        assertEquals(0, publicOrProtectedDeclaredNestedTypes(type).size());

        Set<List<Class<?>>> constructorSignatures = new HashSet<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            constructorSignatures.add(Arrays.asList(constructor.getParameterTypes()));
        }
        assertEquals(
            Set.<List<Class<?>>>of(List.of(), List.of(Throwable.class)),
            constructorSignatures
        );
    }

    @Test
    void test_exceptionConstructorsUseSafeMessageAndRetainIdenticalCause() {
        JsonCanonicalizationException withoutCause = new JsonCanonicalizationException();
        IllegalStateException cause = new IllegalStateException("internal diagnostic");
        JsonCanonicalizationException withCause = new JsonCanonicalizationException(cause);

        assertEquals(SAFE_MESSAGE, withoutCause.getMessage());
        assertNull(withoutCause.getCause());
        assertEquals(SAFE_MESSAGE, withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }

    @Test
    void test_publicAndProtectedSignaturesRecursivelyExcludeImplementationPackages() {
        assertPublicAndProtectedSurfaceHasNoForbiddenTypes(Rfc8785JsonCanonicalizer.class);
        assertPublicAndProtectedSurfaceHasNoForbiddenTypes(JsonCanonicalizationException.class);
    }

    @Test
    void test_jacksonSyntaxFailureIsWrappedWithCauseAndSafeOuterMessage() {
        JsonCanonicalizationException exception = assertThrows(
            JsonCanonicalizationException.class,
            () -> Rfc8785JsonCanonicalizer.canonicalizeObject(
                "{\"canary-property\":}".getBytes(StandardCharsets.UTF_8)
            )
        );

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertFalse(exception.getMessage().contains("canary-property"));
        assertNotNull(exception.getCause());
    }

    @Test
    void test_directPreconditionFailuresKeepSafeMessageWithoutSyntheticCause() {
        assertDirectPreconditionFailure(null);
        assertDirectPreconditionFailure(new byte[0]);
        assertDirectPreconditionFailure(new byte[1_048_577]);
    }

    private static Class<?> loadApiClass(String classFileName) {
        String binaryName = API_PACKAGE + '.' + classFileName.substring(0, classFileName.length() - 6);
        try {
            return Class.forName(binaryName, false, Rfc8785JsonCanonicalizer.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to inspect compiled API type " + binaryName, exception);
        }
    }

    private static List<Field> publicDeclaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()))
            .toList();
    }

    private static List<Method> publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .toList();
    }

    private static List<Class<?>> publicOrProtectedDeclaredNestedTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredClasses())
            .filter(nested -> isPublicOrProtected(nested.getModifiers()))
            .toList();
    }

    private static void assertDirectPreconditionFailure(byte[] input) {
        JsonCanonicalizationException exception = assertThrows(
            JsonCanonicalizationException.class,
            () -> Rfc8785JsonCanonicalizer.canonicalizeObject(input)
        );

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertNull(exception.getCause());
    }

    private static void assertPublicAndProtectedSurfaceHasNoForbiddenTypes(Class<?> rootType) {
        inspectClassHierarchy(rootType, new HashSet<>());
    }

    private static void inspectClassHierarchy(Class<?> type, Set<Class<?>> visitedClasses) {
        if (type == null || !visitedClasses.add(type)) {
            return;
        }

        inspectType(type, new HashSet<>());
        inspectType(type.getGenericSuperclass(), new HashSet<>());
        for (Type genericInterface : type.getGenericInterfaces()) {
            inspectType(genericInterface, new HashSet<>());
        }
        for (TypeVariable<?> typeParameter : type.getTypeParameters()) {
            inspectType(typeParameter, new HashSet<>());
        }
        for (Field field : type.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers())) {
                inspectMemberOwner(field);
                inspectType(field.getGenericType(), new HashSet<>());
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isPublicOrProtected(constructor.getModifiers())) {
                inspectExecutable(constructor.getGenericParameterTypes(), constructor.getGenericExceptionTypes());
                for (TypeVariable<?> typeParameter : constructor.getTypeParameters()) {
                    inspectType(typeParameter, new HashSet<>());
                }
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers())) {
                inspectMemberOwner(method);
                inspectType(method.getGenericReturnType(), new HashSet<>());
                inspectExecutable(method.getGenericParameterTypes(), method.getGenericExceptionTypes());
                for (TypeVariable<?> typeParameter : method.getTypeParameters()) {
                    inspectType(typeParameter, new HashSet<>());
                }
            }
        }
        for (Class<?> nestedType : type.getDeclaredClasses()) {
            if (isPublicOrProtected(nestedType.getModifiers())) {
                inspectClassHierarchy(nestedType, visitedClasses);
            }
        }

        inspectClassHierarchy(type.getSuperclass(), visitedClasses);
        for (Class<?> implementedInterface : type.getInterfaces()) {
            inspectClassHierarchy(implementedInterface, visitedClasses);
        }
    }

    private static void inspectExecutable(Type[] parameterTypes, Type[] exceptionTypes) {
        for (Type parameterType : parameterTypes) {
            inspectType(parameterType, new HashSet<>());
        }
        for (Type exceptionType : exceptionTypes) {
            inspectType(exceptionType, new HashSet<>());
        }
    }

    private static void inspectMemberOwner(Member member) {
        inspectType(member.getDeclaringClass(), new HashSet<>());
    }

    private static void inspectType(Type type, Set<Type> visitedTypes) {
        if (type == null || !visitedTypes.add(type)) {
            return;
        }

        if (type instanceof Class<?> classType) {
            assertTypeNameAllowed(classType.getName());
            if (classType.isArray()) {
                inspectType(classType.getComponentType(), visitedTypes);
            }
        } else if (type instanceof ParameterizedType parameterizedType) {
            inspectType(parameterizedType.getRawType(), visitedTypes);
            inspectType(parameterizedType.getOwnerType(), visitedTypes);
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                inspectType(argument, visitedTypes);
            }
        } else if (type instanceof GenericArrayType genericArrayType) {
            inspectType(genericArrayType.getGenericComponentType(), visitedTypes);
        } else if (type instanceof TypeVariable<?> typeVariable) {
            assertTypeNameAllowed(typeVariable.getName());
            for (Type bound : typeVariable.getBounds()) {
                inspectType(bound, visitedTypes);
            }
        } else if (type instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getLowerBounds()) {
                inspectType(bound, visitedTypes);
            }
            for (Type bound : wildcardType.getUpperBounds()) {
                inspectType(bound, visitedTypes);
            }
        } else {
            assertTypeNameAllowed(type.getTypeName());
        }
    }

    private static void assertTypeNameAllowed(String typeName) {
        for (String forbiddenPrefix : FORBIDDEN_PACKAGE_PREFIXES) {
            assertFalse(
                typeName.startsWith(forbiddenPrefix),
                () -> "Forbidden implementation type leaked through public/protected API: " + typeName
            );
        }
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }
}
