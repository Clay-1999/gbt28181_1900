
A.2.1.15 录像计划配置类型
<complexType name="videoRecordPlanCfgType"> <sequence>
<!-- 是否启用时间计划录像配置:0-否，1-是(必选) -->
<element name="RecordEnable" type="integer"/>
<!-- 每周录像计划总天数(必选) -->
<element name="RecordScheduleSumNum" type="integer"/>
<!-- 一个星期的录像计划，可配置7天，对应周一至周日，每天最大支持8个时间段配置(必 选)-->
<element name="RecordSchedule" minOccurs="0" maxOccurs="7"> <complexType>
<!-- 周几(必选)取值1~7，表示周一到周日，如当天无录像计划可缺少 --> <element name="WeekDayNum" type="integer"/>
<!-- 每天录像计划时间段(必选);每天支持最多8个时间段 -->
GB/T 28181—2022
67
<!-- 每天录像计划时间段总数(必选) -->
<element name="TimeSegmentSumNum" type="integer"/> <element name="TimeSegment" minOccurs="1" maxOccurs="8">
              <complexType>
                <sequence>
<!-- 开始时间:时,0~23-->
<element name="StartHour" type="integer"/> <!-- 开始时间:分,0~59-->
<element name="StartMin" type="integer"/> <!-- 开始时间:秒,0~59-->
<element name="StartSec" type="integer"/> <!-- 结束时间:时,0~23-->
<element name="StopHour" type="integer"/> <!-- 结束时间:分,0~59-->
<element name="StopMin" type="integer"/> <!-- 结束时间:秒,0~59-->
<element name="StopSec" type="integer"/>
                </sequence>
              </complexType>
            </element>
          </complexType>
</element>
<!-- 码流类型:0-主码流，1-子码流1，2-子码流2，以此类推(必选) --> <element name="StreamNumber" type="integer"/>
      </sequence>
    </complexType>
A.2.1.16 报警录像配置类型
<complexType name="videoAlarmRecordCfgType"> <sequence>
<!-- 是否启用报警录像配置:0-否，1-是(必选) -->
<element name="RecordEnable" type="integer"/>
<!-- 录像延时时间，报警时间点后的时间,单位“秒”(可选)--> <element name="RecordTime" type="integer" minOccurs="0"/>
<!-- 预录时间:报警时间点前的时间,单位“秒”(可选) -->
<element name="PreRecordTime" type="integer" minOccurs="0"/>
<!-- 码流编号:0-主码流，1-子码流1，2-子码流2，以此类推(必选) --> <element name="StreamNumber" type="integer"/>
      </sequence>
    </complexType>
A.2.1.17 视频画面遮挡类型
<complexType name="pictureMaskCfgType"> <sequence>
GB/T 28181—2022
68

<!-- 画面遮挡开关，取值0-关闭，1-打开(必选)--> <element name="On" type="integer"/>
<!-- 区域总数(必选) -->
<element name="SumNum" type="integer"/>
<!-- 区域列表(可选)-->
<element name="RegionList" minOccurs="0">
<!-- 区域(必选)--> <complexType>
<sequence>
<element name="Item" minOccurs="0" maxOccurs="4">
                <complexType>
                  <sequence>
<!-- 区域编号，取值范围1~4(必选)-->
<element name="Seq" type="integer"/>
<!-- 区域左上角、右下角坐标(lx,ly,rx,ry,单位像素)， 格式如“20,30,50,60”(必选)-->
<element name="Point" type="string"/>
                  </sequence>
                </complexType>
</element>
</sequence>
<!-- 当前区域个数，当无区域时取值为 0(必选) --> <attribute name="Num" type="integer"/>
          </complexType>
        </element>
      </sequence>
    </complexType>
A.2.1.18 报警上报开关类型
<complexType name="alarmReportCfgType"> <sequence>
<!-- 移动侦测事件上报开关，取值0-关闭，1-打开(必选)--> <element name="MotionDetection" type="integer"/>
<!-- 区域入侵事件上报开关，取值0-关闭，1-打开(必选)--> <element name="FieldDetection" type="integer"/>
      </sequence>
    </complexType>
A.2.1.19 基本参数配置类型
<complexType name="basicParamCfgType"> <sequence>
<!-- 设备名称(可选)-->
<element name="Name" type="string" minOccurs="0"/> <!-- 注册过期时间(可选)-->
GB/T 28181—2022
69

