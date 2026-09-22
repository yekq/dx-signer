# Apk签名和多渠道打包工具

## 编译方法

```bash
./gradlew fatjar
# 输出文件位于 signer/build/libs/dx-signer.jar
```

使用 Java 11 执行 `./gradlew :signer:check :signer:fatJar`，会同时验证多项目配置保存、APK 包名读取和界面项目切换。

## 图形界面

请双击`dx-signer.jar`文件启动，或者使用命令行启动。

```
java -jar dx-signer.jar
```

    您需要Java 8+的运行环境，推荐使用OpenJDK的实现。
    请根据界面提示操作。
    当指定渠道清单时，工具进入多渠道模式， 如果 输出apk/aab 指向一个文件，那么渠道包会保存在同目录下； 如果 输出apk/aab 指向目录，那么渠道包会保存在这个目录下。

### 多项目配置

配置文件仍命名为 `cfg.properties`，内容改为 UTF-8 JSON 数组。默认从 JAR 同目录读取；
传入 `-path <目录>` 时从指定目录读取。为兼容旧工具，当该位置没有配置时才读取 `etc/cfg.properties`。
旧 Properties 格式也可以读取，保存后转换为数组。

```json
[
  {
    "projectName": "示例项目",
    "applicationPackageName": "cn.example.app",
    "config-read-only": false,
    "in": "input",
    "out": "output",
    "in-filename": "",
    "ks": "keys/example.jks",
    "ks-pass": "",
    "key-pass": "",
    "ks-key-alias": "{{auto}}",
    "channel-list": ""
  }
]
```

`projectName` 必须非空且唯一；`applicationPackageName` 使用 APK 的真实包名。
数组中的相对路径以配置文件目录为基准，旧 `etc/cfg.properties` 的相对路径以项目根目录为基准。
保存时只更新当前项目，保留其他项目及扩展字段；只读配置不自动写回。

界面默认选择“自动”，读取所选 APK 内的包名并匹配项目。有多个匹配、无匹配或包名为空时，
需要在下拉框手动选择项目；AAB 使用手动选择。手动指定项目时以该项目的证书配置为准。
选择 APK 后继续原有自动开始签名的流程。

历史记录以右侧独立列表窗口显示，可以通过主界面“签名历史”按钮重新打开。
两扇窗口的位置与尺寸分别保存在 JAR 同目录的 `window-state.json`，
历史数据仍保存在 `signing-history.json`。

```powershell
java -jar signer/build/libs/dx-signer.jar -path D:\CodeWorkSpace\tools
```

## 命令行界面

```bash
java -jar dx-signer.jar sign [--option value]+
```

    您需要Java 8+的运行环境，推荐使用OpenJDK的实现。
    其中第一个参数必须是`sign`用于区分命令行还是图形界面。
    option可以重复出现，后面的值覆盖前面的。


支持的`option`如下

| option       | type   | 必须  | 描述                                                             |
| :----------- | :----- | :---: | ---------------------------------------------------------------- |
| config       | Path   |       | 多项目 JSON 数组配置文件，兼容旧 Properties 格式 |
| projectName  | String |       | 指定项目名称，auto 表示根据输入 APK 包名匹配 |
| in           | Path   |  是   | 输入文件apk、aab                                                 |
| out          | Path   |  是   | 输出文件或文件夹                                                 |
| ks           | Path   |  是   | Keystore位置                                                     |
| ks-pass      | String |       | Keystore密码, 默认android                                        |
| ks-key-alias | String |       | alias，默认第一个                                                |
| key-pass     | String |       | alias密码，默认与ks-pass相同                                     |
| channel-list | Path   |       | 渠道清单，格式见 多渠道                                          |
| in-filename  | String |       | 多渠道模式下，指定输入文件名                                     |

    当指定渠道清单时，工具进入多渠道模式，out参数需要指向一个存在的目录。
    config可以视作option的集合， 其避免命令行过长。

例如：

```bash

# 使用etc/cfg.properties指定的参数进行签名
java -jar dx-signer.apk sign --config etc/cfg.properties

# 指定项目；显式命令行参数覆盖配置，与参数顺序无关
java -jar dx-signer.jar sign --config cfg.properties --projectName 示例项目 --in input.apk --out signed.apk

# 按 APK 包名自动匹配项目
java -jar dx-signer.jar sign --config cfg.properties --projectName auto --in input.apk --out signed.apk

# 使用etc/cfg.properties指定的参数, 但是修改掉apk的输出路径
java -jar dx-signer.apk sign --config etc/cfg.properties --out path/to/other/location.apk

# 不使用config进行签名
java -jar dx-signer.apk sign --in in.apk --out signed.apk --ks keystore.JKS --ks-pass android

# 多渠道
mkdir -p out-apks
java -jar dx-signer.apk sign --config keystore.properties \
    --in in.apk --channel-list channel.txt \
    --out out-apks/

```


## 多渠道

请准备渠道清单文件`channel.txt`， 格式为每一行一个渠道， 例如：

```
0001_my
0003_baidu
0004_huawei
0005_oppo
0006_vivo
0007_360
0008_xiaomi
0009_yingyongbao
0011_lianxiang
0012_meizu
0013_yingyonghui
0014_ali
# 注释行
0015_test  # 注释内容
```

### 读取渠道信息：UMENG_CHANNEL

输出的Apk中将会包含`UMENG_CHANNEL`的`mata-data`

```xml
<application ... >
    <meta-data
        android:name="UMENG_CHANNEL"
        android:value="XXX" />
</application>
```

您可以读取这个字段。

```java
public static String getChannel(Context ctx) {
    String channel = "";
    try {
        ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(),
                PackageManager.GET_META_DATA);
        channel = appInfo.metaData.getString("UMENG_CHANNEL");
    } catch (PackageManager.NameNotFoundException ignore) {
    }
    return channel;
}
```

### 读取渠道信息：Walle

输出的Apk也包含Walle风格的渠道信息

您可以在使用[Walle](https://github.com/Meituan-Dianping/walle)的方式进行读取。


```gradle
implementation 'com.meituan.android.walle:library:1.1.7'
```


```java

String channel = WalleChannelReader.getChannel(this.getApplicationContext());

```

## License

```
dx-signer

Copyright 2022 北京顶象技术有限公司

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
