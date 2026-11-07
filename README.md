# Steam 界面翻译（Java 版）

在 Steam 客户端里打开商店、社区、创意工坊页面时，**自动把英文内容替换成中文**，不用再复制到浏览器里翻译。翻译后鼠标悬停还能看到英文原文。

原理一句话：Steam 界面本质是内嵌的 Chromium（CEF），它暴露了一个调试端口；本程序通过这个端口往页面里注入一小段 JavaScript，收集英文文本，交给必应翻译，再把译文写回页面。

---

## 一、前置条件（每次重启电脑都要做）

1. **完全退出 Steam**（右键任务栏托盘里的 Steam 图标 → 退出，不是关窗口）
2. 双击 **`启动Steam(调试模式).bat`**，等 Steam 正常启动

> 这一步是让 Steam 打开 8080 调试端口。不做的话程序会提示「连不上调试端口」。

---

## 二、启动翻译（三选一）

### 方式 A：直接用打包好的 exe（推荐，不用装 JDK）

从 **Releases** 下载压缩包，解压后双击 `SteamZh.exe`。自带 Java 运行环境，整个文件夹可以复制到任何地方用。

### 方式 B：静默启动（需要 JDK，无窗口）

双击 **`静默启动.vbs`** —— 直接进系统托盘。

### 方式 C：带控制台（需要 JDK，能看日志）

双击 **`启动翻译.bat`** —— 默认只打印启动信息和错误，很干净。

需要看详细过程（连接、注入、每批翻译）时，用命令行加 `--verbose`：

```bat
"启动翻译.bat" --verbose
```

---

## 从源码构建

环境要求：**JDK 21**（`javac -version` 能显示 21.x）。

```bat
build.bat
```

编译产物在 `out\`，然后双击 `启动翻译.bat` 即可运行。

### 打包成独立 exe（可选）

```bat
"%JAVA_HOME%\bin\jar.exe" --create --file dist\SteamZh.jar --main-class com.steamzh.Main -C out .
"%JAVA_HOME%\bin\jpackage.exe" --type app-image --name SteamZh --input dist --main-jar SteamZh.jar --main-class com.steamzh.Main --dest app --java-options "-Dfile.encoding=UTF-8"
```

产物在 `app\SteamZh\SteamZh.exe`，内含精简 JRE（约 150MB），可脱离 JDK 运行。

---

## 三、使用

启动后系统托盘会出现蓝色「译」图标。右键图标：

| 菜单 | 作用 |
|---|---|
| 暂停翻译 / 恢复翻译 | 临时停掉，页面恢复原样刷新后生效 |
| 设置... | 打开设置窗口 |
| 退出 | 关闭程序 |

### 悬停看原文

页面翻译后，把鼠标停在任意译文上，会弹出一个小黑框显示**英文原文**（跟沉浸式翻译的交互一样）。可以在设置里关掉。

### 设置窗口

- **翻译范围**：商店页面 / 社区页面 / 帮助页面，可单独开关
- **显示**：鼠标悬停时显示英文原文
- **翻译语言**：简体中文 / 繁体中文
- **并发请求数**：2 / 4 / 6 / 8，越大越快，也越容易被必应限流

设置保存在程序目录的 `config.properties`，改完立即生效（并发数下次处理时生效）。

---

## 四、工作原理

```
┌─────────────────┐   HTTP  /json/list      ┌──────────────────┐
│  Java 程序       │ ──────────────────────> │  Steam 调试端口   │
│                 │   WebSocket  CDP        │  (127.0.0.1:8080)│
│  1. 找页面       │ <─────────────────────> │                  │
│  2. 注入 JS      │                         │  内嵌 Chromium    │
│  3. 取英文文本   │                         │  └─ 商店/社区页面  │
│  4. 调必应翻译   │                         └──────────────────┘
│  5. 写回页面     │
└────────┬────────┘
         │ HTTPS
         ▼
   必应翻译接口（免 Key）
```

1. 每 1 秒请求 `http://127.0.0.1:8080/json/list`，找出所有 `steampowered.com` / `steamcommunity.com` 的页面
2. 对每个页面建立 WebSocket（CDP 协议），注入 `PageScript.INIT`
3. 注入的脚本用 `MutationObserver` 盯着 DOM 变化，把「像英文」的文本节点登记进队列
4. Java 端每轮取 20 条，按设置的并发数并行调必应翻译
5. 把译文通过 `window.__stzh.apply()` 写回原文本节点，并给父元素打上 `data-stzh-src` 供悬停显示

