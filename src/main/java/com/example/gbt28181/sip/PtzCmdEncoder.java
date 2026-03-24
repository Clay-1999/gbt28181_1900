package com.example.gbt28181.sip;

/**
 * GB/T 28181 附录 A.3 规定的 PTZCmd 8 字节编码工具。
 *
 * <p>字节格式：
 * <pre>
 * 字节1: A5H（固定）
 * 字节2: 组合码1，高4位=版本0H，低4位=校验位=（B1高4位+B1低4位+B2高4位）%16
 * 字节3: 地址低8位（前端设备控制不用，填00H）
 * 字节4: 指令码
 * 字节5: 数据1（Pan 速度 / 聚焦速度）
 * 字节6: 数据2（Tilt 速度 / 光圈速度）
 * 字节7: 组合码2，高4位=变焦速度（Zoom 速度），低4位=地址高4位（填0）
 * 字节8: 校验码 = (字节1+…+字节7) % 256
 * </pre>
 */
public class PtzCmdEncoder {

    private PtzCmdEncoder() {}

    /**
     * 编码 PTZ 方向控制指令。
     *
     * @param action 动作字符串：up / down / left / right /
     *               left-up / right-up / left-down / right-down / stop
     * @param speed  速度值 0~255（用于水平/垂直速度字节）
     * @return 16 进制字符串（大写，16 字符）
     */
    public static String encodePtz(String action, int speed) {
        int panSpeed = 0;
        int tiltSpeed = 0;
        int cmd = 0x00;

        switch (action) {
            case "up"         -> { cmd = 0x08; tiltSpeed = speed; }
            case "down"       -> { cmd = 0x04; tiltSpeed = speed; }
            case "left"       -> { cmd = 0x02; panSpeed = speed; }
            case "right"      -> { cmd = 0x01; panSpeed = speed; }
            case "left-up"    -> { cmd = 0x0A; panSpeed = speed; tiltSpeed = speed; }
            case "right-up"   -> { cmd = 0x09; panSpeed = speed; tiltSpeed = speed; }
            case "left-down"  -> { cmd = 0x06; panSpeed = speed; tiltSpeed = speed; }
            case "right-down" -> { cmd = 0x05; panSpeed = speed; tiltSpeed = speed; }
            case "stop"       -> cmd = 0x00;
            default           -> cmd = 0x00;
        }
        return buildCmd(cmd, panSpeed, tiltSpeed, 0);
    }

    /**
     * 编码变倍指令（PTZ 指令格式）。
     *
     * @param zoomIn true=放大（字节4 bit4=1），false=缩小（字节4 bit5=1）
     * @param speed  变倍速度 0~15（字节7高4位）
     */
    public static String encodeZoom(boolean zoomIn, int speed) {
        int cmd = zoomIn ? 0x10 : 0x20;
        int zoomSpeed = Math.min(speed, 15);
        return buildCmd(cmd, 0, 0, zoomSpeed);
    }

    /**
     * 编码聚焦/光圈指令（FI 指令格式，字节4高2位=01）。
     *
     * @param action focus_in / focus_out / iris_in / iris_out / fi_stop
     * @param speed  速度 0~255
     */
    public static String encodeFi(String action, int speed) {
        int cmd = 0x40;
        int focusSpeed = 0;
        int irisSpeed = 0;

        switch (action) {
            case "focus_in"   -> { cmd = 0x42; focusSpeed = speed; }  // 聚焦近 bit1=1
            case "focus_out"  -> { cmd = 0x41; focusSpeed = speed; }  // 聚焦远 bit0=1
            case "iris_in"    -> { cmd = 0x44; irisSpeed = speed; }   // 光圈放大 bit2=1
            case "iris_out"   -> { cmd = 0x48; irisSpeed = speed; }   // 光圈缩小 bit3=1
            case "fi_stop"    -> cmd = 0x40;
            default           -> cmd = 0x40;
        }
        // 字节5=聚焦速度，字节6=光圈速度，字节7高4位=0（FI 无变焦）
        return buildFiCmd(cmd, focusSpeed, irisSpeed);
    }

