package com.example.gbt28181.ivs1900;

import com.example.gbt28181.config.ZlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * ZLMediaKit HTTP API 客户端。
 * 负责创建/关闭 RTP 收流端口，构造 HTTP-FLV 播放地址。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZLMediaKitClient {

    private final ZlmConfig zlmConfig;
    private final RestTemplate restTemplate;

    /**
     * 创建 RTP 收流端口。
     *
     * @param streamId 流标识（唯一）
     * @return ZLMediaKit 分配的 RTP 端口号，失败返回 -1
     */
    public int openRtpServer(String streamId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(zlmConfig.getBaseUrl())
                    .path("/index/api/openRtpServer")
                    .queryParam("secret", zlmConfig.getSecret())
                    .queryParam("port", 0)
                    .queryParam("tcp_mode", 0)
                    .queryParam("stream_id", streamId)
                    .queryParam("local_ip", "0.0.0.0")
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null) {
                log.error("ZLM openRtpServer 返回 null，streamId={}", streamId);
                return -1;
            }
            int code = (int) resp.getOrDefault("code", -1);
            if (code != 0) {
                log.error("ZLM openRtpServer 失败 code={} msg={} streamId={}", code, resp.get("msg"), streamId);
                return -1;
            }
            int port = (int) resp.getOrDefault("port", -1);
            log.info("ZLM openRtpServer 成功 streamId={} port={}", streamId, port);
            return port;
        } catch (Exception e) {
            log.error("ZLM openRtpServer 异常 streamId={}: {}", streamId, e.getMessage());
            return -1;
        }
    }

    /**
     * 关闭 RTP 收流端口。
     */
    public void closeRtpServer(String streamId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(zlmConfig.getBaseUrl())
                    .path("/index/api/closeRtpServer")
                    .queryParam("secret", zlmConfig.getSecret())
                    .queryParam("stream_id", streamId)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            int code = resp != null ? (int) resp.getOrDefault("code", -1) : -1;
            if (code != 0) {
                log.warn("ZLM closeRtpServer 失败 code={} streamId={}", code, streamId);
            } else {
                log.info("ZLM closeRtpServer 成功 streamId={}", streamId);
            }
        } catch (Exception e) {
            log.warn("ZLM closeRtpServer 异常 streamId={}: {}", streamId, e.getMessage());
        }
    }

    /**
     * 构造 HTTP-FLV 播放地址。
     */
    public String buildStreamUrl(String streamId) {
        // HLS 地址，支持 H265；ZLM 路径: /rtp/{streamId}/hls.m3u8
        return "http://" + zlmConfig.getRtpIp() + ":" + zlmConfig.getHttpPort()
                + "/rtp/" + streamId + "/hls.m3u8";
    }

    /** HTTP-FLV 播放地址（仅限 H264，flv.js 不支持 H265） */
    public String buildFlvUrl(String streamId) {
        return "http://" + zlmConfig.getRtpIp() + ":" + zlmConfig.getHttpPort()
                + "/rtp/" + streamId + ".live.flv";
    }
}
