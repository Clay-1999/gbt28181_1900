<template>
  <el-container style="height: 100vh;">
    <el-header class="app-header">
      <span class="app-logo">GB/T 28181 管理平台</span>
    </el-header>
    <el-container>
      <el-aside width="180px" class="app-aside">
        <el-menu
          :default-active="$route.path"
          router
          background-color="#ffffff"
          text-color="#333333"
          active-text-color="#00a0a0"
        >
          <el-menu-item index="/local-config">
            <el-icon><Setting /></el-icon>
            <span>本端配置</span>
          </el-menu-item>
          <el-menu-item index="/interconnects">
            <el-icon><Connection /></el-icon>
            <span>互联管理</span>
          </el-menu-item>
          <el-menu-item index="/devices">
            <el-icon><VideoCamera /></el-icon>
            <span>设备列表</span>
          </el-menu-item>
          <el-menu-item index="/ivs1900-config">
            <el-icon><Cpu /></el-icon>
            <span>IVS1900</span>
          </el-menu-item>
          <el-menu-item index="/alarms">
            <el-icon><Bell /></el-icon>
            <span>告警管理</span>
          </el-menu-item>
          <el-menu-item index="/recordings">
            <el-icon><Film /></el-icon>
            <span>录像管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { Setting, Connection, VideoCamera, Cpu, Bell, Film } from '@element-plus/icons-vue'
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif; }

.app-header {
  background-color: #1a1f2e;
  color: #fff;
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 20px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0,0,0,0.3);
  z-index: 100;
}

.app-logo {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #ffffff;
}

.app-aside {
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  overflow: hidden;
}

.app-aside .el-menu {
  border-right: none;
  height: 100%;
}

.app-aside .el-menu-item {
  height: 46px;
  line-height: 46px;
  font-size: 13px;
  color: #4a4a4a;
}

.app-aside .el-menu-item .el-icon {
  color: #6b7280;
  font-size: 16px;
}

.app-aside .el-menu-item:hover {
  background-color: #f0fafa !important;
  color: #00a0a0 !important;
}

.app-aside .el-menu-item:hover .el-icon {
  color: #00a0a0 !important;
}

.app-aside .el-menu-item.is-active {
  background-color: #e6f7f7 !important;
  color: #00a0a0 !important;
  border-left: 3px solid #00a0a0;
  font-weight: 500;
}

.app-aside .el-menu-item.is-active .el-icon {
  color: #00a0a0 !important;
}

.app-main {
  background-color: #f5f7fa;
  overflow-y: auto;
  padding: 0;
}
</style>
