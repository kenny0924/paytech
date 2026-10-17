package com.paytech.platform.support.test.builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Operations 人员夹具的场景参数；只描述数据，不访问数据库。
 *
 * <p>默认是内部租户 ACTIVE 成员、持有平台管理员角色、已修改初始密码的普通账号，并带一个 ACTIVE 会话。
 */
public final class OperatorBuilder {

    /** 夹具统一的合成初始密码，满足生产密码规则。 */
    public static final String PASSWORD = "Mock-Operator-Passw0rd!";

    private long accountId = nextId();
    private String email = "operator-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    private String type = "HUMAN";
    private String accountStatus = "ACTIVE";
    private Instant lockedUntil;
    private String password = PASSWORD;
    private boolean forceChange;
    private String passwordStatus = "ACTIVE";
    private boolean member = true;
    private String memberStatus = "ACTIVE";
    private final List<Long> roleIds = new ArrayList<>(List.of(OperationsCatalog.ADMIN_ROLE_ID));
    private boolean session = true;
    private String sessionStatus = "ACTIVE";

    private OperatorBuilder() {
    }

    /** 平台管理员。 */
    public static OperatorBuilder administrator() {
        return new OperatorBuilder();
    }

    /** 只持有运营只读角色的普通人员。 */
    public static OperatorBuilder viewer() {
        return new OperatorBuilder().roles(OperationsCatalog.VIEWER_ROLE_ID);
    }

    /** 初始化账号：登录名 admin、无成员关系、BOOTSTRAP 会话，默认仍须修改初始密码。 */
    public static OperatorBuilder bootstrap() {
        OperatorBuilder builder = new OperatorBuilder();
        builder.type = "BOOTSTRAP_ADMIN";
        builder.email = null;
        builder.member = false;
        builder.roleIds.clear();
        builder.forceChange = true;
        return builder;
    }

    /** 替换授予的角色，可为空。 */
    public OperatorBuilder roles(Long... roleIds) {
        this.roleIds.clear();
        this.roleIds.addAll(List.of(roleIds));
        return this;
    }

    public OperatorBuilder email(String email) {
        this.email = email;
        return this;
    }

    public OperatorBuilder accountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
        return this;
    }

    public OperatorBuilder lockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
        return this;
    }

    public OperatorBuilder password(String password) {
        this.password = password;
        return this;
    }

    public OperatorBuilder forceChange(boolean forceChange) {
        this.forceChange = forceChange;
        return this;
    }

    public OperatorBuilder passwordStatus(String passwordStatus) {
        this.passwordStatus = passwordStatus;
        return this;
    }

    /** 不创建内部租户成员关系。 */
    public OperatorBuilder withoutMember() {
        this.member = false;
        return this;
    }

    public OperatorBuilder memberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
        return this;
    }

    /** 不预置会话，用于登录场景。 */
    public OperatorBuilder withoutSession() {
        this.session = false;
        return this;
    }

    public OperatorBuilder sessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
        return this;
    }

    /** 构建不可变规格，供本地 Mocker 插入。 */
    public Spec build() {
        return new Spec(accountId, email, type, accountStatus, lockedUntil, password, forceChange, passwordStatus,
            member, memberStatus, List.copyOf(roleIds), session, sessionStatus);
    }

    /** 测试侧生成的 19 位正整数标识。 */
    public static long nextId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000_000_000_000L, Long.MAX_VALUE);
    }

    /** 人员夹具的不可变规格。 */
    public record Spec(long accountId, String email, String type, String accountStatus, Instant lockedUntil,
        String password, boolean forceChange, String passwordStatus, boolean member, String memberStatus,
        List<Long> roleIds, boolean session, String sessionStatus) {
    }

    /**
     * 已插入的人员夹具。
     *
     * @param refreshToken 预置会话的原始刷新令牌；没有会话时为 null
     */
    public record Operator(long accountId, String email, Long memberId, Long sessionId, String refreshToken,
        String password) {

        /** Gateway 转发的可信账号 Header 值。 */
        public String actor() {
            return Long.toString(accountId);
        }

        /** Gateway 转发的可信会话 Header 值。 */
        public String session() {
            return Long.toString(sessionId);
        }
    }
}
