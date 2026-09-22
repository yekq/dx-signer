package dx.signer;

import pxb.android.Res_value;
import pxb.android.axml.Axml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 10:00
 * description: 校验 APK 包名读取及损坏清单的错误处理，无需真实签名证书。
 */
public final class ApkPackageReaderTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("apk-package-reader-test-");
        Path valid = directory.resolve("valid.apk");
        Path missing = directory.resolve("missing.apk");
        Path corrupt = directory.resolve("corrupt.apk");
        Path withoutPackage = directory.resolve("no-package.apk");
        try {
            Axml manifest = new Axml();
            manifest.child(null, "manifest").attr(null, "package", -1,
                    "cn.example.actual", Res_value.newStringValue("cn.example.actual"));
            writeApk(valid, "AndroidManifest.xml", manifest.toByteArray());
            if (!"cn.example.actual".equals(ApkPackageReader.readPackageName(valid))) {
                throw new AssertionError("没有读取到清单中的真实包名");
            }
            writeApk(missing, "classes.dex", new byte[]{0});
            writeApk(corrupt, "AndroidManifest.xml", new byte[]{0, 1, 2});
            Axml empty = new Axml();
            empty.child(null, "manifest");
            writeApk(withoutPackage, "AndroidManifest.xml", empty.toByteArray());
            expectFailure(missing);
            expectFailure(corrupt);
            expectFailure(withoutPackage);
            expectFailure(directory.resolve("absent.apk"));
            System.out.println("APK 包名读取测试通过：正常包名、缺少清单、损坏清单、缺少包名及文件不存在");
        } finally {
            Files.deleteIfExists(valid);
            Files.deleteIfExists(missing);
            Files.deleteIfExists(corrupt);
            Files.deleteIfExists(withoutPackage);
            Files.deleteIfExists(directory);
        }
    }

    private static void writeApk(Path file, String entry, byte[] bytes) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry(entry));
            output.write(bytes);
            output.closeEntry();
        }
    }

    private static void expectFailure(Path file) throws IOException {
        try {
            ApkPackageReader.readPackageName(file);
            throw new AssertionError("无效 APK 没有被拒绝");
        } catch (IOException expected) {
            if (expected.getMessage() == null || expected.getMessage().isEmpty()) {
                throw new AssertionError("缺少可展示的错误说明");
            }
        }
    }
}
