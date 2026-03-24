package com.example.gbt28181.api.dto;

import com.example.gbt28181.domain.entity.AlarmEvent;
import lombok.Data;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
public class AlarmEventResponse {

    private Long id;
    private String deviceId;
    private String alarmPriority;
    private String alarmMethod;
    private String alarmType;
    private String alarmDescription;
    private String alarmTime;
    private Double longitude;
    private Double latitude;
    private String sourceIp;
    private String receivedAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    public static AlarmEventResponse from(AlarmEvent e) {
        AlarmEventResponse r = new AlarmEventResponse();
        r.setId(e.getId());
        r.setDeviceId(e.getDeviceId());
        r.setAlarmPriority(e.getAlarmPriority());
        r.setAlarmMethod(e.getAlarmMethod());
        r.setAlarmType(e.getAlarmType());
        r.setAlarmDescription(e.getAlarmDescription());
        r.setAlarmTime(e.getAlarmTime());
        r.setLongitude(e.getLongitude());
        r.setLatitude(e.getLatitude());
        r.setSourceIp(e.getSourceIp());
        r.setReceivedAt(e.getReceivedAt() != null ? FMT.format(e.getReceivedAt()) : null);
        return r;
    }
}
