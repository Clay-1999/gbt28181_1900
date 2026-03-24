<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">设备列表</span>
      <el-button @click="refresh" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="refresh">
      <el-tab-pane label="本端设备" name="local">
        <el-table :data="localDevices" border stripe style="width: 100%" v-loading="loading">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="name" label="相机名称" min-width="180" />
          <el-table-column prop="gbDeviceId" label="国标设备 ID" min-width="200" />
          <el-table-column label="相机类型" width="110" align="center">
            <template #default="{ row }">{{ formatPtzType(row.ptzType) }}</template>
          </el-table-column>
          <el-table-column label="在线状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ON' ? 'success' : 'info'">
                {{ row.status === 'ON' ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后同步时间" min-width="180">
            <template #default="{ row }">{{ formatDate(row.syncedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openConfig(row, 'local')">配置</el-button>
              <el-button size="small" type="primary" @click="openStream(row, 'local')">播放</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="外域设备" name="remote">
        <el-table :data="remoteDevices" border stripe style="width: 100%" v-loading="loading">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="name" label="设备名称" min-width="180" />
          <el-table-column prop="deviceId" label="国标设备 ID" min-width="200" />
          <el-table-column label="相机类型" width="110" align="center">
            <template #default="{ row }">{{ formatPtzType(row.ptzType) }}</template>
          </el-table-column>
          <el-table-column label="在线状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ON' ? 'success' : 'info'">
                {{ row.status === 'ON' ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="interconnectName" label="所属互联平台" min-width="160" />
          <el-table-column label="最后同步时间" min-width="180">
            <template #default="{ row }">{{ formatDate(row.syncedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openConfig(row, 'remote')">配置</el-button>
              <el-button size="small" type="primary" @click="openStream(row)">播放</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 配置对话框 -->
    <el-dialog v-model="configDialogVisible" :title="configDialogTitle" width="640px" @closed="onDialogClosed">
      <el-tabs v-model="configTab" @tab-change="loadConfig">
        <!-- VideoParamAttribute -->
        <el-tab-pane label="视频参数" name="video-param">
          <div v-loading="configLoading">
            <template v-if="videoForm.streamInfoList && videoForm.streamInfoList.length">
              <el-table :data="videoForm.streamInfoList" border size="small">
                <el-table-column label="码流" width="80">
                  <template #default="{ row }">
                    {{ row.streamType === 1 ? '主码流' : '辅码流' }}
                  </template>
                </el-table-column>
                <el-table-column label="编码格式" width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.encodeType" size="small" style="width:100%">
                      <el-option :value="1" label="MPEG-4" />
                      <el-option :value="2" label="H.264" />
                      <el-option :value="3" label="SVAC" />
                      <el-option :value="4" label="3GP" />
                      <el-option :value="5" label="H.265" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="分辨率" width="130">
                  <template #default="{ row }">
                    <el-input v-model="row.resolution" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="帧率" width="110">
                  <template #default="{ row }">
                    <el-input-number v-model="row.frameRate" :min="1" :max="99" size="small" style="width:100%" :controls="false" />
                  </template>
                </el-table-column>
                <el-table-column label="码率类型" width="110">
                  <template #default="{ row }">
                    <el-select v-model="row.bitRateType" size="small" style="width:100%">
                      <el-option :value="1" label="CBR" />
                      <el-option :value="2" label="VBR" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="码率(kbps)" width="130">
                  <template #default="{ row }">
                    <el-input-number v-model="row.bitRate" :min="64" size="small" style="width:100%" :controls="false" />
                  </template>
                </el-table-column>
              </el-table>
            </template>
            <el-empty v-else description="暂无数据" />
          </div>
        </el-tab-pane>

        <!-- OSDConfig -->
        <el-tab-pane label="OSD 叠加" name="osd">
          <div v-loading="configLoading">
            <el-form :model="osdForm" label-width="130px" style="margin-top:12px">
              <el-form-item label="文字叠加开关">
                <el-switch v-model="osdForm.textEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
              <el-form-item label="时间显示开关">
                <el-switch v-model="osdForm.timeEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
              <el-form-item label="时间格式">
                <el-select v-model="osdForm.timeType" style="width:200px">
                  <el-option :value="0" label="年月日时分秒" />
                  <el-option :value="1" label="月日时分秒" />
                </el-select>
              </el-form-item>
              <el-form-item label="时间 X 坐标">
                <el-input-number v-model="osdForm.timeX" :min="0" :controls="false" style="width:120px" />
              </el-form-item>
              <el-form-item label="时间 Y 坐标">
                <el-input-number v-model="osdForm.timeY" :min="0" :controls="false" style="width:120px" />
              </el-form-item>
              <el-form-item label="字幕列表">
                <div v-for="(item, idx) in osdForm.items" :key="idx" style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
                  <el-input v-model="item.text" placeholder="文字内容" style="width:140px" size="small" />
                  <span style="color:#606266;font-size:12px">X:</span>
                  <el-input-number v-model="item.x" :min="0" size="small" :controls="false" style="width:80px" />
                  <span style="color:#606266;font-size:12px">Y:</span>
                  <el-input-number v-model="item.y" :min="0" size="small" :controls="false" style="width:80px" />
                </div>
                <el-empty v-if="!osdForm.items || !osdForm.items.length" description="无字幕" :image-size="40" />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- PictureMask -->
        <el-tab-pane label="视频遮挡" name="picture-mask">
          <div v-loading="configLoading">
            <el-form :model="maskForm" label-width="120px" style="margin-top:12px">
              <el-form-item label="遮挡开关">
                <el-switch v-model="maskForm.enableVideoMask" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- FrameMirror -->
        <el-tab-pane label="镜像翻转" name="frame-mirror">
          <div v-loading="configLoading">
            <el-form :model="mirrorForm" label-width="120px" style="margin-top:12px">
              <el-form-item label="镜像模式">
                <el-select v-model="mirrorForm.frameMirrorMode" style="width:200px">
                  <el-option :value="0" label="不翻转" />
                  <el-option :value="1" label="水平翻转" />
                  <el-option :value="2" label="垂直翻转" />
                  <el-option :value="3" label="水平+垂直" />
                </el-select>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- VideoRecordPlan -->
        <el-tab-pane label="录像计划" name="video-record-plan">
          <div v-loading="configLoading">
            <el-form :model="recordPlanForm" label-width="130px" style="margin-top:12px">
              <el-form-item label="启用录像计划">
                <el-switch v-model="recordPlanForm.recordEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
              <el-form-item label="码流编号">
                <el-select v-model="recordPlanForm.streamNumber" style="width:200px">
                  <el-option :value="0" label="主码流" />
                  <el-option :value="1" label="子码流1" />
                  <el-option :value="2" label="子码流2" />
                </el-select>
              </el-form-item>
              <el-form-item label="录像时间计划">
                <div style="width:100%">
                  <el-table
                    v-if="recordPlanForm.recordSchedules && recordPlanForm.recordSchedules.length"
                    :data="recordPlanForm.recordSchedules"
                    border size="small"
                    style="width:100%;margin-bottom:8px"
                  >
                    <el-table-column label="星期" width="100" align="center">
                      <template #default="{ row }">
                        <el-select v-model="row.weekDayNum" size="small" style="width:76px">
                          <el-option :value="1" label="周一" />
                          <el-option :value="2" label="周二" />
                          <el-option :value="3" label="周三" />
                          <el-option :value="4" label="周四" />
                          <el-option :value="5" label="周五" />
                          <el-option :value="6" label="周六" />
                          <el-option :value="7" label="周日" />
                        </el-select>
                      </template>
                    </el-table-column>
                    <el-table-column label="时间段">
                      <template #default="{ row }">
                        <div v-for="(seg, si) in row.timeSegments" :key="si" style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
                          <el-time-picker
                            v-model="seg.startTime"
                            format="HH:mm:ss"
                            value-format="HH:mm:ss"
                            placeholder="开始"
                            size="small"
                            style="width:120px"
                            @change="v => onTimeChange(seg, 'start', v)"
                          />
                          <span>~</span>
                          <el-time-picker
                            v-model="seg.stopTime"
                            format="HH:mm:ss"
                            value-format="HH:mm:ss"
                            placeholder="结束"
                            size="small"
                            style="width:120px"
                            @change="v => onTimeChange(seg, 'stop', v)"
                          />
                          <el-button size="small" type="danger" link @click="removeTimeSegment(row, si)">删除</el-button>
                        </div>
                        <el-button size="small" link @click="addTimeSegment(row)">+ 添加时间段</el-button>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="70" align="center">
                      <template #default="{ $index }">
                        <el-button size="small" type="danger" link @click="removeScheduleRow($index)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-button size="small" @click="addScheduleRow">+ 添加天</el-button>
                </div>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- VideoAlarmRecord -->
        <el-tab-pane label="报警录像" name="video-alarm-record">
          <div v-loading="configLoading">
            <el-form :model="alarmRecordForm" label-width="130px" style="margin-top:12px">
              <el-form-item label="启用报警录像">
                <el-switch v-model="alarmRecordForm.recordEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
              <el-form-item label="码流编号">
                <el-select v-model="alarmRecordForm.streamNumber" style="width:200px">
                  <el-option :value="0" label="主码流" />
                  <el-option :value="1" label="子码流1" />
                  <el-option :value="2" label="子码流2" />
                </el-select>
              </el-form-item>
              <el-form-item label="录像延时(秒)">
                <el-input-number v-model="alarmRecordForm.recordTime" :min="0" :max="3600" />
              </el-form-item>
              <el-form-item label="预录时间(秒)">
                <el-input-number v-model="alarmRecordForm.preRecordTime" :min="0" :max="300" />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- AlarmReport -->
        <el-tab-pane label="报警上报" name="alarm-report">
          <div v-loading="configLoading">
            <el-form :model="alarmReportForm" label-width="130px" style="margin-top:12px">
              <el-form-item label="移动侦测上报">
                <el-switch v-model="alarmReportForm.motionDetection" :active-value="1" :inactive-value="0" />
              </el-form-item>
              <el-form-item label="区域入侵上报">
                <el-switch v-model="alarmReportForm.fieldDetection" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- SnapShot -->
        <el-tab-pane label="抓图" name="snap-shot">
          <div v-loading="configLoading">
            <el-form :model="snapShotForm" label-width="130px" style="margin-top:12px">
              <el-form-item label="连拍张数">
                <el-input-number v-model="snapShotForm.snapNum" :min="1" :max="10" />
              </el-form-item>
              <el-form-item label="抓拍间隔(秒)">
                <el-input-number v-model="snapShotForm.interval" :min="1" />
              </el-form-item>
              <el-form-item label="上传路径">
                <el-input v-model="snapShotForm.uploadURL" style="width:300px" placeholder="http://..." />
              </el-form-item>
              <el-form-item label="会话ID">
                <el-input v-model="snapShotForm.sessionID" style="width:300px" placeholder="32~128字节" />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="configDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 视频播放对话框 -->
    <el-dialog
      v-model="streamDialogVisible"
      :title="streamDialogTitle"
      :width="isPtzDevice ? '1100px' : '720px'"
      :before-close="closeStream"
      destroy-on-close
    >
      <div style="display:flex;gap:12px;align-items:flex-start;">
        <!-- 左侧：视频区 -->
        <div :style="isPtzDevice ? 'flex:3;min-width:0' : 'flex:1'">
          <div style="background:#000; position:relative; width:100%; height:400px;">
            <div v-if="streamLoading" style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#fff;z-index:1;">
              正在建立视频流连接...
            </div>
            <div v-if="streamError" style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#f56c6c;z-index:1;">
              {{ streamError }}
            </div>
            <video
              ref="videoEl"
              style="width:100%;height:100%;display:block;object-fit:contain;"
              autoplay
              muted
            />
          </div>
        </div>

        <!-- 右侧：PTZ 控制面板（仅球机/遥控类） -->
        <div v-if="isPtzDevice" style="flex:2;min-width:280px;border-left:1px solid #e4e7ed;padding-left:12px;">
          <el-tabs v-model="ptzTab" @tab-change="onPtzTabChange">

            <!-- 云台控制 -->
            <el-tab-pane label="云台控制" name="ptz">
              <!-- 速度滑块 -->
              <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;">
                <span style="font-size:13px;color:#606266;white-space:nowrap;">速度</span>
                <el-slider v-model="ptzSpeed" :min="1" :max="9" :step="1" style="flex:1" show-stops />
                <span style="font-size:13px;color:#303133;width:16px;text-align:center;">{{ ptzSpeed }}</span>
              </div>

              <!-- 9宫格方向键 -->
              <div class="ptz-grid">
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('left-up')"   @mouseup="ptzStop()" @mouseleave="ptzStop()">↖</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('up')"        @mouseup="ptzStop()" @mouseleave="ptzStop()">↑</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('right-up')"  @mouseup="ptzStop()" @mouseleave="ptzStop()">↗</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('left')"      @mouseup="ptzStop()" @mouseleave="ptzStop()">←</el-button>
                <el-button class="ptz-btn ptz-stop" @click="ptzStop()">■</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('right')"     @mouseup="ptzStop()" @mouseleave="ptzStop()">→</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('left-down')" @mouseup="ptzStop()" @mouseleave="ptzStop()">↙</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('down')"      @mouseup="ptzStop()" @mouseleave="ptzStop()">↓</el-button>
                <el-button class="ptz-btn" @mousedown.prevent="ptzStart('right-down')"@mouseup="ptzStop()" @mouseleave="ptzStop()">↘</el-button>
              </div>

              <!-- 变倍 / 变焦 / 光圈 -->
              <div class="ptz-extra">
                <span class="ptz-label">变倍</span>
                <el-button size="small" @mousedown.prevent="ptzStart('zoom_in')"  @mouseup="ptzStop()" @mouseleave="ptzStop()">+</el-button>
                <el-button size="small" @mousedown.prevent="ptzStart('zoom_out')" @mouseup="ptzStop()" @mouseleave="ptzStop()">-</el-button>
              </div>
              <div class="ptz-extra">
                <span class="ptz-label">变焦</span>
                <el-button size="small" @mousedown.prevent="ptzStart('focus_in')"  @mouseup="ptzStop()" @mouseleave="ptzStop()">+</el-button>
                <el-button size="small" @mousedown.prevent="ptzStart('focus_out')" @mouseup="ptzStop()" @mouseleave="ptzStop()">-</el-button>
              </div>
              <div class="ptz-extra">
                <span class="ptz-label">光圈</span>
                <el-button size="small" @mousedown.prevent="ptzStart('iris_in')"  @mouseup="ptzStop()" @mouseleave="ptzStop()">+</el-button>
                <el-button size="small" @mousedown.prevent="ptzStart('iris_out')" @mouseup="ptzStop()" @mouseleave="ptzStop()">-</el-button>
              </div>
            </el-tab-pane>

            <!-- 预置位 -->
            <el-tab-pane label="预置位" name="preset">
              <div v-loading="presetLoading">
                <!-- 预置位列表 -->
                <el-table v-if="presets.length" :data="presets" size="small" border style="width:100%;margin-bottom:10px;">
                  <el-table-column prop="presetId" label="编号" width="60" align="center" />
                  <el-table-column prop="presetName" label="名称" />
                  <el-table-column label="操作" width="110" align="center">
                    <template #default="{ row }">
                      <el-button size="small" type="primary" link @click="callPreset(row.presetId)">调用</el-button>
                      <el-button size="small" type="danger"  link @click="deletePreset(row.presetId)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else-if="!presetLoading && !presetTimeout" description="暂无预置位" :image-size="48" />
                <el-alert v-if="presetTimeout" title="设备不支持预置位查询" type="warning" :closable="false" show-icon style="margin-bottom:8px;" />

                <!-- 设置预置位表单 -->
                <el-divider content-position="left" style="margin:10px 0 8px;">设置预置位</el-divider>
                <div style="display:flex;gap:6px;align-items:center;flex-wrap:wrap;">
                  <el-input-number v-model="newPresetIndex" :min="1" :max="255" :controls="false" style="width:72px;" placeholder="编号" size="small" />
                  <el-input v-model="newPresetName" placeholder="名称" style="width:100px;" size="small" />
                  <el-button size="small" type="primary" @click="setPreset">保存当前位置</el-button>
                </div>
              </div>
            </el-tab-pane>

            <!-- 巡航轨迹 -->
            <el-tab-pane label="巡航轨迹" name="cruise">
              <div v-loading="cruiseLoading">
                <el-table v-if="cruiseTracks.length" :data="cruiseTracks" size="small" border style="width:100%;margin-bottom:10px;">
                  <el-table-column prop="number" label="编号" width="55" align="center" />
                  <el-table-column prop="name" label="名称" />
                  <el-table-column label="操作" width="130" align="center">
                    <template #default="{ row }">
                      <el-button size="small" link @click="showCruiseDetail(row)">详情</el-button>
                      <el-button v-if="cruisingTrack !== row.name" size="small" type="primary" link @click="startCruise(row)">启动</el-button>
                      <el-button v-else size="small" type="danger" link @click="stopCruise()">停止</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else-if="!cruiseLoading && !cruiseTimeout" description="暂无巡航轨迹" :image-size="48" />
                <el-alert v-if="cruiseTimeout" title="设备不支持巡航轨迹查询" type="warning" :closable="false" show-icon />
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <template #footer>
        <el-button @click="closeStream">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 巡航轨迹详情对话框 -->
    <el-dialog v-model="cruiseDetailVisible" title="巡航轨迹详情" width="420px" append-to-body>
      <div v-loading="cruiseDetailLoading">
        <div style="margin-bottom:8px;font-weight:500;">{{ cruiseDetailData.name || '轨迹 ' + cruiseDetailData.number }}</div>
        <el-table v-if="cruiseDetailData.points && cruiseDetailData.points.length" :data="cruiseDetailData.points" size="small" border>
          <el-table-column prop="presetIndex" label="预置位" width="70" align="center" />
          <el-table-column prop="stayTime" label="停留(秒)" width="80" align="center" />
          <el-table-column prop="speed" label="速度" width="60" align="center" />
        </el-table>
        <el-empty v-else description="无轨迹点" :image-size="48" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import axios from 'axios'
import Hls from 'hls.js'

const activeTab = ref('local')
const localDevices = ref([])
const remoteDevices = ref([])
const loading = ref(false)
let timer = null

// 配置对话框状态
const configDialogVisible = ref(false)
const configTab = ref('video-param')
const configLoading = ref(false)
const saving = ref(false)
const currentDevice = ref(null)
const currentDeviceType = ref('local') // 'local' | 'remote'

// 各 tab 表单数据
const videoForm = ref({ streamInfoList: [] })
const osdForm = ref({ textEnable: 0, timeEnable: 0, timeType: 0, timeX: 0, timeY: 0, length: 0, width: 0, items: [] })
const maskForm = ref({ enableVideoMask: 0 })
const mirrorForm = ref({ frameMirrorMode: 0 })
const recordPlanForm = ref({ recordEnable: 0, streamNumber: 0, recordScheduleSumNum: 0, recordSchedules: [] })
const alarmRecordForm = ref({ recordEnable: 0, streamNumber: 0, recordTime: 0, preRecordTime: 0 })
const alarmReportForm = ref({ motionDetection: 0, fieldDetection: 0 })
const snapShotForm = ref({ snapNum: 1, interval: 1, uploadURL: '', sessionID: '' })

const configDialogTitle = computed(() => {
  const name = currentDevice.value?.name || currentDevice.value?.gbDeviceId || currentDevice.value?.deviceId || ''
  return `相机配置 - ${name}`
})

const deviceId = computed(() => {
  if (!currentDevice.value) return ''
  return currentDeviceType.value === 'local'
    ? currentDevice.value.gbDeviceId
    : currentDevice.value.deviceId
})

const configBaseUrl = computed(() => {
  return currentDeviceType.value === 'local'
    ? `/api/devices/local/${deviceId.value}/config`
    : `/api/devices/remote/${deviceId.value}/config`
})

// ===== 设备列表 =====

const fetchLocal = async () => {
  const { data } = await axios.get('/api/devices/local')
  localDevices.value = data
}

const fetchRemote = async () => {
  const { data } = await axios.get('/api/devices/remote')
  remoteDevices.value = data
}

const refresh = async () => {
  loading.value = true
  try {
    if (activeTab.value === 'local') await fetchLocal()
    else if (activeTab.value === 'remote') await fetchRemote()
  } catch (error) {
    ElMessage.error('获取设备列表失败: ' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

// ===== 配置对话框 =====

function openConfig(row, type) {
  currentDevice.value = row
  currentDeviceType.value = type
  configTab.value = 'video-param'
  configDialogVisible.value = true
  loadConfig('video-param')
}

async function loadConfig(tab) {
  configLoading.value = true
  try {
    const { data } = await axios.get(`${configBaseUrl.value}/${tab}`)
    applyFormData(tab, data)
  } catch (e) {
    if (e.response?.status === 504) {
      ElMessage.warning('SIP 响应超时，无法获取配置')
    } else {
      ElMessage.error('加载配置失败: ' + (e.response?.data?.error || e.message))
    }
    resetFormData(tab)
  } finally {
    configLoading.value = false
  }
}

function applyFormData(tab, data) {
  if (tab === 'video-param') {
    videoForm.value = { streamInfoList: data.streamInfoList || [] }
  } else if (tab === 'osd') {
    osdForm.value = {
      textEnable: data.textEnable ?? 0,
      timeEnable: data.timeEnable ?? 0,
      timeType: data.timeType ?? 0,
      timeX: data.timeX ?? 0,
      timeY: data.timeY ?? 0,
      length: data.length ?? 0,
      width: data.width ?? 0,
      items: (data.items || []).map(item => ({ ...item }))
    }
  } else if (tab === 'picture-mask') {
    maskForm.value = { enableVideoMask: data.enableVideoMask ?? 0 }
  } else if (tab === 'frame-mirror') {
    // 本端返回 streamInfoList，取第一条的 frameMirrorMode；外域直接返回字段
    const mode = data.streamInfoList?.[0]?.frameMirrorMode ?? data.frameMirrorMode ?? data.MirrorEnabled ?? 0
    mirrorForm.value = { frameMirrorMode: Number(mode) }
  } else if (tab === 'video-record-plan') {
    recordPlanForm.value = {
      recordEnable: data.recordEnable ?? 0,
      streamNumber: data.streamNumber ?? 0,
      recordScheduleSumNum: data.recordScheduleSumNum ?? 0,
      recordSchedules: (data.recordSchedules || []).map(s => ({
        weekDayNum: s.weekDayNum,
        timeSegmentSumNum: s.timeSegmentSumNum,
        timeSegments: (s.timeSegments || []).map(seg => ({
          ...seg,
          startTime: `${String(seg.startHour).padStart(2,'0')}:${String(seg.startMin).padStart(2,'0')}:${String(seg.startSec).padStart(2,'0')}`,
          stopTime:  `${String(seg.stopHour).padStart(2,'0')}:${String(seg.stopMin).padStart(2,'0')}:${String(seg.stopSec).padStart(2,'0')}`
        }))
      }))
    }
  } else if (tab === 'video-alarm-record') {
    alarmRecordForm.value = {
      recordEnable: data.recordEnable ?? 0,
      streamNumber: data.streamNumber ?? 0,
      recordTime: data.recordTime ?? 0,
      preRecordTime: data.preRecordTime ?? 0
    }
  } else if (tab === 'alarm-report') {
    alarmReportForm.value = {
      motionDetection: data.motionDetection ?? 0,
      fieldDetection: data.fieldDetection ?? 0
    }
  } else if (tab === 'snap-shot') {
    snapShotForm.value = {
      snapNum: data.snapNum ?? 1,
      interval: data.interval ?? 1,
      uploadURL: data.uploadURL ?? '',
      sessionID: data.sessionID ?? ''
    }
  }
}

function resetFormData(tab) {
  if (tab === 'video-param') videoForm.value = { streamInfoList: [] }
  else if (tab === 'osd') osdForm.value = { textEnable: 0, timeEnable: 0, timeType: 0, timeX: 0, timeY: 0, length: 0, width: 0, items: [] }
  else if (tab === 'picture-mask') maskForm.value = { enableVideoMask: 0 }
  else if (tab === 'frame-mirror') mirrorForm.value = { frameMirrorMode: 0 }
  else if (tab === 'video-record-plan') recordPlanForm.value = { recordEnable: 0, streamNumber: 0, recordScheduleSumNum: 0, recordSchedules: [] }
  else if (tab === 'video-alarm-record') alarmRecordForm.value = { recordEnable: 0, streamNumber: 0, recordTime: 0, preRecordTime: 0 }
  else if (tab === 'alarm-report') alarmReportForm.value = { motionDetection: 0, fieldDetection: 0 }
  else if (tab === 'snap-shot') snapShotForm.value = { snapNum: 1, interval: 1, uploadURL: '', sessionID: '' }
}

async function saveConfig() {
  saving.value = true
  try {
    const patch = buildPatch(configTab.value)
    const { data } = await axios.put(`${configBaseUrl.value}/${configTab.value}`, patch)
    if (data.success) {
      ElMessage.success('保存成功')
    } else {
      ElMessage.error('保存失败，设备返回错误')
    }
  } catch (e) {
    if (e.response?.status === 504) {
      ElMessage.warning('SIP 响应超时')
    } else {
      ElMessage.error('保存失败: ' + (e.response?.data?.error || e.message))
    }
  } finally {
    saving.value = false
  }
}

function buildPatch(tab) {
  if (tab === 'video-param') return videoForm.value
  if (tab === 'osd') return {
    textEnable: osdForm.value.textEnable,
    timeEnable: osdForm.value.timeEnable,
    timeType: osdForm.value.timeType,
    timeX: osdForm.value.timeX,
    timeY: osdForm.value.timeY,
    length: osdForm.value.length,
    width: osdForm.value.width,
    items: osdForm.value.items
  }
  if (tab === 'picture-mask') return { enableVideoMask: maskForm.value.enableVideoMask }
  if (tab === 'frame-mirror') return { frameMirrorMode: mirrorForm.value.frameMirrorMode }
  if (tab === 'video-record-plan') {
    return {
      recordEnable: recordPlanForm.value.recordEnable,
      streamNumber: recordPlanForm.value.streamNumber,
      recordSchedules: (recordPlanForm.value.recordSchedules || []).map(s => ({
        weekDayNum: s.weekDayNum,
        timeSegmentSumNum: s.timeSegments?.length ?? 0,
        timeSegments: (s.timeSegments || []).map(seg => ({
          startHour: seg.startHour, startMin: seg.startMin, startSec: seg.startSec,
          stopHour: seg.stopHour, stopMin: seg.stopMin, stopSec: seg.stopSec
        }))
      }))
    }
  }
  if (tab === 'video-alarm-record') return { recordEnable: alarmRecordForm.value.recordEnable, streamNumber: alarmRecordForm.value.streamNumber, recordTime: alarmRecordForm.value.recordTime, preRecordTime: alarmRecordForm.value.preRecordTime }
  if (tab === 'alarm-report') return { motionDetection: alarmReportForm.value.motionDetection, fieldDetection: alarmReportForm.value.fieldDetection }
  if (tab === 'snap-shot') return { snapNum: snapShotForm.value.snapNum, interval: snapShotForm.value.interval, uploadURL: snapShotForm.value.uploadURL, sessionID: snapShotForm.value.sessionID }
  return {}
}

// 时间选择器变更时同步回各独立的 hour/min/sec 字段
function onTimeChange(seg, type, value) {
  if (!value) return
  const [h, m, s] = value.split(':').map(Number)
  if (type === 'start') {
    seg.startHour = h; seg.startMin = m; seg.startSec = s
  } else {
    seg.stopHour = h; seg.stopMin = m; seg.stopSec = s
  }
}

function addScheduleRow() {
  recordPlanForm.value.recordSchedules.push({
    weekDayNum: 1,
    timeSegmentSumNum: 1,
    timeSegments: [{ startHour: 0, startMin: 0, startSec: 0, stopHour: 23, stopMin: 59, stopSec: 59, startTime: '00:00:00', stopTime: '23:59:59' }]
  })
}

function removeScheduleRow(index) {
  recordPlanForm.value.recordSchedules.splice(index, 1)
}

function addTimeSegment(row) {
  row.timeSegments.push({ startHour: 0, startMin: 0, startSec: 0, stopHour: 23, stopMin: 59, stopSec: 59, startTime: '00:00:00', stopTime: '23:59:59' })
  row.timeSegmentSumNum = row.timeSegments.length
}

function removeTimeSegment(row, index) {
  row.timeSegments.splice(index, 1)
  row.timeSegmentSumNum = row.timeSegments.length
}

function onDialogClosed() {
  currentDevice.value = null
}

function formatPtzType(ptzType) {
  if (!ptzType) return '-'
  const map = { '1': '球机', '2': '半球', '3': '固定枪机', '4': '遥控枪机', '5': '遥控半球', '6': '全景/拼接', '7': '分割通道' }
  return map[String(ptzType)] || ptzType
}

// ===== 视频流播放 =====

const streamDialogVisible = ref(false)
const streamLoading = ref(false)
const streamError = ref('')
const streamDevice = ref(null)
const streamDeviceType = ref('remote') // 'local' | 'remote'
const videoEl = ref(null)
let flvPlayer = null

const streamDialogTitle = computed(() => {
  const name = streamDevice.value?.name || streamDevice.value?.deviceId || streamDevice.value?.gbDeviceId || ''
  return `视频播放 - ${name}`
})

const isPtzDevice = computed(() => {
  const t = streamDevice.value?.ptzType
  return t != null && ['1', '4', '5'].includes(String(t))
})

const ptzBaseUrl = computed(() => {
  if (!streamDevice.value) return ''
  const id = streamDeviceType.value === 'local'
    ? streamDevice.value.gbDeviceId
    : streamDevice.value.deviceId
  return `/api/devices/${streamDeviceType.value}/${id}/ptz`
})

async function openStream(row, type = 'remote') {
  streamDevice.value = row
  streamDeviceType.value = type
  streamError.value = ''
  streamLoading.value = true
  streamDialogVisible.value = true
  // Reset PTZ state
  ptzTab.value = 'ptz'
  presets.value = []
  presetTimeout.value = false
  cruiseTracks.value = []
  cruiseTimeout.value = false
  cruisingTrack.value = null
  try {
    const id = type === 'local' ? row.gbDeviceId : row.deviceId
    const { data } = await axios.post(`/api/devices/${type}/${id}/stream/start`)
    initFlvPlayer(data.streamUrl)
  } catch (e) {
    streamError.value = e.response?.data?.error || '视频流启动失败'
    ElMessage.error(streamError.value)
  } finally {
    streamLoading.value = false
  }
}

function initFlvPlayer(url) {
  destroyFlvPlayer()
  if (Hls.isSupported()) {
    flvPlayer = new Hls({ liveSyncDurationCount: 1, liveMaxLatencyDurationCount: 3 })
    flvPlayer.loadSource(url)
    flvPlayer.attachMedia(videoEl.value)
    flvPlayer.on(Hls.Events.MANIFEST_PARSED, () => { videoEl.value.play() })
    flvPlayer.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) streamError.value = 'HLS 播放失败: ' + data.details
    })
  } else if (videoEl.value.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari 原生 HLS
    videoEl.value.src = url
    videoEl.value.play()
  } else {
    streamError.value = '当前浏览器不支持 HLS 播放'
  }
}

function destroyFlvPlayer() {
  if (flvPlayer) {
    if (flvPlayer instanceof Hls) {
      flvPlayer.destroy()
    }
    flvPlayer = null
  }
}

async function closeStream() {
  destroyFlvPlayer()
  streamDialogVisible.value = false
  if (streamDevice.value) {
    try {
      const type = streamDeviceType.value
      const id = type === 'local' ? streamDevice.value.gbDeviceId : streamDevice.value.deviceId
      await axios.post(`/api/devices/${type}/${id}/stream/stop`)
    } catch (e) {
      // 忽略停止失败（会话可能已不存在）
    }
    streamDevice.value = null
  }
}

// ===== PTZ 云台控制 =====

const ptzTab = ref('ptz')
const ptzSpeed = ref(5)

function speedToRaw(s) {
  // 滑块 1~9 映射到 0~255
  return Math.round((s - 1) / 8 * 255)
}

async function ptzStart(action) {
  try {
    await axios.post(`${ptzBaseUrl.value}/control`, { action, speed: speedToRaw(ptzSpeed.value) })
  } catch (e) {
    // 忽略网络错误，不影响操作体验
  }
}

async function ptzStop() {
  try {
    await axios.post(`${ptzBaseUrl.value}/control`, { action: 'stop', speed: 0 })
  } catch (e) {
    // ignore
  }
}

function onPtzTabChange(tab) {
  if (tab === 'preset') loadPresets()
  else if (tab === 'cruise') loadCruiseTracks()
}

// ===== 预置位 =====

const presets = ref([])
const presetLoading = ref(false)
const presetTimeout = ref(false)
const newPresetIndex = ref(1)
const newPresetName = ref('')

async function loadPresets() {
  presetLoading.value = true
  presetTimeout.value = false
  try {
    const { data } = await axios.get(`${ptzBaseUrl.value}/preset`)
    presets.value = data || []
  } catch (e) {
    if (e.response?.status === 504) {
      presetTimeout.value = true
    } else {
      ElMessage.error('查询预置位失败: ' + (e.response?.data?.error || e.message))
    }
    presets.value = []
  } finally {
    presetLoading.value = false
  }
}

async function callPreset(presetId) {
  try {
    await axios.post(`${ptzBaseUrl.value}/preset/call`, { presetIndex: Number(presetId) })
    ElMessage.success('调用成功')
  } catch (e) {
    ElMessage.error('调用失败: ' + (e.response?.data?.error || e.message))
  }
}

async function setPreset() {
  try {
    await axios.post(`${ptzBaseUrl.value}/preset/set`, {
      presetIndex: newPresetIndex.value,
      presetName: newPresetName.value
    })
    ElMessage.success('预置位已保存')
    loadPresets()
  } catch (e) {
    ElMessage.error('设置失败: ' + (e.response?.data?.error || e.message))
  }
}

async function deletePreset(presetId) {
  try {
    await ElMessageBox.confirm('确认删除该预置位？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await axios.post(`${ptzBaseUrl.value}/preset/delete`, { presetIndex: Number(presetId) })
    presets.value = presets.value.filter(p => p.presetId !== presetId)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.response?.data?.error || e.message))
  }
}

// ===== 巡航轨迹 =====

const cruiseTracks = ref([])
const cruiseLoading = ref(false)
const cruiseTimeout = ref(false)
const cruisingTrack = ref(null)

const cruiseDetailVisible = ref(false)
const cruiseDetailLoading = ref(false)
const cruiseDetailData = ref({})

async function loadCruiseTracks() {
  cruiseLoading.value = true
  cruiseTimeout.value = false
  try {
    const { data } = await axios.get(`${ptzBaseUrl.value}/cruise`)
    cruiseTracks.value = data || []
  } catch (e) {
    if (e.response?.status === 504) {
      cruiseTimeout.value = true
    } else {
      ElMessage.error('查询巡航轨迹失败: ' + (e.response?.data?.error || e.message))
    }
    cruiseTracks.value = []
  } finally {
    cruiseLoading.value = false
  }
}

async function showCruiseDetail(track) {
  cruiseDetailData.value = { number: track.number, name: track.name, points: [] }
  cruiseDetailVisible.value = true
  cruiseDetailLoading.value = true
  try {
    const { data } = await axios.get(`${ptzBaseUrl.value}/cruise/${track.number}`)
    cruiseDetailData.value = data
  } catch (e) {
    ElMessage.error('查询详情失败: ' + (e.response?.data?.error || e.message))
  } finally {
    cruiseDetailLoading.value = false
  }
}

async function startCruise(track) {
  try {
    await axios.post(`${ptzBaseUrl.value}/cruise/start`, { trackName: track.name })
    cruisingTrack.value = track.name
    ElMessage.success('巡航已启动')
  } catch (e) {
    ElMessage.error('启动失败: ' + (e.response?.data?.error || e.message))
  }
}

async function stopCruise() {
  try {
    await axios.post(`${ptzBaseUrl.value}/cruise/stop`)
    cruisingTrack.value = null
    ElMessage.success('巡航已停止')
  } catch (e) {
    ElMessage.error('停止失败: ' + (e.response?.data?.error || e.message))
  }
}

// ===== 工具 =====

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  destroyFlvPlayer()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
  background: #fff;
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

/* PTZ 9宫格方向键 */
.ptz-grid {
  display: grid;
  grid-template-columns: repeat(3, 56px);
  grid-template-rows: repeat(3, 56px);
  gap: 4px;
  margin: 0 auto 14px;
  width: fit-content;
}

.ptz-btn {
  width: 56px;
  height: 56px;
  font-size: 18px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ptz-stop {
  font-size: 16px;
  background: #f56c6c;
  border-color: #f56c6c;
  color: #fff;
}

.ptz-stop:hover {
  background: #f78989;
  border-color: #f78989;
}

/* 变倍/变焦/光圈行 */
.ptz-extra {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ptz-label {
  font-size: 13px;
  color: #606266;
  width: 32px;
}
</style>
