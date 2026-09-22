package dx.signer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 10:00
 * description: 将工具目录内全部旧项目配置合并为 JSON 数组，保留字段并校验迁移完整性。
 */
public final class MergeProjectConfigs {
    private MergeProjectConfigs() {
    }

    /**
     * 参数依次为工具根目录、已经人工核实的项目包名映射 JSON、目标配置文件。
     * 映射格式为项目名称到包名的对象；未确认的包名留空，禁止根据项目名推断。
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3) {
            throw new IOException("请传入工具根目录、包名映射 JSON 和目标配置路径");
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path destination = Paths.get(arguments[2]).toAbsolutePath().normalize();
        JSONObject packages = new JSONObject(new String(Files.readAllBytes(Paths.get(arguments[1])),
                StandardCharsets.UTF_8));
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(root)) {
            sources = paths.filter(Files::isRegularFile)
                    .filter(path -> "cfg.properties".equals(path.getFileName().toString()))
                    .filter(path -> path.getParent().getFileName().toString().equals("etc"))
                    .filter(path -> !path.equals(destination))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }
        if (sources.isEmpty()) {
            throw new IOException("没有找到可合并的 etc/cfg.properties");
        }
        JSONArray merged = new JSONArray();
        Set<String> names = new HashSet<>();
        List<String> unknownPackages = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();
        for (Path source : sources) {
            Path projectRoot = source.getParent().getParent();
            String fallbackName = projectRoot.equals(root) ? "default"
                    : root.relativize(projectRoot).toString().replace('\\', '/');
            List<SignerConfigBean> projects = new ProjectConfigStore(source).projects();
            for (SignerConfigBean project : projects) {
                Properties original = project.toProperties();
                String name = project.getProjectName();
                if (projectRoot.equals(root) && name.equals(root.getFileName().toString())) {
                    name = fallbackName;
                }
                if (!names.add(name)) {
                    throw new IOException("项目名称重复，请先明确区分：" + name);
                }
                JSONObject item = new JSONObject();
                for (String key : original.stringPropertyNames()) {
                    item.put(key, original.getProperty(key));
                }
                item.put("projectName", name);
                item.put("applicationPackageName", packages.optString(name,
                        packages.optString(fallbackName, project.getApplicationPackageName())));
                item.put("config-read-only", project.isReadOnly());
                verifyOriginalFields(source, item);
                if (item.getString("applicationPackageName").isEmpty()) {
                    unknownPackages.add(name);
                }
                for (String key : new String[]{"ks", "in", "out", "channel-list"}) {
                    String value = item.optString(key, "");
                    if (!value.isEmpty() && !Files.exists(Paths.get(value))) {
                        missingPaths.add(name + ":" + key);
                    }
                }
                merged.put(item);
            }
        }
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination)) {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
            Files.copy(destination, destination.resolveSibling(destination.getFileName() + "." + timestamp + ".bak"));
        }
        Path temporary = Files.createTempFile(destination.getParent(), "cfg-merge-", ".tmp");
        try {
            Files.write(temporary, merged.toString(2).getBytes(StandardCharsets.UTF_8));
            List<SignerConfigBean> verified = new ProjectConfigStore(temporary).projects();
            if (verified.size() != merged.length()) {
                throw new IOException("合并结果的项目数量校验失败");
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        // 仅输出项目和字段名称，不输出密码、密钥内容或整个配置对象。
        System.out.println("配置来源数量：" + sources.size());
        System.out.println("合并项目数量：" + merged.length());
        System.out.println("包名待确认项目：" + unknownPackages);
        System.out.println("原配置中当前不存在的路径字段：" + missingPaths);
    }

    /** 对照原始 Properties 逐字段校验，避免迁移过程中静默遗漏或改写凭证。 */
    private static void verifyOriginalFields(Path source, JSONObject item) throws IOException {
        String content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        if (content.trim().startsWith("[")) {
            return;
        }
        Properties original = new Properties();
        original.load(new StringReader(content));
        for (String key : original.stringPropertyNames()) {
            if ("applicationPackageName".equals(key) && original.getProperty(key).isEmpty()) {
                continue;
            }
            String expected = original.getProperty(key);
            if (!expected.isEmpty() && ("in".equals(key) || "out".equals(key)
                    || "ks".equals(key) || "channel-list".equals(key))) {
                Path path = Paths.get(expected);
                expected = (path.isAbsolute() ? path : source.getParent().getParent().resolve(path))
                        .normalize().toString();
            }
            if (!expected.equals(item.optString(key))) {
                throw new IOException("原配置字段完整性校验失败：" + item.getString("projectName") + ":" + key);
            }
        }
    }
}
