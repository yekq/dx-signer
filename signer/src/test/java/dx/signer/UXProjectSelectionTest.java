package dx.signer;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 12:04
 * description: 使用临时配置验证项目切换与自动模式清理，防止沿用上一项目的签名凭据。
 */
public final class UXProjectSelectionTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("ux-project-selection-");
        Field root = field("applicationRoot");
        Object previousRoot = root.get(null);
        PrintStream output = System.out;
        PrintStream error = System.err;
        UX[] ui = new UX[1];
        try {
            JSONArray projects = new JSONArray().put(project("测试甲", "a", true))
                    .put(project("测试乙", "b", false));
            Files.write(directory.resolve("cfg.properties"), projects.toString(2).getBytes(StandardCharsets.UTF_8));
            root.set(null, directory.toString());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    ui[0] = new UX();
                    verifySelection(ui[0], directory);
                } catch (Exception exception) {
                    throw new IllegalStateException("项目选择验证失败", exception);
                }
            });
            output.println("界面项目切换测试通过：自动默认、手动凭据切换、只读切换、返回自动时清空凭据");
        } finally {
            System.setOut(output);
            System.setErr(error);
            root.set(null, previousRoot);
            if (ui[0] != null) {
                ((ExecutorService) field("signingExecutor").get(ui[0])).shutdownNow();
            }
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifySelection(UX ui, Path directory) throws Exception {
        JComboBox<?> selector = (JComboBox<?>) field("projectSelector").get(ui);
        JTextField keyStore = (JTextField) field("ksPathTF").get(ui);
        JPasswordField storePassword = (JPasswordField) field("ksPassPF").get(ui);
        JPasswordField keyPassword = (JPasswordField) field("keyPassPF").get(ui);
        JLabel status = (JLabel) field("projectStatus").get(ui);
        require(selector.getItemCount() == 3 && selector.getSelectedIndex() == 0, "应默认自动模式并包含两个项目");
        require(field("activeProject").get(ui) == null, "自动模式不能预先选中某项目");

        selector.setSelectedItem("测试甲");
        require(keyStore.getText().equals(directory.resolve("a.jks").toString()), "手动选择甲应加载甲的证书");
        require("store-a".equals(new String(storePassword.getPassword())), "应加载甲的密钥库密码");
        require("key-a".equals(new String(keyPassword.getPassword())), "应分别加载甲的证书密码");
        require(!keyStore.isEnabled() && !storePassword.isEnabled(), "只读项目应禁用配置编辑");
        require(status.getText().contains("测试甲") && status.getText().contains("cn.test.a"), "状态应显示当前项目及包名");

        selector.setSelectedItem("测试乙");
        require(keyStore.getText().equals(directory.resolve("b.jks").toString()), "切换到乙应替换证书路径");
        require("store-b".equals(new String(storePassword.getPassword())), "切换到乙应替换密钥库密码");
        require("key-b".equals(new String(keyPassword.getPassword())), "切换到乙应替换证书密码");
        require(keyStore.isEnabled() && storePassword.isEnabled(), "可编辑项目应恢复配置编辑");

        selector.setSelectedIndex(0);
        require(field("activeProject").get(ui) == null, "返回自动模式应清除已选项目");
        require(keyStore.getText().isEmpty() && storePassword.getPassword().length == 0
                && keyPassword.getPassword().length == 0, "返回自动模式应清除上一项目的证书与密码");
        require(!(Boolean) field("readOnly").get(ui), "返回自动模式应清除上一项目的只读状态");
    }

    private static JSONObject project(String name, String suffix, boolean readOnly) {
        return new JSONObject().put("projectName", name).put("applicationPackageName", "cn.test." + suffix)
                .put("ks", suffix + ".jks").put("ks-pass", "store-" + suffix)
                .put("key-pass", "key-" + suffix).put("config-read-only", readOnly);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = UX.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