### 几个关键设计

| 问题 | 做法 |
|---|---|
| 页面是 HTTPS，不能直接请求本地 HTTP 服务 | 页面不碰网络，全部由 Java 侧轮询取队列 |
| React 重渲染会覆盖译文 | `MutationObserver` 重新捕获，缓存命中后瞬间补回 |
| 必应令牌会过期 | 45 分钟自动刷新，失败时重抓重试 |
| 重复文本浪费请求 | 内存缓存，同一句话只翻一次 |
| 中文判断不能只看「非 ASCII」 | `’` `®` `™` 会误伤，改用中日韩字符范围 |

---

## 五、文件说明

```
SteamZh/
├─ app\SteamZh\SteamZh.exe  打包好的程序（自带 JRE，双击即用）
├─ 静默启动.vbs            从源码启动（无窗口）
├─ 启动翻译.bat            从源码启动（有控制台）
├─ 启动Steam(调试模式).bat  带调试参数启动 Steam
├─ build.bat               编译源码
├─ README.md               本文件
└─ src\com\steamzh\
   ├─ Main.java            入口：主循环
   ├─ Session.java         单个页面的翻译会话
   ├─ CdpClient.java       WebSocket + CDP 协议客户端
   ├─ Target.java          页面目标解析
   ├─ PageScript.java      注入到页面的 JS（含悬停原文）
   ├─ BingTranslator.java  必应翻译客户端（并发、缓存）
   ├─ Config.java          设置读写
   ├─ SettingsDialog.java  设置窗口
   ├─ Tray.java            系统托盘
   ├─ Http.java            HTTP 工具
   └─ JsonUtil.java        极简 JSON 工具
```

---

## 六、常见问题

**Q：托盘没图标，程序好像没起来？**
A：任务管理器里结束 `SteamZh.exe` / `javaw.exe` 再启动。exe 版可以用 `启动翻译.bat` 的日志版排查。

**Q：提示「连不上调试端口 8080」？**
A：Steam 不是用调试模式启动的。完全退出 Steam，用 `启动Steam(调试模式).bat` 重开。

**Q：页面有的词还是英文？**
A：专有名词（`Windows® 10`、`Ubuntu 20.04`、`Steamworks`）必应会原样返回；用户名、URL 也会保留。正常现象。

**Q：翻译很慢？**
A：首次翻译一个页面 1～2 分钟。翻译过的文本会缓存，刷新页面很快。可以在设置里把并发调到 8。

**Q：会被 VAC 封号吗？**
A：程序只读写 Steam 界面的 DOM，不碰游戏进程、不修改游戏文件。但它依赖 Steam 的非官方调试端口，属于非官方用法，**风险自负**。

---

## 七、开发笔记（踩过的坑）

1. **Java 文本块里的 `\u` 会在编译期被当成 Unicode 转义**，哪怕在注释里。JS 正则里的 `\u4e00` 必须改写成 `charCodeAt` 判断。
2. **`www.bing.com` 会 302 到 `cn.bing.com`**，POST 必须发到重定向后的域名，否则返回 200 但 body 为空。
3. **必应请求必须带抓页面时拿到的 Cookie**，否则同样返回空 body。
4. **`indexOf` 找 JSON key 一定要带引号**，否则会匹配到后续对象的同名字段。
5. **`’` `®` `™` 不是中文**，判断「是否已翻译」不能用「非 ASCII」，要用中日韩字符范围。
6. **VBScript 不认 UTF-8 源码**，`.vbs` 里写中文会报「语句未结束」，必须纯 ASCII。
7. **cmd 默认 GBK 代码页**，Java 输出 UTF-8 会乱码，`.bat` 里要先 `chcp 65001`。
8. **jpackage 的 app-image 不能直接用 runtime 里的 java**，它没有独立的 `java.exe`，调试要打 `--win-console` 版本。
---

## 免责声明

本程序只通过 Steam 客户端暴露的调试端口读写界面 DOM，不修改游戏文件、不注入游戏进程、不涉及账号凭证。

但它依赖 Steam 的非官方调试接口，属于非官方用法。使用风险自负，与 Valve 无关。

---

## License

[MIT](LICENSE)