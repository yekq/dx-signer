package dx.signer;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.UIManager;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/28 18:42
 * description: 验证签名类型识别、项目名称持久化和旧版历史兼容性。
 */
public final class SigningHistoryStoreTest {
    public static void main(String[] args) throws IOException {
        assertEquals("360", SigningHistoryStore.signingTypeForFileName("client_jiagu_20260928.apk"));
        assertEquals("360", SigningHistoryStore.signingTypeForFileName("client_360.apk"));
        assertEquals("顶象", SigningHistoryStore.signingTypeForFileName("dx_unsigned_client.apk"));
        assertEquals("爱加密", SigningHistoryStore.signingTypeForFileName("client_unsign.APK"));
        assertEquals("梆梆", SigningHistoryStore.signingTypeForFileName("client_protected.apk"));
        assertEquals("梆梆", SigningHistoryStore.signingTypeForFileName(
                "未加固_V1.0_protected.8_Demo试用包V2_0409.apk"));
        assertEquals("未识别", SigningHistoryStore.signingTypeForFileName("client.apk"));

        Path directory = Files.createTempDirectory("signing-history-test-");
        try {
            Path historyPath = directory.resolve("signing-history.json");
            SigningHistoryStore store = new SigningHistoryStore(historyPath);
            Path input = directory.resolve("client_protected.apk");
            Path output = directory.resolve("client_signed.apk");
            store.add(input, true, "测试项目", Collections.singletonList(output));
            SigningHistoryStore reloaded = new SigningHistoryStore(historyPath);
            assertEquals("测试项目", reloaded.records().get(0).projectName());
            assertEquals("梆梆", SigningHistoryStore.signingTypeForFileName(
                    reloaded.records().get(0).fileName()));
            if (!reloaded.records().get(0).successful()) {
                throw new AssertionError("成功状态未保存");
            }
            assertEquals(output.toAbsolutePath().normalize().toString(),
                    reloaded.records().get(0).outputPaths().get(0));
            assertContains(reloaded.successfulPathKeys(), input, true);
            assertContains(reloaded.successfulPathKeys(), output, true);
            assertContains(reloaded.historyApkPathKeys(), input, true);
            assertContains(reloaded.historyApkPathKeys(), output, true);

            Path failedOutput = directory.resolve("failed_signed.apk");
            Path failedInput = directory.resolve("gd.apk");
            store.add(failedInput, false, "gd",
                    Collections.singletonList(failedOutput));
            assertContains(store.successfulPathKeys(), failedOutput, false);
            assertContains(store.successfulPathKeys(), failedInput, false);
            assertContains(store.historyApkPathKeys(), failedOutput, true);
            assertContains(store.historyApkPathKeys(), failedInput, true);
            if (new SigningHistoryStore(historyPath).records().get(0).outputPaths().size() != 1) {
                throw new AssertionError("失败时已生成的输出路径未保存");
            }
            store.add(directory.resolve("gd-next.apk"), false, "gd-next");
            Path channelOne = directory.resolve("sc-cmcc.apk");
            Path channelTwo = directory.resolve("sc-cu.apk");
            store.add(directory.resolve("sc.apk"), true, "sc",
                    Arrays.asList(channelOne, channelTwo, channelOne,
                            directory.resolve("readme.txt")));
            SigningHistoryStore channels = new SigningHistoryStore(historyPath);
            if (channels.records().get(0).outputPaths().size() != 3) {
                throw new AssertionError("多渠道输出路径未持久化或去重失败");
            }
            assertContains(channels.successfulPathKeys(), channelOne, true);
            assertContains(channels.successfulPathKeys(), channelTwo, true);
            assertContains(channels.successfulPathKeys(), directory.resolve("readme.txt"), false);
            channels.add(directory.resolve("bundle.aab"), true, "sc",
                    Collections.emptyList());
            assertContains(channels.successfulPathKeys(), directory.resolve("bundle.aab"), false);
            assertContains(channels.historyApkPathKeys(), directory.resolve("bundle.aab"), false);
            List<SigningHistoryStore.Record> records = store.records();
            Map<String, Color> automatic = SigningHistoryWindow.createProjectColors(records, false);
            if (automatic.size() != 4 || new HashSet<>(automatic.values()).size() != 4) {
                throw new AssertionError("不同项目未分配唯一颜色");
            }
            Object previous = UIManager.get("Signer.projectColors");
            try {
                UIManager.put("Signer.projectColors", Collections.singletonMap("gd", Color.RED));
                Map<String, Color> overridden = SigningHistoryWindow.createProjectColors(records, false);
                if (!Color.RED.equals(overridden.get("gd"))
                        || new HashSet<>(overridden.values()).size() != 4) {
                    throw new AssertionError("自定义项目颜色未生效或自动颜色重复");
                }
            } finally {
                UIManager.put("Signer.projectColors", previous);
            }

            Files.write(historyPath, ("{\"records\":[{\"path\":\"C:/old.apk\","
                    + "\"fileName\":\"old_unsign.apk\",\"timestamp\":1,\"successful\":true}]}")
                    .getBytes(StandardCharsets.UTF_8));
            SigningHistoryStore legacy = new SigningHistoryStore(historyPath);
            assertEquals("", legacy.records().get(0).projectName());
            if (!legacy.records().get(0).outputPaths().isEmpty()) {
                throw new AssertionError("旧记录不应推断输出路径");
            }
            assertContains(legacy.successfulPathKeys(), java.nio.file.Paths.get("C:/old.apk"), true);
            assertContains(legacy.historyApkPathKeys(), java.nio.file.Paths.get("C:/old.apk"), true);
            assertEquals("爱加密", SigningHistoryStore.signingTypeForFileName(
                    legacy.records().get(0).fileName()));
        } finally {
            Files.deleteIfExists(directory.resolve("signing-history.json"));
            Files.delete(directory);
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("预期 " + expected + "，实际 " + actual);
        }
    }

    private static void assertContains(java.util.Set<String> paths, Path path, boolean expected) {
        boolean actual = paths.contains(SigningHistoryStore.normalizePathKey(path.toString()));
        if (actual != expected) {
            throw new AssertionError("路径筛选状态不符：" + path);
        }
    }
}
