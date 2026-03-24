package com.example.gbt28181.api.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterconnectConfigRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String remoteSipId;

    @NotBlank
    private String remoteIp;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer remotePort;

    @NotBlank
    private String remoteDomain;

    @NotBlank
    private String password;

    @NotNull
    private Boolean enabled;

    private Boolean upLinkEnabled = false;
}
