package com.paytech.card.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.api.instrument.CardInstrumentApi;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.card.ext.rest.converter.CardInstrumentConverter;
import com.paytech.card.service.processor.CreateCardInstrumentProcessor;
import com.paytech.card.service.processor.request.CreateCardInstrumentRequest;

import lombok.AllArgsConstructor;

/** 卡交易工具创建私网入口；请求含卡原值，访问日志按全局配置只记元数据。 */
@RestController
@AllArgsConstructor
public class CardInstrumentController implements CardInstrumentApi {
    final CardCallerResolver callers;
    final CardInstrumentConverter converter;
    final CreateCardInstrumentProcessor processor;

    /** 创建卡交易工具 */
    @Override
    public CreateCardInstrumentResponseBody createInstrument(String caller, CreateCardInstrumentRequestBody body) {
        return converter.convertResponse(
            processor.process(new CreateCardInstrumentRequest(callers.resolve(caller), converter.convertRequest(body))));
    }
}
