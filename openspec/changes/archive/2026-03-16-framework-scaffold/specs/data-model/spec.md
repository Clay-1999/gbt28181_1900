## ADDED Requirements

### Requirement: JPA 实体定义

系统 SHALL 定义三个 JPA 实体并在应用启动时自动建表：`LocalSipConfig`（单行）、`InterconnectConfig`（多行）、`Ivs1900CameraMapping`（多行）。

#### Scenario: 启动时自动建表

- **WHEN** 应用首次启动
- **THEN** H2 数据库中自动创建 `local_sip_config`、`interconnect_config`、`ivs1900_camera_mapping` 三张表

#### Scenario: Repository 可用

- **WHEN** 通过 Spring 注入对应 Repository
- **THEN** 可调用 `save()`、`findById()`、`findAll()` 等基础方法无异常

---

### Requirement: 密码字段 AES 加密存储

系统 SHALL 对 `LocalSipConfig.password` 和 `InterconnectConfig.password` 字段自动进行 AES 加密存储、解密读取，业务代码无需感知。

#### Scenario: 密码写入数据库时加密

- **WHEN** 保存含密码的实体到数据库
- **THEN** 数据库中存储的密码为密文（非明文）

#### Scenario: 密码从数据库读取时解密

- **WHEN** 从数据库读取含密码的实体
- **THEN** 实体对象的 password 字段为明文，可直接使用