<element name="Expiration" type="integer" minOccurs="0"/>
<!-- 心跳间隔时间(可选)-->
<element name="HeartBeatInterval" type="integer" minOccurs="0"/> <!-- 心跳超时次数(可选)-->
<element name="HeartBeatCount" type="integer" minOccurs="0"/> </sequence>
    </complexType>
A.2.1.20 视频参数范围配置类型
<complexType name="videoParamOptCfgType"> <sequence>
<!-- 下载倍速范围(可选)，各可选参数以“/”分隔，如设备支持1,2,4倍速下载则应写为 “1/2/4”-->
<element name="DownloadSpeed" type="string" minOccurs="0"/> <!--摄像机支持的分辨率(可选)，可有多个分辨率值，各个取值见以“/”分隔。分辨率取 值参见附录G中SDP f字段规定。-->
<element name="Resolution" type="string" minOccurs="0"/>
      </sequence>
    </complexType>
A.2.1.21 SVAC 编码配置类型
<complexType name="SVACEncodeCfgType"> <sequence>
<!-- 感兴趣区域参数(必选)-->
<element name="ROIParam" minOccurs="0">
          <complexType>
            <sequence>
<!-- 感兴趣区域开关，取值0:关闭，1:打开(配置可选，查询应答必选)--> <element name="ROIFlag" type="integer"/>
<!-- 感兴趣区域数量，取值范围0~16(配置可选，查询应答必选)--> <element name="ROINumber" type="integer"/>
<!-- 感兴趣区域(可选)-->
<element name="Item" minOccurs="0" maxOccurs="16">
                  <complexType>
                    <sequence>
<!-- 感兴趣区域编号，取值范围1~16(配置可选，查询应答必选)--> <element name="ROISeq" type="integer"/>
<!-- 感兴趣区域左上角坐标，取值为将图像按32x32划分后该坐标所在块按 光栅扫描顺序的序号(配置可选，查询应答必选)-->
<element name="TopLeft" type="integer"/>
<!-- 感兴趣区域右下角坐标，取值为将图像按32x32划分后该坐标所在块按 光栅扫描顺序的序号(配置可选，查询应答必选)-->
<element name="BottomRight" type="integer"/>
GB/T 28181—2022
70

GB/T 28181—2022 <!-- ROI区域编码质量等级，取值0-一般;1-较好;2-好;3-很好(配置可
选，查询应答必选)-->
<element name="ROIQP" type="integer"/> </sequence>
</complexType>
</element>
</sequence>
</complexType>
</element>
<!-- SVC参数(可选)-->
<element name="SVCParam" minOccurs="0">
  <complexType>
    <sequence>
<!-- 空域编码方式，取值0-基本层;1-1级增强(1个增强层);2-2级增强(2个增强 层);3-3级增强(3个增强层)(必选)-->
<element name="SVCSpaceDomainMode" type="integer"/>
<!-- 时域编码方式，取值0-基本层;1-1级增强;2-2级增强;3-3级增强(必选)--> <element name="SVCTimeDomainMode" type="integer"/>
<!-- —SSVC增强层与基本层比例值，取值字符串，如4:3、2:1、4:1、6:1、8:1等具体 比例值(可选)-->
<element name="SSVCRatioValue" type="string" minOccurs="0"/>
<!-- 空域编码能力，取值0-不支持;1-1级增强(1个增强层);2-2级增强(2个增强 层);3-3级增强(3个增强层)(仅查询应答必选)-->
<element name="SVCSpaceSupportMode" type="integer"/>
<!-- 时域编码能力，取值0:不支持;1-1级增强;2-2级增强;3-3级增强(仅查询应
答必选)-->
<element name="SVCTimeSupportMode" type="integer"/>
<!-- SSVC增强层与基本层比例能力，取值字符串，多个取值间用英文半角“/”分割， 如4:3/2:1/4:1/6:1/8:1等具体比例值的一种或者多种(仅查询应答可选)--> <element name="SSVCRatioSupportList" type="string" minOccurs="0"/>
    </sequence>
  </complexType>
</element> <!--监控专用信息参数(仅查询应答可选)--> <element name="SurveillanceParam" minOccurs="0">
  <complexType>
    <sequence>
