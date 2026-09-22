/**
 * dx-signer
 *
 * Copyright 2022 北京顶象技术有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dx.signer;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import org.slf4j.impl.SimpleLogger;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.DefaultCaret;

import dx.channel.ApkSigns;

public final class UX {
    private static final String AUTO_KEY_ALIAS = "{{auto}}";
    private static final String CONFIG_DIRECTORY = "etc";
    private static final String CONFIG_FILE_NAME = "cfg.properties";
    private static final String AUTO_PROJECT = "自动（按 APK 包名匹配）";
    private static final String HISTORY_FILE_NAME = "signing-history.json";
    private static String applicationRoot = "";
    private final ExecutorService signingExecutor = Executors.newSingleThreadExecutor();
    // 表单字段必须与 UX.form 中的绑定名称保持一致。
    private JButton inBtn;
    private JTextField inPathTF;
    private JTabbedPane tabbedPane1;
    private JTextField ksPathTF;
    private JButton ksBtn;
    private JTextField outPathTF;
    private JButton signBtn;
    private JTextArea loggingTA;
    private JCheckBox savePwCheckBox;
    private JComboBox<String> keyAliasCB;
    private JPasswordField keyPassPF;
    private JPasswordField ksPassPF;
    public JPanel topPanel;
    private JProgressBar progressBar1;
    private JTextField channelPathTF;
    private JButton channelBtn;
    private JCheckBox v1SigningEnabledCheckBox;
    private JCheckBox v2SigningEnabledCheckBox;

    private boolean readOnly = false;
    private String inputFileName = "";
    private SigningHistoryStore signingHistoryStore;
    private ProjectConfigStore projectConfigStore;
    private SignerConfigBean activeProject;
    private final JComboBox<String> projectSelector = new JComboBox<>();
    private final JLabel projectStatus = new JLabel("请选择 APK 或指定项目");
    private SigningHistoryWindow historyWindow;
    private boolean signingBusy;

    private static int mainWindowWidth;
    private static int mainWindowHeight;
    private static boolean initialChooserShown;

    public static void main(String[] args) throws IOException {
        configureLoggingProperties();
        if (args.length > 0 && "sign".equals(args[0])) {
            CommandLine.main(args);
            return;
        }

        applicationRoot = parseApplicationRoot(args);
        SwingUtilities.invokeLater(UX::showMainWindow);
    }

    private static void configureLoggingProperties() {
        System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false");
        System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");
    }

    private static String parseApplicationRoot(String[] args) {
        if (args.length == 0) {
            return "";
        }
        if (args.length == 2 && "-path".equals(args[0])) {
            return args[1];
        }
        throw new IllegalArgumentException("Usage: UX [-path <application-root>]");
    }

    private static void showMainWindow() {
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        mainWindowWidth = Math.max(480, screen.width / 2);
        mainWindowHeight = screen.height * 9 / 10;
        initialChooserShown = false;

        UX ux = new UX();
        JFrame frame = new JFrame("Apk签名&多渠道工具:" + applicationRoot);
        frame.setContentPane(ux.createMainContent());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(mainWindowWidth, mainWindowHeight);
        frame.setLocation(screen.x, screen.y + (screen.height - mainWindowHeight) / 2);
        try {
            WindowStateStore states = new WindowStateStore(
                    ux.getHistoryPath().resolveSibling("window-state.json"));
            states.track(frame, "main", frame.getBounds());
            ux.historyWindow = new SigningHistoryWindow(frame, states);
            ux.refreshHistoryWindow();
        } catch (IOException exception) {
            ux.log("读取窗口布局失败: " + exception.getMessage());
            ux.historyWindow = new SigningHistoryWindow(frame, null);
            ux.refreshHistoryWindow();
        }
        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                if (!initialChooserShown) {
                    initialChooserShown = true;
                    ux.showChooseAppFileDialog();
                }
            }

            @Override
            public void windowLostFocus(WindowEvent event) {
                // The initial chooser is the only focus-driven action.
            }
        });
        frame.setVisible(true);
        if (ux.historyWindow != null) {
            ux.historyWindow.showHistory();
        }
    }

    public UX() {
        configureKeyAliasSelector();
        initializeSigningHistory();
        configureProjectSelector();
        loadLocalConfig();
        configureActions();
        configureLogging();
        refreshHistoryWindow();
        applyReadOnlyState();
    }

    /** 项目选择区独立于设计器布局，避免重新生成表单时覆盖业务监听器。 */
    private JPanel createMainContent() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel("签名项目"));
        toolbar.add(projectSelector);
        JButton historyButton = new JButton("签名历史");
        historyButton.addActionListener(event -> {
            if (historyWindow != null) {
                historyWindow.showHistory();
            }
        });
        toolbar.add(historyButton);
        JPanel header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        header.add(projectStatus, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout());
        content.add(header, BorderLayout.NORTH);
        JScrollPane formScrollPane = new JScrollPane(topPanel);
        formScrollPane.setBorder(null);
        formScrollPane.getVerticalScrollBar().setUnitIncrement(24);
        content.add(formScrollPane, BorderLayout.CENTER);
        return content;
    }

    private void configureProjectSelector() {
        projectSelector.addItem(AUTO_PROJECT);
        projectSelector.addActionListener(event -> {
            if (projectConfigStore == null || signingBusy) {
                return;
            }
            if (projectSelector.getSelectedIndex() == 0) {
                clearProjectSelection();
                projectStatus.setText("自动模式：签名前根据 APK 包名匹配配置");
            } else {
                SignerConfigBean project = projectConfigStore.findByProjectName(
                        (String) projectSelector.getSelectedItem());
                if (project != null) {
                    applyProject(project);
                }
            }
        });
    }

    private void configureActions() {
        inBtn.addActionListener(event -> showChooseAppFileDialog());
        ksBtn.addActionListener(event -> showKeystoreFileDialog());
        channelBtn.addActionListener(event -> showChannelFileDialog());
        signBtn.addActionListener(event -> onSubmitClick());
    }

    private void configureKeyAliasSelector() {
        resetKeyAliasOptions();
        keyAliasCB.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                loadKeyAliases();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                // No action is required when the list closes.
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
                // No action is required when selection is canceled.
            }
        });
    }

    private void loadKeyAliases() {
        char[] password = ksPassPF.getPassword();
        try {
            byte[] keyStoreBytes = Files.readAllBytes(Paths.get(ksPathTF.getText()));
            Set<String> passwords = Collections.singleton(new String(password));
            KeyStore keyStore = ApkSigns.loadKeyStore(keyStoreBytes, passwords);
            if (keyStore == null) {
                return;
            }

            resetKeyAliasOptions();
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                keyAliasCB.addItem(aliases.nextElement());
            }
            keyAliasCB.setSelectedItem(AUTO_KEY_ALIAS);
        } catch (Exception exception) {
            log("无法读取 KeyStore 别名: " + exception.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void resetKeyAliasOptions() {
        keyAliasCB.removeAllItems();
        keyAliasCB.addItem(AUTO_KEY_ALIAS);
        keyAliasCB.setSelectedItem(AUTO_KEY_ALIAS);
    }

    private void configureLogging() {
        DefaultCaret caret = (DefaultCaret) loggingTA.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JTextAreaOutputStream.hijack(loggingTA);
    }

    private void applyReadOnlyState() {
        boolean editable = !readOnly && !signingBusy;
        savePwCheckBox.setEnabled(editable);
        channelBtn.setEnabled(editable);
        channelPathTF.setEnabled(editable);
        inBtn.setEnabled(!signingBusy);
        inPathTF.setEnabled(false);
        outPathTF.setEnabled(editable);
        ksBtn.setEnabled(editable);
        ksPathTF.setEnabled(editable);
        keyAliasCB.setEnabled(editable);
        ksPassPF.setEnabled(editable);
        keyPassPF.setEnabled(editable);
        projectSelector.setEnabled(!signingBusy);
    }

    private File chooseFile(Component parent, JTextField pathField, String description,
            String... extensions) {
        String path = pathField.getText().trim();
        File initialPath = path.isEmpty() ? null : new File(path);
        Set<String> successfulPaths = signingHistoryStore == null
                ? Collections.emptySet()
                : signingHistoryStore.successfulPathKeys();
        return FileChooserDialog.chooseFile(
                parent, initialPath, description, successfulPaths, extensions);
    }

    private void showChooseAppFileDialog() {
        File selected = chooseFile(inBtn, inPathTF, "*.apk, *.aab", ".apk", ".aab");
        if (selected != null) {
            setInput(selected);
            onSubmitClick();
        }
    }

    private void showKeystoreFileDialog() {
        File selected = chooseFile(ksBtn, ksPathTF,
                "*.ks, *.keystore, *.p12, *.pfx, *.jks",
                ".ks", ".keystore", ".p12", ".pfx", ".jks");
        if (selected != null) {
            ksPathTF.setText(selected.getAbsolutePath());
        }
    }

    private void showChannelFileDialog() {
        File selected = chooseFile(channelBtn, channelPathTF, "*.txt", ".txt");
        if (selected != null) {
            channelPathTF.setText(selected.getAbsolutePath());
        }
    }

    private void onSubmitClick() {
        if (signingBusy) {
            return;
        }
        if (inPathTF.getText().trim().isEmpty()
                || !Files.isRegularFile(new File(inPathTF.getText()).toPath())) {
            JOptionPane.showMessageDialog(topPanel, "请先选择有效的 APK 或 AAB 文件");
            return;
        }
        if (projectSelector.getSelectedIndex() == 0) {
            matchProjectAndSign();
        } else if (activeProject != null) {
            submitSigningRequest();
        } else {
            JOptionPane.showMessageDialog(topPanel, "请先配置并选择签名项目");
        }
    }

    /** 包名读取在后台执行；无匹配或存在歧义时禁止沿用上一次的证书。 */
    private void matchProjectAndSign() {
        if (projectConfigStore == null || projectConfigStore.projects().isEmpty()) {
            JOptionPane.showMessageDialog(topPanel, "未加载项目配置，请检查 cfg.properties");
            return;
        }
        final Path input = Paths.get(inPathTF.getText());
        final String idleText = progressBar1.getString();
        clearProjectSelection();
        setSigningInProgress(true, idleText);
        progressBar1.setString("识别 APK 包名...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return ApkPackageReader.readPackageName(input);
            }

            @Override
            protected void done() {
                setSigningInProgress(false, idleText);
                try {
                    String packageName = get();
                    List<SignerConfigBean> matches = projectConfigStore.matchPackageName(packageName);
                    if (matches.size() != 1) {
                        projectStatus.setText("包名：" + packageName + "，匹配项目数：" + matches.size());
                        JOptionPane.showMessageDialog(topPanel, matches.isEmpty()
                                ? "包名 " + packageName + " 没有对应配置，请手动选择签名项目"
                                : "包名 " + packageName + " 对应多个项目，请手动指定签名项目");
                        return;
                    }
                    applyProject(matches.get(0));
                    submitSigningRequest();
                } catch (Exception exception) {
                    projectStatus.setText("包名识别失败，请手动选择签名项目");
                    JOptionPane.showMessageDialog(topPanel,
                            "无法识别包名，请确认 APK 有效；AAB 请手动选择签名项目",
                            "项目识别失败", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void submitSigningRequest() {
        SigningRequest request;
        try {
            request = createSigningRequest();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(topPanel, "签名参数无效: " + exception.getMessage(),
                    "参数错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!confirmOverwrite(request)) {
            return;
        }

        saveLocalConfig(request);
        loggingTA.setText("");
        setSigningInProgress(true, request.originalProgressText);
        signingExecutor.submit(() -> executeSigning(request));
    }

    private SigningRequest createSigningRequest() {
        if (activeProject == null || ksPathTF.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("当前项目未配置 KeyStore");
        }
        if (!Files.isRegularFile(Paths.get(ksPathTF.getText().trim()))) {
            throw new IllegalArgumentException("KeyStore 文件不存在，请检查项目配置");
        }
        if (outPathTF.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("请选择输出路径");
        }
        Path inputPath = Paths.get(inPathTF.getText().trim());
        Path outputPath = Paths.get(outPathTF.getText().trim());
        Path keyStorePath = Paths.get(ksPathTF.getText().trim());
        String channelListValue = channelPathTF.getText().trim();
        Path channelListPath = channelListValue.isEmpty() ? null : Paths.get(channelListValue);
        String keyStorePassword = new String(ksPassPF.getPassword());
        String keyPassword = new String(keyPassPF.getPassword());
        String selectedAlias = (String) keyAliasCB.getSelectedItem();
        String keyAlias = selectedAlias == null ? AUTO_KEY_ALIAS : selectedAlias;

        return new SigningRequest(inputPath, outputPath, keyStorePath, channelListPath,
                inputFileName, keyStorePassword, keyPassword, keyAlias, progressBar1.getString(),
                activeProject.getApplicationPackageName());
    }

    private boolean confirmOverwrite(SigningRequest request) {
        if (request.hasChannelList()) {
            Path outputDirectory = CommandLine.detectOutDir(request.outputPath.toString());
            return !Files.exists(outputDirectory) || JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                    topPanel,
                    "多渠道输出APK目录已经存在，是否覆盖:\n" + outputDirectory,
                    "输出APK已经存在，是否覆盖",
                    JOptionPane.YES_NO_OPTION);
        }

        return !Files.isRegularFile(request.outputPath) || JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                topPanel,
                "输出APK已经存在，是否覆盖:\n" + request.outputPath,
                "输出APK已经存在，是否覆盖",
                JOptionPane.YES_NO_OPTION);
    }

    /** 仅更新当前项目，保留数组中其他项目及当前项目的扩展字段。 */
    private void saveLocalConfig(SigningRequest request) {
        if (readOnly || projectConfigStore == null || activeProject == null) {
            return;
        }
        SignerConfigBean updated = new SignerConfigBean(activeProject.toProperties());
        updated.setKs(request.keyStorePath.toString());
        updated.setIn(parentPath(request.inputPath));
        updated.setKsKeyAlias(request.keyAlias);
        updated.setInFilename("");
        updated.setOut(parentPath(request.outputPath));
        updated.setChannelList(request.channelListPath == null ? "" : request.channelListPath.toString());
        updated.setKsPass(savePwCheckBox.isSelected() ? request.keyStorePassword : "");
        updated.setKeyPass(savePwCheckBox.isSelected() ? request.keyPassword : "");
        try {
            projectConfigStore.update(updated);
            activeProject = updated;
        } catch (IOException | RuntimeException exception) {
            log("保存配置失败: " + exception.getMessage());
        }
    }

    private static String parentPath(Path path) {
        Path parent = path.toAbsolutePath().getParent();
        return parent == null ? path.toString() : parent.toString();
    }

    private void executeSigning(SigningRequest request) {
        try {
            int result;
            Path actualOutputPath;
            if (request.hasChannelList()) {
                actualOutputPath = CommandLine.detectOutDir(request.outputPath.toString());
                result = SignWorker.signChannelApk(
                        request.inputPath,
                        request.inputFileName,
                        actualOutputPath,
                        request.channelListPath,
                        request.keyStorePath,
                        request.keyStorePassword,
                        request.keyAlias,
                        request.keyPassword, request.applicationPackageName);
            } else {
                actualOutputPath = request.outputPath;
                result = SignWorker.signApk(
                        request.inputPath,
                        actualOutputPath,
                        request.keyStorePath,
                        request.keyStorePassword,
                        request.keyAlias,
                        request.keyPassword, request.applicationPackageName);
            }

            recordSigningResult(request.inputPath, result == 0);
            Path completedOutputPath = actualOutputPath;
            SwingUtilities.invokeLater(() -> showSigningResult(request, completedOutputPath, result));
        } catch (Exception exception) {
            recordSigningResult(request.inputPath, false);
            log("签名失败: " + exception.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    topPanel, "签名失败: " + exception.getMessage(), "签名结果", JOptionPane.ERROR_MESSAGE));
        } finally {
            SwingUtilities.invokeLater(() -> setSigningInProgress(false, request.originalProgressText));
        }
    }

    private void showSigningResult(SigningRequest request, Path outputPath, int result) {
        if (result != 0) {
            JOptionPane.showMessageDialog(topPanel,
                    request.hasChannelList() ? "多渠道失败" : "签名失败",
                    "签名结果",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (request.hasChannelList()) {
            JOptionPane.showMessageDialog(topPanel, "多渠道成功, 输出APK文件夹\n" + outputPath);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(topPanel,
                "签名成功, 输出APK\n" + outputPath,
                "签名结果",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION && outputPath.toFile().getParent() != null) {
            Tools.openDir(topPanel, outputPath.toFile().getParent());
        }
    }

    private void setSigningInProgress(boolean signing, String idleText) {
        signingBusy = signing;
        signBtn.setEnabled(!signing);
        applyReadOnlyState();
        progressBar1.setIndeterminate(signing);
        progressBar1.setString(signing ? "签名中..." : idleText);
        progressBar1.setStringPainted(true);
    }

    private void initializeSigningHistory() {
        try {
            signingHistoryStore = new SigningHistoryStore(
                    getHistoryPath());
        } catch (IOException | RuntimeException exception) {
            log("读取签名历史失败: " + exception.getMessage());
        }
    }

    private void recordSigningResult(Path inputPath, boolean successful) {
        if (signingHistoryStore == null) {
            return;
        }
        try {
            signingHistoryStore.add(inputPath, successful);
            SwingUtilities.invokeLater(this::refreshHistoryWindow);
        } catch (IOException | RuntimeException exception) {
            log("保存签名历史失败: " + exception.getMessage());
        }
    }

    private void refreshHistoryWindow() {
        if (historyWindow == null || signingHistoryStore == null) {
            return;
        }
        historyWindow.setRecords(signingHistoryStore.records());
    }

    private void log(String message) {
        if (loggingTA == null) {
            System.out.println(message);
            return;
        }

        Runnable appendMessage = () -> {
            if (loggingTA.getDocument().getLength() > 0) {
                loggingTA.append(System.lineSeparator());
            }
            loggingTA.append(message);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            appendMessage.run();
        } else {
            SwingUtilities.invokeLater(appendMessage);
        }
    }

    private void loadLocalConfig() {
        try {
            Path configFile = getConfigPath();
            projectConfigStore = new ProjectConfigStore(configFile);
            for (SignerConfigBean project : projectConfigStore.projects()) {
                projectSelector.addItem(project.getProjectName());
            }
            if (!projectConfigStore.projects().isEmpty()) {
                inPathTF.setText(projectConfigStore.projects().get(0).getIn());
            }
            projectStatus.setText("已加载 " + projectConfigStore.projects().size() + " 个项目，自动按 APK 包名匹配");
        } catch (IOException | RuntimeException exception) {
            log("读取配置失败: " + exception.getMessage());
            projectStatus.setText("项目配置加载失败，请检查 cfg.properties");
        }
    }

    private void clearProjectSelection() {
        activeProject = null;
        readOnly = false;
        ksPathTF.setText("");
        ksPassPF.setText("");
        keyPassPF.setText("");
        channelPathTF.setText("");
        resetKeyAliasOptions();
        applyReadOnlyState();
    }

    private void applyProject(SignerConfigBean project) {
        activeProject = project;
        readOnly = project.isReadOnly();
        ksPathTF.setText(project.getKs());
        ksPassPF.setText(project.getKsPass());
        keyPassPF.setText(project.getKeyPass());
        channelPathTF.setText(project.getChannelList());
        resetKeyAliasOptions();
        if (!project.getKsKeyAlias().isEmpty() && !AUTO_KEY_ALIAS.equals(project.getKsKeyAlias())) {
            keyAliasCB.addItem(project.getKsKeyAlias());
            keyAliasCB.setSelectedItem(project.getKsKeyAlias());
        }
        savePwCheckBox.setSelected(!project.getKsPass().isEmpty() || !project.getKeyPass().isEmpty());
        File input = new File(inPathTF.getText());
        if (input.isFile()) {
            configureOutputPath(input);
        } else {
            inPathTF.setText(project.getIn());
            outPathTF.setText(project.getOut());
        }
        projectStatus.setText("当前项目：" + project.getProjectName() + "  包名：" + project.getApplicationPackageName());
        applyReadOnlyState();
    }

    private void setInput(File file) {
        setInput(file, null);
    }

    private void setInput(File file, String configuredFileName) {
        inputFileName = configuredFileName == null || configuredFileName.isEmpty()
                ? file.getName()
                : configuredFileName;
        inPathTF.setText(file.getAbsolutePath());
        configureOutputPath(file);
    }

    private void configureOutputPath(File input) {
        String outputName = deriveOutputFileName(input.getName());
        if (activeProject == null || activeProject.getOut().isEmpty()) {
            outPathTF.setText(new File(input.getParent(), outputName).toString());
            return;
        }
        Path configured = Paths.get(activeProject.getOut());
        String lowerName = configured.toString().toLowerCase(java.util.Locale.ROOT);
        boolean outputFile = lowerName.endsWith(".apk") || lowerName.endsWith(".aab");
        outPathTF.setText((outputFile && !Files.isDirectory(configured)
                ? configured : configured.resolve(outputName)).toString());
    }

    /** Applies the product-specific naming rules used for signed output files. */
    private static String deriveOutputFileName(String inputName) {
        int extensionIndex = inputName.lastIndexOf('.');
        if (extensionIndex < 0) {
            return inputName;
        }

        String extension = inputName.substring(extensionIndex);
        String outputName = inputName;
        outputName = outputName.replaceFirst("未加固_", "");
        if (outputName.startsWith("dx_unsigned")) {
            outputName = "正式" + outputName.substring("dx_unsigned".length());
        }

        int jiaguMarker = outputName.indexOf("_jiagu");
        if (jiaguMarker >= 0) {
            outputName = outputName.substring(0, Math.max(0, jiaguMarker - 4)) + extension;
        }

        int unsignedMarker = outputName.indexOf("_unsign");
        if (unsignedMarker >= 0) {
            outputName = outputName.substring(0, unsignedMarker) + extension;
        }

        int protectedMarker = outputName.indexOf("_protected");
        if (protectedMarker >= 0) {
            outputName = outputName.substring(0, protectedMarker) + extension;
        }

        return outputName;
    }

    private Path getConfigPath() throws IOException {
        Path directory = applicationRoot.isEmpty() ? getHistoryPath().getParent()
                : Paths.get(applicationRoot).toAbsolutePath().normalize();
        Path config = directory.resolve(CONFIG_FILE_NAME);
        Path legacyConfig = directory.resolve(CONFIG_DIRECTORY).resolve(CONFIG_FILE_NAME);
        return !Files.exists(config) && Files.isRegularFile(legacyConfig) ? legacyConfig : config;
    }

    /**
     * Resolves the history file beside the running JAR, with an IDE-friendly
     * fallback.
     */
    private Path getHistoryPath() {
        try {
            if (UX.class.getProtectionDomain().getCodeSource() != null) {
                Path codeLocation = Paths.get(
                        UX.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (Files.isRegularFile(codeLocation) && codeLocation.getParent() != null) {
                    return codeLocation.getParent().resolve(HISTORY_FILE_NAME);
                }
            }
        } catch (URISyntaxException | RuntimeException exception) {
            log("无法定位运行中的 JAR，将使用应用目录保存历史: " + exception.getMessage());
        }

        Path fallbackDirectory = applicationRoot.isEmpty()
                ? Paths.get("").toAbsolutePath()
                : Paths.get(applicationRoot).toAbsolutePath();
        if (Files.isRegularFile(fallbackDirectory) && fallbackDirectory.getParent() != null) {
            fallbackDirectory = fallbackDirectory.getParent();
        }
        return fallbackDirectory.normalize().resolve(HISTORY_FILE_NAME);
    }

    /**
     * 从界面线程传递给签名线程的不可变参数快照。
     */
    private static final class SigningRequest {
        private final Path inputPath;
        private final Path outputPath;
        private final Path keyStorePath;
        private final Path channelListPath;
        private final String inputFileName;
        private final String keyStorePassword;
        private final String keyPassword;
        private final String keyAlias;
        private final String originalProgressText;
        private final String applicationPackageName;

        private SigningRequest(Path inputPath, Path outputPath, Path keyStorePath, Path channelListPath,
                String inputFileName, String keyStorePassword, String keyPassword,
                String keyAlias, String originalProgressText, String applicationPackageName) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.keyStorePath = keyStorePath;
            this.channelListPath = channelListPath;
            this.inputFileName = inputFileName;
            this.keyStorePassword = keyStorePassword;
            this.keyPassword = keyPassword;
            this.keyAlias = keyAlias;
            this.originalProgressText = originalProgressText;
            this.applicationPackageName = applicationPackageName;
        }

        private boolean hasChannelList() {
            return channelListPath != null;
        }
    }

    {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        topPanel = new JPanel();
        topPanel.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
        tabbedPane1 = new JTabbedPane();
        topPanel.add(tabbedPane1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                        new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("Apk签名 & 多渠道", panel1);
        inPathTF = new JTextField();
        inPathTF.setEditable(false);
        inPathTF.setText("");
        panel1.add(inPathTF,
                new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                        new Dimension(150, -1), null, 0, false));
        ksPathTF = new JTextField();
        panel1.add(ksPathTF,
                new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                        new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("输入apk/aab");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        inBtn = new JButton();
        inBtn.setText("1.选择输入APK");
        panel1.add(inBtn,
                new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("KeyStore");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ksBtn = new JButton();
        ksBtn.setText("2.选择KeyStore");
        panel1.add(ksBtn,
                new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("KeyStore密码");
        panel1.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("3.输入KeyStore密码");
        panel1.add(label4, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        outPathTF = new JTextField();
        panel1.add(outPathTF,
                new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                        new Dimension(150, -1), null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("输出apk/aab");
        panel1.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        savePwCheckBox = new JCheckBox();
        savePwCheckBox.setSelected(true);
        savePwCheckBox.setText("保存密码");
        panel1.add(savePwCheckBox,
                new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ksPassPF = new JPasswordField();
        panel1.add(ksPassPF,
                new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                        new Dimension(150, -1), null, 0, false));
        channelPathTF = new JTextField();
        panel1.add(channelPathTF,
                new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                        new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("渠道清单[可选]");
        panel1.add(label6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        channelBtn = new JButton();
        channelBtn.setText("选择渠道清单");
        panel1.add(channelBtn,
                new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("高级", panel2);
        keyAliasCB = new JComboBox<>();
        panel2.add(keyAliasCB,
                new GridConstraints(1, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                        false));
        final JLabel label7 = new JLabel();
        label7.setText("KeyAlias");
        panel2.add(label7, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        keyPassPF = new JPasswordField();
        panel2.add(keyPassPF,
                new GridConstraints(2, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                        new Dimension(150, -1), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("证书密码");
        panel2.add(label8, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("<html>如果 KeyStore 包含多个证书，<br>或证书密码与 KeyStore 密码不同，请设置下列参数。</html>");
        panel2.add(label9, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        v2SigningEnabledCheckBox = new JCheckBox();
        v2SigningEnabledCheckBox.setEnabled(false);
        v2SigningEnabledCheckBox.setSelected(true);
        v2SigningEnabledCheckBox.setText("--v2-signing-enabled");
        panel2.add(v2SigningEnabledCheckBox,
                new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        v1SigningEnabledCheckBox = new JCheckBox();
        v1SigningEnabledCheckBox.setEnabled(false);
        v1SigningEnabledCheckBox.setSelected(true);
        v1SigningEnabledCheckBox.setText("--v1-signing-enabled");
        panel2.add(v1SigningEnabledCheckBox,
                new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        topPanel.add(panel3,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                        0, false));
        signBtn = new JButton();
        signBtn.setText("         4.签名         ");
        panel3.add(signBtn,
                new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        progressBar1 = new JProgressBar();
        progressBar1.setString("点击\"4.签名\"按钮开始  >>>>");
        progressBar1.setStringPainted(true);
        panel3.add(progressBar1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                        false));
        final JScrollPane scrollPane1 = new JScrollPane();
        topPanel.add(scrollPane1,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null,
                        0, false));
        scrollPane1.setBorder(BorderFactory.createTitledBorder("运行日志"));
        loggingTA = new JTextArea();
        loggingTA.setDoubleBuffered(true);
        loggingTA.setEditable(true);
        loggingTA.setInheritsPopupMenu(true);
        loggingTA.setLineWrap(true);
        loggingTA.setText("   点击“4.签名”按钮开始签名...");
        scrollPane1.setViewportView(loggingTA);
        label1.setLabelFor(inPathTF);
        label2.setLabelFor(ksPathTF);
        label3.setLabelFor(ksPassPF);
        label4.setLabelFor(ksPassPF);
        label5.setLabelFor(outPathTF);
        label7.setLabelFor(keyAliasCB);
        label8.setLabelFor(keyPassPF);

        fixFontStyle();
    }

    /**
     * 优化风格
     */
    private void fixFontStyle() {
        FontUIResource defaultFont = new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 18);
        setComponentFont(topPanel, defaultFont);

        int iconSize = 32;
        // 选择器的字体
        UIManager.put("FileChooser.listFont", new Font(Font.SERIF, Font.PLAIN, 26));
        // 设置文件选择器的文件图标大小
        updateIconSize("FileView.directoryIcon", iconSize);
        updateIconSize("FileView.fileIcon", iconSize);
        updateIconSize("FileChooser.upFolderIcon", iconSize);
        updateIconSize("FileChooser.homeFolderIcon", iconSize);
        updateIconSize("FileChooser.newFolderIcon", iconSize);
        updateIconSize("FileChooser.listViewIcon", iconSize);
        updateIconSize("FileChooser.detailsViewIcon", iconSize);
    }

    private static void updateIconSize(String iconName, int size) {
        try {
            UIManager.put(iconName, createScaledIcon(UIManager.getIcon(iconName), size, size));
        } catch (RuntimeException exception) {
            System.err.println("无法缩放图标 " + iconName + ": " + exception.getMessage());
        }
    }

    private static Icon createScaledIcon(Icon originalIcon, int width, int height) {
        if (originalIcon instanceof ImageIcon) {
            Image image = ((ImageIcon) originalIcon).getImage();
            Image scaledImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaledImage);
        }
        return originalIcon;
    }

    private static void setComponentFont(Component component, Font font) {
        component.setFont(font);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setComponentFont(child, font);
            }
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return topPanel;
    }

}
