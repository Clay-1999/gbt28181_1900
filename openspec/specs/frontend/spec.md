## Source: frontend-scaffold

## ADDED Requirements

### Requirement: Vue 3 前端项目初始化

系统 SHALL 在 `frontend/` 目录下提供可运行的 Vue 3 项目，包含必要依赖和开发代理配置。

#### Scenario: 前端开发服务器可启动

- **WHEN** 在 `frontend/` 目录执行 `npm install && npm run dev`
- **THEN** Vite 开发服务器在 5173 端口启动成功，浏览器可访问默认页面

#### Scenario: API 请求代理到后端

- **WHEN** 前端代码发起 `/api/**` 请求
- **THEN** Vite 开发代理将请求转发到 `http://localhost:8080`，不出现 CORS 错误

#### Scenario: 基础路由可访问

- **WHEN** 浏览器访问 `/local-config` 或 `/interconnects`
- **THEN** Vue Router 正确渲染对应的占位页面组件，不报 404
