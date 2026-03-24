package com.example.gbt28181.api.dto;

import com.example.gbt28181.domain.entity.LinkStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Ivs1900InterconnectResponse {

    private Long id;
    private String sipId;
    private String ip;
    private Integer port;
    private String domain;
    private String password;
    private LinkStatus upLinkStatus;
    private LocalDateTime createdAt;
}
