## 1. 配置层

- [x] 1.1 在 `Ivs1900Properties.java` 中新增 `username` 和 `password` 字段
- [x] 1.2 在 `application.yml` 的 `ivs1900` 节点下新增 `username` 和 `password` 配置项，支持环境变量 `IVS1900_USERNAME` / `IVS1900_PASSWORD` 注入，并添加生产环境必须覆盖的注释

## 2. 登录服务

- [x] 2.1 在 IVS1900 登录服务中注入 `Ivs1900Properties`，将登录请求体的 `userName` / `password` 改为从配置读取
- [x] 2.2 确保登录日志中不打印 `password` 字段

## 3. 验证

- [x] 3.1 编译通过：`mvn compile -q`
- [x] 3.2 启动应用，确认 IVS1900 登录使用配置中的凭据（日志可见登录成功）
