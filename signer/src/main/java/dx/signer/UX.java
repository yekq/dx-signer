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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
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

import dx.channel.ApkSigns;

public class UX {
    private static String rootPath = "";
    ExecutorService es = Executors.newSingleThreadExecutor();
    private JButton inBtn;
    private JTextField inPathTF;
    private JTabbedPane tabbedPane1;
    private JTextField ksPathTF;
    private JButton ksBtn;
    private JTextField outPathTF;
    private JButton signBtn;
    private JTextArea loggingTA;
    private JCheckBox 保存密码CheckBox;
    private JComboBox keyAliasCB;
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

    private SignerConfigBean configBean;

    public static void main(String[] args) throws IOException {

        System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false");
        System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");

        // 设置全局样式属性
//        UIManager.put("OptionPane.background", Color.LIGHT_GRAY);
//        UIManager.put("Panel.background", Color.LIGHT_GRAY);
//        UIManager.put("Button.background", Color.WHITE);
//        UIManager.put("Button.foreground", Color.BLACK);

        if (args.length >= 1 && args[0].equals("sign")) {
            CommandLine.main(args);
            return;
        }
        if (args.length >= 1 && args[0].equals("-path")) {
            rootPath = args[1];
        }

        JFrame frame = new JFrame("Apk签名&多渠道工具");
        frame.setContentPane(new UX().topPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.pack();

        // make the frame half the height and width
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width1 = screenSize.width * 9 / 10;
        int height1 = screenSize.height * 9 / 10;

        frame.setSize(width1, height1);

        // here's the part where i center the jframe on screen
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
    }

    /**
     * This method returns JFileChooser with Windows look instead of native java
     */
    public static JFileChooser windowsJFileChooser() {
        LookAndFeel previousLF = UIManager.getLookAndFeel();
        JFileChooser chooser;
        // 获取屏幕大小
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            chooser = new JFileChooser() {

                @Override
                protected JDialog createDialog(Component parent) throws HeadlessException {

                    // 计算 JFileChooser 的尺寸
                    Dimension dialogSize = getPreferredSize();
                    // 计算中心位置
                    int x = (screenSize.width - dialogSize.width) / 2;
                    int y = (screenSize.height - dialogSize.height) / 2;
                    // 设置 JFileChooser 的位置
                    parent.setLocation(x, y);
                    return super.createDialog(parent);
                }
            };
            UIManager.setLookAndFeel(previousLF);
        } catch (IllegalAccessException | UnsupportedLookAndFeelException | InstantiationException | ClassNotFoundException e) {
            e.printStackTrace();
            chooser = new JFileChooser();
        }
        setFileChooserDetailsView(chooser);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setPreferredSize(new Dimension(screenSize.width / 2, screenSize.height / 2));
        // Enable the address bar for input
        chooser.setControlButtonsAreShown(true);
        chooser.setMultiSelectionEnabled(false);
        return chooser;
    }

