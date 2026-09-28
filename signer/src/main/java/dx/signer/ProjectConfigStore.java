package dx.signer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 10:00
 * description: 读取多项目签名配置，兼容旧配置并保留未知字段。
 */
final class ProjectConfigStore {
    private static final Set<String> EDITABLE_KEYS = new HashSet<>(Arrays.asList(
            "projectName", "applicationPackageName", "config-read-only", "in", "out",
            "in-filename", "ks", "ks-pass", "key-pass", "ks-key-alias", "channel-list"));
    private static final String[] PATH_KEYS = {"in", "out", "ks", "channel-list"};

    private final Path configFile;
    private List<JSONObject> items;

    ProjectConfigStore(Path configFile) throws IOException {
        this.configFile = configFile.toAbsolutePath().normalize();
        items = readItems();
    }

    Path getConfigFile() {
        return configFile;
    }

    synchronized List<SignerConfigBean> projects() {
        List<SignerConfigBean> result = new ArrayList<>();
        for (JSONObject item : items) {
            result.add(toBean(item));
        }
        return Collections.unmodifiableList(result);
    }

    synchronized SignerConfigBean findByProjectName(String projectName) {
        for (SignerConfigBean project : projects()) {
            if (project.getProjectName().equals(projectName)) {
                return project;
            }
        }
        return null;
    }

    synchronized List<SignerConfigBean> matchPackageName(String packageName) {
        List<SignerConfigBean> matches = new ArrayList<>();
        if (packageName != null && !packageName.trim().isEmpty()) {
            for (SignerConfigBean project : projects()) {
                if (packageName.equals(project.getApplicationPackageName())) {
                    matches.add(project);
                }
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /** 仅替换指定项目；重新读取文件以保留其它项目的外部修改及未知字段。 */
    synchronized void update(SignerConfigBean project) throws IOException {
        if (project.getProjectName().isEmpty()) {
            throw new IOException("项目名称不能为空");
        }
        List<JSONObject> updated = readItems();
        JSONObject target = null;
        for (JSONObject item : updated) {
            if (project.getProjectName().equals(item.optString("projectName"))) {
                target = item;
                break;
            }
        }
        if (target == null) {
            target = new JSONObject();
            updated.add(target);
        }
        Properties properties = project.toProperties();
        for (String key : properties.stringPropertyNames()) {
            if (EDITABLE_KEYS.contains(key) || !target.has(key)) {
                if ("config-read-only".equals(key)) {
                    target.put(key, project.isReadOnly());
                } else {
                    target.put(key, properties.getProperty(key));
                }
            }
        }
        writeItems(updated);
        items = updated;
    }

    private List<JSONObject> readItems() throws IOException {
        List<JSONObject> result = new ArrayList<>();
        if (!Files.exists(configFile)) {
            return result;
        }
        String content = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        content = content.trim();
        if (content.isEmpty()) {
            return result;
        }
        boolean legacy = !content.startsWith("[");
        try {
            if (legacy) {
                if (content.startsWith("{")) {
                    throw new IOException("项目配置必须使用 JSON 数组");
                }
                Properties properties = new Properties();
                properties.load(new StringReader(content));
                if (Collections.disjoint(properties.stringPropertyNames(), EDITABLE_KEYS)) {
                    throw new IOException("旧配置中没有有效的签名配置字段");
                }
                JSONObject item = new JSONObject();
                for (String key : properties.stringPropertyNames()) {
                    item.put(key, properties.getProperty(key));
                }
                Path projectDirectory = legacyBaseDirectory();
                if (item.optString("projectName", "").trim().isEmpty()) {
                    item.put("projectName", projectDirectory.getFileName() == null
                            ? "默认项目" : projectDirectory.getFileName().toString());
                }
                item.put("applicationPackageName", item.optString("applicationPackageName", ""));
                result.add(item);
            } else {
                JSONArray array = new JSONArray(content);
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.optJSONObject(index);
                    if (item == null) {
                        throw new IOException("项目配置数组中的第 " + (index + 1) + " 项必须为对象");
                    }
                    result.add(item);
                }
            }
            Set<String> names = new HashSet<>();
            for (JSONObject item : result) {
                validateFields(item);
                String name = item.optString("projectName", "").trim();
                if (name.isEmpty() || !names.add(name)) {
                    throw new IOException("项目名称不能为空或重复");
                }
                item.put("projectName", name);
                if (legacy) {
                    resolvePaths(item, legacyBaseDirectory());
                } else {
                    // 校验路径但保留其它项目原有的相对路径写法。
                    resolvePaths(new JSONObject(item.toString()), configFile.getParent());
                }
            }
        } catch (IllegalArgumentException | org.json.JSONException exception) {
            // 避免将解析异常中的配置片段（可能包含密码）写入日志。
            throw new IOException("配置文件格式或路径无效，请检查 JSON 数组及字段内容");
        }
        return result;
    }

    private static void validateFields(JSONObject item) throws IOException {
        for (String key : EDITABLE_KEYS) {
            if (!item.has(key)) {
                continue;
            }
            Object value = item.get(key);
            if (!(value instanceof String) && !("config-read-only".equals(key) && value instanceof Boolean)) {
                throw new IOException("配置字段 " + key + " 的类型无效");
            }
        }
    }

    private Path legacyBaseDirectory() {
        Path directory = configFile.getParent();
        if (directory.getFileName() != null && "etc".equalsIgnoreCase(directory.getFileName().toString())
                && directory.getParent() != null) {
            return directory.getParent();
        }
        return directory;
    }

    private static void resolvePaths(JSONObject item, Path baseDirectory) {
        for (String key : PATH_KEYS) {
            String value = item.optString(key, "");
            if (!value.trim().isEmpty()) {
                Path path = Paths.get(value);
                String resolved = (path.isAbsolute() ? path : baseDirectory.resolve(path)).normalize().toString();
                // 末尾分隔符可表示尚未创建的输出目录，不能在规范化时丢失。
                if ((value.endsWith("/") || value.endsWith("\\"))
                        && !resolved.endsWith(File.separator)) {
                    resolved += File.separator;
                }
                item.put(key, resolved);
            }
        }
    }

    private SignerConfigBean toBean(JSONObject item) {
        item = new JSONObject(item.toString());
        resolvePaths(item, configFile.getParent());
        Properties properties = new Properties();
        for (String key : item.keySet()) {
            if (!item.isNull(key)) {
                properties.setProperty(key, String.valueOf(item.get(key)));
            }
        }
        return new SignerConfigBean(properties);
    }

    private void writeItems(List<JSONObject> updated) throws IOException {
        Files.createDirectories(configFile.getParent());
        Path temporary = Files.createTempFile(configFile.getParent(), "cfg-", ".tmp");
        try {
            Files.write(temporary, new JSONArray(updated).toString(2).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
