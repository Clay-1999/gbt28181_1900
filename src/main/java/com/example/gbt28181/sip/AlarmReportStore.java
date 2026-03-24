package com.example.gbt28181.sip;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存管理的报警上报配置存储（AlarmReport 无对应 ivs1900 接口，由本系统自行维护）。
 */
@Component
public class AlarmReportStore {

    /** key: GB 设备 ID，value: 原始 AlarmReport XML 片段 */
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    public void put(String gbDeviceId, String xmlFragment) {
        store.put(gbDeviceId, xmlFragment);
    }

    /** 返回 AlarmReport XML 片段，不存在则返回 null */
    public String get(String gbDeviceId) {
        return store.get(gbDeviceId);
    }
}