<!-- 绝对时间信息开关，取值0-关闭;1-打开(必选)--> <element name="TimeFlag" type="integer" minOccurs="0"/> <!-- OSD信息开关，取值0-关闭;1-打开(必选)--> <element name="OSDFlag" type="integer" minOccurs="0"/> <!-- 智能分析信息开关，取值0-关闭;1-打开(必选)--> <element name="AIFlag" type="integer" minOccurs="0"/> <!-- 地理信息开关，取值0-关闭;1-打开(必选)-->
71

<element name="GISFlag" type="integer" minOccurs="0"/> </sequence>
</complexType>
</element>
<!--音频参数(可选)-->
<element name="AudioParam" minOccurs="0">
          <complexType>
            <sequence>
<!-- 声音识别特征参数开关，取值0-关闭;1-打开(必选)-->
<element name="AudioRecognitionFlag" type="integer"/> </sequence>
          </complexType>
        </element>
      </sequence>
    </complexType>
A.2.1.22 SVAC 解码配置类型
<complexType name="SVACDecodeCfgType"> <sequence>
<!-- SVC参数(可选)-->
<element name="SVCParam" minOccurs="0">
          <complexType>
            <sequence>
<!-- 码流显示模式，取值0-基本层码流单独显示方式;1-基本层+1个增强层码流方 式;2-基本层+2个增强层码流方式;3-基本层+3个增强层码流方式;(配置必选，查询 应答可选)-->
<element name="SVCSTMMode" type="integer"/>
<!-- 空域编码能力，取值0-不支持;1-1级增强(1个增强层);2-2级增强(2个增强 层);3-3级增强(3个增强层)(仅查询应答必选)-->
<element name="SVCSpaceSupportMode" type="integer"/>
<!-- 时域编码能力，取值0-不支持;1-1级增强;2-2级增强;3-3级增强(仅查询应 答必选)-->
<element name="SVCTimeSupportMode" type="integer"/> </sequence>
</complexType>
</element>
<!--监控专用信息参数(可选)-->
<element name="SurveillanceParam" minOccurs="0">
          <complexType>
            <sequence>
<!-- 绝对时间信息显示开关，取值0-关闭;1-打开(配置可选，查询应答必选)--> <element name="TimeShowFlag" type="integer" minOccurs="0"/>
<!-- OSD信息显示开关，取值0-关闭;1-打开(配置可选，查询应答必选)--> <element name="OSDShowFlag" type="integer" minOccurs="0"/>
GB/T 28181—2022
72

GB/T 28181—2022 <!-- 智能分析信息显示开关，取值0-关闭;1-打开(配置可选，查询应答必选)-->
<element name="AIShowFlag" type="integer" minOccurs="0"/>
<!-- 地理信息开关，取值0-关闭;1-打开(配置可选，查询应答必选)--> <element name="GISShowFlag" type="integer" minOccurs="0"/>
            </sequence>
          </complexType>
        </element>
      </sequence>
    </complexType>
A.2.1.23 画面翻转配置类型
<simpleType name="frameMirrorCfgType"> <restriction base="integer">
<enumeration value="0"/>
<!-- 0-不启用镜像，基准画面 --> <enumeration value="1"/>
<!-- 1-水平镜像(左右翻转) --> <enumeration value="2"/>
<!-- 2-上下镜像(上下翻转) --> <enumeration value="3"/>
<!-- 3-中心镜像(上下左右都翻转)-->
      </restriction>
    </simpleType>
A.2.1.24 图像抓拍配置类型
<complexType name="snapShotCfgType"> <sequence>
<!-- 连拍张数(必选)，最多10张,当手动抓拍时，取值为1--> <element name="SnapNum">
<simpleType>
<restriction base="integer">
              <minInclusive value="1"/>
              <maxInclusive value="10"/>
            </restriction>
</simpleType>
</element> <!--单张抓拍间隔时间，单位:秒(必选)，取值范围:最短1秒--> <element name="Interval" minOccurs="0">
<simpleType>
<restriction base="integer">
              <minInclusive value="1"/>
            </restriction>
          </simpleType>
        </element>
73

GB/T 28181—2022
<!--抓拍图像上传路径(必选) -->
<element name="UploadURL" type="string"/> <!--会话ID，由平台生成，用于关联抓拍的图像与平台请求(必选)，SessionID由大小写英 文字母、数字、短划线组成，长度不小于32字节，不大于128字节。 -->
<element name="SessionID">
<simpleType>
<restriction base="string">
<minLength value="32"/>
<maxLength value="128"/> </restriction>
         </simpleType>
        </element>
      </sequence>
    </complexType>
