# 设备目录信息查询应答
<!-- 命令类型:设备目录查询(必选) -->
<element name="CmdType" fixed ="Catalog"/>
<!-- 命令序列号(必选) -->
<element name="SN" type="tg:SNType"/>
<!-- 目标设备/区域/系统/业务分组/虚拟组织的编码，取值与目录查询请求相同(必选) --> <element name="DeviceID" type="tg:deviceIDType"/>
<!-- 查询结果总数(必选) -->
<element name="SumNum" type="integer"/> <!-- 设备目录项列表,Num表示目录项个数 --> <element name="DeviceList" minOccurs="0">
<complexType>
<choice minOccurs="0" maxOccurs="unbounded">
<element name="Item" type="tg:itemType"/> </choice>
<attribute name="Num" type="integer"/>
</complexType>
</element>
<!-- 扩展信息，可多项 -->
<element name="ExtraInfo" minOccurs="0" maxOccurs="unbounded">
<simpleType>
<restriction base="string">
        <maxLength value="1024"/>
       </restriction>
      </simpleType>
    </element>
# 2、目录项类型
<complexType name="itemType"> <sequence>
<!-- 目标设备/区域/系统/业务分组/虚拟组织编码(必选) --> <element name="DeviceID" type="tg:deviceIDType"/>
<!-- 设备/区域/系统/业务分组/虚拟组织名称(必选) --> <element name="Name" type="string"/>
<!-- 当为设备时，设备厂商(必选) -->
<element name="Manufacturer" type="string"/>
<!-- 当为设备时，设备型号(必选) -->
<element name="Model" type="string"/>
<!-- 行政区域，可为2、4、6、8位(必选) -->
<element name="CivilCode" type="string"/>
<!-- 警区(可选) -->
<element name="Block" type="string" minOccurs="0"/>
<!-- 当为设备时，安装地址(必选) -->
<element name="Address" type="string"/>
<!-- 当为设备时，是否有子设备(必选)1-有，0-没有 -->
<element name="Parental" type="integer"/>
<!-- 当为设备时,父节点ID(必选):当无父设备时，为设备所属系统ID;当有父设备时， 为设备父设备ID;
当为业务分组时,父节点ID(必选):所属系统ID;
GB/T 28181—2022
61
GB/T 28181—2022 当为虚拟组织时,父节点ID(若上级节点为虚拟组织必选;若上级节点为业务分组时，无此字
段):父节点虚拟组织ID; 当为系统时，父节点ID(有父节点系统时必选):父节点系统ID; 当为区域时，无父节点ID;
可多值，用英文半角“/”分割-->
<element name="ParentID" type="string"/>
<!-- 注册方式(必选)缺省为1; 1-符合IETF RFC 3261标准的认证注册模式;2-基于口令 的双向认证注册模式;3-基于数字证书的双向认证注册模式(高安全级别要求);4-基于数字证 书的单向认证注册模式(高安全级别要求)-->
<element name="RegisterWay" type="integer"/>
<!-- 摄像机安全能力等级代码(可选);A-GB 35114前端设备安全能力A级;B-GB 35114前 端设备安全能力B级;C-GB 35114前端设备安全能力C级 -->
<element name="SecurityLevelCode" type="string" minOccurs="0"/>
<!-- 保密属性(必选)缺省为0;0-不涉密，1-涉密-->
<element name="Secrecy" type="integer"/>
<!-- 设备/系统IPv4/IPv6地址(可选)-->
<element name="IPAddress" type="string" minOccurs="0"/>
<!-- 设备/系统端口(可选)-->
<element name="Port" type="integer" minOccurs="0"/>
<!-- 设备口令(可选)-->
<element name="Password" type="string" minOccurs="0"/>
<!-- 设备状态(必选) -->
<element name="Status" type="tg:statusType"/>
<!-- 当为设备时，经度(一类、二类视频监控点必选)WGS-84坐标系 -->
<element name="Longitude" type="double"/>
<!-- 当为设备时，纬度(一类、二类视频监控点必选)WGS-84坐标系 -->
<element name="Latitude" type="double"/>
<!-- 虚拟组织所属的业务分组ID，业务分组根据特定的业务需求制定，一个业务分组包含一 组特定的虚拟组织。-->
<element name="BusinessGroupID" type="tg:deviceIDType" minOccurs="0"/>
<element name="Info" minOccurs="0">
  <complexType>
    <sequence>
<!-- 摄像机结构类型，标识摄像机类型:1-球机;2-半球;3-固定枪机;4-遥控枪机; 5-遥控半球;6-多目设备的全景/拼接通道;7-多目设备的分割通道。当为摄像机时可 选。-->
<element name="PTZType" type="string" minOccurs="0"/>
<!-- 摄像机光电成像类型。1-可见光成像;2-热成像;3-雷达成像;4-X光成像;5- 深度光场成像;9-其他。可多值，用英文半角“/”分割。当为摄像机时可选。--> <element name="PhotoelectricImagingType" type="string" minOccurs="0"/>
<!-- 摄像机采集部位类型。采用附录O中的规定。当为摄像机时可选。-->
<element name="CapturePositionType" type="string" minOccurs="0"/>
<!-- 摄像机安装位置室外、室内属性。1-室外、2-室内。当为摄像机时可选，缺省为 1。-->
62

