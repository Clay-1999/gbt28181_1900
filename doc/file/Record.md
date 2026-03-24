A.2.4.5 文件目录检索请求
<!-- 命令类型:文件目录检索(必选) -->
<element name="CmdType" fixed ="RecordInfo"/>
<!-- 命令序列号(必选) -->
<element name="SN" type="tg:SNType"/>
<!-- 目标设备编码(必选) -->
<element name="DeviceID" type="tg:deviceIDType"/>
<!-- 录像检索起始时间(必选)-->
<element name ="StartTime" type="dateTime"/>
<!-- 录像检索终止时间(必选)-->
<element name ="EndTime" type="dateTime"/>
<!-- 文件路径名 (可选)-->
<element name="FilePath" type="string" minOccurs="0"/>
<!-- 录像地址(可选 支持不完全查询) -->
<element name="Address" type="string" minOccurs="0"/>
<!-- 保密属性(可选)缺省为0;0-不涉密;1-涉密-->
<element name="Secrecy" type="integer" minOccurs="0"/>
<!-- 录像产生类型(可选)time或alarm或manual或all -->
<element name="Type" type="string" minOccurs="0"/>
<!-- 录像触发者ID(可选)-->
<element name="RecorderID" type="string" minOccurs="0"/> <!--录像模糊查询属性(可选)缺省为0;0-不进行模糊查询，此时根据SIP消息中To头域URI中的 ID值确定查询录像位置，若ID值为本域系统ID则进行中心历史记录检索，若为前端设备ID则进行前 端设备历史记录检索;1-进行模糊查询，此时设备所在域应同时进行中心检索和前端检索并将结果 统一返回。-->
<element name="IndistinctQuery" type="string" minOccurs= "0"/>
<!-- 码流编号(可选):0- 主码流;1- 子码流1;2-子码流2;以此类推-->
<element name="StreamNumber" type= "integer" minOccurs="0"/>
<!-- 报警方式条件(可选)取值0-全部;1-电话报警;2-设备报警;3-短信报警;4-GPS报警; 5-视频报警;6-设备故障报警;7-其他报警;可以为直接组合如1/2为电话报警或设备报警--> <element name="AlarmMethod" type= "string" minOccurs="0"/>
<!-- 报警类型(可选)。报警类型。报警方式为2时，不携带AlarmType为默认的报警设备报警， 携带AlarmType取值及对应报警类型如下:1-视频丢失报警;2-设备防拆报警;3-存储设备磁盘满 报警;4-设备高温报警;5-设备低温报警。报警方式为5时，取值如下:1-人工视频报警;2-运动 目标检测报警;3-遗留物检测报警;4-物体移除检测报警;5-绊线检测报警;6-入侵检测报警;7- 逆行检测报警;8-徘徊检测报警;9-流量统计报警;10-密度检测报警;11-视频异常检测报警;12- 快速移动报警;13-图像遮挡报警。报警方式为6时，取值如下:1-存储设备磁盘故障报警;2-存储 设备风扇故障报警-->
<element name="AlarmType" type="string" minOccurs="0"/>

A.2.6.7 文件目录检索应答
<!-- 命令类型:文件目录查询(必选) --> <element name="CmdType" fixed =" RecordInfo"/> <!-- 命令序列号(必选) -->
<element name="SN" type="tg:SNType"/>
<!-- 目标设备编码(必选) -->
<element name="DeviceID" type="tg:deviceIDType"/> <!-- 设备/区域名称(必选) -->
<element name="Name" type="string"/>
<!-- 查询结果总数(必选) -->
<element name="SumNum" type="integer"/>
<!-- 文件目录项列表,Num表示目录项个数 --> <element name="RecordList">
<complexType>
<choice minOccurs="0" maxOccurs="unbounded">
<element name="Item" type="tg:itemFileType"/> </choice>
<attribute name="Num" type="integer"/>
</complexType>
</element>
<!-- 扩展信息，可多项 -->
<element name="ExtraInfo" minOccurs="0" maxOccurs="unbounded">
<simpleType>
<restriction base="string">
<maxLength value="1024"/> </restriction>
      </simpleType>
    </element>

A.2.1.10 文件目录项类型
<complexType name="itemFileType"> <sequence>
<!-- 目标设备编码(必选) -->
<element name="DeviceID" type="tg:deviceIDType"/>
<!-- 目标设备名称(必选) -->
<element name="Name" type="string"/>
<!-- 文件路径名(可选)-->
<element name="FilePath" type="string" minOccurs="0"/> <!-- 录像地址(可选) -->
<element name="Address" type="string" minOccurs="0"/> <!-- 录像开始时间(可选)-->
<element name="StartTime" type="dateTime" minOccurs="0"/> <!-- 录像结束时间(可选)-->
<element name="EndTime" type="dateTime" minOccurs="0"/> <!-- 保密属性(必选)缺省为0;0-不涉密，1-涉密--> <element name="Secrecy" type="integer"/>
64
<!-- 录像产生类型(可选)time或alarm或manual--> <element name="Type" type="string" minOccurs="0"/>
<!-- 录像触发者ID(可选)-->
<element name="RecorderID" type="string" minOccurs="0"/> <!-- 录像文件大小，单位:Byte(可选)-->
<element name="FileSize" type="string" minOccurs="0"/>
<!-- 存储录像文件的设备/系统编码，(模糊查询时必选) -->
<element name="RecordLocation" type="tg:deviceIDType " minOccurs="0"/> <!-- 码流类型:0-主码流;1-子码流1;2-子码流2;以此类推(可选) --> <element name="StreamNumber" type="integer" minOccurs="0"/>
      </sequence>
    </complexType>
