package com.paytech.card.service.impl;

import java.util.Arrays;

import org.springframework.stereotype.Service;

import com.paytech.card.service.CardCallerService;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

/** 按入口白名单校验调用方；拒绝时只返回 404。 */
@Service
public class CardCallerServiceImpl implements CardCallerService {
    @Override
    public void require(String caller, String... allowed) {
        if (caller == null || Arrays.stream(allowed).noneMatch(caller::equals)) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "卡资源不存在");
        }
    }
}
