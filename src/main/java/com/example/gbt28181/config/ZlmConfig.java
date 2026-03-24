package com.example.gbt28181.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zlm")
@Getter
@Setter
public class ZlmConfig {
    private String baseUrl = "http://127.0.0.1:8080";
    private String rtpIp = "127.0.0.1";
    private int httpPort = 8080;
    private String secret = "";
}
