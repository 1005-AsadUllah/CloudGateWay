package com.Tulip_Tech.CloudGateWay.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallBackController {

    @GetMapping("/paymentFallBack")
    public String paymentFallBackMethod() {
        return "Payment Service is Down.";
    }

    @GetMapping("/orderFallBack")
    public String orderFallBackMethod() {
        return "Order Service is Down.";
    }

    @GetMapping("/productFallback")
    public String fallBackMethod() {
        return "Product Service is Down.";
    }
}
