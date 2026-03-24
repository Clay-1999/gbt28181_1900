<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">录像管理</span>
    </div>

    <!-- 查询条件 -->
    <div class="query-bar">
      <el-select v-model="deviceKey" placeholder="请选择相机" style="width:260px" clearable>
        <el-option-group label="本端设备">
          <el-option
            v-for="d in allDevices.local"
            :key="'local:' + d.gbDeviceId"
            :value="'local:' + d.gbDeviceId"
            :label="d.name || d.gbDeviceId"
          />
        </el-option-group>
        <el-option-group label="外域设备">
          <el-option
            v-for="d in allDevices.remote"
            :key="'remote:' + d.deviceId"
            :value="'remote:' + d.deviceId"
            :label="(d.name || d.deviceId) + (d.interconnectName ? ' [' + d.interconnectName + ']' : '')"
          />
        </el-option-group>
      </el-select>

      <el-date-picker v-model="startTime" type="datetime" placeholder="开始时间"
        format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" style="width:200px" />
      <el-date-picker v-model="endTime" type="datetime" placeholder="结束时间"
        format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" style="width:200px" />
      <el-button type="primary" :loading="recordLoading" @click="queryRecords">查询</el-button>
    </div>

    <!-- 超时提示 -->
    <el-alert v-if="recordTimeout" title="设备响应超时（504），请稍后重试"
      type="warning" :closable="false" show-icon style="margin-bottom:12px;" />

    <!-- 录像结果表格 -->
    <el-table v-if="recordResults.length" :data="recordResults" border stripe
      style="width:100%;margin-bottom:16px;">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="name" label="录像名称" min-width="160" />
      <el-table-column prop="startTime" label="开始时间" min-width="170" />
      <el-table-column prop="endTime" label="结束时间" min-width="170" />
      <el-table-column prop="type" label="录像类型" width="120" align="center" />
      <el-table-column prop="filePath" label="文件路径" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" :loading="playbackLoading" @click="startPlayback(row)">播放</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!recordLoading && recordQueried && !recordTimeout" description="暂无录像" />

    <!-- 播放器 -->
    <div v-if="streamUrl" class="player-area">
      <div class="player-header">
        <span>正在回放：{{ currentRecord?.name || currentRecord?.startTime }}</span>
        <el-button size="small" type="danger" @click="stopPlayback">停止</el-button>
      </div>

      <!-- 视频 -->
      <div style="background:#000;width:100%;height:400px;">
        <video ref="videoEl" autoplay style="width:100%;height:100%;display:block;object-fit:contain;" />
      </div>

      <!-- 控制栏 -->
      <div class="player-controls">
        <!-- 播放/暂停 -->
        <el-button :icon="playing ? 'VideoPause' : 'VideoPlay'" circle size="small"
          @click="togglePlay" />

        <!-- 进度条 -->
        <span class="time-label">{{ formatAbsTime(currentSec) }}</span>
        <el-slider
          v-model="currentSec"
          :min="0"
          :max="totalSec"
          :step="1"
          :format-tooltip="formatAbsTime"
          style="flex:1;margin:0 12px;"
          @change="onSeek"
        />
        <span class="time-label">{{ formatAbsTime(totalSec) }}</span>

        <!-- 倍速 -->
        <el-select v-model="speed" size="small" style="width:80px;margin-left:12px;" @change="onSpeedChange">
          <el-option label="0.5x" :value="0.5" />
          <el-option label="1x"   :value="1"   />
          <el-option label="2x"   :value="2"   />
          <el-option label="4x"   :value="4"   />
        </el-select>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import Hls from 'hls.js'

const allDevices = ref({ local: [], remote: [] })
const deviceKey = ref('')
const startTime = ref((() => { const d = new Date(); d.setDate(d.getDate() - 1); return d.toISOString().slice(0, 19) })())
const endTime = ref(new Date().toISOString().slice(0, 19))

const recordLoading = ref(false)
const recordResults = ref([])
const recordTimeout = ref(false)
const recordQueried = ref(false)

const playbackLoading = ref(false)
const streamUrl = ref('')
const currentRecord = ref(null)
const videoEl = ref(null)

// 回放状态
const playing = ref(false)
const speed = ref(1)
const currentSec = ref(0)   // 相对于录像开始的秒数（前端维护）
const totalSec = ref(0)
let playbackStartTime = null  // 录像开始的 Date
let progressTimer = null
let hlsPlayer = null

const loadAllDevices = async () => {
  try {
    const [localRes, remoteRes] = await Promise.all([
      axios.get('/api/devices/local'),
      axios.get('/api/devices/remote')
    ])
    allDevices.value = { local: localRes.data, remote: remoteRes.data }
  } catch (e) { /* 静默失败 */ }
}

const queryRecords = async () => {
  if (!deviceKey.value) { ElMessage.warning('请先选择相机'); return }
  const [type, deviceId] = deviceKey.value.split(':')
  recordLoading.value = true
  recordTimeout.value = false
  recordResults.value = []
  recordQueried.value = false
  try {
    const { data } = await axios.post(`/api/devices/${type}/${deviceId}/records/query`, {
      startTime: startTime.value, endTime: endTime.value, type: 'all'
    })
    recordResults.value = data.items || []
    recordQueried.value = true
  } catch (e) {
    if (e.response?.status === 504) { recordTimeout.value = true }
    else { ElMessage.error('查询失败: ' + (e.response?.data?.error || e.message)) }
    recordQueried.value = true
  } finally {
    recordLoading.value = false
  }
}

