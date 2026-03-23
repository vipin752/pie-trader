package com.pietrader.broker.angel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class AngelAuthController {

    @GetMapping("/api/broker/angel/redirect")
    public String angelRedirect(@RequestParam(required = false) String code,
	    @RequestParam(required = false) String state) {

	log.info("Angel redirect received → code={} state={}", code, state);

	// TODO: Exchange code for JWT token
	return "Angel login successful. You can close this window.";
    }
}
