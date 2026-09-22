/**
 * dx-signer
 *
 * Copyright 2022 北京顶象技术有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dx.signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class CommandLine {
    public static void main(String... args) throws IOException {
        System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false");
        System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");

        Logger log = LoggerFactory.getLogger(CommandLine.class);


        Properties p = new Properties();
        try {
            p = parseOptions(args);
            for (String k : new String[]{"in", "out", "ks"}) {
                String v = p.getProperty(k, "");
                if (v == null || v.length() == 0) {
                    throw new RuntimeException("请指定参数" + k);
                }
            }
        } catch (Exception e) {
            log.error("参数解析失败：{}", e.getMessage());
            System.err.println("用法：java -jar dx-signer.jar sign [--option value]*");
            System.err.println("  参数：");
            System.err.println("    --config 配置文件");
            System.err.println("    --projectName 项目名称（auto 表示按 APK 包名匹配）");
            System.err.println("    --in 输入文件apk、aab");
            System.err.println("    --out 输出文件、文件夹");
            System.err.println("    --ks Keystore位置");
            System.err.println("    --ks-pass Keystore密码");
            System.err.println("    --ks-key-alias");
            System.err.println("    --key-pass");
            System.err.println("    --channel-list 渠道清单");
            System.exit(3);
        }
        Path input = Paths.get(p.getProperty("in"));
        Path ks = Paths.get(p.getProperty("ks"));
        String ksPass = p.getProperty("ks-pass", "");

        String ksKeyAlias = p.getProperty("ks-key-alias", "");
        String keyPass = p.getProperty("key-pass", "");
        String applicationPackageName = p.getProperty("applicationPackageName", "");
        if (p.getProperty("channel-list", "").length() > 0) {
            Path out = detectOutDir(p.getProperty("out"));

            int result = SignWorker.signChannelApk(input, p.getProperty("in-filename", ""),
                    out,
                    Paths.get(p.getProperty("channel-list")),
                    ks, ksPass, ksKeyAlias, keyPass, applicationPackageName);

            if (result != 0) {
                log.error("多渠道失败");
                System.exit(2);
            }
        } else {
            Path out = Paths.get(p.getProperty("out"));
            int result = SignWorker.signApk(input, out, ks,
                    ksPass, ksKeyAlias, keyPass, applicationPackageName);

            if (result != 0) {
                log.error("签名失败");
                System.exit(2);
            }
        }
    }

    static Properties load(Path configFile) throws IOException {
        List<SignerConfigBean> projects = new ProjectConfigStore(configFile).projects();
        if (projects.size() != 1) {
            throw new IOException("配置文件必须包含一个项目，多个项目请指定项目名称或输入 APK 自动匹配");
        }
        return projects.get(0).toProperties();
    }

    /** 显式命令行参数始终覆盖项目配置，不受参数排列顺序影响。 */
    static Properties parseOptions(String... args) throws IOException {
        if (args.length < 1 || !"sign".equals(args[0]) || args.length % 2 != 1) {
            throw new IOException("参数必须采用 --名称 值 成对传入");
        }
        Properties overrides = new Properties();
        Path configFile = null;
        for (int index = 1; index < args.length; index += 2) {
            String key = args[index];
            if (!key.startsWith("--") || key.length() <= 2) {
                throw new IOException("参数名称必须以 -- 开头");
            }
            if ("--config".equals(key)) {
                if (configFile != null) {
                    throw new IOException("一次签名只能指定一个配置文件");
                }
                configFile = Paths.get(args[index + 1]);
            } else {
                overrides.setProperty(key.substring(2), args[index + 1]);
            }
        }
        Properties result = new Properties();
        if (configFile != null) {
            ProjectConfigStore store = new ProjectConfigStore(configFile);
            result.putAll(selectProject(store, overrides).toProperties());
        }
        if (overrides.containsKey("in")) {
            result.setProperty("in-filename", "");
        }
        result.putAll(overrides);
        return result;
    }

    private static SignerConfigBean selectProject(ProjectConfigStore store, Properties overrides)
            throws IOException {
        String name = overrides.getProperty("projectName", "").trim();
        if (!name.isEmpty() && !"auto".equalsIgnoreCase(name) && !"自动".equals(name)) {
            SignerConfigBean project = store.findByProjectName(name);
            if (project == null) {
                throw new IOException("未找到指定的项目配置");
            }
            return project;
        }
        List<SignerConfigBean> projects = store.projects();
        // 未显式要求自动匹配时，兼容原来只有一份配置的命令行用法。
        if (name.isEmpty() && projects.size() == 1) {
            return projects.get(0);
        }
        String input = overrides.getProperty("in", "").trim();
        if (input.isEmpty()) {
            throw new IOException("多个项目或自动模式下，请通过 --in 指定 APK，或通过 --projectName 指定项目");
        }
        String packageName = ApkPackageReader.readPackageName(Paths.get(input));
        List<SignerConfigBean> matches = store.matchPackageName(packageName);
        if (matches.isEmpty()) {
            throw new IOException("没有与 APK 包名匹配的项目配置，请指定 --projectName");
        }
        if (matches.size() > 1) {
            throw new IOException("APK 包名匹配了多个项目，请指定 --projectName");
        }
        return matches.get(0);
    }

    static Path detectOutDir(String out) {
        Path path = Paths.get(out);
        return (out.endsWith("/") || out.endsWith("\\") || Files.isDirectory(path))
                ? path : path.toAbsolutePath().getParent();
    }
}
