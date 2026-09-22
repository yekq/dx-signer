package dx.signer;

import pxb.android.Res_value;
import pxb.android.axml.Axml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 10:00
 * description: 从 APK 二进制清单读取实际应用包名，供签名配置自动匹配使用。
 */
public final class ApkPackageReader {
    private static final String MANIFEST_ENTRY = "AndroidManifest.xml";
    private static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;

    private ApkPackageReader() {
    }

    /**
     * 直接读取 APK 内的清单，不依赖外部 Android SDK，也不推断文件名中的包名。
     */
    public static String readPackageName(Path apkPath) throws IOException {
        if (apkPath == null || !Files.isRegularFile(apkPath)) {
            throw new IOException("请选择存在的 APK 文件");
        }
        try (ZipFile apk = new ZipFile(apkPath.toFile())) {
            ZipEntry entry = apk.getEntry(MANIFEST_ENTRY);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("APK 中缺少 AndroidManifest.xml");
            }
            if (entry.getSize() > MAX_MANIFEST_BYTES) {
                throw new IOException("APK 清单文件过大，无法读取包名");
            }
            byte[] manifestBytes;
            try (InputStream input = apk.getInputStream(entry);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    // 同时限制实际解压大小，避免依赖 ZIP 头中可能不准确的长度。
                    if (output.size() > MAX_MANIFEST_BYTES - count) {
                        throw new IOException("APK 清单文件过大，无法读取包名");
                    }
                    output.write(buffer, 0, count);
                }
                manifestBytes = output.toByteArray();
            }
            Axml.Node manifest = Axml.parse(manifestBytes).findFirst("manifest");
            Axml.Node.Attr packageAttribute = manifest == null ? null : manifest.findFirstAttr("package");
            if (packageAttribute == null || packageAttribute.value == null
                    || packageAttribute.value.type != Res_value.TYPE_STRING
                    || packageAttribute.value.raw == null || packageAttribute.value.raw.trim().isEmpty()) {
                throw new IOException("APK 清单中缺少有效的应用包名");
            }
            return packageAttribute.value.raw.trim();
        } catch (ZipException exception) {
            throw new IOException("无法读取 APK 压缩结构，文件可能已经损坏", exception);
        } catch (RuntimeException exception) {
            // 二进制解析库会抛出非受检异常，统一交给界面按读取失败处理。
            throw new IOException("无法解析 APK 清单中的应用包名", exception);
        }
    }
}
