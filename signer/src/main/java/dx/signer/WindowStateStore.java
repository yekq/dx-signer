package dx.signer;

import org.json.JSONObject;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 11:53
 * description: 保存并恢复各窗口的位置与大小，避免窗口移出当前屏幕。
 */
final class WindowStateStore {
    private static final Logger LOGGER = Logger.getLogger(WindowStateStore.class.getName());
    private final Path stateFile;
    private final Map<String, Rectangle> boundsByWindow = new LinkedHashMap<>();
    private final Object writeLock = new Object();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "window-state-writer");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> pendingWrite;

    /** 在界面初始化前读取状态，后续磁盘写入均在后台执行。 */
    WindowStateStore(Path stateFile) throws IOException {
        this.stateFile = stateFile.toAbsolutePath().normalize();
        load();
        // 正常关闭应用时，等待最后一份状态落盘，避免防抖任务尚未执行就退出。
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveSafely, "window-state-shutdown"));
    }

    /** 在事件分发线程恢复窗口，并监听正常状态下的位置和大小。 */
    void track(Window window, String key, Rectangle fallbackBounds) {
        Rectangle saved;
        synchronized (this) {
            saved = boundsByWindow.get(key);
        }
        window.setBounds(fitToScreens(saved == null ? fallbackBounds : saved, window.getMinimumSize()));
        remember(window, key);
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                remember(window, key);
            }

            @Override
            public void componentResized(ComponentEvent event) {
                remember(window, key);
            }
        });
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                remember(window, key);
                flush();
            }
        });
    }

    synchronized void flush() {
        scheduleWrite(0L);
    }

    private synchronized void remember(Window window, String key) {
        if (window instanceof Frame && ((Frame) window).getExtendedState() != Frame.NORMAL) {
            return;
        }
        Rectangle bounds = window.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0 || bounds.equals(boundsByWindow.get(key))) {
            return;
        }
        boundsByWindow.put(key, bounds);
        scheduleWrite(350L);
    }

    private void scheduleWrite(long delayMillis) {
        if (pendingWrite != null) {
            pendingWrite.cancel(false);
        }
        pendingWrite = writer.schedule(this::saveSafely, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        String text = new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(text);
            for (String key : root.keySet()) {
                JSONObject item = root.optJSONObject(key);
                if (item != null && item.optInt("width") > 0 && item.optInt("height") > 0) {
                    boundsByWindow.put(key, new Rectangle(item.optInt("x"), item.optInt("y"),
                            item.optInt("width"), item.optInt("height")));
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "窗口位置配置格式错误，将使用默认位置", exception);
        }
    }

    private void saveSafely() {
        // 退出钩子与后台任务可能同时执行，串行写入防止旧快照覆盖新快照。
        synchronized (writeLock) {
            JSONObject root = new JSONObject();
            synchronized (this) {
                for (Map.Entry<String, Rectangle> entry : boundsByWindow.entrySet()) {
                    Rectangle bounds = entry.getValue();
                    root.put(entry.getKey(), new JSONObject().put("x", bounds.x).put("y", bounds.y)
                            .put("width", bounds.width).put("height", bounds.height));
                }
            }
            try {
                Files.createDirectories(stateFile.getParent());
                Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
                Files.write(temporary, root.toString(2).getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "保存窗口位置失败", exception);
            }
        }
    }

    static Rectangle usableBounds(GraphicsConfiguration configuration) {
        Rectangle bounds = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private static Rectangle fitToScreens(Rectangle requested, Dimension minimumSize) {
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        Rectangle bestScreen = usableBounds(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration());
        long largestIntersection = 0;
        for (GraphicsDevice screen : screens) {
            Rectangle usable = usableBounds(screen.getDefaultConfiguration());
            Rectangle intersection = usable.intersection(requested);
            long area = intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
            if (area > largestIntersection) {
                largestIntersection = area;
                bestScreen = usable;
            }
        }
        return fitToBounds(requested, minimumSize, bestScreen);
    }

    /** 屏幕断开或分辨率缩小时，把窗口完整移回可用区域。 */
    static Rectangle fitToBounds(Rectangle requested, Dimension minimumSize, Rectangle available) {
        int width = Math.min(available.width, Math.max(minimumSize.width, requested.width));
        int height = Math.min(available.height, Math.max(minimumSize.height, requested.height));
        int x = Math.max(available.x, Math.min(requested.x, available.x + available.width - width));
        int y = Math.max(available.y, Math.min(requested.y, available.y + available.height - height));
        return new Rectangle(x, y, width, height);
    }
}
