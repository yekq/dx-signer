package dx.signer;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dx.channel.ApkSigns;
import dx.channel.SigningOptions;
import pxb.android.Res_value;
import pxb.android.axml.Axml;
import pxb.android.axml.NodeVisitor;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/30 10:54
 * description: 使用临时证书验证签名产物、V1/V2 兼容性和多渠道 V4 伴随文件。
 */
public final class SigningSchemesTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("signing-schemes-");
        try {
            verifyConfiguration(directory);
            verifyInvalidOptions();
            Path keyStore = directory.resolve("test.p12");
            createKey(keyStore);
            KeyStore.PrivateKeyEntry key = ApkSigns.loadKey(keyStore, "testpass", "test", "testpass");
            Path apk = directory.resolve("input.apk");
            createApk(apk);
            verifyLegacyCompatibility(directory, apk, keyStore, key);
            verifySchemes(directory, apk, keyStore);
            verifyChannels(directory, apk, keyStore);
            System.out.println("签名方案测试通过：V1/V2 对照、V3/V4 实际验签、多渠道与配置保存");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.nio.file.FileSystemException ignored) {
                        // Windows 可能暂时保留验签文件句柄，不影响测试结果。
                    }
                }
            }
        }
    }

    private static void verifyConfiguration(Path directory) throws Exception {
        SigningOptions defaults = new SignerConfigBean(new Properties()).getSigningOptions();
        require(defaults.isV1Enabled() && defaults.isV2Enabled()
                && !defaults.isV3Enabled() && !defaults.isV4Enabled(), "旧配置必须保留 V1/V2 默认值");
        Path config = directory.resolve("cfg.properties");
        Files.write(config, new JSONArray().put(new JSONObject().put("projectName", "甲"))
                .put(new JSONObject().put("projectName", "乙"))
                .toString().getBytes(StandardCharsets.UTF_8));
        ProjectConfigStore store = new ProjectConfigStore(config);
        SignerConfigBean project = store.findByProjectName("甲");
        project.setSigningOptions(new SigningOptions(true, true, true, true));
        store.update(project);
        store = new ProjectConfigStore(config);
        require(store.findByProjectName("甲").getSigningOptions().isV4Enabled(), "V4 应保存到当前项目");
        require(!store.findByProjectName("乙").getSigningOptions().isV3Enabled(), "不能修改其他项目");
        Properties parsed = CommandLine.parseOptions("sign", "--config", config.toString(),
                "--projectName", "甲", "--v3-signing-enabled", "false", "--v4-signing-enabled", "false");
        SigningOptions override = SigningOptions.fromProperties(parsed);
        require(!override.isV3Enabled() && !override.isV4Enabled(), "命令行必须覆盖项目方案");
        project.setSigningOptions(SigningOptions.DEFAULT);
        store.update(project);
        require(!new ProjectConfigStore(config).findByProjectName("甲").getSigningOptions().isV4Enabled(),
                "已有签名方案字段必须能够被更新");
    }

    private static void verifyInvalidOptions() {
        expectInvalid(new SigningOptions(false, false, false, false), false, false);
        expectInvalid(new SigningOptions(true, false, false, true), false, false);
        expectInvalid(new SigningOptions(true, false, true, true), false, true);
        expectInvalid(new SigningOptions(true, true, true, false), true, false);
        expectInvalid(new SigningOptions(true, true, false, true), true, false);
        SigningOptions.DEFAULT.validate(true, false);
    }

    private static void expectInvalid(SigningOptions options, boolean aab, boolean channels) {
        try {
            options.validate(aab, channels);
            throw new AssertionError("无效方案未被拒绝");
        } catch (IllegalArgumentException expected) {
            require(!expected.getMessage().isEmpty(), "校验需要中文说明");
        }
    }

    private static void createKey(Path keyStore) throws Exception {
        Path keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool.exe");
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "test",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=SigningTest",
                "-storetype", "PKCS12", "-keystore", keyStore.toString(),
                "-storepass", "testpass", "-keypass", "testpass", "-noprompt")
                .redirectErrorStream(true).start();
        byte[] output;
        try (InputStream input = process.getInputStream()) {
            output = input.readAllBytes();
        }
        require(process.waitFor() == 0, "创建临时证书失败：" + new String(output, StandardCharsets.UTF_8));
    }

    private static void createApk(Path apk) throws Exception {
        Axml axml = new Axml();
        NodeVisitor manifest = axml.child(null, "manifest");
        manifest.attr(null, "package", -1, "cn.test.signing", Res_value.newStringValue("cn.test.signing"));
        NodeVisitor sdk = manifest.child(null, "uses-sdk");
        sdk.attr("http://schemas.android.com/apk/res/android", "minSdkVersion", 0x0101020c,
                "21", Res_value.newDecInt(21));
        sdk.attr("http://schemas.android.com/apk/res/android", "targetSdkVersion", 0x01010270,
                "28", Res_value.newDecInt(28));
        manifest.child(null, "application");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write(axml.toByteArray());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/test.txt"));
            zip.write("签名回归测试".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void verifyLegacyCompatibility(Path directory, Path apk, Path keyStore,
                                                  KeyStore.PrivateKeyEntry key) throws Exception {
        Path aligned = directory.resolve("aligned.apk");
        ApkSigns.zipAlign(apk, aligned, false);
        Path baseline = directory.resolve("baseline.apk");
        Path actual = directory.resolve("legacy.apk");
        legacySign(aligned, baseline, key, false);
        require(SignWorker.signApk(apk, actual, keyStore, "testpass", "test", "testpass") == 0,
                "旧版签名入口失败");
        require(Arrays.equals(Files.readAllBytes(baseline), Files.readAllBytes(actual)),
                "旧入口 V1/V2 产物必须与修改前参数产生的文件逐字节一致");
        verify(actual, true, true, false, false, 21);
        require(!Files.exists(ApkSigns.v4SignaturePath(actual)), "旧入口不能生成 idsig");
        Path aabBaseline = directory.resolve("baseline.aab");
        Path aabActual = directory.resolve("legacy.aab");
        legacySign(aligned, aabBaseline, key, true);
        ApkSigns.sign(aligned, aabActual, key, true);
        require(Arrays.equals(Files.readAllBytes(aabBaseline), Files.readAllBytes(aabActual)),
                "AAB 的原签名参数及输出必须保持一致");
        try (JarFile jar = new JarFile(aabActual.toFile(), true)) {
            java.util.jar.JarEntry entry = jar.getJarEntry("assets/test.txt");
            try (InputStream input = jar.getInputStream(entry)) {
                input.readAllBytes();
            }
            require(entry.getCertificates() != null, "AAB JAR 签名必须有效");
        }
    }

    /**
     * 固定修改前的构建参数，作为旧 V1/V2 行为的独立对照。
     */
    private static void legacySign(Path input, Path output, KeyStore.PrivateKeyEntry key, boolean aab)
            throws Exception {
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("cert", key.getPrivateKey(),
                Collections.singletonList((X509Certificate) key.getCertificate())).build();
        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(config))
                .setOtherSignersSignaturesPreserved(false).setV3SigningEnabled(false)
                .setInputApk(input.toFile()).setOutputApk(output.toFile())
                .setV1SigningEnabled(true).setV2SigningEnabled(true);
        if (aab) {
            builder.setMinSdkVersion(26);
        }
        builder.build().sign();
    }

    private static void verifySchemes(Path directory, Path apk, Path keyStore) throws Exception {
        SigningOptions[] schemes = {
                new SigningOptions(true, true, true, true),
                new SigningOptions(true, true, false, true),
                new SigningOptions(false, false, true, true),
                new SigningOptions(true, false, false, false),
                new SigningOptions(false, true, false, false)
        };
        for (int index = 0; index < schemes.length; index++) {
            SigningOptions scheme = schemes[index];
            Path output = directory.resolve("scheme-" + index + ".apk");
            require(SignWorker.signApk(apk, output, keyStore, "testpass", "test", "testpass", "", scheme) == 0,
                    "签名方案执行失败：" + index);
            verify(output, scheme.isV1Enabled(), scheme.isV2Enabled(), scheme.isV3Enabled(),
                    scheme.isV4Enabled(), scheme.isV1Enabled() ? 21 : scheme.isV2Enabled() ? 24 : 28);
        }
        Path output = directory.resolve("scheme-0.apk");
        require(SignWorker.signApk(apk, output, keyStore, "testpass", "test", "testpass") == 0,
                "关闭 V4 后重签失败");
        require(!Files.exists(ApkSigns.v4SignaturePath(output)), "关闭 V4 重签后必须移除过期 idsig");
    }

    private static void verifyChannels(Path directory, Path apk, Path keyStore) throws Exception {
        Path list = directory.resolve("channels.txt");
        Files.write(list, Arrays.asList("test-one", "test-two"), StandardCharsets.UTF_8);
        Path legacyDir = directory.resolve("legacy-channels");
        require(SignWorker.signChannelApk(apk, "client.apk", legacyDir, list,
                keyStore, "testpass", "test", "testpass") == 0, "旧多渠道入口失败");
        verify(legacyDir.resolve("SIGNED_client-test-one.apk"), true, true, false, false, 21);
        Path outputDir = directory.resolve("channels");
        List<Path> generated = new ArrayList<>();
        require(SignWorker.signChannelApk(apk, "client.apk", outputDir, list,
                keyStore, "testpass", "test", "testpass", "",
                new SigningOptions(true, true, true, true), generated::add) == 0, "V4 多渠道失败");
        require(generated.size() == 4, "两个渠道应回调两个 APK 和两个 idsig");
        for (Path output : generated) {
            if (output.toString().endsWith(".apk")) {
                verify(output, true, true, true, true, 21);
            }
        }
    }

    private static void verify(Path apk, boolean v1, boolean v2, boolean v3, boolean v4, int minimum)
            throws Exception {
        ApkVerifier.Builder builder = new ApkVerifier.Builder(apk.toFile()).setMinCheckedPlatformVersion(minimum);
        if (v4) {
            require(Files.size(ApkSigns.v4SignaturePath(apk)) > 0, "V4 文件不能为空");
            builder.setV4SignatureFile(ApkSigns.v4SignaturePath(apk).toFile());
        }
        ApkVerifier.Result result = builder.build().verify();
        require(result.isVerified(), "APK 验签失败：" + result.getAllErrors());
        require(result.isVerifiedUsingV1Scheme() == v1 && result.isVerifiedUsingV2Scheme() == v2
                        && result.isVerifiedUsingV3Scheme() == v3 && result.isVerifiedUsingV4Scheme() == v4,
                "产物中的签名方案与选择项不一致：" + apk.getFileName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
