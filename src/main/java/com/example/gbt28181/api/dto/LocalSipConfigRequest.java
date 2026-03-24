package com.example.gbt28181.api.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocalSipConfigRequest {

    @NotBlank
    @Size(min = 20, max = 20, message = "设备ID必须为20位国标编码")
    private String deviceId;

    @NotBlank
    private String domain;

    @NotBlank
    private String sipIp;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer sipPort;

    @NotBlank
    @Pattern(regexp = "UDP|TCP", message = "传输协议必须为 UDP 或 TCP")
    private String transport;

    @NotBlank
    private String password;

    @NotNull
    @Min(value = 60, message = "注册有效期不得少于60秒")
    private Integer expires;
}
