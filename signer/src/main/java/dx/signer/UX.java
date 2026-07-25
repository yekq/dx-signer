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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.FileChooserUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.TableRowSorter;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

import dx.channel.ApkSigns;

public final class UX {
    private static final String AUTO_KEY_ALIAS = "{{auto}}";
    private static final String CONFIG_DIRECTORY = "etc";
    private static final String CONFIG_FILE_NAME = "cfg.properties";
    private static final String CONFIG_KEY_STORE = "ks";
    private static final String CONFIG_INPUT = "in";
    private static final String CONFIG_OUTPUT = "out";
    private static final String CONFIG_KEY_ALIAS = "ks-key-alias";
    private static final String CONFIG_INPUT_FILE_NAME = "in-filename";
    private static final String CONFIG_CHANNEL_LIST = "channel-list";
    private static final String CONFIG_KEY_STORE_PASSWORD = "ks-pass";
    private static final String CONFIG_KEY_PASSWORD = "key-pass";
    private static final String FILE_CHOOSER_SORT_LISTENER_INSTALLED =
            "dx.signer.fileChooserSortListenerInstalled";
    private static final int DETAILS_VIEW = 1;
    private static String applicationRoot = "";
    private final ExecutorService signingExecutor = Executors.newSingleThreadExecutor();
    // These fields are bound by UX.form; binding names must remain synchronized.
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
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        mainWindowWidth = screenSize.width * 9 / 10;
        mainWindowHeight = screenSize.height * 9 / 10;
        initialChooserShown = false;

        UX ux = new UX();
        JFrame frame = new JFrame("Apk签名&多渠道工具:" + applicationRoot);
        frame.setContentPane(ux.topPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(mainWindowWidth, mainWindowHeight);
        frame.setLocationRelativeTo(null);
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
    }

    /** Creates a chooser that uses the native system appearance and a large details view. */
    public static JFileChooser windowsJFileChooser() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int baseWidth = mainWindowWidth > 0 ? mainWindowWidth : screenSize.width * 9 / 10;
        int baseHeight = mainWindowHeight > 0 ? mainWindowHeight : screenSize.height * 9 / 10;
        int dialogWidth = baseWidth * 3 / 4;
        int dialogHeight = baseHeight * 3 / 4;

