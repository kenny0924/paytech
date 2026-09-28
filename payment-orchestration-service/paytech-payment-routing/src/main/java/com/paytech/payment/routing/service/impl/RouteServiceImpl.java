package com.paytech.payment.routing.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.payment.routing.domain.RouteContext;
import com.paytech.payment.routing.domain.RouteResult;
import com.paytech.payment.routing.service.RouteService;

@Service
public class RouteServiceImpl implements RouteService {
    @Override
    public RouteResult route(RouteContext context) {
        // TODO Routing：按上下文筛选同 OE 的有效渠道，匹配能力与策略，返回结果及选择证据。
        RouteResult result = new RouteResult();
        result.setChannelId(9001L);
        result.setChannelAccountId(9002L);
        result.setMidId(9003L);
        return result;
    }
}
