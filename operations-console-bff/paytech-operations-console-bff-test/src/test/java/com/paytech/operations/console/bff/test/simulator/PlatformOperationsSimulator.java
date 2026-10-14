package com.paytech.operations.console.bff.test.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.authentication.response.AccountCreatedResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.api.authentication.request.ChangePasswordRequestBody;
import com.paytech.platform.support.api.authentication.request.FirstAdministratorRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.response.OperationsContextResponseBody;
import com.paytech.platform.support.api.authentication.request.OperationsRefreshRequestBody;
import com.paytech.platform.support.api.authorization.response.AuthorizationDecisionResponseBody;
import com.paytech.platform.support.api.authorization.request.AuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.ConsoleResource;
import com.paytech.platform.support.api.authorization.response.NavigationResponseBody;
import com.paytech.platform.support.api.operations.response.AccountResponseBody;
import com.paytech.platform.support.api.operations.response.AuditEventResponseBody;
import com.paytech.platform.support.api.operations.request.CreateAccountRequestBody;
import com.paytech.platform.support.api.operations.request.DeleteAccountRequestBody;
import com.paytech.platform.support.api.operations.request.EnableAccountRequestBody;
import com.paytech.platform.support.api.operations.response.RoleSummary;
import com.paytech.platform.support.api.operations.request.CreateRoleRequestBody;
import com.paytech.platform.support.api.operations.request.DeleteRoleRequestBody;
import com.paytech.platform.support.api.operations.response.RoleEmployeeCount;
import com.paytech.platform.support.api.operations.request.RoleEmployeeCountsRequestBody;
import com.paytech.platform.support.api.operations.response.RoleEmployeeCountsResponseBody;
import com.paytech.platform.support.api.operations.request.UpdateRoleRequestBody;
import com.paytech.platform.support.api.operations.response.RoleResponseBody;
import com.paytech.platform.support.api.operations.response.PermissionCatalogResponseBody;
import com.paytech.platform.support.api.operations.response.PermissionSummary;
import com.paytech.platform.support.api.operations.response.RoleResourcesResponseBody;
import com.paytech.platform.support.api.operations.response.RoleResource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** 通过真实 HTTP 提供最小账号、Session、Refresh 轮换和授权状态，不替换 BFF 生产 Bean。 */
public final class PlatformOperationsSimulator implements AutoCloseable {
    public static final String ACCOUNT_ID = "account_10001";
    public static final String RISK_ACCOUNT_ID = "10001";
    public static final String SESSION_ID = "session_10001";
    public static final String EMAIL = "sensitive.operator@example.test";
    public static final String PASSWORD = "sensitive-password-for-simulator";
    public static final String AUTH_PATH = "/authentication/v1/operations";
    public static final String AUTHORIZE_PATH = "/authorization/v1/operations/authorize";
    public static final String ACCOUNTS_PATH = "/operations/v1/accounts";
    public static final String ACCOUNT_CREATE_PERMISSION_ID = "900000000000000526";
    public static final String ACCOUNT_DISABLE_PERMISSION_ID = "900000000000000527";
    public static final String ACCOUNT_ASSIGN_ROLES_PERMISSION_ID = "900000000000000528";
    public static final String ACCOUNT_DELETE_PERMISSION_ID = "900000000000000532";
    public static final String ACCOUNT_ENABLE_PERMISSION_ID = "900000000000000533";
    public static final String ROLE_CREATE_PERMISSION_ID = "900000000000000529";
    public static final String ROLE_UPDATE_PERMISSION_ID = "900000000000000530";
    public static final String ROLE_DELETE_PERMISSION_ID = "900000000000000531";
    public static final String ROLE_COUNTS_PATH = "/operations/v1/roles/employee-counts";
    public static final String AUDIT_PATH = "/operations/v1/audit-events";
    public static final String AUDIT_READ_PERMISSION_ID = "900000000000000523";
    public static final String UPSTREAM_REQUEST_ID = "platform-upstream-request";
    public static final String UPSTREAM_DETAIL_CANARY = "platform-private-detail-canary";
    public static final String UPSTREAM_EXTENSION_CANARY = "platform-private-extension-canary";

