package dx.signer;

import org.json.JSONObject;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/28 18:43
 * description: 从窗口状态配置读取界面颜色，并统一设置 Swing 默认配色。
 */
final class UiTheme {
    private static final String[][] DEFAULT_COLORS = {
            {"background", "#EDF2F4"},
            {"surface", "#FFFFFF"},
            {"foreground", "#1D2B33"},
            {"muted", "#5C6A70"},
            {"accent", "#087E8B"},
            {"selection", "#DCEDEF"},
            {"success", "#247A55"},
            {"failure", "#B2463C"},
            {"border", "#C7D3D8"}
    };

    private UiTheme() {
    }

    static JSONObject defaultSettings() {
        JSONObject colors = new JSONObject();
        for (String[] entry : DEFAULT_COLORS) {
            colors.put(entry[0], entry[1]);
        }
        colors.put("projectColors", new JSONObject());
        return colors;
    }

    static void apply(JSONObject configured) {
        JSONObject defaults = defaultSettings();
        Color background = readColor(configured, defaults, "background");
        Color surface = readColor(configured, defaults, "surface");
        Color foreground = readColor(configured, defaults, "foreground");
        Color muted = readColor(configured, defaults, "muted");
        Color accent = readColor(configured, defaults, "accent");
        Color selection = readColor(configured, defaults, "selection");
        Color success = readColor(configured, defaults, "success");
        Color failure = readColor(configured, defaults, "failure");
        Color border = readColor(configured, defaults, "border");

        put("Signer.background", background);
        put("Signer.surface", surface);
        put("Signer.foreground", foreground);
        put("Signer.muted", muted);
        put("Signer.accent", accent);
        put("Signer.selection", selection);
        put("Signer.success", success);
        put("Signer.failure", failure);
        put("Signer.border", border);
        Map<String, Color> projectColors = new LinkedHashMap<>();
        JSONObject projectSettings = configured == null ? null : configured.optJSONObject("projectColors");
        if (projectSettings != null) {
            for (String projectName : projectSettings.keySet()) {
                String value = projectSettings.optString(projectName, "");
                if (isHexColor(value)) {
                    projectColors.put(projectName, Color.decode(value));
                }
            }
        }
        UIManager.put("Signer.projectColors", Collections.unmodifiableMap(projectColors));

        put("control", background);
        put("Panel.background", background);
        put("Viewport.background", surface);
        put("ScrollPane.background", surface);
        put("Label.foreground", foreground);
        put("Label.disabledForeground", muted);
        put("CheckBox.background", background);
        put("CheckBox.foreground", foreground);
        put("Button.background", surface);
        put("Button.foreground", foreground);
        put("ComboBox.background", surface);
        put("ComboBox.foreground", foreground);
        put("TextField.background", surface);
        put("TextField.foreground", foreground);
        put("PasswordField.background", surface);
        put("PasswordField.foreground", foreground);
        put("TextArea.background", surface);
        put("TextArea.foreground", foreground);
        put("Table.background", surface);
        put("Table.foreground", foreground);
        put("Table.selectionBackground", selection);
        put("Table.selectionForeground", foreground);
        put("TableHeader.background", background);
        put("TableHeader.foreground", foreground);
        put("TabbedPane.background", background);
        put("TabbedPane.foreground", foreground);
        put("OptionPane.background", background);
    }

    private static Color readColor(JSONObject configured, JSONObject defaults, String key) {
        String fallback = defaults.getString(key);
        String value = configured == null ? fallback : configured.optString(key, fallback);
        if (!isHexColor(value)) {
            value = fallback;
        }
        return Color.decode(value);
    }

    private static boolean isHexColor(String value) {
        return value != null && value.matches("#[0-9a-fA-F]{6}");
    }

    private static void put(String key, Color color) {
        UIManager.put(key, new ColorUIResource(color));
    }
}
