package dx.signer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/10/11 10:00
 * description: 保存用户自定义隐藏的完整文件路径，支持恢复显示及重启后继续筛选。
 */
final class CustomHiddenFileStore {
    private static final String FILE_NAME = "chooser-hidden-files.json";
    private static final String PATHS_KEY = "paths";

    private final Path storeFile;
    private final JSONObject settings;
    private Map<String, String> paths = new LinkedHashMap<>();

    CustomHiddenFileStore(Path storeFile) throws IOException {
        this.storeFile = storeFile.toAbsolutePath().normalize();
        settings = readSettings();
        JSONArray storedPaths = settings.optJSONArray(PATHS_KEY);
        if (storedPaths != null) {
            for (int index = 0; index < storedPaths.length(); index++) {
                Object value = storedPaths.get(index);
                if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
                    throw new IOException("自定义隐藏文件配置中的路径格式错误：" + this.storeFile);
                }
                try {
                    String path = new File((String) value).getAbsoluteFile().toPath().normalize().toString();
                    paths.put(SigningHistoryStore.normalizePathKey(path), path);
                } catch (RuntimeException exception) {
                    throw new IOException("自定义隐藏文件配置中的路径无效：" + this.storeFile, exception);
                }
            }
        }
    }

    /** 打包运行时使用 JAR 所在目录，IDE 运行时使用当前工作目录。 */
    static CustomHiddenFileStore loadDefault() throws IOException {
        Path directory = Paths.get("").toAbsolutePath().normalize();
        try {
            if (CustomHiddenFileStore.class.getProtectionDomain().getCodeSource() != null) {
                Path location = Paths.get(CustomHiddenFileStore.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                if (Files.isRegularFile(location) && location.getParent() != null) {
                    directory = location.getParent();
                }
            }
        } catch (URISyntaxException | RuntimeException exception) {
            throw new IOException("无法定位自定义隐藏文件配置的保存目录", exception);
        }
        return new CustomHiddenFileStore(directory.resolve(FILE_NAME));
    }

    synchronized boolean contains(File file) {
        return file != null && paths.containsKey(SigningHistoryStore.normalizePathKey(file.toString()));
    }

    synchronized void hide(File file) throws IOException {
        String path = absolutePath(file);
        String key = SigningHistoryStore.normalizePathKey(path);
        if (paths.containsKey(key)) {
            return;
        }
        Map<String, String> updated = new LinkedHashMap<>(paths);
        updated.put(key, path);
        save(updated);
        paths = updated;
    }

    synchronized void unhide(File file) throws IOException {
        String key = SigningHistoryStore.normalizePathKey(absolutePath(file));
        if (!paths.containsKey(key)) {
            return;
        }
        Map<String, String> updated = new LinkedHashMap<>(paths);
        updated.remove(key);
        save(updated);
        paths = updated;
    }

    private static String absolutePath(File file) throws IOException {
        if (file == null) {
            throw new IOException("请选择需要隐藏或恢复显示的文件");
        }
        try {
            return file.getAbsoluteFile().toPath().normalize().toString();
        } catch (RuntimeException exception) {
            throw new IOException("文件路径无效：" + file, exception);
        }
    }

    private JSONObject readSettings() throws IOException {
        if (!Files.exists(storeFile)) {
            return new JSONObject();
        }
        if (!Files.isRegularFile(storeFile)) {
            throw new IOException("自定义隐藏文件配置不是普通文件：" + storeFile);
        }
        String text = new String(Files.readAllBytes(storeFile), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        try {
            JSONObject root = new JSONObject(text);
            if (root.has(PATHS_KEY) && !(root.get(PATHS_KEY) instanceof JSONArray)) {
                throw new IOException("自定义隐藏文件配置中的路径列表格式错误：" + storeFile);
            }
            return root;
        } catch (RuntimeException exception) {
            throw new IOException("自定义隐藏文件配置格式错误：" + storeFile, exception);
        }
    }

    /** 先完整写入临时文件，再替换原文件；写入失败时保留原记录和内存状态。 */
    private void save(Map<String, String> updated) throws IOException {
        JSONObject root = new JSONObject(settings.toString());
        root.put(PATHS_KEY, new JSONArray(updated.values()));
        Path temporary = null;
        try {
            Files.createDirectories(storeFile.getParent());
            temporary = Files.createTempFile(storeFile.getParent(), FILE_NAME + "-", ".tmp");
            Files.write(temporary, root.toString(2).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, storeFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, storeFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IOException("保存自定义隐藏文件配置失败：" + storeFile, exception);
        } finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
