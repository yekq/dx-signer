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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.function.Consumer;

import dx.channel.ApkSigns;
import dx.channel.ChannelBuilder;
import dx.channel.SigningOptions;

public class SignWorker {
    private static final Logger log = LoggerFactory.getLogger(SignWorker.class);

    private static void sign(Path inApk, Path ksPath, String ksPass, String keyAlias, String keyPass,
                             Path outApk, SigningOptions options) throws Throwable {
        KeyStore.PrivateKeyEntry key = ApkSigns.loadKey(ksPath, ksPass, keyAlias, keyPass);
        ApkSigns.sign(inApk, outApk, key, isAab(inApk), options);
    }

    public static int signApk(Path apkUnsigned, Path apkOut, Path ksPath, String ksPass, String keyAlias, String keyPass) {
        return signApk(apkUnsigned, apkOut, ksPath, ksPass, keyAlias, keyPass, "");
    }

    public static int signApk(Path apkUnsigned, Path apkOut, Path ksPath, String ksPass,
                              String keyAlias, String keyPass, String applicationPackageName) {
        return signApk(apkUnsigned, apkOut, ksPath, ksPass, keyAlias, keyPass,
                applicationPackageName, SigningOptions.DEFAULT);
    }

    public static int signApk(Path apkUnsigned, Path apkOut, Path ksPath, String ksPass,
                              String keyAlias, String keyPass, String applicationPackageName,
                              SigningOptions options) {
        options.validate(isAab(apkUnsigned), false);
        Path tmp = null;
        String suffix = isAab(apkUnsigned) ? "aab" : "apk";
        try {
            Path p = apkOut.toAbsolutePath().getParent();
            if (!Files.exists(p)) {
                Files.createDirectories(p);
            }
            tmp = Files.createTempFile(p, "tmpsigner", "." + suffix);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return 2;
        }

        try {
            log.info("{}", "> 签名中, 请稍等 ...");

            log.info("{}", ">> 清理原有签名, 对齐 ...");
            ApkSigns.zipAlign(apkUnsigned, tmp, false);
            log.info("{}", "<< 完成");

            logSigningConfiguration(ksPath, applicationPackageName);
            sign(tmp, ksPath, ksPass, keyAlias, keyPass, apkOut, options);
            log.info("{}", "<< 完成");

            log.info("{}", "< 签名结束, 结果： 完成");
            log.info("  输出APK: {}", apkOut);
            if (options.isV4Enabled()) {
                log.info("  输出 V4 签名: {}", ApkSigns.v4SignaturePath(apkOut));
            }
        } catch (Throwable e) {
            log.info("签名结束, 结果： 失败", e);
            return -1;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignore) {
                }
            }
        }
        return 0;
    }

    public static int signChannelApk(Path input, String inputFileName, Path outDir,
                                     Path channelListFile,
                                     Path ksPath,
                                     String ksPass,
                                     String keyAlias,
                                     String keyPass) throws IOException {
        return signChannelApk(input, inputFileName, outDir, channelListFile,
                ksPath, ksPass, keyAlias, keyPass, "");
    }

    public static int signChannelApk(Path input, String inputFileName, Path outDir,
                                     Path channelListFile, Path ksPath, String ksPass,
                                     String keyAlias, String keyPass, String applicationPackageName) throws IOException {
        return signChannelApk(input, inputFileName, outDir, channelListFile,
                ksPath, ksPass, keyAlias, keyPass, applicationPackageName, null);
    }

    /**
     * 每个渠道 APK 成功生成后，在当前签名线程回调实际输出路径。
     * 后续渠道失败时，之前已经成功生成的 APK 仍会保留对应的回调记录。
     *
     * @param onGenerated 可选的成功输出回调，传入 null 表示无需通知
     */
    public static int signChannelApk(Path input, String inputFileName, Path outDir,
                                     Path channelListFile, Path ksPath, String ksPass,
                                     String keyAlias, String keyPass, String applicationPackageName,
                                     Consumer<Path> onGenerated) throws IOException {
        return signChannelApk(input, inputFileName, outDir, channelListFile,
                ksPath, ksPass, keyAlias, keyPass, applicationPackageName,
                SigningOptions.DEFAULT, onGenerated);
    }

    /**
     * 将方案选项传递到每个渠道，并在 APK 和 V4 伴随文件全部生成后回调。
     */
    public static int signChannelApk(Path input, String inputFileName, Path outDir,
                                     Path channelListFile, Path ksPath, String ksPass,
                                     String keyAlias, String keyPass, String applicationPackageName,
                                     SigningOptions options, Consumer<Path> onGenerated) throws IOException {
        options.validate(isAab(input), true);
        if (inputFileName == null || inputFileName.trim().length() == 0) {
            inputFileName = input.getFileName().toString();
        }
        if (inputFileName.endsWith(".aab")) {
            throw new IllegalArgumentException("多渠道仅支持 APK 文件");
        }

        List<String> channelList = ChannelBuilder.readChannelList(channelListFile);
        log.info("读取到{}个渠道", channelList.size());

        logSigningConfiguration(ksPath, applicationPackageName);
        KeyStore.PrivateKeyEntry key = ApkSigns.loadKey(ksPath, ksPass, keyAlias, keyPass);
        try (ChannelBuilder cb = new ChannelBuilder(input, key)) {
            log.info("已加载模板: {}", input);
            int dot = inputFileName.lastIndexOf('.');
            String apkName = dot > 0 ? inputFileName.substring(0, dot) : inputFileName;
            if (apkName.startsWith("dx_unsigned_")) {
                apkName = apkName.substring("dx_unsigned_".length());
            }
            for (String channel : channelList) {
                String safeName = String.format("SIGNED_%s-%s.apk", apkName, channel)
                        .replace('/', '_')
                        .replace('\\', '_')
                        .replace(' ', '_');
                Path outPath = outDir.resolve(safeName);
                log.info("正在输出渠道: {}", channel);
                try {
                    cb.build(channel, outPath, options);
                    if (onGenerated != null) {
                        onGenerated.accept(outPath);
                        if (options.isV4Enabled()) {
                            onGenerated.accept(ApkSigns.v4SignaturePath(outPath));
                        }
                    }
                    log.info("已经生成: {}", outPath);
                    if (options.isV4Enabled()) {
                        log.info("已经生成 V4 签名: {}", ApkSigns.v4SignaturePath(outPath));
                    }
                }catch (Throwable e) {
                    log.error("多渠道失败", e);
                    return 1;
                }
            }
            log.info("多渠道完成: {}", outDir);
        }

        return 0;
    }

    /** 仅展示证书文件名和配置包名，不输出证书路径及密码。 */
    private static void logSigningConfiguration(Path ksPath, String applicationPackageName) {
        String packageName = applicationPackageName == null ? "" : applicationPackageName.trim();
        log.info(">> 签名 ...  {}{}", ksPath.getFileName(),
                packageName.isEmpty() ? "" : " " + packageName);
    }

    static boolean isAab(Path input) {
        return input.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".aab");
    }
}
