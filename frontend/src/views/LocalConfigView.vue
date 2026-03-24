<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">本端 SIP 配置</span>
      <el-tag
        :type="statusTagType"
        size="large"
        class="status-badge"
      >
        {{ statusLabel }}
      </el-tag>
    </div>

    <el-alert
      v-if="form.status === 'ERROR' && form.errorMsg"
      :title="form.errorMsg"
      type="error"
      show-icon
      :closable="false"
      class="error-alert"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="config-form"
    >
      <el-row :gutter="40">
        <el-col :span="12">
          <el-form-item label="设备 ID" prop="deviceId">
            <el-input v-model="form.deviceId" placeholder="20位国标编码" maxlength="20" />
          </el-form-item>
          <el-form-item label="SIP 域" prop="domain">
            <el-input v-model="form.domain" placeholder="例如：3402000000" />
          </el-form-item>
          <el-form-item label="SIP 地址" prop="sipIp">
            <el-input v-model="form.sipIp" placeholder="本端监听 IP" />
          </el-form-item>
          <el-form-item label="SIP 端口" prop="sipPort">
            <el-input-number v-model="form.sipPort" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="传输协议" prop="transport">
            <el-select v-model="form.transport" style="width: 100%">
              <el-option label="UDP" value="UDP" />
              <el-option label="TCP" value="TCP" />
            </el-select>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="SIP 认证密码"
            />
          </el-form-item>
          <el-form-item label="注册有效期" prop="expires">
            <el-input-number v-model="form.expires" :min="60" :max="86400" style="width: 100%" />
            <span style="margin-left: 8px; color: #909399; font-size: 12px">秒</span>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div class="form-footer">
      <el-button
        type="primary"
        :disabled="saving"
        :loading="saving"
        @click="handleSave"
      >
        保存
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const formRef = ref()
const saving = ref(false)
let pollTimer = null
let pollCount = 0

const form = ref({
  deviceId: '',
  domain: '',
  sipIp: '',
  sipPort: 5060,
  transport: 'UDP',
  password: '',
  expires: 3600,
  status: null,
  errorMsg: ''
})

const rules = {
  deviceId: [
    { required: true, message: '请输入设备 ID', trigger: 'blur' },
    { len: 20, message: '设备 ID 必须为 20 位', trigger: 'blur' }
  ],
  domain: [{ required: true, message: '请输入 SIP 域', trigger: 'blur' }],
  sipIp: [{ required: true, message: '请输入 SIP 地址', trigger: 'blur' }],
  sipPort: [{ required: true, message: '请输入 SIP 端口', trigger: 'blur' }],
  transport: [{ required: true, message: '请选择传输协议', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  expires: [{ required: true, message: '请输入注册有效期', trigger: 'blur' }]
}

const statusTagType = computed(() => {
  const map = { RUNNING: 'success', RELOADING: 'warning', ERROR: 'danger' }
  return map[form.value.status] || 'info'
})

const statusLabel = computed(() => {
  const map = { RUNNING: '运行中', RELOADING: '重载中', ERROR: '错误' }
  return map[form.value.status] || '未知'
})

async function loadConfig() {
  try {
    const { data } = await axios.get('/api/local-config')
    Object.assign(form.value, data)
    // password is always *** from server, clear it for user to re-enter if they want
    if (form.value.password === '***') form.value.password = ''
  } catch (e) {
    ElMessage.error('加载配置失败')
  }
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    saving.value = true
    await axios.put('/api/local-config', {
      deviceId: form.value.deviceId,
      domain: form.value.domain,
      sipIp: form.value.sipIp,
      sipPort: form.value.sipPort,
      transport: form.value.transport,
      password: form.value.password,
      expires: form.value.expires
    })
    form.value.status = 'RELOADING'
    startPolling()
  } catch (e) {
    saving.value = false
    if (e.response?.status === 409) {
      ElMessage.warning('SIP Stack 正在重载，请稍后再试')
    } else {
      ElMessage.error('保存失败：' + (e.response?.data?.message || e.message))
    }
  }
}

function startPolling() {
  pollCount = 0
  pollTimer = setInterval(async () => {
    pollCount++
    try {
      const { data } = await axios.get('/api/local-config/status')
      form.value.status = data.status
      form.value.errorMsg = data.errorMsg
      if (data.status === 'RUNNING' || data.status === 'ERROR') {
        stopPolling()
        saving.value = false
        if (data.status === 'RUNNING') {
          ElMessage.success('SIP Stack 已启动')
        }
      } else if (pollCount >= 12) {
        stopPolling()
        saving.value = false
        ElMessage.warning('热重载超时（60秒），请检查配置')
      }
    } catch (e) {
      // ignore transient errors
    }
  }, 5000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(loadConfig)
onUnmounted(stopPolling)
</script>

<style scoped>
.page-container {
  padding: 24px;
  max-width: 900px;
}

.page-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.status-badge {
  font-size: 13px;
}

.error-alert {
  margin-bottom: 20px;
}

.config-form {
  background: #fff;
  padding: 24px 16px 8px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.form-footer {
  margin-top: 20px;
  padding-left: 4px;
}
</style>
