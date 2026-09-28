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

/** Persists bounded signing history and exposes successful input paths for the chooser. */
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
        records.add(0, new Record(
                inputFile.toAbsolutePath().normalize().toString(),
                inputFile.getFileName() == null ? inputFile.toString() : inputFile.getFileName().toString(),
                System.currentTimeMillis(),
                successful));
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
                paths.add(normalizePathKey(record.path));
            }
        }
        return paths;
    }

    static String normalizePathKey(String path) {
        return new java.io.File(path).getAbsoluteFile().toPath().normalize().toString()
                .toLowerCase(Locale.ROOT);
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
            records.add(new Record(
                    path,
                    item.optString("fileName", new java.io.File(path).getName()),
                    item.optLong("timestamp", 0L),
                    item.optBoolean("successful", false)));
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

        private Record(String path, String fileName, long timestamp, boolean successful) {
            this.path = path;
            this.fileName = fileName;
            this.timestamp = timestamp;
            this.successful = successful;
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
    }
}
