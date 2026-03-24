#!/usr/bin/env python3
"""
GBT28181 集成测试套件

用法:
  python3 test_gbt28181.py                     # 运行默认套件 1-4 6 7
  python3 test_gbt28181.py --suite 1 3         # 运行指定套件
  python3 test_gbt28181.py --suite all         # 运行全部（含破坏性套件 5）
  python3 test_gbt28181.py --log path/to/log   # 指定日志路径（套件2用）

套件:
  1  time_sync         9.10 校时 — REGISTER 200 OK Date 头域
  2  config_routing    SIP ConfigDownload/DeviceConfig 命令路由
  3  uplink_catalog    上联注册 → Catalog Query → NOTIFY 同步
  4  basic_signaling   9.5 DeviceInfo/Status + 9.3 DeviceControl
  5  downlink_catalog  下联注册（Spring Boot → Python）→ Catalog 同步  [破坏性]
  6  snapshot_upgrade  9.14 图像抓拍 + 9.13 软件升级（REST + SIP 南向路由）
  7  playback          9.8 历史回放接口（REST 参数校验 + 幂等性）

前置条件:
  - Spring Boot 已启动（mvn spring-boot:run）
  - 数据库中存在 remoteSipId=34020000001320000002 的互联配置（password=test123）
  - 套件 2/6 需本端 IVS1900 相机 CFG_DEVICE_ID 在线（SIP 子用例）
  - 套件 5 会修改数据库中的互联配置，需谨慎使用
"""

import argparse
import hashlib
import json
import socket
import sys
import threading
import time
import uuid
import re
from urllib.error import URLError
from urllib.request import Request as UrllibRequest
from urllib.request import urlopen

# ── 全局参数 ──────────────────────────────────────────────────────────────────

SERVER_IP   = "192.168.10.4"   # Spring Boot SIP 绑定 IP
SERVER_PORT = 15060             # Spring Boot SIP 端口
PYTHON_IP   = "127.0.0.1"      # 本脚本绑定 IP

SERVER_SIP_ID = "64010400002000000001"
SERVER_DOMAIN = "6401040000"

CLIENT_SIP_ID = "34020000001320000002"   # 互联配置 remoteSipId
CLIENT_DOMAIN = "3402000001"
CLIENT_PASSWORD = "test123"
REALM         = "gbt28181"

REST_BASE = "http://127.0.0.1:8080"

# 套件 2 专用设备 ID
CFG_DEVICE_ID  = "34020000001320000034"   # 本端 IVS1900 相机
CFG_UNKNOWN_ID = "34020000001320000099"   # 未知设备（期望 404）
CFG_REMOTE_ID  = "34020000001320000101"   # 外域设备（透传）

# 套件 3/4 模拟外域设备列表
UPLINK_DEVICES = [
    {"id": "34020000001320000101", "name": "外域摄像头A", "status": "ON"},
    {"id": "34020000001320000102", "name": "外域摄像头B", "status": "ON"},
    {"id": "34020000001320000103", "name": "外域摄像头C", "status": "OFF"},
]

# 套件 5 下联模拟设备列表
DOWNLINK_DEVICES = [
    {"id": "34020000001320000201", "name": "外域摄像头D", "status": "ON"},
    {"id": "34020000001320000202", "name": "外域摄像头E", "status": "OFF"},
    {"id": "34020000001320000203", "name": "外域摄像头F", "status": "ON"},
]

# ── 共用工具 ──────────────────────────────────────────────────────────────────

def md5(s):
    return hashlib.md5(s.encode()).hexdigest()


def digest_response(method, uri, nonce, sip_id=CLIENT_SIP_ID, password=CLIENT_PASSWORD):
    ha1 = md5(f"{sip_id}:{REALM}:{password}")
    ha2 = md5(f"{method}:{uri}")
    return md5(f"{ha1}:{nonce}:{ha2}")


def get_header(msg, name):
    for line in msg.split("\r\n"):
        if line.lower().startswith(name.lower() + ":"):
            return line.split(":", 1)[1].strip()
    return None


def parse_nonce(www_auth):
    if not www_auth:
        return None
    for part in www_auth.split(","):
        p = part.strip()
        if p.lower().startswith("nonce="):
            return p.split('"')[1]
    return None


def make_register(call_id, cseq, auth_line="", python_port=25061):
    branch = f"z9hG4bK{uuid.uuid4().hex[:10]}"
    tag    = uuid.uuid4().hex[:8]
    return (
        f"REGISTER sip:{SERVER_DOMAIN} SIP/2.0\r\n"
        f"Via: SIP/2.0/UDP {PYTHON_IP}:{python_port};branch={branch}\r\n"
        f"From: <sip:{CLIENT_SIP_ID}@{CLIENT_DOMAIN}>;tag={tag}\r\n"
        f"To: <sip:{CLIENT_SIP_ID}@{CLIENT_DOMAIN}>\r\n"
        f"Call-ID: {call_id}\r\n"
        f"CSeq: {cseq} REGISTER\r\n"
        f"Contact: <sip:{CLIENT_SIP_ID}@{PYTHON_IP}:{python_port}>\r\n"
        f"Expires: 3600\r\n"
        f"Max-Forwards: 70\r\n"
        f"{auth_line}"
        f"Content-Length: 0\r\n\r\n"
    )


