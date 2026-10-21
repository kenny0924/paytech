package com.paytech.reconciliation.service.processor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.domain.Amounts;
import com.paytech.reconciliation.domain.view.ChannelStatementView;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementService;
import com.paytech.reconciliation.service.processor.request.AppendChannelStatementLinesRequest;

import lombok.AllArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 追加一段渠道账单明细行（UC-R06，#13，technique D-01）
 * <li>锁 channel-statement:{id}；账单须为 RECEIVING，操作人须为开始导入的上传人。</li>
 * <li>按行号幂等：已接收的同号同内容忽略，同号不同内容拒绝；段内行号不得重复，行号不超过声明行数。行金额非负、不超过币种精度，
 * 渠道特有列序列化后不超过 4 KiB。行的组合规则（交易号、方向）在完成导入时一次性校验。</li>
 */
@Component
@AllArgsConstructor
public class AppendChannelStatementLinesProcessor
    extends AbstractLockProcessor<AppendChannelStatementLinesRequest, ChannelStatementView> {
    /** 渠道特有列 JSON 上限（字节）。 */
    static final int RAW_ATTRIBUTES_LIMIT = 4096;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelStatementService channelStatementService;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 渠道账单资源锁。 */
    @Override
    protected LockCommand lockCommand(AppendChannelStatementLinesRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.channelStatement(request.getStatementId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ChannelStatementView processWithinLock(AppendChannelStatementLinesRequest request) {
        // 业务验证
        ChannelStatement statement = loadReceiving(request);
        checkSegment(statement, request.getLines());

        // 按行号比较已接收的行，只保留新行
        List<ChannelStatementLine> newLines = newLines(statement, request.getLines());

        // 本地保存
        channelStatementService.appendLines(statement, newLines);
        return new ChannelStatementView(statement, clearingAccountRepository.findById(statement.getClearingAccountId()));
    }

    /** 账单存在、RECEIVING 且操作人为上传人。 */
    ChannelStatement loadReceiving(AppendChannelStatementLinesRequest request) {
        ChannelStatement statement = channelStatementRepository.findById(request.getStatementId());
        if (statement == null) {
            throw new BizCheckFailException("渠道账单不存在: " + request.getStatementId());
        }
        if (!statement.getMakerId().equals(request.getActorId())) {
            throw new BizCheckFailException("只有开始导入的上传人可以追加明细: 账单 " + statement.getId());
        }
        if (statement.getStatus() != ChannelStatementStatus.RECEIVING) {
            throw new BizCheckFailException("渠道账单不在接收中，不能追加明细: 账单 " + statement.getId() + ", 状态 "
                + statement.getStatus().getCode());
        }
        return statement;
    }

    /** 段内行号唯一且不超过声明行数；金额非负且不超过币种精度。 */
    static void checkSegment(ChannelStatement statement, List<AppendChannelStatementLinesRequest.Line> lines) {
        Set<Integer> seen = new HashSet<>();
        for (AppendChannelStatementLinesRequest.Line line : lines) {
            String label = "账单 " + statement.getId() + " 第 " + line.getLineNo() + " 行";
            if (!seen.add(line.getLineNo())) {
                throw new BizCheckFailException("同一段内行号重复: " + label);
            }
            if (line.getLineNo() < 1 || line.getLineNo() > statement.getDeclaredLineCount()) {
                throw new BizCheckFailException("行号超出声明行数 " + statement.getDeclaredLineCount() + ": " + label);
            }
            if (line.getAmount().signum() < 0 || line.getFeeAmount().signum() < 0) {
                throw new BizCheckFailException("行金额与行上费用必须非负: " + label);
            }
            Amounts.checkPrecision(statement.getCurrency(), line.getAmount(), label + " 金额");
            Amounts.checkPrecision(statement.getCurrency(), line.getFeeAmount(), label + " 费用");
        }
    }

    /** 同号同内容忽略、同号不同内容拒绝，返回需要插入的新行。 */
    List<ChannelStatementLine> newLines(ChannelStatement statement, List<AppendChannelStatementLinesRequest.Line> lines) {
        Map<Integer, ChannelStatementLine> existing = channelStatementLineRepository.listByStatementAndLineNos(
            statement.getId(), lines.stream().map(AppendChannelStatementLinesRequest.Line::getLineNo).toList()).stream()
            .collect(Collectors.toMap(ChannelStatementLine::getLineNo, Function.identity()));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<ChannelStatementLine> result = new ArrayList<>();
        for (AppendChannelStatementLinesRequest.Line line : lines) {
            ChannelStatementLine candidate = build(statement, line, now);
            ChannelStatementLine received = existing.get(line.getLineNo());
            if (received == null) {
                result.add(candidate);
            } else if (!sameContent(received, candidate)) {
                throw new BizCheckFailException("同一行号已接收不同内容: 账单 " + statement.getId() + " 第 " + line.getLineNo()
                    + " 行");
            }
        }
        return result;
    }

    /** 由请求行构造 UNMATCHED 账单行。 */
    ChannelStatementLine build(ChannelStatement statement, AppendChannelStatementLinesRequest.Line input, Instant now) {
        ChannelStatementLine line = new ChannelStatementLine();
        line.setId(idGenerator.nextId());
        line.setStatementId(statement.getId());
        line.setClearingAccountId(statement.getClearingAccountId());
        line.setLineNo(input.getLineNo());
        line.setLineType(input.getLineType());
        line.setDirection(input.getDirection());
        line.setAmount(input.getAmount());
        line.setFeeAmount(input.getFeeAmount());
        line.setReferenceType(input.getReferenceType());
        line.setExternalTransactionId(input.getExternalTransactionId());
        line.setTransactionDate(input.getTransactionDate());
        line.setRawAttributes(rawAttributes(statement, input));
        line.setMatchStatus(LineMatchStatus.UNMATCHED);
        line.setVersion(1L);
        line.setCreateTime(now);
        line.setUpdateTime(now);
        return line;
    }

    /** 渠道特有列按列名排序序列化为 JSON Object；空为 null，超过 4 KiB 拒绝。 */
    static String rawAttributes(ChannelStatement statement, AppendChannelStatementLinesRequest.Line input) {
        if (input.getRawAttributes() == null || input.getRawAttributes().isEmpty()) {
            return null;
        }
        String json = JSON.writeValueAsString(new TreeMap<>(input.getRawAttributes()));
        if (json.getBytes(StandardCharsets.UTF_8).length > RAW_ATTRIBUTES_LIMIT) {
            throw new BizCheckFailException("渠道特有列超过 4 KiB: 账单 " + statement.getId() + " 第 " + input.getLineNo()
                + " 行");
        }
        return json;
    }

    /** 文件内容是否相同（金额按数值比较，渠道特有列按键值比较）。 */
    static boolean sameContent(ChannelStatementLine left, ChannelStatementLine right) {
        return left.getLineType() == right.getLineType() && left.getDirection() == right.getDirection()
            && left.getAmount().compareTo(right.getAmount()) == 0
            && left.getFeeAmount().compareTo(right.getFeeAmount()) == 0
            && Objects.equals(left.getReferenceType(), right.getReferenceType())
            && Objects.equals(left.getExternalTransactionId(), right.getExternalTransactionId())
            && Objects.equals(left.getTransactionDate(), right.getTransactionDate())
            && Objects.equals(attributes(left.getRawAttributes()), attributes(right.getRawAttributes()));
    }

    /** 解析渠道特有列；空为空 Map。 */
    static Map<String, String> attributes(String json) {
        return json == null ? Map.of() : JSON.readValue(json, new TypeReference<Map<String, String>>() {});
    }
}