    private static final long DELAY_RELEASE_TIMEOUT_SECONDS = 10;
    private static final Map<String, String> RISK_ROUTE_PERMISSIONS = Map.ofEntries(
        Map.entry("operations-risk-variable-manage-context", "900000000000000536"),
        Map.entry("operations-risk-variable-list", "900000000000000536"),
        Map.entry("operations-risk-variable-detail", "900000000000000536"),
        Map.entry("operations-risk-variable-members", "900000000000000536"),
        Map.entry("operations-risk-variable-references", "900000000000000536"),
        Map.entry("operations-risk-variable-create", "900000000000031101"),
        Map.entry("operations-risk-variable-update", "900000000000031101"),
        Map.entry("operations-risk-variable-members-replace", "900000000000031101"),
        Map.entry("operations-risk-variable-delete", "900000000000031102"),
        Map.entry("operations-risk-flow-authoring-context", "900000000000000536"),
        Map.entry("operations-risk-flow-list", "900000000000000536"),
        Map.entry("operations-risk-flow-save", "900000000000021101"),
        Map.entry("operations-risk-flow-validate", "900000000000021101"),
        Map.entry("operations-risk-flow-detail", "900000000000000536"),
        Map.entry("operations-risk-flow-history", "900000000000000536"),
        Map.entry("operations-risk-flow-version", "900000000000000536"),
        Map.entry("operations-risk-flow-delete", "900000000000021102"),
        Map.entry("operations-risk-flow-rule-set-reference", "900000000000000536"),
        Map.entry("operations-risk-flow-legacy-point", "900000000000000536"),
        Map.entry("operations-risk-rule-set-versions", "900000000000000536"),
        Map.entry("operations-risk-rule-set-release-references", "900000000000000536"),
        Map.entry("operations-risk-rejection-reason-list", "900000000000000536"),
        Map.entry("operations-risk-rejection-reason-detail", "900000000000000536"),
        Map.entry("operations-risk-rejection-reason-create", "900000000000012101"),
        Map.entry("operations-risk-rejection-reason-update", "900000000000012102"),
        Map.entry("operations-risk-rejection-reason-status", "900000000000012103"),
        Map.entry("operations-risk-rejection-reason-references", "900000000000000536"),
        Map.entry("operations-risk-subject-tag-list", "900000000000000536"),
        Map.entry("operations-risk-subject-tag-detail", "900000000000000536"),
        Map.entry("operations-risk-subject-tag-create", "900000000000012104"),
        Map.entry("operations-risk-subject-tag-update", "900000000000012105"),
        Map.entry("operations-risk-subject-tag-status", "900000000000012106"),
        Map.entry("operations-risk-subject-tag-references", "900000000000000536"),
        Map.entry("operations-risk-rule-authoring-context", "900000000000000536"),
        Map.entry("operations-risk-rule-set-list", "900000000000000536"),
        Map.entry("operations-risk-rule-set-detail", "900000000000000536"),
        Map.entry("operations-risk-rule-set-save", "900000000000000537"),
        Map.entry("operations-risk-rule-set-delete", "900000000000000541"),
        Map.entry("operations-risk-rule-set-freeze", "900000000000000538"),
        Map.entry("operations-risk-test-candidate-list", "900000000000000536"),
        Map.entry("operations-risk-test-candidate-detail", "900000000000000536"),
        Map.entry("operations-risk-test-candidate-create", "900000000000000539"),
        Map.entry("operations-risk-test-candidate-validate", "900000000000000540"));

    /** Client 入驻审核路由（registration-plan.json 的 O01—O09）到权限；权限 ID 为本 Simulator 的合成值。 */
    public static final Map<String, String> CLIENT_REVIEW_ROUTE_PERMISSIONS = Map.ofEntries(
        Map.entry("client-onboarding-o01", "900000000000041001"),
        Map.entry("client-onboarding-o02", "900000000000041001"),
        Map.entry("client-onboarding-o03", "900000000000041002"),
        Map.entry("client-onboarding-o04", "900000000000041002"),
        Map.entry("client-onboarding-o05", "900000000000041003"),
        Map.entry("client-onboarding-o07", "900000000000041004"),
        Map.entry("client-onboarding-o08", "900000000000041005"),
        Map.entry("client-onboarding-o09", "900000000000041005"),
        Map.entry("client-onboarding-o10", "900000000000041001"));

    // 这是 Platform Simulator 的当前目录事实；BFF 生产代码仍只传可信 Route Code。
    private static final Map<String, String> ROUTE_PERMISSIONS = Map.ofEntries(
        Map.entry("operations-account-create", ACCOUNT_CREATE_PERMISSION_ID),
        Map.entry("operations-account-disable", ACCOUNT_DISABLE_PERMISSION_ID),
        Map.entry("operations-account-roles", ACCOUNT_ASSIGN_ROLES_PERMISSION_ID),
        Map.entry("operations-account-delete", ACCOUNT_DELETE_PERMISSION_ID),
        Map.entry("operations-account-enable", ACCOUNT_ENABLE_PERMISSION_ID),
        Map.entry("operations-role-permission-options", ROLE_CREATE_PERMISSION_ID),
        Map.entry("operations-role-create", ROLE_CREATE_PERMISSION_ID),
        Map.entry("operations-role-detail", ROLE_UPDATE_PERMISSION_ID),
        Map.entry("operations-role-update", ROLE_UPDATE_PERMISSION_ID),
        Map.entry("operations-role-delete", ROLE_DELETE_PERMISSION_ID),
        Map.entry("operations-audit-list", AUDIT_READ_PERMISSION_ID),
        Map.entry("operations-audit-detail", AUDIT_READ_PERMISSION_ID));

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final List<String> unexpectedRequests = new CopyOnWriteArrayList<>();
    private final Map<String, AccountResponseBody> accounts = new LinkedHashMap<>();
    private final Map<String, RoleResponseBody> roles = new LinkedHashMap<>();
    private final Map<String, AuditEventResponseBody> auditEvents = new LinkedHashMap<>();
    private final Set<String> grantedPermissions = new HashSet<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpServer server;
    private final AtomicLong journalSequence;
    private volatile DelayedResponse delayedResponse;
    private volatile AuthorizationResponseBarrier authorizationResponseBarrier;
    private boolean sessionActive;
    private boolean allowed;
    private boolean disconnect;
    private int generation;
    private String refreshToken;
    private Failure legacyFailure;
    private ProblemResponse problemResponse;
    private RawResponse rawResponse;
    private final Map<String, RawResponse> groupedResponses = new LinkedHashMap<>();
    private String activeAccountId;
    private int completedAuthorizationCount;
    private boolean riskIdentity;

