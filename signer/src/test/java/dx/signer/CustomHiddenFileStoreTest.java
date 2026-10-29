package dx.signer;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/10/11 10:00
 * description: 验证自定义隐藏路径持久化、目录隔离、恢复显示及写入失败的一致性。
 */
public final class CustomHiddenFileStoreTest {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("chooser-hidden-files-test-");
        try {
            Path config = directory.resolve("chooser-hidden-files.json");
            CustomHiddenFileStore store = new CustomHiddenFileStore(config);
            File first = directory.resolve("project-one/client.apk").toFile();
            File second = directory.resolve("project-two/client.apk").toFile();
            assertContains(store, first, false);
            store.unhide(first);
            if (Files.exists(config)) {
                throw new AssertionError("未隐藏任何文件时不应创建配置");
            }

            store.hide(first);
            store.hide(first);
            CustomHiddenFileStore reloaded = new CustomHiddenFileStore(config);
            assertContains(reloaded, first, true);
            assertContains(reloaded, second, false);
            assertContains(reloaded, directory.resolve("project-one/child/../client.apk").toFile(), true);
            JSONObject saved = new JSONObject(new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
            if (saved.getJSONArray("paths").length() != 1
                    || !saved.getJSONArray("paths").getString(0).equals(first.getAbsolutePath())) {
                throw new AssertionError("隐藏路径未按完整路径持久化或重复保存");
            }

            reloaded.hide(second);
            reloaded.unhide(first);
            CustomHiddenFileStore restored = new CustomHiddenFileStore(config);
            assertContains(restored, first, false);
            assertContains(restored, second, true);
            restored.unhide(second);
            assertContains(new CustomHiddenFileStore(config), second, false);

            // Windows 路径比较应兼容大小写和两种目录分隔符。
            if (File.separatorChar == '\\') {
                File windowsPath = new File("C:/Chooser-Test/Client.APK");
                restored.hide(windowsPath);
                assertContains(new CustomHiddenFileStore(config), new File("c:\\chooser-test\\client.apk"), true);
                restored.unhide(new File("c:\\chooser-test\\client.apk"));
            }

            Files.write(config, "{\"paths\":[],\"futureSetting\":{\"value\":123}}"
                    .getBytes(StandardCharsets.UTF_8));
            CustomHiddenFileStore future = new CustomHiddenFileStore(config);
            future.hide(first);
            JSONObject withFuture = new JSONObject(new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
            if (withFuture.getJSONObject("futureSetting").getInt("value") != 123) {
                throw new AssertionError("未知顶层配置字段未保留");
            }

            verifyMalformedConfig(config);
            verifyFailedWrite(directory, first);
            System.out.println("自定义隐藏文件持久化验证通过");
        } finally {
            List<Path> paths;
            try (Stream<Path> entries = Files.walk(directory)) {
                paths = entries.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            }
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyMalformedConfig(Path config) throws IOException {
        for (String text : new String[]{"not-json", "{\"paths\":true}", "{\"paths\":[1]}"}) {
            Files.write(config, text.getBytes(StandardCharsets.UTF_8));
            try {
                new CustomHiddenFileStore(config);
                throw new AssertionError("损坏配置应提示错误");
            } catch (IOException expected) {
                if (!new String(Files.readAllBytes(config), StandardCharsets.UTF_8).equals(text)) {
                    throw new AssertionError("损坏配置不应被覆盖");
                }
            }
        }
    }

    private static void verifyFailedWrite(Path directory, File file) throws IOException {
        Path target = directory.resolve("write-failure/chooser-hidden-files.json");
        CustomHiddenFileStore store = new CustomHiddenFileStore(target);
        Files.createDirectories(target.getParent());
        Files.createDirectory(target);
        Files.write(target.resolve("blocker"), new byte[]{1});
        try {
            store.hide(file);
            throw new AssertionError("写入失败应抛出异常");
        } catch (IOException expected) {
            assertContains(store, file, false);
        }
        Files.delete(target.resolve("blocker"));
        Files.delete(target);
        store.hide(file);
        Files.delete(target);
        Files.createDirectory(target);
        Files.write(target.resolve("blocker"), new byte[]{1});
        try {
            store.unhide(file);
            throw new AssertionError("恢复记录写入失败应抛出异常");
        } catch (IOException expected) {
            assertContains(store, file, true);
        }
    }

    private static void assertContains(CustomHiddenFileStore store, File file, boolean expected) {
        if (store.contains(file) != expected) {
            throw new AssertionError("文件隐藏状态不符：" + file);
        }
    }
}
