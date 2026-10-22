package com.paytech.card.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.domain.binlibrary.BinLibraryLock;
import com.paytech.card.domain.binlibrary.BinLibrarySourceFile;
import com.paytech.card.domainservice.repository.CardBinLibraryVersionRepository;
import com.paytech.card.integration.PlatformFileClient;
import com.paytech.card.service.CardBinLibraryService;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.processor.request.CreateBinLibraryVersionRequest;
import com.paytech.card.service.processor.response.CreateBinLibraryVersionResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;

/**
 * 用已上传到 Platform 的 CSV 创建 BIN 库版本（技术方案 §3.3）：锁 card-bin-library（TRY_LOCK）→ 按文件地址查原版本，已有则原样返回 →
 * 事务外查询 Platform 文件元数据并核对地址、来源、上传原因、类型与扫描状态（扫描中允许创建）→ 一个本地事务插入 VALIDATING 版本（版本号 = 最大 + 1，
 * 冻结文件摘要与文件名）并登记导入任务 CARD_BIN_LIBRARY_IMPORT → 提交后由补偿执行器异步扫描、校验与写入号段。
 * <li>版本号与文件地址另有唯一索引兜底；锁竞争返回 409。</li>
 */
@Component
@AllArgsConstructor
public class CreateBinLibraryVersionProcessor
    extends AbstractLockProcessor<CreateBinLibraryVersionRequest, CreateBinLibraryVersionResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CardCallerService cardCallerService;
    private final PlatformFileClient platformFileClient;
    private final CardBinLibraryService cardBinLibraryService;
    private final CardBinLibraryVersionRepository cardBinLibraryVersionRepository;

    /** 只接受 operations-console-bff。 */
    @Override
    protected void validate(CreateBinLibraryVersionRequest request) {
        cardCallerService.require(request.getCaller(), CardApiConstants.OPERATIONS_CONSOLE_BFF);
    }

    /** 立即尝试获取 BIN 库锁。 */
    @Override
    protected LockCommand lockCommand(CreateBinLibraryVersionRequest request) {
        return LockCommand.tryLock(BinLibraryLock.KEY);
    }

    @Override
    protected CreateBinLibraryVersionResponse processWithinLock(CreateBinLibraryVersionRequest request) {
        // 同一文件已建过版本
        CardBinLibraryVersion original = cardBinLibraryVersionRepository.findByFileAddress(request.getFileAddress());
        if (original != null) {
            return new CreateBinLibraryVersionResponse(original, false);
        }

        // 事务外查询并核对 Platform 文件
        BinLibrarySourceFile file = getAndCheckFile(FileAddress.parse(request.getFileAddress()));

        // 保存版本并登记导入任务
        CardBinLibraryVersion version = buildVersion(request, file);
        cardBinLibraryService.createVersion(version);
        return new CreateBinLibraryVersionResponse(version, true);
    }

    /**
     * 文件必须存在、Platform 登记的地址与请求一致、是 operations-console-bff 以 CARD_BIN_LIBRARY_IMPORT 上传的 CSV，且扫描未判定隔离或拒绝
     * <li>异常消息只带文件 ID，不回显 Platform 返回的地址。</li>
     */
    BinLibrarySourceFile getAndCheckFile(FileAddress fileAddress) {
        long fileId = fileAddress.fileId();
        BinLibrarySourceFile file = platformFileClient.getFile(fileId);
        if (file == null) {
            throw new BizCheckFailException("导入文件不存在: " + fileId);
        }
        if (!file.isAddressedBy(fileAddress)) {
            throw new BizCheckFailException("导入文件地址与文件服务登记不一致: " + fileId);
        }
        if (!file.isBinLibraryCsv()) {
            throw new BizCheckFailException("导入文件不是运营控制台上传的 BIN 库 CSV: " + fileId);
        }
        if (file.isRejected()) {
            throw new BizCheckFailException("导入文件扫描未通过或已不可用: " + fileId);
        }
        return file;
    }

    /** VALIDATING 新版本：版本号取最大 + 1，冻结文件摘要与展示名。 */
    CardBinLibraryVersion buildVersion(CreateBinLibraryVersionRequest request, BinLibrarySourceFile file) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CardBinLibraryVersion version = new CardBinLibraryVersion();
        version.setVersionId(idGenerator.nextId());
        version.setVersionNo(cardBinLibraryVersionRepository.nextVersionNo());
        version.setStatus(BinLibraryVersionStatus.VALIDATING);
        version.setFileAddress(request.getFileAddress());
        version.setFileContentHash(file.getContentHash());
        version.setFileName(file.getDisplayName());
        version.setProcessedRows(0);
        version.setErrorRows(0);
        version.setCreatedBy(request.getActorAccountId());
        version.setVersion(1);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        return version;
    }
}
