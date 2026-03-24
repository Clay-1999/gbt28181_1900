## 1. 日志 pattern 配置

- [ ] 1.1 在 `application.yml` 的 `logging.pattern.console` 和 `logging.pattern.file` 中加入 `[%X{callId:-}]` 占位符

## 2. DeviceCommandRouter — 写入 MDC

- [ ] 2.1 在 `route()` 方法路由到 handler 之前，从 SIP header 取出 Call-ID，调用 `MDC.put("callId", callId)`
- [ ] 2.2 Call-ID header 为 null 时跳过写入，不影响正常流程

## 3. GbtSipListener — 清理主线程 MDC

- [ ] 3.1 在 `processRequest()` 的 MESSAGE 分支处理完后调用 `MDC.remove("callId")`

## 4. DeviceConfigHandler — 异步线程继承 MDC

- [ ] 4.1 在 `handle()` 的 `CompletableFuture.runAsync()` 前，用 `MDC.getCopyOfContextMap()` 获取快照
- [ ] 4.2 lambda 入口恢复 MDC，finally 块中 `MDC.clear()`

## 5. ConfigDownloadHandler — 异步线程继承 MDC

- [ ] 5.1 同 4.1：获取 MDC 快照
- [ ] 5.2 同 4.2：lambda 入口恢复，finally 清理

## 6. 测试脚本 tmp_sip_config_test.py

- [ ] 6.1 `make_message()` 返回值增加 `call_id`（当前已生成但未返回）
- [ ] 6.2 `run_test()` 接收并返回 `(passed: bool, call_id: str)`
- [ ] 6.3 `main()` 收集所有失败用例的 `(name, call_id)`
- [ ] 6.4 测试结束后，若有失败用例，从 `logs/gbt28181.log` 中按 call_id 过滤并打印对应日志行
- [ ] 6.5 支持命令行参数 `--log` 指定日志文件路径，默认 `logs/gbt28181.log`

## 7. 验证

- [ ] 7.1 运行测试脚本，确认失败用例输出对应的 IVS1900 HTTP 入参/出参日志
- [ ] 7.2 确认定时同步的 IVS1900 HTTP 日志中 callId 字段为 `-`（空占位符），不报错