const startPlayback = async (record) => {
  if (!deviceKey.value) return
  const [type, deviceId] = deviceKey.value.split(':')
  playbackLoading.value = true
  destroyPlayer()
  streamUrl.value = ''
  try {
    const { data } = await axios.post(`/api/devices/${type}/${deviceId}/playback/start`, {
      startTime: record.startTime, endTime: record.endTime
    })
    streamUrl.value = data.streamUrl
    currentRecord.value = record

    // 计算录像总时长
    playbackStartTime = new Date(record.startTime.replace('T', ' '))
    const endDate = new Date(record.endTime.replace('T', ' '))
    totalSec.value = Math.max(1, Math.round((endDate - playbackStartTime) / 1000))
    currentSec.value = 0
    speed.value = 1
    playing.value = true

    await new Promise(r => setTimeout(r, 100))
    initPlayer(data.streamUrl)
    startProgressTimer()
  } catch (e) {
    if (e.response?.status === 504) { ElMessage.warning('回放启动超时，请重试') }
    else { ElMessage.error('回放失败: ' + (e.response?.data?.error || e.message)) }
  } finally {
    playbackLoading.value = false
  }
}

const stopPlayback = async () => {
  if (!deviceKey.value) return
  const [type, deviceId] = deviceKey.value.split(':')
  destroyPlayer()
  streamUrl.value = ''
  currentRecord.value = null
  playing.value = false
  currentSec.value = 0
  try { await axios.post(`/api/devices/${type}/${deviceId}/playback/stop`) } catch (e) { /* 忽略 */ }
}

const togglePlay = async () => {
  if (!deviceKey.value) return
  const [type, deviceId] = deviceKey.value.split(':')
  if (playing.value) {
    // 暂停
    try {
      await axios.post(`/api/devices/${type}/${deviceId}/playback/control`, { action: 'pause' })
      playing.value = false
      stopProgressTimer()
    } catch (e) { ElMessage.error('暂停失败') }
  } else {
    // 继续播放
    try {
      await axios.post(`/api/devices/${type}/${deviceId}/playback/control`, { action: 'play' })
      playing.value = true
      startProgressTimer()
    } catch (e) { ElMessage.error('播放失败') }
  }
}

const onSeek = async (sec) => {
  if (!deviceKey.value || !playbackStartTime) return
  const [type, deviceId] = deviceKey.value.split(':')
  // 将秒偏移转回绝对时间字符串
  const seekDate = new Date(playbackStartTime.getTime() + sec * 1000)
  const seekTime = toIsoLocal(seekDate)
  try {
    await axios.post(`/api/devices/${type}/${deviceId}/playback/control`, { action: 'seek', seekTime })
    currentSec.value = sec
    if (!playing.value) {
      playing.value = true
      startProgressTimer()
    }
  } catch (e) { ElMessage.error('跳转失败') }
}

const onSpeedChange = async (val) => {
  if (!deviceKey.value) return
  const [type, deviceId] = deviceKey.value.split(':')
  try {
    await axios.post(`/api/devices/${type}/${deviceId}/playback/control`, { action: 'scale', scale: val })
  } catch (e) { ElMessage.error('倍速切换失败') }
}

// 前端进度推进（每秒 +speed 秒）
const startProgressTimer = () => {
  stopProgressTimer()
  progressTimer = setInterval(() => {
    if (currentSec.value < totalSec.value) {
      currentSec.value = Math.min(currentSec.value + speed.value, totalSec.value)
    } else {
      stopProgressTimer()
      playing.value = false
    }
  }, 1000)
}

const stopProgressTimer = () => {
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
}

const initPlayer = (url) => {
  if (!videoEl.value) return
  destroyPlayer()
  if (Hls.isSupported()) {
    hlsPlayer = new Hls({ enableWorker: true, lowLatencyMode: false })
    hlsPlayer.loadSource(url)
    hlsPlayer.attachMedia(videoEl.value)
    hlsPlayer.on(Hls.Events.MANIFEST_PARSED, () => { videoEl.value.play() })
    hlsPlayer.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) ElMessage.error('回放加载失败: ' + data.details)
    })
  } else if (videoEl.value.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.value.src = url
    videoEl.value.play()
  } else {
    ElMessage.error('当前浏览器不支持 HLS 播放')
  }
}

const destroyPlayer = () => {
  stopProgressTimer()
  if (hlsPlayer) { hlsPlayer.destroy(); hlsPlayer = null }
  if (videoEl.value) { videoEl.value.src = '' }
}

// 将秒偏移转为绝对时间字符串 HH:mm:ss
const formatAbsTime = (sec) => {
  if (!playbackStartTime) return '--:--:--'
  const d = new Date(playbackStartTime.getTime() + sec * 1000)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// 将 Date 转为 "YYYY-MM-DDTHH:mm:ss" 本地时间字符串
const toIsoLocal = (d) => {
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(() => { loadAllDevices() })
onUnmounted(() => { destroyPlayer() })
</script>

<style scoped>
.page-container { padding: 24px; }
.page-header {
  display: flex; align-items: center;
  justify-content: space-between; margin-bottom: 16px;
}
.page-title { font-size: 18px; font-weight: 600; color: #303133; }
.query-bar {
  display: flex; align-items: center;
  gap: 12px; flex-wrap: wrap; margin-bottom: 16px;
}
.player-area { border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; }
.player-header {
  display: flex; align-items: center;
  justify-content: space-between;
  padding: 8px 12px; background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  font-size: 13px; color: #606266;
}
.player-controls {
  display: flex; align-items: center;
  padding: 8px 16px; background: #1a1a1a; gap: 8px;
}
.time-label { color: #ccc; font-size: 12px; white-space: nowrap; min-width: 60px; text-align: center; }
</style>
