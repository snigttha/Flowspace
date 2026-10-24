
package com.flowspace.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/hello")
    public String hello() {
        return "hello from flowspace!";
    }
}
