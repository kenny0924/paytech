package com.paytech.operations.console.bff.ext.rest;

/** 认证 Controller 与浏览器防护共同使用的入站路径契约。 @author Codex */
public final class OperationsAuthenticationPaths {
    public static final String ROOT = "/auth/v1";
    public static final String LOGIN = ROOT + "/login";
    public static final String REFRESH = ROOT + "/refresh";

    private OperationsAuthenticationPaths() {
    }
}
