package com.paytech.platform.support.service.processor;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteStatusCommand;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetStatusCommand;

/**
 * Gateway 配置写入口共用的输入规范化与写事务异常转换
 * <li>规范化在资源锁内、配置事务前执行，与拆分前的顺序一致。</li>
 * <li>写事务使用 SERIALIZABLE；唯一编码冲突转为业务拒绝，序列化冲突转为并发冲突。</li>
 */
final class GatewayConfigurationWrites {

    /** 稳定配置编码：3 至 128 位小写 kebab-case。 */
    private static final Pattern STABLE_CODE = Pattern.compile("[a-z][a-z0-9-]{2,127}");

    /** 仅提供静态步骤。 */
    private GatewayConfigurationWrites() { }

    /** 规范化 Target 启停命令：正版本、稳定编码、受支持状态与 1 至 500 字的原因。 */
    static TargetStatusCommand normalize(TargetStatusCommand command) {
        if (command == null || command.expectedVersion() <= 0) {
            throw new InvalidParameterException("Target 状态命令及正版本不能为空");
        }
        return new TargetStatusCommand(command.actorId(), command.sessionId(),
            requiredCode(command.targetServiceCode(), "Target Service Code"), requiredStatus(command.status()),
            command.expectedVersion(), reason(command.reason()));
    }

    /** 规范化 Route 启停命令：正版本、稳定编码、受支持状态与 1 至 500 字的原因。 */
    static RouteStatusCommand normalize(RouteStatusCommand command) {
        if (command == null || command.expectedVersion() <= 0) {
            throw new InvalidParameterException("Route 状态命令及正版本不能为空");
        }
        return new RouteStatusCommand(command.actorId(), command.sessionId(),
            requiredCode(command.routeCode(), "Route Code"), requiredStatus(command.status()),
            command.expectedVersion(), reason(command.reason()));
    }

    /** 正数版本是更新请求的必要身份。 */
    static void requireVersion(int version) {
        if (version <= 0) {
            throw new InvalidParameterException("expectedVersion 必须为正数");
        }
    }

    /** 权限 ID 不允许溢出或隐式截断；未提供时为 null。 */
    static Long permissionId(String value) {
        if (value == null) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException("permissionId 必须是有效的十进制主键");
        }
    }

    /**
     * 在 SERIALIZABLE 写事务中执行配置变更。
     *
     * @param transactions 运营库事务执行器
     * @param operation 事务内的配置保存，不含外部调用
     * @return 保存结果
     *         <li>Target 编码、Route 编码或 Method/Path 唯一键冲突返回业务拒绝；其他唯一键冲突原样抛出。</li>
     *         <li>序列化或锁冲突返回 409 并保留原因。</li>
     */
    static <T> T serializableWrite(OperationsTransactionExecutor transactions, Supplier<T> operation) {
        try {
            return transactions.serializableWrite(operation);
        } catch (DuplicateKeyException exception) {
            String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
            if (!(cause.contains("uk_gateway_target_code") || cause.contains("uk_gateway_route_code")
                || cause.contains("uk_gateway_route_path"))) {
                throw exception;
            }
            throw new BizCheckFailException("配置编码或 Method/Path 已存在");
        } catch (ConcurrencyFailureException exception) {
            throw new ConcurrentRequestException("Gateway 配置发生并发冲突，请刷新后重试", exception);
        }
    }

    /** 稳定编码校验。 */
    private static String requiredCode(String value, String field) {
        if (value == null || !STABLE_CODE.matcher(value).matches()) {
            throw new InvalidParameterException(field + " 必须为 3 至 128 位小写 kebab-case");
        }
        return value;
    }

    /** 状态必须为受支持的稳定代码，比较前去除首尾空白。 */
    private static String requiredStatus(String value) {
        String status = value == null ? null : value.strip();
        if (Arrays.stream(GatewayConfigurationStatus.values()).noneMatch(candidate -> candidate.getCode().equals(status))) {
            throw new InvalidParameterException(status == null ? "Status 不能为空" : "Status 不是支持的稳定代码");
        }
        return status;
    }

    /** 变更原因去除首尾空白后为 1 至 500 个字符。 */
    private static String reason(String value) {
        String reason = value == null ? "" : value.strip();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new InvalidParameterException("变更原因必须为 1 至 500 个字符");
        }
        return reason;
    }
}