        LookAndFeel previousLookAndFeel = UIManager.getLookAndFeel();
        JFileChooser chooser;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            chooser = new JFileChooser() {
                @Override
                protected JDialog createDialog(Component parent) throws HeadlessException {
                    JDialog dialog = super.createDialog(parent);
                    dialog.setLocationRelativeTo(parent);
                    configureFileChooserDetailsView(this);
                    return dialog;
                }
            };
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException exception) {
            System.err.println("无法启用系统文件选择器外观: " + exception.getMessage());
            chooser = new JFileChooser();
        } finally {
            try {
                UIManager.setLookAndFeel(previousLookAndFeel);
            } catch (UnsupportedLookAndFeelException exception) {
                System.err.println("无法恢复界面外观: " + exception.getMessage());
            }
        }

        configureFileChooserDetailsView(chooser);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setPreferredSize(new Dimension(dialogWidth, dialogHeight));
        chooser.setControlButtonsAreShown(true);
        chooser.setMultiSelectionEnabled(false);
        return chooser;
    }

    /**
     * Keeps details view sorted by modification time after the chooser changes directories.
     * The Windows chooser exposes this behavior only through its internal file pane API.
     */
    private static void configureFileChooserDetailsView(JFileChooser fileChooser) {
        applyModifiedTimeSort(fileChooser);
        if (fileChooser.getClientProperty(FILE_CHOOSER_SORT_LISTENER_INSTALLED) != null) {
            return;
        }

        fileChooser.putClientProperty(FILE_CHOOSER_SORT_LISTENER_INSTALLED, Boolean.TRUE);
        fileChooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY,
                event -> applyModifiedTimeSort(fileChooser));
    }
    private static void applyModifiedTimeSort(JFileChooser fileChooser) {
        SwingUtilities.invokeLater(() -> {
            try {
                FileChooserUI chooserUI = fileChooser.getUI();
                Field filePaneField = chooserUI.getClass().getDeclaredField("filePane");
                filePaneField.setAccessible(true);
                Object filePane = filePaneField.get(chooserUI);

                Method setViewType = filePane.getClass().getDeclaredMethod("setViewType", int.class);
                setViewType.setAccessible(true);
                setViewType.invoke(filePane, DETAILS_VIEW);

                Field detailsTableField = filePane.getClass().getDeclaredField("detailsTable");
                detailsTableField.setAccessible(true);
                JTable detailsTable = (JTable) detailsTableField.get(filePane);
                if (!(detailsTable.getRowSorter() instanceof TableRowSorter)) {
                    return;
                }

                int modifiedTimeColumn = -1;
                for (int modelColumn = 0; modelColumn < detailsTable.getModel().getColumnCount(); modelColumn++) {
                    if (Date.class.isAssignableFrom(detailsTable.getModel().getColumnClass(modelColumn))) {
                        modifiedTimeColumn = modelColumn;
                        break;
                    }
                }
                if (modifiedTimeColumn < 0) {
                    return;
                }

                TableRowSorter<?> rowSorter = (TableRowSorter<?>) detailsTable.getRowSorter();
                rowSorter.setSortable(modifiedTimeColumn, true);
                rowSorter.setSortKeys(Collections.singletonList(
                        new RowSorter.SortKey(modifiedTimeColumn, SortOrder.DESCENDING)));
                rowSorter.sort();

                int viewColumn = detailsTable.convertColumnIndexToView(modifiedTimeColumn);
                if (viewColumn >= 0) {
                    detailsTable.getColumnModel().getColumn(viewColumn)
                            .setPreferredWidth(mainWindowWidth * 2 / 15);
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                System.err.println("无法设置文件列表排序: " + exception.getMessage());
            }
        });
    }
    public UX() {
        configureKeyAliasSelector();
        loadLocalConfig();
        configureActions();
        configureLogging();
        applyReadOnlyState();
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
        if (!readOnly) {
            return;
        }

        savePwCheckBox.setEnabled(false);
        savePwCheckBox.setSelected(false);
        channelBtn.setEnabled(false);
        channelPathTF.setEnabled(false);
        inBtn.setEnabled(false);
        inPathTF.setEnabled(false);
        outPathTF.setEnabled(false);

        if (!ksPathTF.getText().isEmpty()) {
            ksBtn.setEnabled(false);
            ksPathTF.setEnabled(false);
            keyAliasCB.setEnabled(false);
            ksPassPF.setEnabled(false);
            keyPassPF.setEnabled(false);
        }
    }

    private JFileChooser createFileChooser(JTextComponent pathField, FileFilter filter) {
        JFileChooser fileChooser = windowsJFileChooser();
        fileChooser.setFileFilter(filter);

        String currentPath = pathField.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            fileChooser.setCurrentDirectory(currentFile.isDirectory() ? currentFile : currentFile.getParentFile());
        }
        return fileChooser;
    }

    private static FileFilter createExtensionFilter(String description, String... extensions) {
        return new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    return true;
                }
                String lowerCaseName = file.getName().toLowerCase(Locale.ROOT);
                for (String extension : extensions) {
                    if (lowerCaseName.endsWith(extension)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    private void showChooseAppFileDialog() {
        JFileChooser fileChooser = createFileChooser(inPathTF,
                createExtensionFilter("*.apk, *.aab", ".apk", ".aab"));
        if (fileChooser.showOpenDialog(inBtn) == JFileChooser.APPROVE_OPTION) {
            setInput(fileChooser.getSelectedFile());
            onSubmitClick();
        }
    }

    private void showKeystoreFileDialog() {
        JFileChooser fileChooser = createFileChooser(ksPathTF,
                createExtensionFilter("*.ks, *.keystore, *.p12, *.pfx, *.jks",
                        ".ks", ".keystore", ".p12", ".pfx", ".jks"));
        if (fileChooser.showOpenDialog(ksBtn) == JFileChooser.APPROVE_OPTION) {
            ksPathTF.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void showChannelFileDialog() {
        JFileChooser fileChooser = createFileChooser(channelPathTF,
                createExtensionFilter("*.txt", ".txt"));
        if (fileChooser.showOpenDialog(channelBtn) == JFileChooser.APPROVE_OPTION) {
            channelPathTF.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }
    private void onSubmitClick() {
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
                inputFileName, keyStorePassword, keyPassword, keyAlias, progressBar1.getString());
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

    /** Persists non-secret paths and optionally the passwords selected by the user. */
    private void saveLocalConfig(SigningRequest request) {
        if (readOnly) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(CONFIG_KEY_STORE, request.keyStorePath.toString());
        properties.setProperty(CONFIG_INPUT, parentPath(request.inputPath));
        properties.setProperty(CONFIG_KEY_ALIAS, request.keyAlias);
        properties.setProperty(CONFIG_INPUT_FILE_NAME, "");
        properties.setProperty(CONFIG_OUTPUT, parentPath(request.outputPath));
        properties.setProperty(CONFIG_CHANNEL_LIST,
                request.channelListPath == null ? "" : request.channelListPath.toString());

        if (savePwCheckBox.isSelected()) {
            properties.setProperty(CONFIG_KEY_STORE_PASSWORD, request.keyStorePassword);
            properties.setProperty(CONFIG_KEY_PASSWORD, request.keyPassword);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(getConfigPath(), StandardCharsets.UTF_8)) {
            properties.store(writer, "#");
        } catch (IOException exception) {
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
                        request.keyPassword);
            } else {
                actualOutputPath = request.outputPath;
                result = SignWorker.signApk(
                        request.inputPath,
                        actualOutputPath,
                        request.keyStorePath,
                        request.keyStorePassword,
                        request.keyAlias,
                        request.keyPassword);
            }

            Path completedOutputPath = actualOutputPath;
            SwingUtilities.invokeLater(() -> showSigningResult(request, completedOutputPath, result));
        } catch (Exception exception) {
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
        signBtn.setEnabled(!signing);
        progressBar1.setIndeterminate(signing);
        progressBar1.setString(signing ? "签名中..." : idleText);
        progressBar1.setStringPainted(true);
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
            if (!Files.isRegularFile(configFile)) {
                return;
            }

            SignerConfigBean config = new SignerConfigBean(CommandLine.load(configFile));
            readOnly = config.isReadOnly();
            ksPathTF.setText(config.getKs());
            ksPassPF.setText(config.getKsPass());
            keyPassPF.setText(config.getKeyPass());
            channelPathTF.setText(config.getChannelList());

            if (!config.getIn().isEmpty()) {
                setInput(new File(config.getIn()), config.getInFilename());
            }
            if (!config.getOut().isEmpty()) {
                outPathTF.setText(config.getOut());
            }
            if (!AUTO_KEY_ALIAS.equals(config.getKsKeyAlias()) && !config.getKsKeyAlias().isEmpty()) {
                keyAliasCB.addItem(config.getKsKeyAlias());
                keyAliasCB.setSelectedItem(config.getKsKeyAlias());
            }
            inPathTF.setText(config.getIn());
        } catch (IOException exception) {
            log("读取配置失败: " + exception.getMessage());
        }
    }

    private void setInput(File file) {
        setInput(file, null);
    }

    private void setInput(File file, String configuredFileName) {
        inputFileName = configuredFileName == null || configuredFileName.isEmpty()
                ? file.getName()
                : configuredFileName;
        inPathTF.setText(file.getAbsolutePath());
        outPathTF.setText(new File(file.getParent(), deriveOutputFileName(inputFileName)).toString());
    }

    /** Applies the product-specific naming rules used for signed output files. */
    private static String deriveOutputFileName(String inputName) {
        int extensionIndex = inputName.lastIndexOf('.');
        if (extensionIndex < 0) {
            return inputName;
        }

        String extension = inputName.substring(extensionIndex);
        String outputName = inputName;
        if (outputName.startsWith("dx_unsigned")) {
            outputName = "正式" + outputName.substring("dx_unsigned".length());
        }

        int protectedMarker = outputName.indexOf("_jiagu");
        if (protectedMarker >= 0) {
            outputName = outputName.substring(0, Math.max(0, protectedMarker - 4)) + extension;
        }

        int unsignedMarker = outputName.indexOf("_unsign");
        if (unsignedMarker >= 0) {
            outputName = outputName.substring(0, unsignedMarker) + extension;
        }
        return outputName;
    }

    private Path getConfigPath() throws IOException {
        Path configDirectory = Paths.get(applicationRoot).resolve(CONFIG_DIRECTORY);
        Files.createDirectories(configDirectory);
        return configDirectory.resolve(CONFIG_FILE_NAME);
    }

    /** Immutable snapshot passed from the Swing event thread to the signing worker. */
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

        private SigningRequest(Path inputPath, Path outputPath, Path keyStorePath, Path channelListPath,
                               String inputFileName, String keyStorePassword, String keyPassword,
                               String keyAlias, String originalProgressText) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.keyStorePath = keyStorePath;
            this.channelListPath = channelListPath;
            this.inputFileName = inputFileName;
            this.keyStorePassword = keyStorePassword;
            this.keyPassword = keyPassword;
            this.keyAlias = keyAlias;
            this.originalProgressText = originalProgressText;
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
        topPanel.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("Apk签名 & 多渠道", panel1);
        inPathTF = new JTextField();
        inPathTF.setEditable(false);
        inPathTF.setText("");
        panel1.add(inPathTF, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        ksPathTF = new JTextField();
        panel1.add(ksPathTF, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("输入apk/aab");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        inBtn = new JButton();
        inBtn.setText("1.选择输入APK");
        panel1.add(inBtn, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("KeyStore");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ksBtn = new JButton();
        ksBtn.setText("2.选择KeyStore");
        panel1.add(ksBtn, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("KeyStore密码");
        panel1.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("3.输入KeyStore密码");
        panel1.add(label4, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        outPathTF = new JTextField();
        panel1.add(outPathTF, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("输出apk/aab");
        panel1.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        savePwCheckBox = new JCheckBox();
        savePwCheckBox.setSelected(true);
        savePwCheckBox.setText("保存密码");
        panel1.add(savePwCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ksPassPF = new JPasswordField();
        panel1.add(ksPassPF, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        channelPathTF = new JTextField();
        panel1.add(channelPathTF, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("渠道清单[可选]");
        panel1.add(label6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        channelBtn = new JButton();
        channelBtn.setText("选择渠道清单");
        panel1.add(channelBtn, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("高级", panel2);
        keyAliasCB = new JComboBox<>();
        panel2.add(keyAliasCB, new GridConstraints(1, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("KeyAlias");
        panel2.add(label7, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        keyPassPF = new JPasswordField();
        panel2.add(keyPassPF, new GridConstraints(2, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("证书密码");
        panel2.add(label8, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("如果您的Keystore包含多个证书，或者您的证书密码与Keystore密码不同, 请设置下列参数");
        panel2.add(label9, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        v2SigningEnabledCheckBox = new JCheckBox();
        v2SigningEnabledCheckBox.setEnabled(false);
        v2SigningEnabledCheckBox.setSelected(true);
        v2SigningEnabledCheckBox.setText("--v2-signing-enabled");
        panel2.add(v2SigningEnabledCheckBox, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        v1SigningEnabledCheckBox = new JCheckBox();
        v1SigningEnabledCheckBox.setEnabled(false);
        v1SigningEnabledCheckBox.setSelected(true);
        v1SigningEnabledCheckBox.setText("--v1-signing-enabled");
        panel2.add(v1SigningEnabledCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        topPanel.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        signBtn = new JButton();
        signBtn.setText("         4.签名         ");
        panel3.add(signBtn, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        progressBar1 = new JProgressBar();
        progressBar1.setString("点击\"4.签名\"按钮开始  >>>>");
        progressBar1.setStringPainted(true);
        panel3.add(progressBar1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        topPanel.add(scrollPane1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
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
        FontUIResource defaultFont = new FontUIResource(Font.SERIF, Font.BOLD, 24);
        setComponentFont(topPanel, defaultFont);

        int iconSize = 32;
        //选择器的字体
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
