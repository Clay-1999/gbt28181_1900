package com.example.gbt28181.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sip")
@Getter
@Setter
public class SipProperties {
    private int defaultPort = 5060;
    private String defaultTransport = "UDP";
}
