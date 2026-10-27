package com.paytech.platform.support.referencedata.service.processor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;

import lombok.AllArgsConstructor;

/**
 * 参考数据 logo 与文件服务的进程内协作：查询时批量换取公开地址，维护时核对 logo 是公开文件
 * <li>经文件 Owner 的同库能力 {@link FileLifecycleService} 读取元数据，不发起 HTTP；数据库只保存文件地址。</li>
 */
@Component
@AllArgsConstructor
public class ReferenceDataLogos {

    private final FileLifecycleService files;

    /**
     * 一次查询的全部 logo 只读取一次文件元数据
     *
     * @param fileAddresses 本次结果中的 logo 文件地址，可含空值
     * @return 文件地址到公开地址；不是公开文件、检查未通过、文件不存在或地址不合法的不出现在结果中
     */
    Map<String, String> publicUrls(Collection<String> fileAddresses) {
        Map<Long, String> addressesById = new HashMap<>();
        for (String address : fileAddresses) {
            Long fileId = storedFileId(address);
            if (fileId != null) {
                addressesById.put(fileId, address);
            }
        }
        Map<Long, StoredFileView> views = files.describeAll(addressesById.keySet());
        return views.values().stream().filter(view -> view.getPublicUrl() != null)
            .collect(Collectors.toMap(view -> addressesById.get(view.getFileId()), StoredFileView::getPublicUrl));
    }

    /**
     * 维护时设置的 logo 必须是已存在的公开文件
     *
     * @param fileAddress 已通过格式校验的文件地址
     * @throws RequestBodyValidationException 文件不存在或不是公开文件（422，字段 logoFileAddress）
     */
    void requirePublicFile(String fileAddress) {
        long fileId = FileAddress.parse(fileAddress).fileId();
        StoredFileView view = files.describeAll(List.of(fileId)).get(fileId);
        if (view == null) {
            throw logoRejected("logo 文件不存在: " + fileAddress);
        }
        if (view.getAccessLevel() != FileAccessLevel.PUBLIC) {
            throw logoRejected("logo 文件不是公开文件: " + fileAddress);
        }
    }

    /**
     * 已保存的 logo 地址对应的文件 ID
     * <li>写入时已校验格式；历史数据格式不合法时只让该行没有公开地址，不影响整次查询。</li>
     *
     * @param address 已保存的文件地址，可空
     * @return 文件 ID；为空或格式不合法时为空
     */
    static Long storedFileId(String address) {
        if (address == null) {
            return null;
        }
        try {
            return FileAddress.parse(address).fileId();
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** logoFileAddress 字段的 422 拒绝。 */
    static RequestBodyValidationException logoRejected(String message) {
        return new RequestBodyValidationException(CommonErrorCode.VALIDATION_ERROR,
            List.of(new ProblemFieldError("logoFileAddress", message)), null);
    }
}
