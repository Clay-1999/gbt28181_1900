package com.example.gbt28181.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Ivs1900InterconnectRequest {

    @NotBlank
    private String sipId;

    @NotBlank
    private String ip;

    @NotNull
    private Integer port;

    @NotBlank
    private String domain;

    private String password;
}
