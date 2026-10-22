package com.paytech.card.ext.rest.converter;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.paytech.card.api.binlibrary.response.BinImportErrorResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.card.api.binlibrary.response.BinLookupResponseBody;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinImportError;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.domain.binlibrary.BinLibraryActivationContext;
import com.paytech.card.domain.binlibrary.BinLibraryActivationView;
import com.paytech.card.domain.binlibrary.CardBinMatchView;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.files.FileAddress;

/** BIN 库运营接口协议转换：路径与查询参数解析、内部结果到 ResponseBody；不含完整卡号。 */
@Component
public class CardBinLibraryConverter {
    /** 解析可选状态筛选。 */
    public BinLibraryVersionStatus convertStatus(String status) {
        if (status == null) {
            return null;
        }
        return Arrays.stream(BinLibraryVersionStatus.values()).filter(value -> value.getCode().equals(status))
            .findFirst().orElseThrow(() -> new InvalidParameterException("版本状态无效"));
    }

    /** 用 Platform {@link FileAddress} 解析文件地址；不符合地址格式（例如文件 ID 超出范围）按参数无效 400。 */
    public FileAddress convertFileAddress(String fileAddress) {
        try {
            return FileAddress.parse(fileAddress);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidParameterException("文件地址无效");
        }
    }

    /** 版本详情。 */
    public BinLibraryVersionResponseBody convertVersion(CardBinLibraryVersion version) {
        BinLibraryVersionResponseBody body = new BinLibraryVersionResponseBody();
        body.setVersionNo(version.getVersionNo());
        body.setStatus(version.getStatus().getCode());
        body.setActive(version.isActive());
        body.setFileAddress(version.getFileAddress());
        body.setFileName(version.getFileName());
        body.setFileContentHash(version.getFileContentHash());
        body.setTotalRows(version.getTotalRows());
        body.setProcessedRows(version.getProcessedRows());
        body.setErrorRows(version.getErrorRows());
        body.setFailureReason(version.getFailureReason());
        body.setCreatedBy(version.getCreatedBy() == null ? null : version.getCreatedBy().toString());
        body.setCreateTime(version.getCreateTime());
        body.setUpdateTime(version.getUpdateTime());
        return body;
    }

    /** 一条导入错误。 */
    public BinImportErrorResponseBody convertError(CardBinImportError error) {
        BinImportErrorResponseBody body = new BinImportErrorResponseBody();
        body.setLineNo(error.getLineNo());
        body.setColumnName(error.getColumnName());
        body.setErrorCode(error.getErrorCode().getCode());
        body.setErrorMessage(error.getErrorMessage());
        body.setValueExcerpt(error.getValueExcerpt());
        return body;
    }

    /** 激活历史的一行。 */
    public BinLibraryActivationResponseBody convertActivation(BinLibraryActivationView view) {
        BinLibraryActivationResponseBody body = new BinLibraryActivationResponseBody();
        body.setActivationType(view.getActivationType().getCode());
        body.setVersionNo(view.getVersionNo());
        body.setPreviousVersionNo(view.getPreviousVersionNo());
        body.setOperatorAccountId(view.getOperatorAccountId() == null ? null : view.getOperatorAccountId().toString());
        body.setActivatedAt(view.getActivatedAt());
        return body;
    }

    /** 本次激活或回切的结果。 */
    public BinLibraryActivationResponseBody convertActivationResult(BinLibraryActivationContext context) {
        BinLibraryActivationResponseBody body = new BinLibraryActivationResponseBody();
        body.setActivationType(context.activationType().getCode());
        body.setVersionNo(context.getTarget().getVersionNo());
        body.setPreviousVersionNo(context.previousVersionNo());
        body.setOperatorAccountId(context.getActivation().getOperatorAccountId().toString());
        body.setActivatedAt(context.getActivation().getCreateTime());
        return body;
    }

    /** BIN 查询结果；没有生效版本时只返回未识别。 */
    public BinLookupResponseBody convertLookup(CardBinMatchView match) {
        BinLookupResponseBody body = new BinLookupResponseBody();
        if (match == null) {
            return body;
        }
        body.setBinLibraryVersionNo(match.getVersionNo());
        if (!match.isRecognized()) {
            return body;
        }
        body.setRecognized(true);
        body.setBinPrefix(match.getBinPrefix());
        body.setMatchedPrefixLength(match.getPrefixLength());
        body.setCardBrand(code(match.getCardBrand()));
        body.setFundingType(code(match.getFundingType()));
        body.setCardLevel(match.getCardLevel());
        body.setCardProduct(code(match.getCardProduct()));
        body.setIssuerCountry(match.getIssuerCountry());
        body.setIssuerRegion(match.getIssuerRegion());
        body.setIssuerName(match.getIssuerName());
        return body;
    }

    /** 可空枚举的稳定代码。 */
    static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }
}