GB/T 28181—2022
<element name="RoomType" type="integer" minOccurs="0"/>
<!-- 摄像机补光属性。1-无补光;2-红外补光;3-白光补光;4-激光补光;9-其他。 当为摄像机时可选，缺省为1。-->
<element name="SupplyLightType" type="integer" minOccurs="0"/>
<!-- 摄像机监视方位(光轴方向)属性。1-东(西向东)、2-西(东向西)、3-南(北 向南)、4-北(南向北)、5-东南(西北到东南)、6-东北(西南到东北)、7-西南(东 北到西南)、8-西北(东南到西北)。当为摄像机时且为固定摄像机或设置看守位摄像 机时可选。-->
<element name="DirectionType" type="integer" minOccurs="0"/>
<!-- 摄像机支持的分辨率，可多值，用英文半角“/”。分辨率取值参见附录G中SDP f字段规定。当为摄像机时可选。-->
<element name="Resolution" type="string" minOccurs="0"/>
<!-- 摄像机支持的码流编号列表，用于实时点播时指定码流编号(可选)，多个取值 间用英文半角“/”分割。如“0/1/2”,表示支持主码流，子码流1，子码流2，以此类 推。-->
<element name="StreamNumberList" type="string" minOccurs="0"/>
<!-- 下载倍速(可选)，可多值，用英文半角“/”分割，如设备支持1,2,4倍速下载 则应写为“1/2/4”-->
<element name="DownloadSpeed" type="string" minOccurs="0"/>
<!-- 空域编码能力，取值0-不支持;1-1级增强(1个增强层);2-2级增强(2个增强 层);3-3级增强(3个增强层)(可选)-->
<element name="SVCSpaceSupportMode" type="integer" minOccurs="0"/>
<!-- 时域编码能力，取值0-不支持;1-1级增强;2-2级增强;3-3级增强(可选)--> <element name="SVCTimeSupportMode" type="integer" minOccurs="0"/>
<!-- SSVC增强层与基本层比例能力，多个取值间用英文半角“/”分割。如 “4:3/2:1/4:1/6:1/8:1”等具体比例值一种或多种(可选)-->
<element name="SSVCRatioSupportList" type="string" minOccurs="0"/>
<!-- 移动采集设备类型(仅移动采集设备适用，必选);1-移动机器人载摄像机;2- 执法记录仪;3-移动单兵设备;4-车载视频记录设备;5-无人机载摄像机;9-其他 --> <element name="MobileDeviceType" type="integer" minOccurs="0"/>
<!-- 摄像机水平视场角(可选)，取值范围大于0度小于等于360度 -->
<element name="HorizontalFieldAngle" type="double" minOccurs="0"/>
<!-- 摄像机竖直视场角(可选)，取值范围大于0度小于等于360度 -->
<element name="VerticalFieldAngle" type="double" minOccurs="0"/>
<!-- 摄像机可视距离(可选)，单位:米 -->
<element name="MaxViewDistance" type="double" minOccurs="0"/>
<!-- 基层组织编码(必选，非基层建设时为“000000”)，编码规则采用附录E.3中 规定的格式。 -->
<element name="GrassrootsCode" type="string"/>
<!-- 监控点位类型(当为摄像机时必选)，1-一类视频监控点;2-二类视频监控点;
3-三类视频监控点;9-其他点位。-->
<element name="PointType" type="integer" minOccurs="0"/>
<!-- 点位俗称(可选)，监控点位附近如有标志性建筑、场所或监控点位处于公众约 定俗成的地点，可以填写标志性建设名称和地点俗称 -->
63

GB/T 28181—2022 <element name="PointCommonName" type="string" minOccurs="0"/>
<!-- 设备MAC地址(可选)，用“XX-XX-XX-XX-XX-XX”格式表达，其中“XX”表示2位 十六进制数，用英文半角“-”隔开 -->
<element name="MAC" type="string" minOccurs="0"/>
<!-- 摄像机卡口功能类型，01-人脸卡口;02-人员卡口;03-机动车卡口;04-非机动 车卡口;05-物品卡口;99-其他。可多值，用英文半角“/”分割。当为摄像机时可选 -->
<element name="FunctionType" type="string" minOccurs="0"/>
<!-- 摄像机视频编码格式(可选)，取值参见附录G中SDP f字段规定。-->
<element name="EncodeType" type="string" minOccurs="0"/>
<!-- 摄像机安装使用时间。一类视频监控点必选;二类、三类可选 -->
<element name="InstallTime" type="dateTime" minOccurs="0"/>
<!-- 摄像机所属管理单位名称(可选)-->
<element name="ManagementUnit" type="string" minOccurs="0"/>
<!-- 摄像机所属管理单位联系人的联系方式(电话号码，可多值，用英文半角“/” 分割)。一类视频监控点必填;二类、三类选填 -->
<element name="ContactInfo" type="string" minOccurs="0"/>
<!-- 录像保存天数(可选)，一类视频监控点必填;二类、三类选填-->
<element name="RecordSaveDays" type="integer" minOccurs="0"/>
<!-- 国民经济行业分类代码(可选)，代码见GB/T 4754第5章 -->
<element name="IndustrialClassification" type="string" minOccurs="0"/>
            </sequence>
          </complexType>
        </element>
      </sequence>
    </complexType>
