package dx.signer;

import org.json.JSONArray;
import org.json.JSONObject;
import pxb.android.Res_value;
import pxb.android.axml.Axml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 10:00
 * description: 验证配置兼容、项目隔离保存及命令行自动匹配，不使用真实证书或密码。
 */
public final class ProjectConfigStoreTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("project-config-test-");
        try {
            verifyLegacyConfiguration(directory);
            verifyProjectUpdatesAndSelection(directory);
            System.out.println("多项目配置测试通过：旧格式、路径解析、字段保留、项目隔离、自动匹配和歧义拒绝");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                Path[] cleanup = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path path : cleanup) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyLegacyConfiguration(Path directory) throws IOException {
        Path file = directory.resolve("legacy/etc/cfg.properties");
        Files.createDirectories(file.getParent());
        Files.write(file, ("ks=etc/test.jks\nin=apk\nout=output\ncustom-field=keep\n"
                + "ks-key-alias=\\u8bc1\\u4e66\nconfig-read-only=true\n")
                .getBytes(StandardCharsets.UTF_8));
        ProjectConfigStore store = new ProjectConfigStore(file);
        SignerConfigBean project = store.projects().get(0);
        require("legacy".equals(project.getProjectName()), "旧配置应使用项目目录作为项目名称");
        require(directory.resolve("legacy/etc/test.jks").toString().equals(project.getKs()),
                "旧配置路径应基于项目根目录");
        require("证书".equals(project.getKsKeyAlias()), "旧配置 Unicode 转义应正常还原");
        require(project.isReadOnly(), "旧配置的只读状态应保留");
        store.update(project);
        JSONObject saved = readArray(file).getJSONObject(0);
        require("keep".equals(saved.getString("custom-field")), "迁移必须保留未知字段");
        require(saved.has("applicationPackageName"), "保存应包含包名字段");
        require(saved.getBoolean("config-read-only"), "JSON 保存的只读字段应为布尔值");
        require("证书".equals(new ProjectConfigStore(file).projects().get(0).getKsKeyAlias()),
                "再次读取应保留 Unicode 字段");
    }

    private static void verifyProjectUpdatesAndSelection(Path directory) throws Exception {
        Path file = directory.resolve("cfg.properties");
        JSONObject first = project("项目甲", "cn.example.first");
        first.put("extra", new JSONObject().put("nested", true));
        JSONObject second = project("项目乙", "cn.example.second");
        second.put("ks", "relative/second.jks");
        second.put("out", "new-output/");
        writeArray(file, new JSONArray().put(first).put(second));
        ProjectConfigStore store = new ProjectConfigStore(file);
        require(store.matchPackageName("cn.example.first").size() == 1, "包名应精确匹配");
        require(store.matchPackageName("").isEmpty(), "空包名不得自动匹配");
        require(directory.resolve("relative/second.jks").toString()
                .equals(store.findByProjectName("项目乙").getKs()), "JSON 相对路径应基于配置文件目录");
        require(directory.resolve("new-output").equals(
                CommandLine.detectOutDir(store.findByProjectName("项目乙").getOut())),
                "尚未存在的输出目录应保留末尾分隔符语义");
        second.put("external-change", "retained");
        writeArray(file, new JSONArray().put(first).put(second));
        SignerConfigBean selected = store.findByProjectName("项目甲");
        selected.setOut(directory.resolve("updated.apk").toString());
        store.update(selected);
        JSONArray saved = readArray(file);
        require(saved.getJSONObject(0).getJSONObject("extra").getBoolean("nested"),
                "更新不得破坏未知 JSON 对象类型");
        require(second.similar(saved.getJSONObject(1)), "更新不得改动其它项目和外部修改");

        Properties options = CommandLine.parseOptions("sign", "--out", "manual.apk", "--config",
                file.toString(), "--projectName", "项目乙");
        require("manual.apk".equals(options.getProperty("out")), "显式参数必须覆盖项目配置");
        Path apk = directory.resolve("input.apk");
        Axml manifest = new Axml();
        manifest.child(null, "manifest").attr(null, "package", -1, "cn.example.first",
                Res_value.newStringValue("cn.example.first"));
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(apk))) {
            output.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            output.write(manifest.toByteArray());
            output.closeEntry();
        }
        options = CommandLine.parseOptions("sign", "--config", file.toString(), "--in", apk.toString());
        require("项目甲".equals(options.getProperty("projectName")), "命令行应按实际 APK 包名选项目");
        expectFailure(() -> CommandLine.parseOptions("sign", "--config", file.toString()),
                "多项目无选择依据必须拒绝");
        second.put("applicationPackageName", "cn.example.first");
        writeArray(file, new JSONArray().put(first).put(second));
        expectFailure(() -> CommandLine.parseOptions("sign", "--config", file.toString(),
                "--in", apk.toString()), "包名歧义必须拒绝");
        second.put("applicationPackageName", "cn.example.unknown");
        first.put("applicationPackageName", "cn.example.unknown");
        writeArray(file, new JSONArray().put(first).put(second));
        expectFailure(() -> CommandLine.parseOptions("sign", "--config", file.toString(),
                "--in", apk.toString()), "包名未匹配必须拒绝");
        second.put("projectName", first.getString("projectName"));
        writeArray(file, new JSONArray().put(first).put(second));
        expectFailure(() -> new ProjectConfigStore(file), "重复项目名称必须拒绝");
        Files.write(file, "无效配置内容".getBytes(StandardCharsets.UTF_8));
        expectFailure(() -> new ProjectConfigStore(file), "无签名字段的文本必须拒绝");
    }

    private static JSONObject project(String name, String packageName) {
        return new JSONObject().put("projectName", name).put("applicationPackageName", packageName)
                .put("in", "input.apk").put("out", "output.apk").put("ks", "test.jks");
    }

    private static JSONArray readArray(Path file) throws IOException {
        return new JSONArray(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    private static void writeArray(Path file, JSONArray items) throws IOException {
        Files.write(file, items.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(IoAction action, String message) throws IOException {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            require(expected.getMessage() != null, "配置错误应有可读提示");
        }
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
