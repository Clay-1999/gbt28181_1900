<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">IVS1900 互联配置</span>
    </div>

    <el-card class="config-card" shadow="never">
      <template #header>
        <span>GB/T 28181 接入参数</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        style="max-width: 600px"
      >
        <el-form-item label="SIP 设备 ID" prop="sipId">
          <el-input v-model="form.sipId" placeholder="IVS1900 的 GB/T 28181 设备 ID（20位）" />
        </el-form-item>
        <el-form-item label="SIP IP" prop="ip">
          <el-input v-model="form.ip" placeholder="IVS1900 SIP 监听 IP" />
        </el-form-item>
        <el-form-item label="SIP 端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="SIP 域" prop="domain">
          <el-input v-model="form.domain" placeholder="IVS1900 SIP 域" />
        </el-form-item>
        <el-form-item label="SIP 密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="Digest 认证密码" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSave">保存</el-button>
          <el-button
            v-if="currentId"
            :type="alarmSubscribed ? 'success' : 'warning'"
            :loading="subscribing"
            style="margin-left: 8px"
            @click="handleAlarmSubscribe"
          >{{ alarmSubscribed ? '已订阅告警' : '订阅告警' }}</el-button>
          <el-tag v-if="currentId" :type="statusTagType" style="margin-left: 12px">
            上联状态：{{ statusLabel }}
          </el-tag>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const formRef = ref()
const submitting = ref(false)
const subscribing = ref(false)
const currentId = ref(null)
const currentStatus = ref('OFFLINE')
const alarmSubscribed = ref(false)

const form = ref({
  sipId: '',
  ip: '',
  port: 5060,
  domain: '',
  password: ''
})

const rules = {
  sipId: [{ required: true, message: '请输入 SIP 设备 ID', trigger: 'blur' }],
  ip: [{ required: true, message: '请输入 SIP IP', trigger: 'blur' }],
  port: [{ required: true, message: '请输入 SIP 端口', trigger: 'blur' }],
  domain: [{ required: true, message: '请输入 SIP 域', trigger: 'blur' }]
}

const statusTagType = computed(() => {
  const map = { ONLINE: 'success', OFFLINE: 'info' }
  return map[currentStatus.value] || 'info'
})

const statusLabel = computed(() => {
  const map = { ONLINE: '在线', OFFLINE: '离线' }
  return map[currentStatus.value] || '未知'
})

async function loadConfig() {
  try {
    const { data } = await axios.get('/api/ivs1900/interconnect')
    if (data && data.length > 0) {
      const cfg = data[0]
      currentId.value = cfg.id
      currentStatus.value = cfg.upLinkStatus || 'OFFLINE'
      Object.assign(form.value, {
        sipId: cfg.sipId,
        ip: cfg.ip,
        port: cfg.port,
        domain: cfg.domain,
        password: ''
      })
      // 查询告警订阅状态
      try {
        const { data: s } = await axios.get(`/api/ivs1900/interconnect/${cfg.id}/alarm-subscribe`)
        alarmSubscribed.value = s.subscribed
      } catch (_) {}
    }
  } catch (e) {
    ElMessage.error('加载 IVS1900 配置失败')
  }
}

async function handleAlarmSubscribe() {
  if (!currentId.value) return
  subscribing.value = true
  const wasSubscribed = alarmSubscribed.value
  try {
    const { data } = await axios.post(`/api/ivs1900/interconnect/${currentId.value}/alarm-subscribe`)
    alarmSubscribed.value = data.subscribed
    if (wasSubscribed) {
      ElMessage.success('已取消告警订阅')
    } else if (data.subscribed) {
      ElMessage.success('告警订阅成功')
    } else {
      ElMessage.error('告警订阅失败，请检查 SIP 连通性')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + (e.response?.data?.message || e.message))
  } finally {
    subscribing.value = false
  }
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (currentId.value) {
      const { data } = await axios.put(`/api/ivs1900/interconnect/${currentId.value}`, form.value)
      currentStatus.value = data.upLinkStatus || 'OFFLINE'
    } else {
      const { data } = await axios.post('/api/ivs1900/interconnect', form.value)
      currentId.value = data.id
      currentStatus.value = data.upLinkStatus || 'OFFLINE'
    }
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.page-container {
  padding: 24px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.config-card {
  max-width: 700px;
}
</style>
