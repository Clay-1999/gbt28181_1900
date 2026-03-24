<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">告警管理</span>
      <div class="header-actions">
        <el-input
          v-model="filterDeviceId"
          placeholder="按设备 ID 过滤"
          clearable
          style="width: 240px; margin-right: 8px"
          @keyup.enter="loadAlarms"
          @clear="loadAlarms"
        />
        <el-button @click="loadAlarms">查询</el-button>
        <el-button :icon="Refresh" @click="loadAlarms" style="margin-left: 4px">刷新</el-button>
      </div>
    </div>

    <el-table :data="alarms" border stripe style="width: 100%" v-loading="loading">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="deviceId" label="设备 ID" min-width="160" />
      <el-table-column label="优先级" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="priorityTagType(row.alarmPriority)" size="small">
            {{ priorityLabel(row.alarmPriority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="告警类型" width="140" align="center">
        <template #default="{ row }">
          {{ alarmTypeLabel(row.alarmMethod, row.alarmType) }}
        </template>
      </el-table-column>
      <el-table-column label="报警方式" width="110" align="center">
        <template #default="{ row }">
          {{ alarmMethodLabel(row.alarmMethod) }}
        </template>
      </el-table-column>
      <el-table-column prop="alarmDescription" label="告警描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="alarmTime" label="发生时间" min-width="160" />
      <el-table-column prop="sourceIp" label="来源 IP" width="130" />
      <el-table-column prop="receivedAt" label="接收时间" min-width="160" />
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadAlarms"
        @size-change="loadAlarms"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const alarms = ref([])
const loading = ref(false)
const filterDeviceId = ref('')
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

async function loadAlarms() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value
    }
    if (filterDeviceId.value.trim()) {
      params.deviceId = filterDeviceId.value.trim()
    }
    const { data } = await axios.get('/api/alarms', { params })
    alarms.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载告警列表失败')
  } finally {
    loading.value = false
  }
}

function priorityTagType(p) {
  const map = { '1': 'danger', '2': 'warning', '3': '', '4': 'info' }
  return map[p] || 'info'
}

function priorityLabel(p) {
  const map = { '1': '一级', '2': '二级', '3': '三级', '4': '四级' }
  return map[p] || (p || '-')
}

function alarmMethodLabel(m) {
  const map = {
    '0': '全部', '1': '电话报警', '2': '设备报警', '3': '短信报警',
    '4': 'GPS报警', '5': '视频报警', '6': '设备故障报警', '7': '其他报警'
  }
  return map[m] || (m || '-')
}

function alarmTypeLabel(method, type) {
  if (!type) return '-'
  if (method === '2') {
    const map = {
      '1': '视频丢失', '2': '设备防拆', '3': '磁盘满', '4': '设备高温', '5': '设备低温'
    }
    return map[type] || type
  }
  if (method === '5') {
    const map = {
      '1': '人工视频', '2': '移动目标检测', '3': '遗留物检测', '4': '物体移除',
      '5': '绊线检测', '6': '入侵检测', '7': '逆行检测', '8': '徘徊检测',
      '9': '流量统计', '10': '密度检测', '11': '视频异常', '12': '快速移动', '13': '图像遮挡'
    }
    return map[type] || type
  }
  if (method === '6') {
    const map = { '1': '磁盘故障', '2': '风扇故障' }
    return map[type] || type
  }
  return type
}

onMounted(loadAlarms)
</script>

<style scoped>
.page-container {
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