    /**
     * 编码预置位指令。
     *
     * @param operation set / call / delete
     * @param presetIndex 预置位编号 1~255
     */
    public static String encodePreset(String operation, int presetIndex) {
        int cmd = switch (operation) {
            case "set"    -> 0x81;
            case "call"   -> 0x82;
            case "delete" -> 0x83;
            default -> throw new IllegalArgumentException("Unknown preset operation: " + operation);
        };
        int index = Math.max(1, Math.min(presetIndex, 255));
        // 字节4=cmd, 字节5=0x00, 字节6=预置位编号
        return buildPresetCmd(cmd, index);
    }

    /**
     * 编码巡航启动指令（字节4=0x88，字节5=巡航组号，字节6=0x00）。
     *
     * @param trackGroup 巡航组号 0~255（通常用1）
     */
    public static String encodeCruiseStart(int trackGroup) {
        return buildPresetCmd(0x88, 0x00, trackGroup & 0xFF, 0x00);
    }

    /** 编码停止所有 PTZ 动作（字节4=0x00）。 */
    public static String encodeStop() {
        return buildCmd(0x00, 0, 0, 0);
    }

    // ===== 私有构建方法 =====

    /** 构造 PTZ 指令（字节4指令码，字节5 pan，字节6 tilt，字节7高4位 zoom）。 */
    private static String buildCmd(int cmd4, int byte5, int byte6, int zoomSpeed) {
        int b1 = 0xA5;
        int b2 = buildB2(b1);
        int b3 = 0x00;
        int b4 = cmd4 & 0xFF;
        int b5 = byte5 & 0xFF;
        int b6 = byte6 & 0xFF;
        int b7 = ((zoomSpeed & 0x0F) << 4);  // 高4位=变焦速度，低4位=地址高4位=0
        int b8 = (b1 + b2 + b3 + b4 + b5 + b6 + b7) & 0xFF;
        return toHex(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    /** 构造 FI 指令（字节5=聚焦速度，字节6=光圈速度）。 */
    private static String buildFiCmd(int cmd4, int focusSpeed, int irisSpeed) {
        int b1 = 0xA5;
        int b2 = buildB2(b1);
        int b3 = 0x00;
        int b4 = cmd4 & 0xFF;
        int b5 = focusSpeed & 0xFF;
        int b6 = irisSpeed & 0xFF;
        int b7 = 0x00;
        int b8 = (b1 + b2 + b3 + b4 + b5 + b6 + b7) & 0xFF;
        return toHex(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    /** 构造预置位指令（字节5=0x00，字节6=预置位编号）。 */
    private static String buildPresetCmd(int cmd4, int presetIndex) {
        return buildPresetCmd(cmd4, 0x00, presetIndex, 0x00);
    }

    private static String buildPresetCmd(int cmd4, int byte5, int byte6, int byte7) {
        int b1 = 0xA5;
        int b2 = buildB2(b1);
        int b3 = 0x00;
        int b4 = cmd4 & 0xFF;
        int b5 = byte5 & 0xFF;
        int b6 = byte6 & 0xFF;
        int b7 = byte7 & 0xFF;
        int b8 = (b1 + b2 + b3 + b4 + b5 + b6 + b7) & 0xFF;
        return toHex(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    /**
     * 计算字节2：高4位=版本0H，低4位=校验位=(B1高4+B1低4+B2高4)%16。
     * 版本号=0，即 B2高4=0，简化为 (0xA+0x5+0)%16 = 15 = 0xF，低4位=0xF → B2=0x0F。
     */
    private static int buildB2(int b1) {
        int version = 0;  // 版本0H
        int checkLow = ((b1 >> 4) + (b1 & 0x0F) + version) % 16;
        return (version << 4) | checkLow;
    }

    private static String toHex(int... bytes) {
        StringBuilder sb = new StringBuilder(16);
        for (int b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
