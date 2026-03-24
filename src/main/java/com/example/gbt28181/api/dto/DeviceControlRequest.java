package com.example.gbt28181.api.dto;

import lombok.Data;

@Data
public class DeviceControlRequest {
    /** GuardCmd: SetGuard / ResetGuard；RecordCmd: Record / StopRecord */
    private String cmd;
}