    /**
     * 设置字体,默认排序
     */
    private static void setFileChooserDetailsView(JFileChooser fileChooser) {
        try {
            FileChooserUI chooserUI = fileChooser.getUI();
            // 获取 filePane 字段
            Field filePaneField = chooserUI.getClass().getDeclaredField("filePane");
            filePaneField.setAccessible(true);
            Object filePane = filePaneField.get(chooserUI);

            // 调用 setViewType 方法设置为详细信息视图
            Method setViewType = filePane.getClass().getDeclaredMethod("setViewType", int.class);
            setViewType.setAccessible(true);
            setViewType.invoke(filePane, 1); // 1 表示详细信息视图

            // 获取 filePane 内部的 detailsTable 字段
            Field detailsTableField = filePane.getClass().getDeclaredField("detailsTable");
            detailsTableField.setAccessible(true);
            JTable detailsTable = (JTable) detailsTableField.get(filePane);

            // 获取 detailsTable 的 rowSorter
            TableRowSorter<?> rowSorter = (TableRowSorter<?>) detailsTable.getRowSorter();
            // 设置默认排序方式（按文件名称排序）
            int dateTimeIndex = 3;
            rowSorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(dateTimeIndex, SortOrder.DESCENDING)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public UX() {
        loadLocalConfig();

        JFileChooser fileChooser = windowsJFileChooser();
        fileChooser.setCurrentDirectory(new File(configBean.getIn()));

        inBtn.addActionListener(e -> {
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    String s = f.getName().toLowerCase();
                    return f.isDirectory() || s.endsWith(".apk") || s.endsWith(".aab");
                }

                @Override
                public String getDescription() {
                    return "*.apk,*.aab";
                }
            });
            int result = fileChooser.showOpenDialog(inBtn);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                setInput(file);
                onSubmitClick();
            }
        });
        ksBtn.addActionListener(e -> {
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    String s = f.getName().toLowerCase();
                    return f.isDirectory() || s.endsWith(".ks") || s.endsWith(".keystore") || s.endsWith(".p12") || s.endsWith(".pfx") || s.endsWith(".jks");
                }

                @Override
                public String getDescription() {
                    return "*.ks, *.keystore, *.p12, *.pfx, *.jks";
                }
            });
            int result = fileChooser.showOpenDialog(ksBtn);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                ksPathTF.setText(file.getAbsolutePath());
            }
        });

        channelBtn.addActionListener(e -> {
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    String s = f.getName().toLowerCase();
                    return f.isDirectory() || (f.isFile() && s.endsWith(".txt"));
                }

                @Override
                public String getDescription() {
                    return "*.txt";
                }
            });
            int result = fileChooser.showOpenDialog(channelBtn);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                channelPathTF.setText(file.getAbsolutePath());
            }
        });

        signBtn.addActionListener(e -> onSubmitClick());

        keyAliasCB.removeAllItems();
        keyAliasCB.addItem("{{auto}}");
        keyAliasCB.setSelectedItem("{{auto}}");
        keyAliasCB.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                KeyStore keyStore = null;
                try {
                    byte[] d = Files.readAllBytes(Paths.get(ksPathTF.getText()));
                    Set<String> keyList = new HashSet<>();
                    keyList.add(new String(ksPassPF.getPassword()));
                    keyStore = ApkSigns.loadKeyStore(d, keyList);
                } catch (Exception ignore) {
                    keyStore = null;
                }

                if (keyStore != null) {
                    keyAliasCB.removeAllItems();
                    keyAliasCB.addItem("{{auto}}");
                    try {
                        Enumeration<String> aliases = keyStore.aliases();
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            keyAliasCB.addItem(alias);
                        }
                    } catch (Exception ignore) {
                    }
                    keyAliasCB.setSelectedItem("{{auto}}");
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {

            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {

            }
        });


        DefaultCaret caret = (DefaultCaret) loggingTA.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JTextAreaOutputStream.hijack(loggingTA);

        if (readOnly) {
            保存密码CheckBox.setEnabled(false);
            保存密码CheckBox.setSelected(false);
            channelBtn.setEnabled(false);
            channelPathTF.setEnabled(false);

            inBtn.setEnabled(false);
            inPathTF.setEnabled(false);

            if (ksPathTF.getText().length() > 0) {
                ksBtn.setEnabled(false);
                ksPathTF.setEnabled(false);
                keyAliasCB.setEnabled(false);
                ksPassPF.setEnabled(false);
                keyPassPF.setEnabled(false);
            }
            outPathTF.setEnabled(false);
        }
    }

    private void onSubmitClick() {
        String channelPath = channelPathTF.getText();

        String out = outPathTF.getText();
        Path outApkDir = CommandLine.detectOutDir(out);
        File file = outApkDir.toFile();
        if (channelPath != null && !channelPath.isEmpty()) {
            if (Files.exists(outApkDir)) {
                if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(topPanel, "多渠道输出APK目录已经存在，是否覆盖:\n" + outApkDir, "输出APK已经存在，是否覆盖", JOptionPane.OK_CANCEL_OPTION)) {
                    return;
                }
            }
        } else {
            if (file.isFile() && file.exists() && JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(topPanel, "输出APK已经存在，是否覆盖:\n" + out, "输出APK已经存在，是否覆盖", JOptionPane.OK_CANCEL_OPTION)) {
                return;
            }
        }

        signBtn.setEnabled(false);

        String in = inPathTF.getText();

        String ksPass;
        String keyPass;
        try {
            ksPass = new String(ksPassPF.getPassword());
        } catch (NullPointerException ignore) {
            ksPass = "";
        }
        try {
            keyPass = new String(keyPassPF.getPassword());
        } catch (NullPointerException ignore) {
            keyPass = null;
        }
        String keyAlias = (String) keyAliasCB.getSelectedItem();
        Properties mConfig = new Properties();
        String ksPath0 = ksPathTF.getText();
        mConfig.put("ks", ksPath0);
        mConfig.put("in", in);
        mConfig.put("ks-key-alias", keyAlias);
        mConfig.put("in-filename", this.inputFileName);
        mConfig.put("out", this.outPathTF.getText());
        mConfig.put("channel-list", this.channelPathTF.getText());

        if (保存密码CheckBox.isSelected()) {
            mConfig.put("ks-pass", ksPass);
            mConfig.put("key-pass", keyPass);
        }

        if (!readOnly) {
            try {
                Path configFile = getConfigPath();
                try (BufferedWriter r = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                    mConfig.store(r, "#");
                }
            } catch (IOException ignore) {
            }
        }

        loggingTA.setText("");

        String finalKsPass = ksPass;
        String finalKeyPass = keyPass;
        String pbOrg = progressBar1.getString();
        progressBar1.setString("签名中...");
        progressBar1.setStringPainted(true);
        progressBar1.setIndeterminate(true);

        Path ksPath = Paths.get(ksPath0);
        Path input = Paths.get(in);

        es.submit(() -> {
            try {
                int result;

                if (channelPath != null && !channelPath.isEmpty()) {
                    Path apkDir = CommandLine.detectOutDir(out);
                    result = SignWorker.signChannelApk(input, inputFileName,
                            apkDir,
                            Paths.get(channelPath),
                            ksPath, finalKsPass, keyAlias, finalKeyPass);
                    progressBar1.setIndeterminate(false);
                    progressBar1.setString(pbOrg);
                    if (result == 0) {
                        JOptionPane.showMessageDialog(topPanel, "多渠道成功, 输出APK文件夹\n" + apkDir);
                    } else {
                        JOptionPane.showMessageDialog(topPanel, "多渠道失败");
                    }
                } else {
                    Path outPath = Paths.get(out);
                    result = SignWorker.signApk(input, outPath, ksPath,
                            finalKsPass, keyAlias, finalKeyPass);
                    progressBar1.setIndeterminate(false);
                    progressBar1.setString(pbOrg);
                    if (result == 0) {
                        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(topPanel, "签名成功, 输出APK\n" + out, "签名结果", JOptionPane.OK_CANCEL_OPTION)) {
                            Tools.openDir(topPanel, outPath.toFile().getParent());
                        }
                    } else {
                        JOptionPane.showMessageDialog(topPanel, "签名失败");
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }

            signBtn.setEnabled(true);
        });
    }


    private void loadLocalConfig() {
        try {
            Path configFile = getConfigPath();
            Properties initConfig = CommandLine.load(configFile);

            loggingTA.append("\n地址:" + rootPath);
            configBean = new SignerConfigBean(initConfig);

            readOnly = configBean.isReadOnly();
            ksPathTF.setText(configBean.getKs());

            String inPath = configBean.getIn();
            if (inPath.length() > 0) {
                setInput(new File(inPath), configBean.getInFilename());
            }
            String outPath = configBean.getOut();
            if (outPath.length() > 0) {
                outPathTF.setText(outPath);
            }
            ksPassPF.setText(configBean.getKsPass());
            keyPassPF.setText(configBean.getKsPass());
            channelPathTF.setText(configBean.getChannelList());

            String s = configBean.getKsKeyAlias();
            if (!s.equals("{{auto}}") && s.length() != 0) {
                keyAliasCB.addItem(s);
                keyAliasCB.setSelectedItem(s);
            }

        } catch (Exception ignore) {
            ignore.printStackTrace();
            configBean = new SignerConfigBean();
        }
    }

    private void setInput(File file) {
        setInput(file, null);
    }

    private void setInput(File file, String name) {
        if (name == null || name.length() == 0) {
            name = file.getName();
        }
        this.inputFileName = name;
        inPathTF.setText(file.getAbsolutePath());

        String fileName = inputFileName;
        String fileExtension = inputFileName.substring(inputFileName.lastIndexOf('.'));
        if (fileName.startsWith("dx_unsigned")) {
            fileName = "顶象_" + fileName.substring("dx_unsigned".length());
        }
        if (fileName.contains("_jiagu")) {
            int index = fileName.indexOf("_jiagu");
            fileName = fileName.substring(0, index - 4) + fileExtension;
        }
        if (fileName.contains("_unsign")) {
            int index = fileName.indexOf("_unsign");
            fileName = fileName.substring(0, index) + fileExtension;
        }
        File out = new File(file.getParent(), fileName);
        outPathTF.setText(out.toString());
    }

    private Path getConfigPath() {
        Path HOME = Paths.get(rootPath);
        Path configDir = HOME.resolve("etc");
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException ignore) {
                // Do Nothing
            }
        }
        Path config = configDir.resolve("cfg.properties");
        File fileConfig = config.toFile();
        System.out.println("-------启动-------:" + fileConfig.getAbsolutePath() + ",是否存在:" + fileConfig.exists());
        return config;
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
        保存密码CheckBox = new JCheckBox();
        保存密码CheckBox.setSelected(true);
        保存密码CheckBox.setText("保存密码");
        panel1.add(保存密码CheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
        keyAliasCB = new JComboBox();
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
        } catch (Exception e) {
            e.printStackTrace();
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

    public static void setComponentFont(Component component, Font font) {
        component.setFont(font);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setComponentFont(child, font);
            }
        }
    }

    public static void setLookAndFeel(String lookAndFeel) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (lookAndFeel.equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            // 更新当前显示的组件，以应用新的 Look and Feel
            SwingUtilities.updateComponentTreeUI(JFrame.getFrames()[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return topPanel;
    }

}
