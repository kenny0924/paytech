package com.paytech.platform.support.core.reliability.service.impl;

import java.time.Instant;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileCompletionReceipt;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography.HmacInput;
import com.paytech.platform.support.core.reliability.repository.CommandIdempotencyRepository;
import com.paytech.platform.support.core.reliability.service.ClientFileCommandService;
import tools.jackson.databind.ObjectMapper;

/** 沿用现有命令机制；不同通道与操作不共享回执。 */
@Service
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class ClientFileCommandServiceImpl implements ClientFileCommandService {
    private static final String OPERATION = "CLIENT_FILE_UPLOAD_START";
    private static final String COMPLETION = "CLIENT_FILE_UPLOAD_COMPLETE";
    private final IdGenerator ids;
    private final ObjectMapper json;
    private final CommandIdempotencyRepository repository;
    private final HumanAuthenticationCryptography crypto;
    /** 绑定 CUSTOMER 专用 HMAC 与 Platform 服务级命令仓储。 */
    public ClientFileCommandServiceImpl(IdGenerator ids, ObjectMapper json, CommandIdempotencyRepository repository,
        @Qualifier("clientAuthenticationCryptography") HumanAuthenticationCryptography crypto) {
        this.ids=ids; this.json=json; this.repository=repository; this.crypto=crypto;
    }
    /** 固定 typed record 字段序列，不保存原文件名。 */
    @Override public Prepared prepare(long accountId, String key, Object request) {
        return prepare(accountId,key,request,OPERATION,"client-file-upload-start-v1");
    }
    /** 完成命令只以原 Upload 作为内容身份。 */
    @Override public Prepared prepareCompletion(long accountId,String key,long uploadId) {
        return prepare(accountId,key,Long.toString(uploadId),COMPLETION,"client-file-upload-complete-v1");
    }
    /** 两种操作独立命名，不改变现有 F01 命令摘要。 */
    private Prepared prepare(long accountId,String key,Object request,String operation,String namespace) {
        var hashes = crypto.versionedHmacs(List.of(new HmacInput("client-file-caller-v1", Long.toString(accountId)),
            new HmacInput(namespace, json.writeValueAsString(request))));
        if (hashes == null || hashes.digests().size() != 2) throw new ServiceUnavailableException();
        return new Prepared(hashes.digests().get(0), key, hashes.digests().get(1),operation);
    }
    /** 不用文件当前状态替换首次创建回执。 */
    @Override public Long previous(Prepared command) {
        String value=previousValue(command);
        return value==null ? null : Long.parseLong(value);
    }
    /** 同一键异参必须冲突，不可据新参数创建第二个文件。 */
    private String previousValue(Prepared command) {
        String value = repository.previousCommand(command.callerScope(), command.operation(), command.key());
        if (value == null) return null;
        if (!value.startsWith(command.requestHash() + ":")) throw new ConcurrentRequestException("同一命令标识不能用于不同上传请求");
        return value.substring(command.requestHash().length()+1);
    }
    /** 幂等表唯一键是 Redis Lease 失效后的最终保护。 */
    @Override public void complete(Prepared command, long uploadId, Instant now) {
        repository.completeCommand(ids.nextId(),command.callerScope(),command.operation(),command.key(),command.requestHash(),Long.toString(uploadId),now);
    }
    /** 不读取当前扫描状态来改写成功回执。 */
    @Override public FileCompletionReceipt previousCompletion(Prepared command) {
        String value=previousValue(command);
        return value==null ? null : json.readValue(value,FileCompletionReceipt.class);
    }
    /** JSON 回执仅包含三个 ID 和 UTC 时间，受现有 VARCHAR(255) 上限约束。 */
    @Override public FileCompletionReceipt completeFile(Prepared command,long uploadId,long fileId,Instant now) {
        long id=ids.nextId();
        var receipt=new FileCompletionReceipt(Long.toString(id),Long.toString(uploadId),Long.toString(fileId),now);
        repository.completeCommand(id,command.callerScope(),command.operation(),command.key(),command.requestHash(),json.writeValueAsString(receipt),now);
        return receipt;
    }
}
