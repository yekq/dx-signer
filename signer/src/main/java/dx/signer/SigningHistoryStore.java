package dx.signer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/28 18:42
 * description: 保存签名历史，并提供文件选择器所需的历史 APK 路径。
 */
final class SigningHistoryStore {
    private static final String RECORDS_KEY = "records";
    private static final int MAX_RECORDS = 500;

    private final Path historyFile;
    private final List<Record> records = new ArrayList<>();

    SigningHistoryStore(Path historyFile) throws IOException {
        this.historyFile = historyFile;
        load();
    }

    synchronized void add(Path inputFile, boolean successful) throws IOException {
        add(inputFile, successful, "");
    }

    synchronized void add(Path inputFile, boolean successful, String projectName) throws IOException {
        add(inputFile, successful, projectName, Collections.emptyList());
    }

    synchronized void add(Path inputFile, boolean successful, String projectName,
                          List<Path> outputFiles) throws IOException {
        List<String> outputPaths = new ArrayList<>();
        if (outputFiles != null) {
            Set<String> seen = new HashSet<>();
            for (Path outputFile : outputFiles) {
                if (outputFile == null) {
                    continue;
                }
                String outputPath = outputFile.toAbsolutePath().normalize().toString();
                if (seen.add(normalizePathKey(outputPath))) {
                    outputPaths.add(outputPath);
                }
            }
        }
        records.add(0, new Record(
                inputFile.toAbsolutePath().normalize().toString(),
                inputFile.getFileName() == null ? inputFile.toString() : inputFile.getFileName().toString(),
                System.currentTimeMillis(),
                successful,
                projectName == null ? "" : projectName.trim(),
                outputPaths));
        if (records.size() > MAX_RECORDS) {
            records.subList(MAX_RECORDS, records.size()).clear();
        }
        save();
    }

    synchronized List<Record> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    synchronized Set<String> successfulPathKeys() {
        Set<String> paths = new HashSet<>();
        for (Record record : records) {
            if (record.successful) {
                addApkPaths(paths, record);
            }
        }
        return paths;
    }

    synchronized Set<String> historyApkPathKeys() {
        Set<String> paths = new HashSet<>();
        for (Record record : records) {
            addApkPaths(paths, record);
        }
        return paths;
    }

    private static void addApkPaths(Set<String> paths, Record record) {
        if (isApkPath(record.path)) {
            paths.add(normalizePathKey(record.path));
        }
        for (String outputPath : record.outputPaths) {
            if (isApkPath(outputPath)) {
                paths.add(normalizePathKey(outputPath));
            }
        }
    }

    private static boolean isApkPath(String path) {
        return new java.io.File(path).getName().toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    static String normalizePathKey(String path) {
        return new java.io.File(path).getAbsoluteFile().toPath().normalize().toString()
                .toLowerCase(Locale.ROOT);
    }

    static String signingTypeForFileName(String fileName) {
        if (fileName == null) {
            return "未识别";
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".apk")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.matches(".*_protected(?:\\.\\d+)?(?:_|$).*")) {
            return "梆梆";
        }
        if (name.startsWith("dx_unsigned")) {
            return "顶象";
        }
        if (name.contains("_jiagu") || name.endsWith("_360")) {
            return "360";
        }
        if (name.contains("_unsign")) {
            return "爱加密";
        }
        return "未识别";
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(historyFile)) {
            return;
        }
        String content = new String(Files.readAllBytes(historyFile), StandardCharsets.UTF_8);
        if (content.trim().isEmpty()) {
            return;
        }

        JSONArray array = new JSONObject(content).optJSONArray(RECORDS_KEY);
        if (array == null) {
            return;
        }
        for (int index = 0; index < array.length() && records.size() < MAX_RECORDS; index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String path = item.optString("path", "");
            if (path.isEmpty()) {
                continue;
            }
            List<String> outputPaths = new ArrayList<>();
            JSONArray storedOutputs = item.optJSONArray("outputPaths");
            if (storedOutputs != null) {
                for (int outputIndex = 0; outputIndex < storedOutputs.length(); outputIndex++) {
                    String outputPath = storedOutputs.optString(outputIndex, "");
                    if (!outputPath.isEmpty()) {
                        outputPaths.add(outputPath);
                    }
                }
            }
            records.add(new Record(
                    path,
                    item.optString("fileName", new java.io.File(path).getName()),
                    item.optLong("timestamp", 0L),
                    item.optBoolean("successful", false),
                    item.optString("projectName", ""),
                    outputPaths));
        }
    }

    private void save() throws IOException {
        Files.createDirectories(historyFile.toAbsolutePath().getParent());
        JSONArray array = new JSONArray();
        for (Record record : records) {
            JSONObject item = new JSONObject();
            item.put("path", record.path);
            item.put("fileName", record.fileName);
            item.put("timestamp", record.timestamp);
            item.put("successful", record.successful);
            item.put("projectName", record.projectName);
            item.put("outputPaths", new JSONArray(record.outputPaths));
            array.put(item);
        }
        JSONObject root = new JSONObject();
        root.put(RECORDS_KEY, array);

        Path temporaryFile = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
        Files.write(temporaryFile, root.toString(2).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporaryFile, historyFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, historyFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static final class Record {
        private final String path;
        private final String fileName;
        private final long timestamp;
        private final boolean successful;
        private final String projectName;
        private final List<String> outputPaths;

        private Record(String path, String fileName, long timestamp, boolean successful,
                       String projectName, List<String> outputPaths) {
            this.path = path;
            this.fileName = fileName;
            this.timestamp = timestamp;
            this.successful = successful;
            this.projectName = projectName;
            this.outputPaths = Collections.unmodifiableList(new ArrayList<>(outputPaths));
        }

        String fileName() {
            return fileName;
        }

        String path() {
            return path;
        }

        long timestamp() {
            return timestamp;
        }

        boolean successful() {
            return successful;
        }

        String projectName() {
            return projectName;
        }

        List<String> outputPaths() {
            return outputPaths;
        }
    }
}
