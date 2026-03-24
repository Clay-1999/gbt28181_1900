# build/bin/

此目录用于存放 EulerOS v2r13 aarch64 平台的 ZLMediaKit 可执行文件。

## 如何获取 MediaServer

在 EulerOS v2r13 aarch64 机器上从源码编译：

```bash
# 安装依赖
yum install -y git cmake gcc gcc-c++ openssl-devel

# 克隆源码
git clone --depth=1 https://github.com/ZLMediaKit/ZLMediaKit.git
cd ZLMediaKit
git submodule update --init

# 编译（静态链接，确保跨系统兼容）
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DENABLE_STATIC=ON
make -j$(nproc)
```

编译完成后，将 `ZLMediaKit/release/linux/Release/MediaServer` 复制到此目录：

```bash
cp ZLMediaKit/release/linux/Release/MediaServer build/bin/MediaServer
chmod +x build/bin/MediaServer
```

## 注意

- `build/bin/MediaServer` 已加入 `.gitignore`，不提交到版本库
- 必须在运行 `build/build-package.sh` 之前放置此文件
