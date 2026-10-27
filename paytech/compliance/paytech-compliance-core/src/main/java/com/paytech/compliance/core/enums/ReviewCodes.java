package com.paytech.compliance.core.enums;

/**
 * 审核流程依赖的稳定代码（开放或仅本模块内部使用的集合，集中定义以免散落字面量）
 * <li>只放代码真正分支依赖的值；目录型代码（原因码、材料类型等）不在此列。</li>
 */
public final class ReviewCodes {
    /** 来源命名空间：client-master Client 申请。 */
    public static final String NAMESPACE_CLIENT_APPLICATION = "CCT_CLIENT_APPLICATION";

    /** 案件类型：入驻。 */
    public static final String CASE_TYPE_ONBOARDING = "ONBOARDING";

    /** 角色：Client。 */
    public static final String ROLE_CLIENT = "CLIENT";

    /** 角色：关联人员。 */
    public static final String ROLE_RELATED_PERSON = "RELATED_PERSON";

    /** 主体类型：机构。 */
    public static final String PARTY_ORGANIZATION = "ORGANIZATION";

    /** 主体类型：个人。 */
    public static final String PARTY_INDIVIDUAL = "INDIVIDUAL";

    /** 主体角色：受审主体本身。 */
    public static final String SUBJECT_PRIMARY = "PRIMARY";

    /** 默认尽调档次（本期不自动切换档次）。 */
    public static final String TIER_STANDARD = "STANDARD";

    /** 操作方：平台运营。 */
    public static final String OPERATED_BY_PLATFORM = "PLATFORM";

    /** 人工检查执行方式与提供方。 */
    public static final String CHECK_MODE_MANUAL = "MANUAL";

    /** 材料要求 / 证据 / 检查状态。 */
    public static final String PENDING = "PENDING";

    /** 已收到待核验。 */
    public static final String RECEIVED = "RECEIVED";

    /** 已接受。 */
    public static final String ACCEPTED = "ACCEPTED";

    /** 已拒绝。 */
    public static final String REJECTED = "REJECTED";

    /** 已豁免。 */
    public static final String WAIVED = "WAIVED";

    /** 检查已请求待录入。 */
    public static final String CHECK_REQUESTED = "REQUESTED";

    /** 检查已完成。 */
    public static final String CHECK_COMPLETED = "COMPLETED";

    /** 检查通过。 */
    public static final String OUTCOME_PASS = "PASS";

    /** 筛查执行状态。 */
    public static final String RUN_STARTED = "STARTED";

    /** 筛查执行成功。 */
    public static final String RUN_SUCCEEDED = "SUCCEEDED";

    /** 筛查执行业务失败。 */
    public static final String RUN_FAILED = "FAILED";

    /** 筛查执行结果未知。 */
    public static final String RUN_TIMED_OUT = "TIMED_OUT";

    /** 命中待处置。 */
    public static final String HIT_OPEN = "OPEN";

    /** 命中已关闭。 */
    public static final String HIT_RESOLVED = "RESOLVED";

    /** 命中被新执行取代。 */
    public static final String HIT_SUPERSEDED = "SUPERSEDED";

    /** 制裁名单类型。 */
    public static final String LIST_SANCTIONS = "SANCTIONS";

    /** 推荐结论：批准。 */
    public static final String RECOMMEND_APPROVE = "APPROVE";

    /** 推荐结论：拒绝。 */
    public static final String RECOMMEND_REJECT = "REJECT";

    /** 风险因子来源：审核员。 */
    public static final String FACTOR_SOURCE_REVIEWER = "REVIEWER";

    private ReviewCodes() {
    }
}
