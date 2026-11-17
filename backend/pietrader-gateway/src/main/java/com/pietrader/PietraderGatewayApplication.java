package com.pietrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PietraderGatewayApplication {

    public static void main(String[] args) {
        // CR-001.D: this used to call SslConfig.applyGlobalSslBypass() here,
        // which disabled TLS certificate AND hostname verification for the
        // entire JVM, unconditionally, in every environment — not scoped to
        // Angel One's endpoints at all. Standard JVM certificate verification
        // is used now. If a PKIX path building error ever recurs against a
        // real broker endpoint, add the missing CA/intermediate certificate
        // to a dedicated truststore (see SslConfig's class javadoc) — never
        // reintroduce a trust-all bypass.
        SpringApplication.run(PietraderGatewayApplication.class, args);
    }
}
