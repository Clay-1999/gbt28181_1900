## Context

`Ivs1900DeviceConfigClient` 封装了对 IVS1900 REST API 的调用。当前 set 接口（setStreamConfig / setOsdConfig / setVideoMask）直接将传入的 JsonNode 作为完整 body 下发，要求调用方自行先 GET 再 merge，导致 `DeviceConfigHandler` 中存在重复的 read-modify-write 逻辑。

## Goals / Non-Goals

**Goals:**
- set 接口内部完成 read-modify-write，调用方只传 patch（要修改的字段）
- 删除 `DeviceConfigHandler` 中重复的 GET + merge 代码
- 保持所有对外方法签名不变

**Non-Goals:**
- 深层递归 merge（只做浅层字段覆盖即可满足当前需求）
- 引入新依赖或修改 DTO 结构

## Decisions

**D1：merge 粒度选浅层（shallow merge）**
IVS1900 的配置对象（streamConfig / osdConfig / videoMask）顶层字段即为业务字段，调用方 patch 也以顶层字段为粒度，浅层 merge 足够。深层递归 merge 会引入不必要的复杂度。

**D2：GET 失败时直接返回 false，不降级为直接下发**
若 GET 失败说明设备不可达或配置不存在，此时直接下发 patch 可能覆盖设备上的未知状态，风险更高。失败快速返回更安全。

**D3：merge 实现用 ObjectNode.setAll()**
Jackson 的 `ObjectNode.setAll(ObjectNode)` 即为浅层字段覆盖语义，无需手写循环，简洁且无额外依赖。

**D4：DeviceConfigHandler 中的 applyVideoParamAttribute 保留自身的 StreamID 匹配逻辑**
该方法需要按 StreamID 定位到具体码流再 patch，逻辑比其他方法复杂，仍在 handler 层完成 merge 后传给 setStreamConfig。其余三个方法（applyFrameMirror / applyOsdConfig / applyPictureMask）可直接简化。

## Risks / Trade-offs

- [额外 GET 请求] 每次 set 多一次 GET，增加延迟约 50-200ms → 可接受，配置下发本身是低频操作
- [GET 与 SET 之间的竞态] 极低概率下两次调用之间设备配置被其他来源修改 → 当前场景无并发写入，忽略
