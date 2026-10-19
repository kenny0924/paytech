package com.paytech.account.app.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.request.BalanceMovementRequestBody;
import com.paytech.account.api.response.BalanceMovementResponseBody;
import com.paytech.account.domain.model.BalanceMovementResult;
import com.paytech.account.service.processor.request.MoveBalanceRequest;

/** 余额桶移动的 Wire 与内部请求 / 结果转换；Code 解析为枚举，ID 与序号输出为十进制字符串。 */
@Component
public class BalanceMovementConverter {

    /** 请求体转内部命令。 */
    public MoveBalanceRequest toRequest(BalanceMovementRequestBody body) {
        return new MoveBalanceRequest(CodeEnumConverter.parse(MovementSourceType.class, body.getSourceType()),
            body.getSourceRef(), CodeEnumConverter.parse(AccountEntityType.class, body.getEntityType()), body.getEntityId(),
            body.getCurrency(), body.getAccountCode(), CodeEnumConverter.parse(OwnerType.class, body.getOwnerType()),
            body.getOwnerId(), CodeEnumConverter.parse(BalanceBucket.class, body.getFromBucket()),
            CodeEnumConverter.parse(BalanceBucket.class, body.getToBucket()), body.getAmount(),
            body.getOperatorId() == null ? null : Long.parseLong(body.getOperatorId()));
    }

    /** 移动结果转响应体。 */
    public BalanceMovementResponseBody toResponseBody(BalanceMovementResult result) {
        BalanceMovementResponseBody body = new BalanceMovementResponseBody();
        body.setMovementId(result.getMovement().getId().toString());
        body.setPostingId(result.getPosting().getId().toString());
        body.setPostingSequence(result.getPosting().getPostingSequence().toString());
        body.setReplayed(result.isReplayed());
        return body;
    }
}
