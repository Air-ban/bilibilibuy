# bilibilibuy Android

一个用于配合 bilibili 购票 API 服务使用的 Android 客户端。应用负责登录账号、选择演出和票档、生成配置文件、启动托管抢票任务，并在抢到票后展示支付二维码。

## 功能

- API 服务器地址配置和连接测试
- bilibili 账号二维码登录
- 登录轮询成功后自动收起登录二维码
- 根据演出 ID/链接加载场次、票档、购票人和地址
- 生成并保存抢票配置文件
- 抢票页自动拉取已有配置文件并启动托管抢票任务
- 查询和取消托管任务
- 抢到票后提示用户尽快支付
- 自动识别支付 URL 并渲染支付二维码

## 前置条件

- Android Studio
- JDK 11 或更高版本
- 可用的 bilibili 购票 API 服务
- Android 设备或模拟器

应用默认 API 地址为：

```text
http://10.0.2.2:8000
```

这个地址适用于 Android 模拟器访问宿主机本地 `8000` 端口。如果使用真机，请在设置页改成后端所在电脑的局域网地址，例如：

```text
http://192.168.1.100:8000
```

## 使用流程

1. 打开应用，进入“设置”页。
2. 使用默认服务器或填写自定义 API 地址，点击测试连接。
3. 进入“登录”页，生成二维码。
4. 使用 bilibili App 扫码登录，或打开二维码 URL 到浏览器登录。
5. 登录成功后，应用会收起二维码并显示 cookies 获取结果。
6. 进入“配置”页，填写演出 ID 或演出链接。
7. 加载演出信息后，选择场次、票档、购票人和地址。
8. 生成配置文件。
9. 进入“抢票”页，应用会自动拉取配置文件并选择已有配置。
10. 启动托管抢票任务。
11. 通过“查询状态”刷新任务状态。
12. 抢到票后，应用会显示支付提示和支付二维码。

## 支付说明

抢到票后，后端返回的支付 URL 会被渲染成二维码，用户可以直接扫码支付。

当前客户端展示的二维码仅用于微信支付。如需使用其他支付渠道，请前往哔哩哔哩会员购处理订单。

## 关键接口

客户端当前依赖以下 API 能力：

- `GET /api/health`
- `GET /api/auth/status`
- `POST /api/auth/qrcode/generate`
- `POST /api/auth/qrcode/poll`
- `POST /api/project/purchase-context`
- `POST /api/config/generate`
- `GET /api/config/list`
- `POST /api/task/start-managed`
- `GET /api/task/managed/{run_id}/status`
- `POST /api/task/managed/{run_id}/cancel`

启动托管抢票时，客户端会传入配置文件所在路径，也就是 `/api/config/list` 返回的 `path` 字段；不是单纯的文件名。

## 构建

在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 项目结构

```text
app/src/main/java/com/hg/bilibilibuy/
  MainActivity.kt     # Compose UI 和页面流程
  BiliApiClient.kt    # API 请求封装
  LocalQrCode.kt      # 本地支付二维码生成
```

## 注意事项

- 后端服务必须允许 Android 设备访问。
- 真机访问电脑本地服务时，需要使用电脑的局域网 IP，不能使用 `127.0.0.1`。
- 应用允许明文 HTTP 请求，便于局域网调试。
- 抢票结果和支付状态以服务端任务状态为准。
- 请确认配置文件路径来自服务端返回的 `path` 字段，否则启动托管任务可能会返回 422。
