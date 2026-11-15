package com.pietrader;

import com.pietrader.config.SslConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PietraderGatewayApplication {

    public static void main(String[] args) {
        // Apply SSL bypass BEFORE Spring starts — covers Angel login, REST, WS, contract download
        SslConfig.applyGlobalSslBypass();
        SpringApplication.run(PietraderGatewayApplication.class, args);
    }
}