    public PlatformOperationsSimulator() {
        this(new AtomicLong());
    }

    public PlatformOperationsSimulator(AtomicLong journalSequence) {
        this.journalSequence = journalSequence;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/platform-support", this::handle);
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 Platform 本地 HTTP Simulator", exception);
        }
        reset();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/platform-support"; }
    public synchronized String refreshToken() { return refreshToken; }
    public synchronized boolean sessionActive() { return sessionActive; }
    public synchronized int generation() { return generation; }
    public synchronized String activeAccountId() { return activeAccountId; }
    public synchronized int completedAuthorizationCount() { return completedAuthorizationCount; }
    public List<RecordedRequest> requests() { return List.copyOf(requests); }
    public List<String> unexpectedRequests() { return List.copyOf(unexpectedRequests); }
    public synchronized void allow(boolean value) { allowed = value; }
    public synchronized void addRole(RoleResponseBody role) { roles.put(role.getRoleId(), role); }
    public synchronized boolean hasRole(String roleId) { return roles.containsKey(roleId); }
    public synchronized void addAccount(AccountResponseBody account) { accounts.put(account.getAccountId(), account); }
    public synchronized boolean hasAccount(String accountId) { return accounts.containsKey(accountId); }
    public synchronized void addAuditEvent(AuditEventResponseBody event) { auditEvents.put(event.getEvent().getAuditEventId(), event); }
    public synchronized AccountResponseBody account(String accountId) { return accounts.get(accountId); }
    public synchronized void grantPermissions(String... permissionIds) {
        grantedPermissions.clear();
        grantedPermissions.addAll(List.of(permissionIds));
    }
    public synchronized void useRiskIdentity() { useRiskIdentity(RISK_ACCOUNT_ID); }
    public synchronized void useRiskIdentity(String actor) {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid Risk fixture actor");
        activeAccountId = actor;
        riskIdentity = true;
        grantedPermissions.addAll(RISK_ROUTE_PERMISSIONS.values());
    }
    /** 审核操作者：沿用数字 Risk 身份并授予全部入驻审核权限。 */
    public synchronized void useClientReviewIdentity() {
        useRiskIdentity();
        grantedPermissions.addAll(CLIENT_REVIEW_ROUTE_PERMISSIONS.values());
    }
    public synchronized void disconnect() { disconnect = true; }
    public synchronized void fail(String path, int status, String code) {
        problemResponse = new ProblemResponse(path, status, code, UPSTREAM_REQUEST_ID, UPSTREAM_REQUEST_ID);
    }
    public synchronized void legacyError(String path, int status, String code) {
        legacyFailure = new Failure(path, status, code);
    }
    public synchronized void problem(String path, int status, String code) {
        problem(path, status, code, UPSTREAM_REQUEST_ID, UPSTREAM_REQUEST_ID);
    }
    public synchronized void problem(String path, int status, String code, String headerRequestId,
        String bodyRequestId) {
        problemResponse = new ProblemResponse(path, status, code, headerRequestId, bodyRequestId);
    }
    public synchronized void delay(String path) {
        releaseDelayedResponse();
        delayedResponse = new DelayedResponse(path, new CountDownLatch(1));
    }
    public void releaseDelay() { releaseDelayedResponse(); }
    public void blockAuthorizationResponse() {
        releaseAuthorizationResponse();
        authorizationResponseBarrier = new AuthorizationResponseBarrier(new CountDownLatch(1), new CountDownLatch(1));
    }
    public boolean awaitBlockedAuthorizationResponse(Duration timeout) throws InterruptedException {
        AuthorizationResponseBarrier barrier = authorizationResponseBarrier;
        return barrier != null && barrier.reached().await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
    public void releaseAuthorizationResponse() {
        AuthorizationResponseBarrier barrier = authorizationResponseBarrier;
        if (barrier != null) barrier.release().countDown();
    }
    public synchronized void rawResponse(String path, String body) { rawResponse(path, 200, "application/json", body); }

    /** 通过真实 HTTP 注入协议故障，不改变 BFF 生产实现或 Simulator 的业务状态。 */
    public synchronized void rawResponse(String path, int status, String contentType, String body) {
        rawResponse(path, status, contentType, body, Map.of());
    }

    /** 为严格 Problem 反例显式控制响应 Header，不根据 Body 自动补全。 */
    public synchronized void rawResponse(String path, int status, String contentType, String body,
        Map<String, String> headers) {
        rawResponse = new RawResponse(path, status, contentType, body, Map.copyOf(headers));
    }

    /** 多个独立 Owner 查询响应，用于真实 BFF 聚合验证。 */
    public synchronized void groupedResponses(Map<String, String> values) {
        groupedResponses.clear();
        values.forEach((path, body) -> groupedResponses.put(path, new RawResponse(path, 200, "application/json", body, Map.of())));
    }

    public void reset() {
        releaseAuthorizationResponse();
        synchronized (this) {
            authorizationResponseBarrier = null;
            releaseDelayedResponse();
            requests.clear();
            unexpectedRequests.clear();
            accounts.clear();
            accounts.put(ACCOUNT_ID, summary(ACCOUNT_ID));
            roles.clear();
            auditEvents.clear();
            roles.put("role_10001", new RoleResponseBody("role_10001", "OPERATIONS_ADMIN", "管理员", true, 1,
                List.of("p_menu", "p_read")));
            grantedPermissions.clear();
            permissionCatalog().forEach(permission -> grantedPermissions.add(permission.getPermissionId()));
            sessionActive = true;
            allowed = true;
            disconnect = false;
            activeAccountId = ACCOUNT_ID;
            riskIdentity = false;
            completedAuthorizationCount = 0;
            generation = 0;
            refreshToken = newRefreshToken();
            legacyFailure = null;
            problemResponse = null;
            rawResponse = null;
            groupedResponses.clear();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        long sequence = journalSequence.incrementAndGet();
        String path = exchange.getRequestURI().getPath().substring("/platform-support".length());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), List.copyOf(values)));
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path,
            exchange.getRequestURI().getRawQuery(), headers, body, sequence));
        DelayedResponse delay = delayedResponse;
        if (delay != null && delay.path().equals(path)) {
            awaitDelayedResponse(delay);
            exchange.close();
            return;
        }
        synchronized (this) {
            if (disconnect) { exchange.close(); return; }
            if (rawResponse != null && rawResponse.path().equals(path)) {
                writeRaw(exchange, rawResponse);
                return;
            }
            if (problemResponse != null && problemResponse.path().equals(path)) {
                writeProblem(exchange, problemResponse);
                return;
            }
            if (legacyFailure != null && legacyFailure.path().equals(path)) {
                writeLegacyError(exchange, legacyFailure.status(), legacyFailure.code());
                return;
            }
            if (groupedResponses.containsKey(path)) { writeRaw(exchange, groupedResponses.get(path)); return; }
            route(exchange, path, body);
        }
    }

    private void route(HttpExchange exchange, String path, String body) throws IOException {
        if (path.equals(AUTH_PATH + "/login") && method(exchange, "POST")) { login(exchange, body); return; }
        if (path.equals(AUTH_PATH + "/refresh") && method(exchange, "POST")) { refresh(exchange, body); return; }
        if (!validIdentity(exchange)) { error(exchange, 400, "OPERATIONS_SESSION_INVALID"); return; }
        if (path.equals(AUTHORIZE_PATH) && method(exchange, "POST")) { authorize(exchange, body); return; }
        if (path.startsWith(AUTH_PATH + "/")) { authentication(exchange, path, body); return; }
        if (path.equals("/authorization/v1/console-navigation") && method(exchange, "POST")) {
            write(exchange, 200, new NavigationResponseBody(grantedResources().stream().map(resource ->
                new ConsoleResource(resource.getResourceId(), resource.getResourceCode(), resource.getResourceType(),
                    resource.getParentResourceId(), resource.getDisplayName(), resource.getFrontendRoute(), resource.getComponentKey(),
                    resource.getSortOrder())).toList()));
            return;
        }
        management(exchange, path, body);
    }

    private void login(HttpExchange exchange, String body) throws IOException {
        OperationsLoginRequestBody request = json.readValue(body, OperationsLoginRequestBody.class);
        if (!EMAIL.equals(request.getLoginIdentifier()) || !PASSWORD.equals(request.getPassword())) {
            error(exchange, 400, "OPERATIONS_AUTHENTICATION_FAILED");
            return;
        }
        sessionActive = true;
        generation++;
        refreshToken = newRefreshToken();
        write(exchange, 200, tokens());
    }

    private void refresh(HttpExchange exchange, String body) throws IOException {
        OperationsRefreshRequestBody request = json.readValue(body, OperationsRefreshRequestBody.class);
        if (!sessionActive || !refreshToken.equals(request.getRefreshToken())) {
            sessionActive = false;
            error(exchange, 400, "OPERATIONS_SESSION_INVALID");
            return;
        }
        generation++;
        refreshToken = newRefreshToken();
        write(exchange, 200, tokens());
    }

    private void authorize(HttpExchange exchange, String body) throws IOException {
        AuthorizationRequestBody request = json.readValue(body, AuthorizationRequestBody.class);
        if (request.getRouteCode() != null && request.getRouteCode().startsWith("client-onboarding-")) {
            String reviewPermission = CLIENT_REVIEW_ROUTE_PERMISSIONS.get(request.getRouteCode());
            if (!riskIdentity || reviewPermission == null) {
                unexpected(exchange, "未准备的入驻审核 Route Code 或身份");
                return;
            }
            write(exchange, 200, new AuthorizationDecisionResponseBody(allowed && grantedPermissions.contains(reviewPermission)));
            completedAuthorizationCount++;
            return;
        }
        if (request.getRouteCode() == null || !request.getRouteCode().startsWith("operations-")) {
            unexpected(exchange, "未识别的 Operations Route Code");
            return;
        }
        String permissionId = (riskIdentity ? RISK_ROUTE_PERMISSIONS : ROUTE_PERMISSIONS).get(request.getRouteCode());
        if (request.getRouteCode().startsWith("operations-risk-") && (!riskIdentity || permissionId == null)) {
            unexpected(exchange, "未准备的 Risk Route Code 或身份");
            return;
        }
        var decision = new AuthorizationDecisionResponseBody(allowed
            && (permissionId == null || grantedPermissions.contains(permissionId)));
        awaitAuthorizationResponseBarrier();
        write(exchange, 200, decision);
        completedAuthorizationCount++;
    }

    private void authentication(HttpExchange exchange, String path, String body) throws IOException {
        if (path.equals(AUTH_PATH + "/context") && method(exchange, "GET")) {
            write(exchange, 200, new OperationsContextResponseBody(activeAccountId, SESSION_ID, "HUMAN", "s***@example.test", "ACTIVE",
                "tenant_10001", "member_10001", false, false, "MOCK"));
            return;
        }
        if (path.equals(AUTH_PATH + "/logout") && method(exchange, "POST")) {
            if (!refreshToken.equals(json.readValue(body, OperationsRefreshRequestBody.class).getRefreshToken())) {
                error(exchange, 400, "OPERATIONS_SESSION_INVALID");
                return;
            }
            sessionActive = false;
            write(exchange, 204, null);
            return;
        }
        if (path.equals(AUTH_PATH + "/password") && method(exchange, "POST")) { changePassword(exchange, body); return; }
        if (path.equals(AUTH_PATH + "/bootstrap/administrator") && method(exchange, "POST")) {
            json.readValue(body, FirstAdministratorRequestBody.class);
            sessionActive = false;
            write(exchange, 200, new AccountCreatedResponseBody("account_10002", "member_10002", 1));
            return;
        }
        unexpected(exchange, "未准备的认证请求: " + exchange.getRequestMethod() + " " + path);
    }

    private void changePassword(HttpExchange exchange, String body) throws IOException {
        ChangePasswordRequestBody request = json.readValue(body, ChangePasswordRequestBody.class);
        if (!PASSWORD.equals(request.getCurrentPassword()) || request.getNewPassword().equals(PASSWORD)) {
            error(exchange, 400, "OPERATIONS_AUTHENTICATION_FAILED");
            return;
        }
        sessionActive = false;
        write(exchange, 204, null);
    }

    private void management(HttpExchange exchange, String path, String body) throws IOException {
        if (path.equals(ACCOUNTS_PATH) && method(exchange, "GET")) {
            writePage(exchange, new ArrayList<>(accounts.values()));
            return;
        }
        if (path.equals(ACCOUNTS_PATH) && method(exchange, "POST")) {
            json.readValue(body, CreateAccountRequestBody.class);
            AccountResponseBody account = summary("account_10002");
            accounts.put(account.getAccountId(), account);
            write(exchange, 200, account);
            return;
        }
        if (path.startsWith(ACCOUNTS_PATH + "/") && method(exchange, "DELETE")) {
            deleteAccount(exchange, path.substring((ACCOUNTS_PATH + "/").length()), body);
            return;
        }
        if (path.startsWith(ACCOUNTS_PATH + "/") && path.endsWith("/enable") && method(exchange, "POST")) {
            enableAccount(exchange, path.substring((ACCOUNTS_PATH + "/").length(), path.length() - "/enable".length()), body);
            return;
        }
        if (path.equals("/operations/v1/roles") && method(exchange, "GET")) {
            writePage(exchange, roles.values().stream().map(role -> new RoleSummary(role.getRoleId(), role.getRoleCode(),
                role.getRoleName(), role.getSystemRole(), role.getVersion())).toList());
            return;
        }
        if (path.equals(ROLE_COUNTS_PATH) && method(exchange, "POST")) {
            RoleEmployeeCountsRequestBody request = json.readValue(body, RoleEmployeeCountsRequestBody.class);
            if (!roles.keySet().containsAll(request.getRoleIds())) {
                unexpected(exchange, "员工数查询包含未准备的角色");
                return;
            }
            write(exchange, 200, new RoleEmployeeCountsResponseBody(request.getRoleIds().stream()
                .map(id -> new RoleEmployeeCount(id, employeeCount(id))).toList()));
            return;
        }
        if (path.equals("/operations/v1/role-permissions") && method(exchange, "GET")) {
            write(exchange, 200, new PermissionCatalogResponseBody(permissionCatalog().stream()
                .filter(permission -> grantedPermissions.contains(permission.getPermissionId())).toList()));
            return;
        }
        if (path.equals("/operations/v1/role-resources") && method(exchange, "GET")) {
            write(exchange, 200, new RoleResourcesResponseBody(grantedResources()));
            return;
        }
        if (path.equals("/operations/v1/roles") && method(exchange, "POST")) {
            CreateRoleRequestBody request = json.readValue(body, CreateRoleRequestBody.class);
            RoleResponseBody role = new RoleResponseBody("role_20001", request.getRoleCode(), request.getRoleName(), false, 1,
                request.getPermissionIds());
            roles.put(role.getRoleId(), role);
            write(exchange, 200, role);
            return;
        }
        if (path.startsWith("/operations/v1/roles/")) { roleDetails(exchange, path, body); return; }
        if (path.equals(AUDIT_PATH) && method(exchange, "GET")) {
            auditPage(exchange);
            return;
        }
        if (path.startsWith(AUDIT_PATH + "/") && method(exchange, "GET")) {
            var event = auditEvents.get(path.substring((AUDIT_PATH + "/").length()));
            if (event == null) {
                error(exchange, 400, "BUSINESS_CHECK_FAILED");
                return;
            }
            write(exchange, 200, event);
            return;
        }
        unexpected(exchange, "未准备的管理请求: " + exchange.getRequestMethod() + " " + path);
    }

    private <T> void writePage(HttpExchange exchange, List<T> rows) throws IOException {
        Map<String, String> query = queryParameters(exchange);
        PagingRequestBody paging = PagingRequestBody.of(query.containsKey("page") ? Integer.valueOf(query.get("page")) : null,
            query.containsKey("size") ? Integer.valueOf(query.get("size")) : null);
        List<T> items = rows.stream().skip(paging.offset()).limit(paging.size()).toList();
        write(exchange, 200, new PagingResponseBody<>(items, paging.page(), paging.size(), rows.size()));
    }

    private Map<String, String> queryParameters(HttpExchange exchange) {
        Map<String, String> query = new LinkedHashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery != null) {
            for (String parameter : rawQuery.split("&")) {
                String[] pair = parameter.split("=", 2);
                query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return query;
    }

    private void auditPage(HttpExchange exchange) throws IOException {
        var query = queryParameters(exchange);
        var events = auditEvents.values().stream().map(AuditEventResponseBody::getEvent)
            .filter(event -> !query.containsKey("occurredFrom") || !event.getOccurredTime().isBefore(Instant.parse(query.get("occurredFrom"))))
            .filter(event -> !query.containsKey("occurredTo") || event.getOccurredTime().isBefore(Instant.parse(query.get("occurredTo"))))
            .filter(event -> !query.containsKey("actorId") || event.getActorId().equals(query.get("actorId")))
            .filter(event -> !query.containsKey("actionCode") || event.getActionCode().equals(query.get("actionCode")))
            .filter(event -> !query.containsKey("result") || event.getResult().equals(query.get("result")))
            .filter(event -> !query.containsKey("requestId") || event.getRequestId().equals(query.get("requestId")))
            .toList();
        writePage(exchange, events);
    }

    private void deleteAccount(HttpExchange exchange, String accountId, String body) throws IOException {
        AccountResponseBody account = accounts.get(accountId);
        if (account == null) { unexpected(exchange, "未准备人员: " + accountId); return; }
        DeleteAccountRequestBody request = json.readValue(body, DeleteAccountRequestBody.class);
        if (ACCOUNT_ID.equals(accountId)) {
            error(exchange, 400, "BUSINESS_CHECK_FAILED");
            return;
        }
        if (request.getExpectedVersion() != account.getVersion() || request.getExpectedMemberVersion() != account.getMemberVersion()) {
            error(exchange, 409, "CONCURRENT_REQUEST");
            return;
        }
        accounts.remove(accountId);
        write(exchange, 204, null);
    }

    private void enableAccount(HttpExchange exchange, String accountId, String body) throws IOException {
        AccountResponseBody account = accounts.get(accountId);
        if (account == null) { unexpected(exchange, "未准备人员: " + accountId); return; }
        EnableAccountRequestBody request = json.readValue(body, EnableAccountRequestBody.class);
        if (!account.getStatus().equals("DISABLED")) {
            error(exchange, 400, "BUSINESS_CHECK_FAILED");
            return;
        }
        if (request.getExpectedVersion() != account.getVersion()) {
            error(exchange, 409, "CONCURRENT_REQUEST");
            return;
        }
        accounts.put(accountId, new AccountResponseBody(accountId, account.getEmailMasked(), "ACTIVE",
            account.getPasswordChangeRequired(), account.getLockedUntil(), account.getVersion() + 1, account.getMemberId(),
            account.getMemberVersion(), account.getRoles()));
        write(exchange, 204, null);
    }

    private AuthenticationTokensResponseBody tokens() {
        return new AuthenticationTokensResponseBody("sensitive-access-token-" + generation, "Bearer", 900,
            refreshToken, 28800, false, false);
    }

    private boolean validIdentity(HttpExchange exchange) {
        return sessionActive && activeAccountId.equals(exchange.getRequestHeaders().getFirst("X-Gateway-Actor-Subject"))
            && SESSION_ID.equals(exchange.getRequestHeaders().getFirst("X-Gateway-Session-Id"));
    }

    private AccountResponseBody summary(String id) {
        return new AccountResponseBody(id, "s***@example.test", "ACTIVE", false, null, 1,
            "member_" + id, 1, List.of(role()));
    }

    private RoleSummary role() { return new RoleSummary("role_10001", "OPERATIONS_ADMIN", "管理员", true, 1); }

    private long employeeCount(String roleId) {
        return accounts.values().stream().filter(account -> account.getRoles().stream()
            .anyMatch(role -> role.getRoleId().equals(roleId))).count();
    }

    private void roleDetails(HttpExchange exchange, String path, String body) throws IOException {
        String roleId = path.substring("/operations/v1/roles/".length());
        RoleResponseBody role = roles.get(roleId);
        if (role == null) { unexpected(exchange, "未准备角色: " + roleId); return; }
        if (method(exchange, "GET")) { write(exchange, 200, role); return; }
        if (method(exchange, "DELETE")) { deleteRole(exchange, role, body); return; }
        if (!method(exchange, "PUT")) { unexpected(exchange, "未准备的角色请求方法"); return; }
        UpdateRoleRequestBody request = json.readValue(body, UpdateRoleRequestBody.class);
        if (role.getSystemRole() || request.getExpectedVersion() != role.getVersion()) {
            error(exchange, 409, "CONCURRENT_REQUEST");
            return;
        }
        RoleResponseBody updated = new RoleResponseBody(roleId, role.getRoleCode(), request.getRoleName(), false,
            role.getVersion() + 1, request.getPermissionIds());
        roles.put(roleId, updated);
        write(exchange, 200, updated);
    }

    private void deleteRole(HttpExchange exchange, RoleResponseBody role, String body) throws IOException {
        DeleteRoleRequestBody request = json.readValue(body, DeleteRoleRequestBody.class);
        if (role.getSystemRole() || employeeCount(role.getRoleId()) > 0) {
            error(exchange, 400, "BUSINESS_CHECK_FAILED");
            return;
        }
        if (request.getExpectedVersion() != role.getVersion()) {
            error(exchange, 409, "CONCURRENT_REQUEST");
            return;
        }
        roles.remove(role.getRoleId());
        write(exchange, 204, null);
    }

    private PermissionSummary permission(String id, String code) {
        return new PermissionSummary(id, code, "ROLE", "READ", "LOW");
    }

    private List<PermissionSummary> permissionCatalog() {
        return List.of(permission("p_menu", "OPERATIONS_SYSTEM_READ"), permission("p_read", "OPERATIONS_ROLE_READ"),
            permission("p_account_read", "OPERATIONS_ACCOUNT_READ"), permission("p_unbound", "OPERATIONS_UNBOUND"),
            permission(ACCOUNT_CREATE_PERMISSION_ID, "OPERATIONS_ACCOUNT_CREATE"),
            permission(ACCOUNT_DISABLE_PERMISSION_ID, "OPERATIONS_ACCOUNT_DISABLE"),
            permission(ACCOUNT_ASSIGN_ROLES_PERMISSION_ID, "OPERATIONS_ACCOUNT_ASSIGN_ROLES"),
            permission(ACCOUNT_DELETE_PERMISSION_ID, "OPERATIONS_ACCOUNT_DELETE"),
            permission(ACCOUNT_ENABLE_PERMISSION_ID, "OPERATIONS_ACCOUNT_ENABLE"),
            permission(ROLE_CREATE_PERMISSION_ID, "OPERATIONS_ROLE_CREATE"),
            permission(ROLE_UPDATE_PERMISSION_ID, "OPERATIONS_ROLE_UPDATE"),
            permission(ROLE_DELETE_PERMISSION_ID, "OPERATIONS_ROLE_DELETE"),
            permission(AUDIT_READ_PERMISSION_ID, "OPERATIONS_AUDIT_READ"));
    }

    private List<RoleResource> grantedResources() {
        return List.of(roleResource("root", "OPERATIONS_SYSTEM", null, "系统管理", "p_menu", 10),
            roleResource("roles", "OPERATIONS_ROLES", "root", "角色管理", "p_read", 10),
            roleResource("accounts", "OPERATIONS_ACCOUNTS", "root", "人员管理", "p_account_read", 20),
            roleResource("audit", "OPERATIONS_AUDIT", "root", "审计日志", AUDIT_READ_PERMISSION_ID, 30),
            roleResource("create", "OPERATIONS_ROLE_CREATE", "roles", "创建角色", ROLE_CREATE_PERMISSION_ID, 10),
            roleResource("edit", "OPERATIONS_ROLE_EDIT", "roles", "编辑角色", ROLE_UPDATE_PERMISSION_ID, 20),
            roleResource("delete", "OPERATIONS_ROLE_DELETE", "roles", "删除角色", ROLE_DELETE_PERMISSION_ID, 30),
            roleResource("account-create", "OPERATIONS_ACCOUNT_CREATE", "accounts", "创建人员", ACCOUNT_CREATE_PERMISSION_ID, 10),
            roleResource("account-disable", "OPERATIONS_ACCOUNT_DISABLE", "accounts", "停用人员", ACCOUNT_DISABLE_PERMISSION_ID, 20),
            roleResource("account-roles", "OPERATIONS_ACCOUNT_ASSIGN_ROLES", "accounts", "分配角色", ACCOUNT_ASSIGN_ROLES_PERMISSION_ID, 30),
            roleResource("account-delete", "OPERATIONS_ACCOUNT_DELETE", "accounts", "删除人员", ACCOUNT_DELETE_PERMISSION_ID, 40),
            roleResource("account-enable", "OPERATIONS_ACCOUNT_ENABLE", "accounts", "启用人员", ACCOUNT_ENABLE_PERMISSION_ID, 50))
            .stream().filter(resource -> grantedPermissions.contains(resource.getPermissionId())).toList();
    }

    private RoleResource roleResource(String id, String code, String parent, String name, String permission, int order) {
        boolean menu = parent == null || parent.equals("root");
        String route = menu && parent != null ? "/" + id : null;
        return new RoleResource(id, code, menu ? "MENU" : "BUTTON", parent,
            name, route, route == null ? null : code, permission, order);
    }
    private boolean method(HttpExchange exchange, String expected) { return expected.equals(exchange.getRequestMethod()); }

    private String newRefreshToken() {
        String lookup = UUID.randomUUID().toString().replace("-", "");
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        return lookup + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private void error(HttpExchange exchange, int status, String code) throws IOException {
        writeProblem(exchange, new ProblemResponse("", status, code, UPSTREAM_REQUEST_ID, UPSTREAM_REQUEST_ID));
    }

    private void writeLegacyError(HttpExchange exchange, int status, String code) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", "敏感上游细节: " + PASSWORD + " " + EMAIL);
        body.put("requestId", "upstream-request-id");
        body.put("timestamp", "2026-09-06T10:20:30.123456Z");
        write(exchange, status, body);
    }

    private void unexpected(HttpExchange exchange, String message) throws IOException {
        unexpectedRequests.add(message);
        error(exchange, 500, "OPERATIONS_SIMULATOR_UNEXPECTED_REQUEST");
    }

    private void write(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) { exchange.sendResponseHeaders(status, -1); exchange.close(); return; }
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void writeRaw(HttpExchange exchange, RawResponse response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        response.headers().forEach(exchange.getResponseHeaders()::set);
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void writeProblem(HttpExchange exchange, ProblemResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", HttpStatus.valueOf(response.status()).getReasonPhrase());
        body.put("detail", UPSTREAM_DETAIL_CANARY + ": " + PASSWORD + " " + EMAIL);
        body.put("code", response.code());
        body.put("requestId", response.bodyRequestId());
        body.put("timestamp", "2026-09-06T10:20:30.123456Z");
        body.put("upstreamCanary", UPSTREAM_EXTENSION_CANARY);
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        exchange.getResponseHeaders().set("X-Request-Id", response.headerRequestId());
        exchange.sendResponseHeaders(response.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void awaitDelayedResponse(DelayedResponse response) {
        try {
            response.release().await(DELAY_RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void releaseDelayedResponse() {
        DelayedResponse response = delayedResponse;
        delayedResponse = null;
        if (response != null) response.release().countDown();
    }

    private void awaitAuthorizationResponseBarrier() {
        AuthorizationResponseBarrier barrier = authorizationResponseBarrier;
        if (barrier == null) return;
        barrier.reached().countDown();
        try {
            barrier.release().await(DELAY_RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        releaseAuthorizationResponse();
        releaseDelayedResponse();
        server.stop(0);
        executor.close();
    }

    public record RecordedRequest(String method, String path, String query, Map<String, List<String>> headers, String body,
                                  long sequence) {
        public String header(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return values == null ? null : values.getFirst();
        }
        @Override public String toString() { return method + " " + path + " [内容已隐藏]"; }
    }

    private record DelayedResponse(String path, CountDownLatch release) { }
    private record AuthorizationResponseBarrier(CountDownLatch reached, CountDownLatch release) { }
    private record Failure(String path, int status, String code) { }
    private record ProblemResponse(String path, int status, String code, String headerRequestId,
                                   String bodyRequestId) { }
    private record RawResponse(String path, int status, String contentType, String body,
                               Map<String, String> headers) { }
}
