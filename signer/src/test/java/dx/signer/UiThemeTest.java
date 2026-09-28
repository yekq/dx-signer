package dx.signer;

import org.json.JSONObject;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.UIManager;

/** 验证自定义配色生效，且窗口状态保存时保留用户配置。 */
public final class UiThemeTest {
    private UiThemeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path stateFile = Files.createTempFile("dx-signer-theme-", ".json");
        JSONObject settings = new JSONObject()
                .put("theme", new JSONObject()
                        .put("accent", "#123456")
                        .put("background", "invalid")
                        .put("projectColors", new JSONObject().put("测试项目", "#AB5432"))
                        .put("custom", "keep-me"))
                .put("customRoot", "keep-me-too");
        Files.write(stateFile, settings.toString().getBytes(StandardCharsets.UTF_8));

        WindowStateStore state = new WindowStateStore(stateFile);
        UiTheme.apply(state.themeSettings());
        assertColor("Signer.accent", new Color(0x123456));
        assertColor("Signer.background", new Color(0xEDF2F4));
        Object projectColors = UIManager.get("Signer.projectColors");
        if (!(projectColors instanceof java.util.Map)
                || !new Color(0xAB5432).equals(((java.util.Map<?, ?>) projectColors).get("测试项目"))) {
            throw new AssertionError("项目颜色未按配置生效");
        }
        state.flush();

        boolean saved = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            Thread.sleep(20);
            String savedText = new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8);
            if (!savedText.contains("\n")) {
                continue;
            }
            JSONObject reloaded = new JSONObject(savedText);
            if (reloaded.has("theme") && "keep-me-too".equals(reloaded.optString("customRoot"))) {
                saved = "keep-me".equals(reloaded.getJSONObject("theme").optString("custom"));
                if (saved) {
                    break;
                }
            }
        }
        if (!saved) {
            throw new AssertionError("保存窗口状态时丢失自定义颜色或扩展字段");
        }
        System.out.println("UiThemeTest passed");
    }

    private static void assertColor(String key, Color expected) {
        if (!expected.equals(UIManager.getColor(key))) {
            throw new AssertionError(key + " 的颜色未按配置生效");
        }
    }
}
