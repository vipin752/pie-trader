package com.pietrader.broker.angel;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:angel/PathAngelRemoting.properties")
@Getter
public class AngelApiConfig {

    @Value("${angel.base.url}")
    private String baseUrl;

    @Value("${angel.login}")
    private String loginUrl;

    @Value("${angel.generate.token}")
    private String generateTokenUrl;

    @Value("${angel.get.profile}")
    private String profileUrl;

    @Value("${angel.ltp}")
    private String ltpUrl;

    @Value("${angel.search.scrip}")
    private String searchScripUrl;

    @Value("${angel.place.order}")
    private String placeOrderUrl;

    @Value("${angel.modify.order}")
    private String modifyOrderUrl;

    @Value("${angel.cancel.order}")
    private String cancelOrderUrl;

    @Value("${angel.order.book}")
    private String orderBookUrl;

    @Value("${angel.trade.book}")
    private String tradeBookUrl;

    @Value("${angel.position}")
    private String positionUrl;

    @Value("${angel.ws.url}")
    private String wsUrl;

    @Value("${angel.contract.master}")
    private String contractMasterUrl;
}
