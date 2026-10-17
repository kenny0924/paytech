package com.paytech.risk.decision.domain.release;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FAILED Static Check 的无 Literal、可持久化错误 Code 与摘要合同。 */
public final class StaticCheckErrorSummary {

    private static final int MAX_SUMMARY_LENGTH = 1_000;
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SUMMARY = Pattern.compile(
        "first=([A-Z][A-Z0-9_]{0,63})@([^;]+); violation_count=([1-9][0-9]*)"
    );
    private static final Pattern FIELD_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]*");
    private static final Pattern NODE_COORDINATE_PATH = Pattern.compile("node[._][1-9][0-9]*");
    private static final Set<String> ROOT_PATHS = Set.of(
        "actionParameters",
        "businessCoordinates",
        "closure",
        "duplicates",
        "features",
        "ids",
        "inputFields",
        "nodes",
        "obligations",
        "operands",
        "persistedActions",
        "point",
        "release",
        "ruleActions",
        "rules",
        "ruleSetBindings",
        "ruleSets"
    );

    private StaticCheckErrorSummary() {
    }

    /** 是否恰好符合当前 Validator 的稳定 Code、结构路径和正违规计数语法。 */
    public static boolean isCanonical(String errorCode, String errorSummary) {
        if (errorCode == null || !ERROR_CODE.matcher(errorCode).matches()
            || errorSummary == null || errorSummary.length() > MAX_SUMMARY_LENGTH) {
            return false;
        }
        Matcher matcher = SUMMARY.matcher(errorSummary);
        if (!matcher.matches() || !errorCode.equals(matcher.group(1)) || !safePath(matcher.group(2))) {
            return false;
        }
        try {
            return Integer.parseInt(matcher.group(3)) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean safePath(String path) {
        if ("-".equals(path) || ROOT_PATHS.contains(path) || NODE_COORDINATE_PATH.matcher(path).matches()) {
            return true;
        }
        int separator = path.indexOf('/');
        if (separator <= 0 || separator != path.lastIndexOf('/')) {
            return false;
        }
        String root = path.substring(0, separator);
        String child = path.substring(separator + 1);
        if ("inputFields".equals(root)) {
            return FIELD_CODE.matcher(child).matches();
        }
        return ("actionVersions".equals(root) || "nodes".equals(root)
            || "operands".equals(root) || "rules".equals(root))
            && POSITIVE_ID.matcher(child).matches();
    }
}
