# mirai-mock-framework

适用于 mirai-console 的 mock 框架 (ALPHA)

--------------------------

## 环境准备

1. `git clone` 此仓库
2. 下载 [FontAwesome](https://github.com/FortAwesome/Font-Awesome/releases)
   的最新版本的
   `fontawesome-free-XXX-web.zip`, 并解压放入 `/fontawesome` (需要自行创建)
3. 使用 Intellij IDEA 或其他 IDE 打开此项目, 并将 JDK 设置为 `JDK 11+`

> 在完成环境准备后, 项目文件结构应该是这样的
> 
> ```text
> mirai-mock-framework
>  |- .idea
>  |- fontawesome
>  |  |- css
>  |  |- js
>  |  |- less
>  |  |- ....
>  |  |- LICENSE.txt
>  |- frontend
>  | ....
> ```

## 构建

在 IDEA 中双击 `Ctrl`, 输入以下命令

```text
# 初次构建
gradle :plugin:buildPlugin :pluginwrapper:build

# 更新
gradle :plugin:buildPlugin
```

之后, 你会得到两个文件

```text
/plugin/build/mirai/mirai-mock-framework-console-1.0.0.mirai2.jar
/pluginwrapper/build/libs/mirai-mock-framework-console-1.0.0.jar
```

使用 wrapper 只需要重新构建而不需要再次手动复制插件到 console

两个文件均能直接放入 console 内使用, 但不能同时存在于 `plugins` 内

## 配置

mirai-mock-framework 的数据文件存放于 `/plugindata` (项目目录),
修改 `/plugindata/mmf-config.conf`

## 行为模拟

在 IDEA 内打开 `/mirai-mock-framework-sdk/src/test/kotlin/testboot.kt`

此文件是一个实例, 你可以创建文件夹 `/mirai-mock-framework-sdk/src/test/kotlin/local` 并将此实例复制进去

## 在 mamoe/mirai 项目中测试

此项目在 mirai 仓库中同样可用

在 mirai 项目中执行 `gradle :mirai-core-mock:buildRuntimeClasspath` 即可完成匹配

## 附录

由于 `mirai-mock-framework` 是分离式设计, 在使用此项目中可能存在 4 个窗口或更多,
建议购置比较大的显示屏, 并且链接两个显示屏, 以减少窗口间切换带来的麻烦

> 四个窗口指
> 
> - IDEA (mirai-mock-framework)
> - IDEA (myplugin) / IDEA (mamoe/mirai)
> - cmd.exe - mirai-console
> - Chrome (Mirai Mock WebUI)

推荐: [snapshot1.png](/imgs/snapshot1.png)
