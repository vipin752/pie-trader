package com.pietrader.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthAppController {

    @GetMapping("/")
    public String home() {
	return "PIE TRADER API RUNNING";
    }

    @GetMapping("/health")
    public String health() {
	return "OK";
    }
}
