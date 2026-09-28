package dx.signer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/28 18:43
 * description: 校验 APK 选择器的历史筛选和最近未加固文件识别。
 */
public final class FileChooserDialogTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("file-chooser-test-");
        Path oldPlain = directory.resolve("未加固_旧版.apk");
        Path newPlain = directory.resolve("新版.apk");
        Path signedOutput = directory.resolve("正式_新版.apk");
        Path protectedApk = directory.resolve("新版_protected.apk");
        Path security360Apk = directory.resolve("新版_360.apk");
        Path jiaguApk = directory.resolve("新版_jiagu.apk");
        Path unsignedApk = directory.resolve("新版_unsign.apk");
        Path dingxiangApk = directory.resolve("dx_unsigned_新版.apk");
        Path bundle = directory.resolve("新版.aab");
        List<Path> files = Arrays.asList(oldPlain, newPlain, signedOutput,
                protectedApk, security360Apk, jiaguApk,
                unsignedApk, dingxiangApk, bundle);
        try {
            for (Path file : files) {
                Files.createFile(file);
            }
            setTime(oldPlain, 1_600_000_000_000L);
            setTime(newPlain, 1_700_000_000_000L);
            setTime(signedOutput, 1_750_000_000_000L);
            setTime(protectedApk, 1_800_000_000_000L);
            setTime(security360Apk, 1_800_000_000_000L);
            setTime(jiaguApk, 1_800_000_000_000L);
            setTime(unsignedApk, 1_800_000_000_000L);
            setTime(dingxiangApk, 1_800_000_000_000L);

            assertTrue(FileChooserDialog.isUnencryptedApk(oldPlain.toFile()));
            assertTrue(FileChooserDialog.isUnencryptedApk(newPlain.toFile()));
            for (Path encrypted : Arrays.asList(protectedApk, security360Apk,
                    jiaguApk, unsignedApk, dingxiangApk, bundle)) {
                assertFalse(FileChooserDialog.isUnencryptedApk(encrypted.toFile()));
            }

            List<File> candidates = Arrays.asList(oldPlain.toFile(), newPlain.toFile(), signedOutput.toFile(),
                    protectedApk.toFile(), security360Apk.toFile(), jiaguApk.toFile(),
                    unsignedApk.toFile(), dingxiangApk.toFile());
            assertEquals(signedOutput.toFile(), FileChooserDialog.findRecentUnencryptedApk(
                    candidates, Collections.emptySet()));

            Set<String> historyPaths = new HashSet<>();
            historyPaths.add(SigningHistoryStore.normalizePathKey(newPlain.toString()));
            historyPaths.add(SigningHistoryStore.normalizePathKey(signedOutput.toString()));
            assertTrue(FileChooserDialog.isInSigningHistoryApk(newPlain.toFile(), historyPaths));
            assertTrue(FileChooserDialog.isInSigningHistoryApk(signedOutput.toFile(), historyPaths));
            assertFalse(FileChooserDialog.isInSigningHistoryApk(bundle.toFile(), historyPaths));
            assertEquals(oldPlain.toFile(), FileChooserDialog.findRecentUnencryptedApk(
                    candidates, historyPaths));
            System.out.println("APK 选择器测试通过：历史路径筛选、未加固命名与最近创建时间");
        } finally {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        }
    }

    private static void setTime(Path file, long millis) throws Exception {
        FileTime time = FileTime.fromMillis(millis);
        Files.getFileAttributeView(file, BasicFileAttributeView.class)
                .setTimes(time, null, time);
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("预期为 true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("预期为 false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("预期 " + expected + "，实际 " + actual);
        }
    }
}