def build_catalog_notify(devices, from_sip_id, from_domain, python_port):
    items = "\n".join(
        f"    <Item>\n"
        f"      <DeviceID>{d['id']}</DeviceID>\n"
        f"      <Name>{d['name']}</Name>\n"
        f"      <Status>{d['status']}</Status>\n"
        f"    </Item>"
        for d in devices
    )
    body = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        "<Notify>\n"
        "  <CmdType>Catalog</CmdType>\n"
        f"  <SN>{int(time.time()) % 100000}</SN>\n"
        f"  <DeviceID>{from_sip_id}</DeviceID>\n"
        f"  <SumNum>{len(devices)}</SumNum>\n"
        f'  <DeviceList Num="{len(devices)}">\n'
        f"{items}\n"
        "  </DeviceList>\n"
        "</Notify>"
    )
    body_bytes = body.encode("utf-8")
    branch  = f"z9hG4bK{uuid.uuid4().hex[:10]}"
    tag     = uuid.uuid4().hex[:8]
    call_id = uuid.uuid4().hex
    headers = (
        f"NOTIFY sip:{SERVER_SIP_ID}@{SERVER_DOMAIN} SIP/2.0\r\n"
        f"Via: SIP/2.0/UDP {PYTHON_IP}:{python_port};branch={branch}\r\n"
        f"From: <sip:{from_sip_id}@{from_domain}>;tag={tag}\r\n"
        f"To: <sip:{SERVER_SIP_ID}@{SERVER_DOMAIN}>\r\n"
        f"Call-ID: {call_id}\r\n"
        f"CSeq: 1 NOTIFY\r\n"
        f"Event: Catalog\r\n"
        f"Subscription-State: active;expires=3600\r\n"
        f"Max-Forwards: 70\r\n"
        f"Contact: <sip:{from_sip_id}@{PYTHON_IP}:{python_port}>\r\n"
        f"Content-Type: Application/MANSCDP+xml\r\n"
        f"Content-Length: {len(body_bytes)}\r\n\r\n"
    )
    return headers.encode() + body_bytes


def send_200ok(sock, via, from_h, to_h, call_id, cseq, dest=(None, None), extra=""):
    ip, port = dest
    ip = ip or SERVER_IP
    port = port or SERVER_PORT
    resp = (
        f"SIP/2.0 200 OK\r\n"
        f"Via: {via}\r\n"
        f"From: {from_h}\r\n"
        f"To: {to_h}\r\n"
        f"Call-ID: {call_id}\r\n"
        f"CSeq: {cseq}\r\n"
        f"{extra}"
        f"Content-Length: 0\r\n\r\n"
    )
    sock.sendto(resp.encode(), (ip, port))


def rest_get(path):
    try:
        with urlopen(f"{REST_BASE}{path}", timeout=15) as r:
            return r.status, json.loads(r.read())
    except URLError as e:
        if hasattr(e, "code") and hasattr(e, "read"):
            try:
                return e.code, json.loads(e.read())
            except Exception:
                return e.code, {}
        return None, str(e)


def rest_post(path, body=None):
    data = json.dumps(body).encode() if body else b""
    req = UrllibRequest(
        f"{REST_BASE}{path}", data=data,
        headers={"Content-Type": "application/json"}, method="POST"
    )
    try:
        with urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read())
    except URLError as e:
        if hasattr(e, "code") and hasattr(e, "read"):
            try:
                return e.code, json.loads(e.read())
            except Exception:
                pass
        return None, str(e)


def rest_put(path, body):
    data = json.dumps(body).encode()
    req = UrllibRequest(
        f"{REST_BASE}{path}", data=data,
        headers={"Content-Type": "application/json"}, method="PUT"
    )
    try:
        with urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read())
    except URLError as e:
        if hasattr(e, "code") and hasattr(e, "read"):
            try:
                return e.code, json.loads(e.read())
            except Exception:
                pass
        return None, str(e)


def recv_until(sock, timeout=8):
    sock.settimeout(1)
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            data, addr = sock.recvfrom(65535)
            return data.decode(errors="replace"), addr
        except socket.timeout:
            pass
    return None, None


