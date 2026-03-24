## 设计方案

### 新增：Ivs1900HttpClient

```java
@Component
@RequiredArgsConstructor
public class Ivs1900HttpClient {
    private final Ivs1900SessionManager sessionManager;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public <T> T get(String url, Class<T> responseType);
    public <T> T post(String url, Object body, Class<T> responseType);
    public <T> T put(String url, Object body, Class<T> responseType);
    public void delete(String url);
}
```

- 每个方法内部：取 session → 构造带 Cookie 的 headers → 发请求
- 捕获 `HttpClientErrorException.Unauthorized` → 调用 `sessionManager.invalidateAndRelogin()` → 重试一次
- session 为 null 时直接返回 null / 不发请求

### 新增请求 DTO

| 类 | 字段 |
|---|---|
| `LoginRequest` | `userName`, `password`, `timeout` |
| `SdcCapabilityRequest` | `cameraCodeList: List<String>`, `capabilityType: int` |
| `CameraNameRequest` | `cameraCode`, `newCameraName` |
| `SetDeviceConfigRequest` | `deviceCode`, `configType: int`, `configItem: JsonNode` |

所有 DTO 使用 `@Data`、`@JsonInclude(NON_NULL)`。

### 循环依赖处理

`Ivs1900HttpClient` 依赖 `Ivs1900SessionManager`，`SessionManager` 的登录接口不依赖 `HttpClient`，因此无循环依赖。登出和保活通过 `HttpClient` 调用。

### 迁移映射

| 原调用位置 | 原方式 | 改为 |
|---|---|---|
| `SessionManager.login()` | `restTemplate.postForEntity` + `Map.of()` | `restTemplate.postForEntity` + `LoginRequest`（保留直接调用） |
| `SessionManager.keepAlive()` | `restTemplate.exchange` | `httpClient.get()` |
| `SessionManager.login()` 登出 | `restTemplate.exchange DELETE` | `httpClient.delete()` |
| `SyncService.fetchCameraList()` | `restTemplate.exchange GET` | `httpClient.get()` |
| `SyncService.fetchOnlineStatus()` | `restTemplate.exchange GET` | `httpClient.get()` |
| `DeviceConfigClient.doGet()` | `restTemplate.exchange GET` + 重登循环 | `httpClient.get()`（重登在 HttpClient 内） |
| `DeviceConfigClient.getSdcCapability()` | `restTemplate.exchange POST` + `ObjectNode` | `httpClient.post()` + `SdcCapabilityRequest` |
| `DeviceConfigClient.setCameraName()` | `restTemplate.exchange PUT` + `ObjectNode` + 重登循环 | `httpClient.put()` + `CameraNameRequest` |
| `DeviceConfigClient.doSetDeviceConfig()` | `restTemplate.exchange POST` + `ObjectNode` + 重登循环 | `httpClient.post()` + `SetDeviceConfigRequest` |
