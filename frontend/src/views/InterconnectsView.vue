<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">互联管理</span>
      <el-button type="primary" @click="openCreate">+ 新增</el-button>
    </div>

    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="remoteSipId" label="对端 SIP ID" min-width="160" />
      <el-table-column label="对端地址" min-width="160">
        <template #default="{ row }">{{ row.remoteIp }}:{{ row.remotePort }}</template>
      </el-table-column>
      <el-table-column prop="remoteDomain" label="对端域" min-width="120" />
      <el-table-column label="启用状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上联状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="!row.upLinkEnabled" type="info">未启用</el-tag>
          <el-tag v-else :type="row.upLinkStatus === 'ONLINE' ? 'success' : 'info'">
            {{ row.upLinkStatus === 'ONLINE' ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下联状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="downLinkTagType(row.downLinkStatus)">
            {{ downLinkLabel(row.downLinkStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后心跳" min-width="160">
        <template #default="{ row }">{{ row.lastHeartbeatAt ? formatDate(row.lastHeartbeatAt) : '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="160">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          <el-button
            size="small"
            :type="alarmSubStatus[row.id] ? 'success' : 'warning'"
            @click="handleAlarmSubscribe(row)"
          >{{ alarmSubStatus[row.id] ? '已订阅' : '订阅告警' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑互联配置' : '新增互联配置'"
      width="560px"
      @closed="resetForm"
    >
      <el-form
        ref="dialogFormRef"
        :model="dialogForm"
        :rules="dialogRules"
        label-width="110px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialogForm.name" placeholder="互联平台名称" />
        </el-form-item>
        <el-form-item label="对端 SIP ID" prop="remoteSipId">
          <el-input v-model="dialogForm.remoteSipId" placeholder="20位国标编码" />
        </el-form-item>
        <el-form-item label="对端 IP" prop="remoteIp">
          <el-input v-model="dialogForm.remoteIp" placeholder="对端 SIP 地址" />
        </el-form-item>
        <el-form-item label="对端端口" prop="remotePort">
          <el-input-number v-model="dialogForm.remotePort" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="对端域" prop="remoteDomain">
          <el-input v-model="dialogForm.remoteDomain" placeholder="对端 SIP 域" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="dialogForm.password" type="password" show-password placeholder="SIP 认证密码" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dialogForm.enabled" />
        </el-form-item>
        <el-form-item label="启用上联">
          <el-switch v-model="dialogForm.upLinkEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const alarmSubStatus = ref({})
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const dialogFormRef = ref()
let refreshTimer = null

const dialogForm = ref({
  name: '',
  remoteSipId: '',
  remoteIp: '',
  remotePort: 5060,
  remoteDomain: '',
  password: '',
  enabled: true,
  upLinkEnabled: false
})

const dialogRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  remoteSipId: [{ required: true, message: '请输入对端 SIP ID', trigger: 'blur' }],
  remoteIp: [{ required: true, message: '请输入对端 IP', trigger: 'blur' }],
  remotePort: [{ required: true, message: '请输入对端端口', trigger: 'blur' }],
  remoteDomain: [{ required: true, message: '请输入对端域', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function loadList() {
  try {
    const { data } = await axios.get('/api/interconnects')
    list.value = data
    // 查询每条配置的告警订阅状态
    for (const item of data) {
      try {
        const { data: s } = await axios.get(`/api/interconnects/${item.id}/alarm-subscribe`)
        alarmSubStatus.value[item.id] = s.subscribed
      } catch (_) {}
    }
  } catch (e) {
    ElMessage.error('加载互联配置失败')
  }
}

async function handleAlarmSubscribe(row) {
  const wasSubscribed = alarmSubStatus.value[row.id]
  try {
    const { data } = await axios.post(`/api/interconnects/${row.id}/alarm-subscribe`)
    alarmSubStatus.value[row.id] = data.subscribed
    if (wasSubscribed) {
      ElMessage.success('已取消告警订阅')
    } else if (data.subscribed) {
      ElMessage.success('告警订阅成功')
    } else {
      ElMessage.error('告警订阅失败，请检查 SIP 连通性')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + (e.response?.data?.message || e.message))
  }
}

function openCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(dialogForm.value, {
    name: row.name,
    remoteSipId: row.remoteSipId,
    remoteIp: row.remoteIp,
    remotePort: row.remotePort,
    remoteDomain: row.remoteDomain,
    password: '',
    enabled: row.enabled,
    upLinkEnabled: row.upLinkEnabled ?? false
  })
  dialogVisible.value = true
}

function resetForm() {
  editingId.value = null
  dialogForm.value = {
    name: '', remoteSipId: '', remoteIp: '', remotePort: 5060,
    remoteDomain: '', password: '', enabled: true, upLinkEnabled: false
  }
  dialogFormRef.value?.clearValidate()
}

async function handleSubmit() {
  const valid = await dialogFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value) {
      await axios.put(`/api/interconnects/${editingId.value}`, dialogForm.value)
      ElMessage.success('编辑成功')
    } else {
      await axios.post('/api/interconnects', dialogForm.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch (e) {
    ElMessage.error('操作失败：' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除互联配置「${row.name}」？`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/api/interconnects/${row.id}`)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
    }
  }
}

function downLinkTagType(status) {
  const map = { ONLINE: 'success', REGISTERING: 'warning', OFFLINE: 'info', ERROR: 'danger' }
  return map[status] || 'info'
}

function downLinkLabel(status) {
  const map = { ONLINE: '已注册', REGISTERING: '注册中', OFFLINE: '离线', ERROR: '错误' }
  return map[status] || '未知'
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadList()
  refreshTimer = setInterval(loadList, 10000)
})
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
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
</style>