def grep_log(log_file, call_ids):
    if not call_ids:
        return
    try:
        with open(log_file, encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
    except FileNotFoundError:
        print(f"\n[日志过滤] 日志文件不存在: {log_file}")
        return
    print(f"\n{'='*60}")
    print(f"[日志过滤] 从 {log_file} 提取失败用例日志")
    print(f"{'='*60}")
    for name, call_id in call_ids:
        matched = [l.rstrip() for l in lines if call_id in l]
        print(f"\n--- [{name}]  call-id={call_id} ({len(matched)} 行) ---")
        if matched:
            for line in matched:
                print(f"  {line}")
        else:
            print("  (日志中未找到该 call-id)")


# ── 套件 1：9.10 校时 ─────────────────────────────────────────────────────────

def suite_time_sync():
    """验证 REGISTER 200 OK 中包含正确格式的 Date 头域"""
    print("\n── 套件 1：9.10 校时（REGISTER 200 OK Date 头域）──")
    python_port = 25061
    results = []

    def check(name, ok, detail=""):
        status = "✅ PASS" if ok else "❌ FAIL"
        msg = f"  {status}  {name}"
        if detail:
            msg += f"\n         {detail}"
        print(msg)
        results.append((name, ok))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(5)
    sock.bind((PYTHON_IP, python_port))
    try:
        call_id = str(uuid.uuid4())
        sock.sendto(make_register(call_id, 1, python_port=python_port).encode(),
                    (SERVER_IP, SERVER_PORT))
        data, _ = recv_until(sock, 5)
        if data is None:
            check("收到 401 Unauthorized", False, f"无响应，确认 Spring Boot 已启动且 SIP 绑定 {SERVER_IP}:{SERVER_PORT}")
            return results

        check("收到 401 Unauthorized", "401" in data.split("\r\n")[0])
        www_auth = get_header(data, "WWW-Authenticate")
        nonce = parse_nonce(www_auth)
        if not nonce:
            check("解析 WWW-Authenticate", False, "未找到 nonce")
            return results

        uri = f"sip:{SERVER_DOMAIN}"
        resp_val = digest_response("REGISTER", uri, nonce)
        auth_line = (
            f'Authorization: Digest username="{CLIENT_SIP_ID}", realm="{REALM}", '
            f'nonce="{nonce}", uri="{uri}", response="{resp_val}", algorithm=MD5\r\n'
        )
        sock.sendto(make_register(call_id, 2, auth_line, python_port).encode(),
                    (SERVER_IP, SERVER_PORT))
        data200, _ = recv_until(sock, 5)
        if data200 is None:
            check("收到 200 OK", False, "带认证的 REGISTER 无响应")
            return results

        check("收到 200 OK", "200 OK" in data200 or "SIP/2.0 200" in data200)
        date_val = get_header(data200, "Date")
        has_date = date_val is not None
        check("200 OK 包含 Date 头域", has_date,
              f"Date: {date_val}" if has_date else "未找到 Date 头")
        if has_date:
            fmt_ok = bool(re.match(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$", date_val.strip()))
            check("Date 格式正确（YYYY-MM-DDTHH:MM:SS）", fmt_ok, date_val)
    finally:
        sock.close()

    return results


# ── 套件 2：ConfigDownload / DeviceConfig 命令路由 ────────────────────────────

def suite_config_routing(log_file="logs/gbt28181.log"):
    """验证 SIP ConfigDownload/DeviceConfig 命令路由"""
    print("\n── 套件 2：ConfigDownload/DeviceConfig 命令路由 ──")
    python_port = 25062

    def make_sip_message(cmd_type, device_id, extra_xml="", sn=None):
        sn = sn or (int(time.time()) % 100000)
        root_open  = "<Query>"   if cmd_type == "ConfigDownload" else "<Control>"
        root_close = "</Query>"  if cmd_type == "ConfigDownload" else "</Control>"
        body = (
            '<?xml version="1.0" encoding="GB2312"?>\n'
            f"{root_open}\n"
            f"<CmdType>{cmd_type}</CmdType>\n"
            f"<SN>{sn}</SN>\n"
            f"<DeviceID>{device_id}</DeviceID>\n"
            f"{extra_xml}"
            f"{root_close}"
        )
        body_bytes = body.encode("utf-8")
        branch  = f"z9hG4bK{uuid.uuid4().hex[:10]}"
        tag     = uuid.uuid4().hex[:8]
        call_id = uuid.uuid4().hex
        msg = (
            f"MESSAGE sip:{device_id}@{SERVER_IP}:{SERVER_PORT} SIP/2.0\r\n"
            f"Via: SIP/2.0/UDP {PYTHON_IP}:{python_port};branch={branch}\r\n"
            f"From: <sip:{CLIENT_SIP_ID}@{PYTHON_IP}:{python_port}>;tag={tag}\r\n"
            f"To: <sip:{device_id}@{SERVER_IP}:{SERVER_PORT}>\r\n"
            f"Call-ID: {call_id}\r\n"
            f"CSeq: 1 MESSAGE\r\n"
            f"Max-Forwards: 70\r\n"
            f"Content-Type: Application/MANSCDP+xml\r\n"
            f"Content-Length: {len(body_bytes)}\r\n\r\n"
        ).encode() + body_bytes
        return msg, sn, call_id

    results  = []
    names    = []
    call_ids = []

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((PYTHON_IP, python_port))
    print(f"  [模拟端] 监听 {PYTHON_IP}:{python_port}")

    def run_test(name, cmd_type, device_id, extra_xml="", expect_tag=None, expect_404=False):
        msg, sn, call_id = make_sip_message(cmd_type, device_id, extra_xml)
        sock.sendto(msg, (SERVER_IP, SERVER_PORT))

        resp, _ = recv_until(sock, 5)
        if resp is None:
            print(f"  ❌ [{name}] 无响应  call-id={call_id}")
            names.append(name); results.append(False); call_ids.append(call_id)
            return

        first = resp.splitlines()[0]
        if expect_404:
            ok = "404" in first
            print(f"  {'✅' if ok else '❌'} [{name}] {'收到 404 Not Found' if ok else f'期望 404，收到: {first}'}  call-id={call_id if not ok else ''}")
            names.append(name); results.append(ok); call_ids.append(call_id)
            return

        if "404" in first:
            print(f"  ❌ [{name}] 意外 404  call-id={call_id}")
            names.append(name); results.append(False); call_ids.append(call_id)
            return
        if "200" not in first:
            print(f"  ❌ [{name}] 期望 200，收到: {first}  call-id={call_id}")
            names.append(name); results.append(False); call_ids.append(call_id)
            return

        resp2, _ = recv_until(sock, 10)
        if resp2 is None:
            print(f"  ❌ [{name}] 未收到响应 MESSAGE  call-id={call_id}")
            names.append(name); results.append(False); call_ids.append(call_id)
            return

        if "MESSAGE" in resp2.splitlines()[0]:
            via  = get_header(resp2, "Via")
            frm  = get_header(resp2, "From")
            to   = get_header(resp2, "To")
            cid  = get_header(resp2, "Call-ID")
            cseq = get_header(resp2, "CSeq")
            ack = (f"SIP/2.0 200 OK\r\nVia: {via}\r\nFrom: {frm}\r\nTo: {to}\r\n"
                   f"Call-ID: {cid}\r\nCSeq: {cseq}\r\nContent-Length: 0\r\n\r\n")
            sock.sendto(ack.encode(), (SERVER_IP, SERVER_PORT))

        tag_found = (f"<{expect_tag}" in resp2) if expect_tag else True
        result_val = "OK" if "Result>OK" in resp2 else ("Error" if "Result>Error" in resp2 else "?")
        ok = tag_found
        print(f"  {'✅' if ok else '❌'} [{name}] Result={result_val}"
              f"{' <' + expect_tag + '> found' if tag_found and expect_tag else ''}"
              f"{'  call-id=' + call_id if not ok else ''}")
        names.append(name); results.append(ok); call_ids.append(call_id)

    run_test("ConfigDownload/BasicParam",              "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>BasicParam</ConfigType>\n",              expect_tag="BasicParam")
    run_test("ConfigDownload/VideoParamOpt",            "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>VideoParamOpt</ConfigType>\n",            expect_tag="VideoParamOpt")
    run_test("ConfigDownload/VideoParamAttribute",      "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>VideoParamAttribute</ConfigType>\n",      expect_tag="VideoParamAttribute")
    run_test("ConfigDownload/PictureMask",              "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>PictureMask</ConfigType>\n",              expect_tag="PictureMask")
    run_test("ConfigDownload/FrameMirror",              "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>FrameMirror</ConfigType>\n",              expect_tag="FrameMirror")
    run_test("ConfigDownload/OSDConfig",                "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>OSDConfig</ConfigType>\n",                expect_tag="OSDConfig")
    run_test("ConfigDownload/SVACDecodeConfig",         "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>SVACDecodeConfig</ConfigType>\n",         expect_tag="SVACDecodeConfig")
    run_test("ConfigDownload/VideoRecordPlan(无接口)",   "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>VideoRecordPlan</ConfigType>\n",          expect_tag="VideoRecordPlan")
    run_test("ConfigDownload/BasicParam+OSDConfig",     "ConfigDownload", CFG_DEVICE_ID, "<ConfigType>BasicParam/OSDConfig</ConfigType>\n",     expect_tag="BasicParam")
    run_test("DeviceConfig/BasicParam(Name)",           "DeviceConfig",   CFG_DEVICE_ID, "<BasicParam>\n<Name>TestCamera</Name>\n</BasicParam>\n")
    run_test("DeviceConfig/VideoParamAttribute",        "DeviceConfig",   CFG_DEVICE_ID,
             "<VideoParamAttribute>\n<VideoParamNum>1</VideoParamNum>\n"
             "<Item>\n<StreamID>0</StreamID>\n<VideoFormat>H.265</VideoFormat>\n"
             "<Resolution>1920*1080</Resolution>\n<FrameRate>25</FrameRate>\n"
             "<BitRateType>0</BitRateType>\n<BitRate>4096</BitRate>\n</Item>\n"
             "</VideoParamAttribute>\n")
    run_test("DeviceConfig/PictureMask",                "DeviceConfig",   CFG_DEVICE_ID, "<PictureMask>\n<MaskEnabled>0</MaskEnabled>\n</PictureMask>\n")
    run_test("DeviceConfig/OSDConfig",                  "DeviceConfig",   CFG_DEVICE_ID, "<OSDConfig>\n<OSDEnabled>1</OSDEnabled>\n<OSDTime>1</OSDTime>\n<OSDFontSize>3</OSDFontSize>\n</OSDConfig>\n")
    run_test("DeviceConfig/SnapShotConfig(无接口)",      "DeviceConfig",   CFG_DEVICE_ID, "<SnapShotConfig>\n<SnapShotInterval>5</SnapShotInterval>\n</SnapShotConfig>\n")
    run_test("Unknown DeviceID → 404",                  "ConfigDownload", CFG_UNKNOWN_ID, "<ConfigType>BasicParam</ConfigType>\n", expect_404=True)

    sock.close()

    failed_ids = [(names[i], call_ids[i]) for i, ok in enumerate(results) if not ok]
    if failed_ids:
        time.sleep(1)
        grep_log(log_file, failed_ids)

    return list(zip(names, results))


# ── 套件 3：上联注册 + Catalog 同步 ──────────────────────────────────────────

def suite_uplink_catalog():
    """Python 作为下级平台上联注册，验证 Catalog Query 触发和设备同步"""
    print("\n── 套件 3：上联注册 → Catalog Query → NOTIFY 同步 ──")
    python_port = 25060
    results = []

    def check(name, ok, detail=""):
        status = "✅ PASS" if ok else "❌ FAIL"
        msg = f"  {status}  {name}"
        if detail:
            msg += f"\n         {detail}"
        print(msg)
        results.append((name, ok))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((PYTHON_IP, python_port))
    sock.settimeout(10)

    # 步骤 1：REGISTER（无认证）→ 401
    call_id = uuid.uuid4().hex
    print(f"  [1] 发送 REGISTER（无认证）...")
    sock.sendto(make_register(call_id, 1, python_port=python_port).encode(),
                (SERVER_IP, SERVER_PORT))
    try:
        data, _ = sock.recvfrom(8192)
        msg = data.decode(errors="replace")
    except socket.timeout:
        check("收到 401 Unauthorized", False, f"无响应，确认 SIP 绑定 {SERVER_IP}:{SERVER_PORT}")
        sock.close()
        return results

    check("收到 401 Unauthorized", "401" in msg.splitlines()[0])
    nonce = parse_nonce(get_header(msg, "WWW-Authenticate"))
    if not nonce:
        check("解析 nonce", False)
        sock.close()
        return results

    # 步骤 2：带 Digest 认证重发
    uri = f"sip:{SERVER_DOMAIN}"
    digest = digest_response("REGISTER", uri, nonce)
    auth_line = (
        f'Authorization: Digest username="{CLIENT_SIP_ID}", realm="{REALM}", '
        f'nonce="{nonce}", uri="{uri}", algorithm=MD5, response="{digest}"\r\n'
    )
    print(f"  [2] 发送带 Digest 认证的 REGISTER ...")
    sock.sendto(make_register(call_id, 2, auth_line, python_port).encode(),
                (SERVER_IP, SERVER_PORT))
    try:
        data, _ = sock.recvfrom(8192)
        msg = data.decode(errors="replace")
    except socket.timeout:
        check("收到 200 OK", False, "认证 REGISTER 无响应")
        sock.close()
        return results

    check("收到 200 OK（注册成功）", "200" in msg.splitlines()[0])

    # 步骤 3：等待 Catalog Query MESSAGE
    print(f"  [3] 等待 Catalog Query ...")
    catalog_ok = False
    deadline = time.time() + 15
    while time.time() < deadline:
        try:
            data, _ = sock.recvfrom(8192)
            msg = data.decode(errors="replace")
            if "MESSAGE" in msg.splitlines()[0] and "Catalog" in msg:
                via   = get_header(msg, "Via")
                frm   = get_header(msg, "From")
                to    = get_header(msg, "To")
                cid   = get_header(msg, "Call-ID")
                cseq  = get_header(msg, "CSeq")
                send_200ok(sock, via, frm, to, cid, cseq)
                catalog_ok = True
                break
            elif "MESSAGE" in msg.splitlines()[0]:
                via  = get_header(msg, "Via")
                frm  = get_header(msg, "From")
                to   = get_header(msg, "To")
                cid  = get_header(msg, "Call-ID")
                cseq = get_header(msg, "CSeq")
                send_200ok(sock, via, frm, to, cid, cseq)
        except socket.timeout:
            pass

    check("收到 Catalog Query MESSAGE", catalog_ok)
    if not catalog_ok:
        sock.close()
        return results

    # 步骤 4：发送 NOTIFY
    print(f"  [4] 发送 NOTIFY（{len(UPLINK_DEVICES)} 台设备）...")
    time.sleep(0.3)
    sock.sendto(build_catalog_notify(UPLINK_DEVICES, CLIENT_SIP_ID, CLIENT_DOMAIN, python_port),
                (SERVER_IP, SERVER_PORT))
    try:
        data, _ = sock.recvfrom(8192)
        notify_ack = data.decode(errors="replace").splitlines()[0]
        check("NOTIFY 收到 200 OK", "200" in notify_ack, notify_ack)
    except socket.timeout:
        check("NOTIFY 收到 200 OK", False, "NOTIFY 响应超时")

    sock.close()

    # 步骤 5：验证 REST API
    print(f"  [5] 验证 GET /api/devices/remote ...")
    time.sleep(1)
    status, devices = rest_get("/api/devices/remote")
    if status == 200 and isinstance(devices, list):
        synced = [d for d in devices if d.get("deviceId") in {dev["id"] for dev in UPLINK_DEVICES}]
        check(f"外域设备已同步（≥{len(UPLINK_DEVICES)} 台）", len(synced) >= len(UPLINK_DEVICES),
              f"已同步: {[d['name'] for d in synced]}")
    else:
        check("GET /api/devices/remote 可达", False, f"status={status}")

    return results


# ── 套件 4：9.5 DeviceInfo/Status + 9.3 DeviceControl ────────────────────────

def suite_basic_signaling():
    """验证 DeviceInfo/Status 查询接口和 DeviceControl 命令接口"""
    print("\n── 套件 4：9.5 DeviceInfo/Status + 9.3 DeviceControl ──")
    # 套件 3 运行后外域设备 34020000001320000101 应已存在
    remote_id = CFG_REMOTE_ID
    results = []

    def check(name, ok, detail=""):
        status = "✅ PASS" if ok else "❌ FAIL"
        msg = f"  {status}  {name}"
        if detail:
            msg += f"\n         {detail}"
        print(msg)
        results.append((name, ok))

    # DeviceInfo/Status 查询（设备不存在 → 404，存在 → 504 或 200）
    for endpoint, label in [("/info", "DeviceInfo"), ("/status", "DeviceStatus")]:
        status, body = rest_get(f"/api/devices/remote/{remote_id}{endpoint}")
        if status == 404:
            check(f"GET /remote/{{id}}{endpoint}（设备不存在 → 404 符合预期）", True, str(body))
        elif status == 504:
            check(f"GET /remote/{{id}}{endpoint}（设备未响应 → 504 符合预期）", True)
        elif status == 200:
            check(f"GET /remote/{{id}}{endpoint} 返回 200", True, str(body))
        else:
            check(f"GET /remote/{{id}}{endpoint} 可达", False, f"status={status} body={body}")

    # DeviceControl 参数校验（非法 cmd → 400）
    status, body = rest_post(f"/api/devices/remote/{remote_id}/control/guard", {"cmd": "InvalidCmd"})
    check("非法 GuardCmd → 400", status == 400, str(body))

    status, body = rest_post(f"/api/devices/remote/{remote_id}/control/record", {"cmd": "BadCmd"})
    check("非法 RecordCmd → 400", status == 400, str(body))

    # DeviceControl 命令发送（设备不存在 → 404，存在 → sent:true）
    for path, payload, label in [
        (f"/api/devices/remote/{remote_id}/control/guard",  {"cmd": "SetGuard"},   "GuardCmd/SetGuard"),
        (f"/api/devices/remote/{remote_id}/control/record", {"cmd": "Record"},      "RecordCmd/Record"),
        (f"/api/devices/remote/{remote_id}/control/reboot", None,                   "TeleBoot"),
    ]:
        status, body = rest_post(path, payload)
        if status == 404:
            check(f"POST {label}（设备不存在 → 404 符合预期）", True, str(body))
        elif status == 200:
            check(f"POST {label} → sent:true", body.get("sent") is True, str(body))
        else:
            check(f"POST {label} 可达", False, f"status={status} body={body}")

    return results


# ── 套件 5：下联注册 + Catalog 同步（破坏性）────────────────────────────────

def suite_downlink_catalog():
    """Spring Boot 主动注册到 Python（下联模式），验证 Catalog 同步"""
    print("\n── 套件 5：下联注册 → Catalog 同步（Python 作为上级平台）──")
    print("  ⚠️  此套件会修改数据库中的互联配置")
    python_port = 25061
    results = []

    def check(name, ok, detail=""):
        status = "✅ PASS" if ok else "❌ FAIL"
        msg = f"  {status}  {name}"
        if detail:
            msg += f"\n         {detail}"
        print(msg)
        results.append((name, ok))

    # 找到可以用于下联测试的互联配置
    _, configs = rest_get("/api/interconnects")
    if not isinstance(configs, list) or not configs:
        check("获取互联配置列表", False, "列表为空，无法执行下联测试")
        return results

    target_config = configs[0]
    config_id = target_config["id"]
    print(f"  [准备] 使用互联配置 id={config_id} name={target_config['name']}")

    # 将目标互联配置指向本机 Python 端口，切为下联模式
    _, updated = rest_put(f"/api/interconnects/{config_id}", {
        "name": target_config["name"],
        "remoteSipId": CLIENT_SIP_ID,
        "remoteIp": PYTHON_IP,
        "remotePort": python_port,
        "remoteDomain": CLIENT_DOMAIN,
        "password": CLIENT_PASSWORD,
        "enabled": True,
        "upLinkEnabled": False
    })
    check("更新互联配置指向本机", updated.get("remotePort") == python_port, str(updated))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((PYTHON_IP, python_port))
    sock.settimeout(15)
    print(f"  [模拟端] 监听 {PYTHON_IP}:{python_port}（等待 Spring Boot REGISTER）")

    nonce_val = uuid.uuid4().hex
    register_ok = False
    catalog_ok  = False
    deadline = time.time() + 30

    while time.time() < deadline:
        try:
            data, addr = sock.recvfrom(8192)
            msg = data.decode(errors="replace")
            first = msg.splitlines()[0]
            via   = get_header(msg, "Via")
            frm   = get_header(msg, "From")
            to    = get_header(msg, "To")
            cid   = get_header(msg, "Call-ID")
            cseq  = get_header(msg, "CSeq")

            if "REGISTER" in first:
                auth_h = get_header(msg, "Authorization")
                if auth_h is None:
                    print(f"  [1] 收到 REGISTER（无认证）→ 发送 401")
                    extra = (f'WWW-Authenticate: Digest realm="test_realm", '
                             f'nonce="{nonce_val}", algorithm=MD5\r\n')
                    resp = (f"SIP/2.0 401 Unauthorized\r\nVia: {via}\r\nFrom: {frm}\r\nTo: {to}\r\n"
                            f"Call-ID: {cid}\r\nCSeq: {cseq}\r\n{extra}Content-Length: 0\r\n\r\n")
                    sock.sendto(resp.encode(), addr)
                else:
                    print(f"  [2] 收到带 Digest 认证的 REGISTER → 发送 200 OK")
                    extra = (f"Contact: <sip:{SERVER_SIP_ID}@{SERVER_IP}:{SERVER_PORT}>\r\n"
                             f"Expires: 3600\r\n")
                    send_200ok(sock, via, frm, to, cid, cseq, dest=addr, extra=extra)
                    register_ok = True
                    check("Spring Boot 注册成功（收到带认证 REGISTER）", True)

            elif "MESSAGE" in first and register_ok and "Catalog" in msg:
                print(f"  [3] 收到 Catalog Query → 发送 200 OK")
                send_200ok(sock, via, frm, to, cid, cseq, dest=addr)
                catalog_ok = True
                check("收到 Catalog Query MESSAGE", True)

                print(f"  [4] 发送 NOTIFY（{len(DOWNLINK_DEVICES)} 台设备）...")
                time.sleep(0.3)
                sock.sendto(
                    build_catalog_notify(DOWNLINK_DEVICES, CLIENT_SIP_ID, CLIENT_DOMAIN, python_port),
                    addr
                )
                try:
                    ack_data, _ = sock.recvfrom(8192)
                    ack_first = ack_data.decode(errors="replace").splitlines()[0]
                    check("NOTIFY 收到 200 OK", "200" in ack_first, ack_first)
                except socket.timeout:
                    check("NOTIFY 收到 200 OK", False, "超时")
                break

            elif "MESSAGE" in first:
                send_200ok(sock, via, frm, to, cid, cseq, dest=addr)

        except socket.timeout:
            pass

    if not register_ok:
        check("Spring Boot 注册成功", False, "超时未收到 REGISTER")
    if not catalog_ok:
        check("收到 Catalog Query MESSAGE", False, "超时" if register_ok else "注册未成功")

    sock.close()

    if catalog_ok:
        print(f"  [5] 验证 GET /api/devices/remote ...")
        time.sleep(1)
        status, devices = rest_get("/api/devices/remote")
        if status == 200 and isinstance(devices, list):
            synced = [d for d in devices if d.get("deviceId") in {dev["id"] for dev in DOWNLINK_DEVICES}]
            check(f"下联外域设备已同步（≥{len(DOWNLINK_DEVICES)} 台）",
                  len(synced) >= len(DOWNLINK_DEVICES),
                  f"已同步: {[d['name'] for d in synced]}")
        else:
            check("GET /api/devices/remote 可达", False, f"status={status}")

    return results


# ── 套件 6：9.14 图像抓拍 + 9.13 软件升级 ────────────────────────────────────

def suite_snapshot_upgrade():
    """
    验证图像抓拍和软件升级功能：
    - REST 接口：本端/外域设备的 snapshot/upgrade 端点
    - SIP 南向路由：SnapShotConfig/DeviceUpgrade 入站命令路由（200 OK）
    """
    print("\n── 套件 6：9.14 图像抓拍 + 9.13 软件升级 ──")
    python_port = 25063
    results = []

    def check(name, ok, detail=""):
        status = "✅ PASS" if ok else "❌ FAIL"
        msg = f"  {status}  {name}"
        if detail:
            msg += f"\n         {detail}"
        print(msg)
        results.append((name, ok))

    remote_id = CFG_REMOTE_ID   # 外域设备（套件3同步后存在）

    # ── Part A：REST 接口行为 ──

    # 外域设备 snapshot（若设备存在，返回 200 sent:true；若不存在，404）
    snapshot_body = {"snapNum": 3, "interval": 2, "uploadAddr": "http://192.168.1.100:8080/upload", "resolution": "1920*1080"}
    status, body = rest_post(f"/api/devices/remote/{remote_id}/snapshot", snapshot_body)
    if status == 404:
        check("POST /remote/{id}/snapshot（设备不存在 → 404 符合预期）", True, str(body))
    elif status == 200:
        check("POST /remote/{id}/snapshot → sent:true", body.get("sent") is True, str(body))
    else:
        check("POST /remote/{id}/snapshot 可达", False, f"status={status} body={body}")

    # 外域设备 upgrade（若设备存在，返回 200 sent:true；若不存在，404）
    upgrade_body = {"firmwareId": "FW-2024-001", "firmwareAddr": "ftp://192.168.1.100/firmware/v2.0.bin"}
    status, body = rest_post(f"/api/devices/remote/{remote_id}/upgrade", upgrade_body)
    if status == 404:
        check("POST /remote/{id}/upgrade（设备不存在 → 404 符合预期）", True, str(body))
    elif status == 200:
        check("POST /remote/{id}/upgrade → sent:true", body.get("sent") is True, str(body))
    else:
        check("POST /remote/{id}/upgrade 可达", False, f"status={status} body={body}")

    # 本端设备 snapshot（设备不存在 → 404；本端无 IVS1900 配置 → 也可能 404/500）
    status, body = rest_post("/api/devices/local/34010000001310000001/snapshot", snapshot_body)
    check("POST /local/{不存在gbDeviceId}/snapshot → 404", status == 404, f"status={status} body={body}")

    # 本端设备 upgrade（设备不存在 → 404）
    status, body = rest_post("/api/devices/local/34010000001310000001/upgrade", upgrade_body)
    check("POST /local/{不存在gbDeviceId}/upgrade → 404", status == 404, f"status={status} body={body}")

    # ── Part B：SIP 南向路由 ──
    # Python 模拟上级平台发送 SnapShotConfig/DeviceUpgrade MESSAGE，验证 Spring Boot 回 200 OK 并正确路由

    def make_control_message(cmd_type, device_id, extra_xml, sn=None):
        sn = sn or (int(time.time()) % 100000)
        body = (
            '<?xml version="1.0" encoding="GB2312"?>\n'
            "<Control>\n"
            f"<CmdType>{cmd_type}</CmdType>\n"
            f"<SN>{sn}</SN>\n"
            f"<DeviceID>{device_id}</DeviceID>\n"
            f"{extra_xml}"
            "</Control>"
        )
        body_bytes = body.encode("utf-8")
        branch  = f"z9hG4bK{uuid.uuid4().hex[:10]}"
        tag     = uuid.uuid4().hex[:8]
        call_id = uuid.uuid4().hex
        msg = (
            f"MESSAGE sip:{device_id}@{SERVER_IP}:{SERVER_PORT} SIP/2.0\r\n"
            f"Via: SIP/2.0/UDP {PYTHON_IP}:{python_port};branch={branch}\r\n"
            f"From: <sip:{CLIENT_SIP_ID}@{PYTHON_IP}:{python_port}>;tag={tag}\r\n"
            f"To: <sip:{device_id}@{SERVER_IP}:{SERVER_PORT}>\r\n"
            f"Call-ID: {call_id}\r\n"
            f"CSeq: 1 MESSAGE\r\n"
            f"Max-Forwards: 70\r\n"
            f"Content-Type: Application/MANSCDP+xml\r\n"
            f"Content-Length: {len(body_bytes)}\r\n\r\n"
        ).encode() + body_bytes
        return msg, call_id

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((PYTHON_IP, python_port))

    def send_and_expect_200(name, cmd_type, device_id, extra_xml):
        msg, call_id = make_control_message(cmd_type, device_id, extra_xml)
        sock.sendto(msg, (SERVER_IP, SERVER_PORT))
        resp, _ = recv_until(sock, 5)
        if resp is None:
            check(f"SIP {name} → 200 OK", False, "无响应")
            return
        first = resp.splitlines()[0]
        ok = "200" in first
        check(f"SIP {name} → 200 OK", ok, first if not ok else "")

    # SnapShotConfig 指向已知本端相机 ID（套件2中的 CFG_DEVICE_ID）
    send_and_expect_200(
        "SnapShotConfig/本端相机",
        "SnapShotConfig", CFG_DEVICE_ID,
        "<SnapNum>1</SnapNum>\n<Interval>0</Interval>\n<UploadAddr>http://127.0.0.1/snap</UploadAddr>\n"
    )
    # SnapShotConfig 指向未知设备 → 200 OK（SIP MESSAGE 命令类请求先回执后路由，与 GuardCmd/DeviceControl 一致）
    send_and_expect_200(
        "SnapShotConfig/未知设备",
        "SnapShotConfig", CFG_UNKNOWN_ID,
        "<SnapNum>1</SnapNum>\n<Interval>0</Interval>\n<UploadAddr>http://127.0.0.1/snap</UploadAddr>\n"
    )

    # DeviceUpgrade 指向已知本端相机
    send_and_expect_200(
        "DeviceUpgrade/本端相机",
        "DeviceUpgrade", CFG_DEVICE_ID,
        "<FirmwareID>FW-001</FirmwareID>\n<FirmwareAddr>ftp://192.168.1.1/fw.bin</FirmwareAddr>\n"
    )
    # DeviceUpgrade 指向未知设备 → 200 OK（命令类请求先回执后路由，未知设备记入日志）
    send_and_expect_200(
        "DeviceUpgrade/未知设备",
        "DeviceUpgrade", CFG_UNKNOWN_ID,
        "<FirmwareID>FW-001</FirmwareID>\n<FirmwareAddr>ftp://192.168.1.1/fw.bin</FirmwareAddr>\n"
    )

    sock.close()
    return results


# ── 套件 7：9.8 历史回放接口 ──────────────────────────────────────────────────

def suite_playback():
    """
    验证历史回放接口的 REST 行为：
    - stop 接口幂等（无活跃会话 → 200）
    - control 接口 404（无活跃会话）
    - start 接口参数传递（设备不存在 → 404；设备存在但无响应 → 504）
    - type 路径参数：local/remote 均支持
    注意：不发起真实 SIP INVITE（需 ZLM + 设备），仅验证接口路由和错误码。
    """
    print("\n── 套件 7：9.8 历史回放接口（REST 参数校验 + 幂等性）──")
    results = []
    remote_id = CFG_REMOTE_ID
    nonexist_local = "34010000001310000001"
    nonexist_remote = "34010000001320099999"
    playback_body = {"startTime": "2024-01-01T00:00:00", "endTime": "2024-01-01T01:00:00"}
    control_pause = {"action": "pause"}

    def check(name, ok, detail=""):
        status = "✅ PASS" if ok else "❌ FAIL"
        msg = f"  {status}  {name}"
        if detail:
            msg += f"\n         {detail}"
        print(msg)
        results.append((name, ok))

    # ── stop 幂等性（无活跃会话 → 200）
    for typ, dev in [("remote", nonexist_remote), ("local", nonexist_local)]:
        status, body = rest_post(f"/api/devices/{typ}/{dev}/playback/stop")
        check(f"POST /{typ}/{dev}/playback/stop（无会话 → 200 幂等）",
              status == 200, f"status={status} body={body}")

    # ── control 无会话 → 404
    for typ, dev in [("remote", nonexist_remote), ("local", nonexist_local)]:
        status, body = rest_post(f"/api/devices/{typ}/{dev}/playback/control", control_pause)
        check(f"POST /{typ}/{dev}/playback/control（无会话 → 404）",
              status == 404, f"status={status} body={body}")

    # ── start 设备不存在 → 404
    status, body = rest_post(f"/api/devices/remote/{nonexist_remote}/playback/start", playback_body)
    check(f"POST /remote/{{不存在}}/playback/start → 404",
          status == 404, f"status={status} body={body}")

    status, body = rest_post(f"/api/devices/local/{nonexist_local}/playback/start", playback_body)
    check(f"POST /local/{{不存在}}/playback/start → 404",
          status == 404, f"status={status} body={body}")

    # ── start 设备存在但无 SIP 响应 → 504（或 404 若设备不在库）
    status, body = rest_post(f"/api/devices/remote/{remote_id}/playback/start", playback_body)
    if status == 404:
        check(f"POST /remote/{remote_id}/playback/start（设备不存在 → 404 符合预期）", True, str(body))
    elif status == 504:
        check(f"POST /remote/{remote_id}/playback/start（设备无响应 → 504 符合预期）", True)
    elif status == 200 and "streamUrl" in body:
        check(f"POST /remote/{remote_id}/playback/start → streamUrl 返回", True, body.get("streamUrl"))
        # 清理
        rest_post(f"/api/devices/remote/{remote_id}/playback/stop")
    else:
        check(f"POST /remote/{remote_id}/playback/start 可达", False, f"status={status} body={body}")

    # ── control action 枚举验证（有会话时才有意义，此处只验证 stop 已幂等）
    # 二次 stop 验证幂等
    status, body = rest_post(f"/api/devices/remote/{remote_id}/playback/stop")
    check("二次 stop 仍返回 200（幂等）", status == 200, f"status={status} body={body}")

    return results


# ── 主入口 ────────────────────────────────────────────────────────────────────

SUITE_MAP = {
    1: ("time_sync",         suite_time_sync),
    2: ("config_routing",    suite_config_routing),
    3: ("uplink_catalog",    suite_uplink_catalog),
    4: ("basic_signaling",   suite_basic_signaling),
    5: ("downlink_catalog",  suite_downlink_catalog),
    6: ("snapshot_upgrade",  suite_snapshot_upgrade),
    7: ("playback",          suite_playback),
}


def main():
    parser = argparse.ArgumentParser(
        description="GBT28181 集成测试套件",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="\n".join(
            f"  {n}  {name}" for n, (name, _) in SUITE_MAP.items()
        )
    )
    parser.add_argument(
        "--suite", nargs="+", default=["1", "2", "3", "4", "6", "7"],
        help="运行指定套件编号（1-7），'all' 运行全部，默认 1-4 6 7"
    )
    parser.add_argument(
        "--log", default="logs/gbt28181.log",
        help="日志文件路径（套件2用，默认 logs/gbt28181.log）"
    )
    args = parser.parse_args()

    if "all" in args.suite:
        suites_to_run = sorted(SUITE_MAP.keys())
    else:
        suites_to_run = []
        for s in args.suite:
            try:
                n = int(s)
                if n in SUITE_MAP:
                    suites_to_run.append(n)
                else:
                    print(f"[警告] 套件 {n} 不存在（有效值：1-{len(SUITE_MAP)}）")
            except ValueError:
                print(f"[警告] 无效套件编号: {s}")
        if not suites_to_run:
            parser.print_help()
            sys.exit(1)

    # 检查 REST 服务可达
    try:
        with urlopen(f"{REST_BASE}/api/devices/local", timeout=3):
            pass
    except URLError as e:
        print(f"\n❌  REST 服务不可达（{e}）")
        print(f"请先启动 Spring Boot：mvn spring-boot:run")
        sys.exit(1)

    print("=" * 60)
    print("GBT28181 集成测试")
    print(f"SIP 目标: {SERVER_IP}:{SERVER_PORT}  REST: {REST_BASE}")
    print(f"运行套件: {suites_to_run}")
    print("=" * 60)

    all_results = []
    for n in suites_to_run:
        name, fn = SUITE_MAP[n]
        if n == 2:
            suite_results = fn(log_file=args.log)
        else:
            suite_results = fn()
        # normalize: each item is (test_name, bool)
        for item in suite_results:
            if isinstance(item, tuple) and len(item) == 2:
                all_results.append(item)

    print("\n" + "=" * 60)
    total  = len(all_results)
    passed = sum(1 for _, ok in all_results if ok)
    print(f"总结果：{passed}/{total} 通过")
    if passed < total:
        print("失败项：")
        for name, ok in all_results:
            if not ok:
                print(f"  - {name}")
    print("=" * 60)

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
