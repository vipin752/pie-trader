package com.pietrader.controller;

import com.pietrader.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/redis")
@RequiredArgsConstructor
public class RedisTestController {

    private final RedisService redisService;

    @GetMapping("/set")
    public String set() {
	redisService.set("test:key", "VIPIN", 60);
	return "SET DONE";
    }

    @GetMapping("/get")
    public String get() {
	return redisService.get("test:key", String.class);
    }

    @GetMapping("/exists")
    public boolean exists() {
	return redisService.exists("test:key");
    }
}