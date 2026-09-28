package dx.signer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/28 18:43
 * description: 使用公开的 Swing 和 {@link FileSystemView} API 选择文件，支持路径粘贴和 APK 签名历史筛选。
 */
public final class FileChooserDialog extends JDialog {
    private static final int NAME_COLUMN = 0;
    private static final int SIZE_COLUMN = 1;
    private static final int TYPE_COLUMN = 2;
    private static final int MODIFIED_COLUMN = 3;
    private static final int CELL_PADDING = 18;

    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final FileTableModel tableModel = new FileTableModel(fileSystemView);
    private final JTable fileTable = new JTable(tableModel) {
        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component component = super.prepareRenderer(renderer, row, column);
            if (isRowSelected(row)) {
                component.setForeground(getSelectionForeground());
                component.setBackground(getSelectionBackground());
            } else {
                Color disabledColor = UIManager.getColor("Label.disabledForeground");
                component.setForeground(isInSigningHistory(row)
                        ? disabledColor == null ? Color.GRAY : disabledColor
                        : getForeground());
                Color accent = UIManager.getColor("Signer.accent");
                boolean recent = isRecentUnencryptedApk(row);
                component.setBackground(recent
                        ? blend(getBackground(), accent == null ? new Color(240, 176, 43) : accent)
                        : getBackground());
                if (convertColumnIndexToModel(column) == NAME_COLUMN && recent
                        && component instanceof JLabel) {
                    ((JLabel) component).setToolTipText("该目录最近新增的未加固 APK");
                }
            }
            return component;
        }
    };
    private final JTextField addressField = new JTextField();
    private final JCheckBox hideHistoryApksCheckBox = new JCheckBox("隐藏签名历史中的 APK", true);
    private final JButton selectButton = new JButton("选择");
    private final List<String> acceptedExtensions;
    private final Set<String> historyPathKeys;
    private final boolean choosingApk;

    private File currentDirectory;
    private File selectedFile;
    private File recentUnencryptedApk;

    private FileChooserDialog(Window owner, File initialPath, String filterDescription,
                              Set<String> historyPathKeys, String... acceptedExtensions) {
        super(owner, "选择文件", ModalityType.APPLICATION_MODAL);
        this.acceptedExtensions = normalizeExtensions(acceptedExtensions);
        this.historyPathKeys = historyPathKeys;
        this.choosingApk = this.acceptedExtensions.contains(".apk");
        initializeUi(filterDescription);
        navigateToInitialPath(initialPath);
    }

    /** 打开文件选择器；取消时返回 {@code null}。 */
    public static File chooseFile(Component parent, File initialPath, String filterDescription,
                                  Set<String> historyPathKeys, String... acceptedExtensions) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        FileChooserDialog dialog = new FileChooserDialog(
                owner, initialPath, filterDescription, historyPathKeys, acceptedExtensions);
        dialog.setVisible(true);
        return dialog.selectedFile;
    }

    private void initializeUi(String filterDescription) {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.add(createAddressBar(), BorderLayout.NORTH);
        if (choosingApk) {
            hideHistoryApksCheckBox.addActionListener(event -> refreshDirectory());
            header.add(hideHistoryApksCheckBox, BorderLayout.SOUTH);
        }
        add(header, BorderLayout.NORTH);
        configureTable();
        add(new JScrollPane(fileTable), BorderLayout.CENTER);
        add(createFooter(filterDescription), BorderLayout.SOUTH);

        Dimension screen = getToolkit().getScreenSize();
        setPreferredSize(new Dimension(screen.width * 3 / 4, screen.height * 3 / 4));
        pack();
        setLocationRelativeTo(getOwner());
    }

    private JPanel createAddressBar() {
        JButton upButton = createToolbarButton(
                UIManager.getIcon("FileChooser.upFolderIcon"), "上级目录", this::navigateUp);
        JButton homeButton = createToolbarButton(
                UIManager.getIcon("FileChooser.homeFolderIcon"), "主目录", this::navigateHome);
        JButton refreshButton = createToolbarButton(
                UIManager.getIcon("FileChooser.detailsViewIcon"), "刷新", this::refreshDirectory);
        addressField.setToolTipText("粘贴文件或目录路径后按 Enter");
        addressField.addActionListener(event -> navigateFromAddress());

        JPanel panel = new JPanel(new BorderLayout(6, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(upButton);
        buttons.add(homeButton);
        buttons.add(refreshButton);
        panel.add(buttons, BorderLayout.WEST);
        panel.add(addressField, BorderLayout.CENTER);
        return panel;
    }

    private JButton createToolbarButton(Icon icon, String tooltip, Runnable action) {
        JButton button = icon == null ? new JButton(tooltip) : new JButton(icon);
        button.setToolTipText(tooltip);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel createFooter(String filterDescription) {
        JLabel filterLabel = new JLabel("文件类型: " + filterDescription);
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(event -> dispose());
        selectButton.setEnabled(false);
        selectButton.addActionListener(event -> approveSelection());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(selectButton);
        actions.add(cancelButton);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(filterLabel, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private void configureTable() {
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        fileTable.setFillsViewportHeight(true);
        fileTable.setRowHeight(Math.max(fileTable.getRowHeight(), 28));
        fileTable.setDefaultRenderer(File.class,
                new FileNameRenderer(fileSystemView, historyPathKeys));
        fileTable.setDefaultRenderer(Long.class, new FileSizeRenderer());
        fileTable.setDefaultRenderer(Date.class, new DateRenderer());

        TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setSortKeys(Collections.singletonList(
                new RowSorter.SortKey(MODIFIED_COLUMN, SortOrder.DESCENDING)));
        fileTable.setRowSorter(sorter);
        fileTable.getSelectionModel().addListSelectionListener(event -> updateSelection());
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    openSelectedEntry();
                }
            }
        });
        fileTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    event.consume();
                    openSelectedEntry();
                } else if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    navigateUp();
                }
            }
        });
    }

    private void navigateToInitialPath(File initialPath) {
        File target = initialPath;
        if (target == null || !target.exists()) {
            target = fileSystemView.getDefaultDirectory();
        }
        if (target.isFile()) {
            File fileToSelect = target;
            loadDirectory(target.getParentFile());
            selectFile(fileToSelect);
        } else {
            loadDirectory(target);
        }
    }

    private void navigateFromAddress() {
        String rawPath = addressField.getText().trim();
        if (rawPath.length() >= 2 && rawPath.startsWith("\"") && rawPath.endsWith("\"")) {
            rawPath = rawPath.substring(1, rawPath.length() - 1);
        }
        if (rawPath.isEmpty()) {
            return;
        }
        File target = new File(rawPath);
        if (!target.exists()) {
            showPathError("路径不存在: " + rawPath);
        } else if (target.isDirectory()) {
            loadDirectory(target);
        } else if (accepts(target) && !shouldHideHistoryApk(target)) {
            loadDirectory(target.getParentFile());
            selectFile(target);
        } else if (shouldHideHistoryApk(target)) {
            showPathError("该 APK 已在签名历史中，请取消勾选隐藏选项后选择");
        } else {
            showPathError("文件类型不符合当前过滤条件");
        }
    }

    private void navigateUp() {
        if (currentDirectory != null && currentDirectory.getParentFile() != null) {
            loadDirectory(currentDirectory.getParentFile());
        }
    }

    private void navigateHome() {
        loadDirectory(fileSystemView.getDefaultDirectory());
    }

    private void refreshDirectory() {
        if (currentDirectory != null) {
            loadDirectory(currentDirectory);
        }
    }

    private void loadDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            showPathError("无法打开目录");
            return;
        }
        File[] files = fileSystemView.getFiles(directory, true);
        List<File> visibleFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory() || (accepts(file) && !shouldHideHistoryApk(file))) {
                visibleFiles.add(file);
            }
        }
        recentUnencryptedApk = choosingApk
                ? findRecentUnencryptedApk(visibleFiles, historyPathKeys)
                : null;
        currentDirectory = directory;
        selectedFile = null;
        selectButton.setEnabled(false);
        addressField.setText(directory.getAbsolutePath());
        tableModel.setFiles(visibleFiles);
        SwingUtilities.invokeLater(this::fitColumnsToContent);
    }

    private boolean isInSigningHistory(int viewRow) {
        File file = tableModel.getFile(fileTable.convertRowIndexToModel(viewRow));
        return historyPathKeys.contains(
                SigningHistoryStore.normalizePathKey(file.getAbsolutePath()));
    }

    private boolean isRecentUnencryptedApk(int viewRow) {
        return recentUnencryptedApk != null && recentUnencryptedApk.equals(
                tableModel.getFile(fileTable.convertRowIndexToModel(viewRow)));
    }

    private boolean shouldHideHistoryApk(File file) {
        return choosingApk && hideHistoryApksCheckBox.isSelected()
                && isInSigningHistoryApk(file, historyPathKeys);
    }

    static boolean isInSigningHistoryApk(File file, Set<String> historyPathKeys) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")
                && historyPathKeys.contains(
                        SigningHistoryStore.normalizePathKey(file.getAbsolutePath()));
    }

    static boolean isUnencryptedApk(File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".apk")) {
            return false;
        }
        String stem = name.substring(0, name.length() - 4);
        return !stem.startsWith("dx_unsigned")
                && !stem.endsWith("_360")
                && !stem.contains("_jiagu")
                && !stem.contains("_unsign")
                && !stem.contains("_protected");
    }

    static File findRecentUnencryptedApk(List<File> files, Set<String> historyPathKeys) {
        File newest = null;
        long newestTime = Long.MIN_VALUE;
        for (File file : files) {
            if (!isUnencryptedApk(file) || isInSigningHistoryApk(file, historyPathKeys)) {
                continue;
            }
            long createdAt = creationTime(file);
            if (createdAt > newestTime) {
                newest = file;
                newestTime = createdAt;
            }
        }
        return newest;
    }

    private static long creationTime(File file) {
        try {
            long createdAt = Files.readAttributes(file.toPath(), BasicFileAttributes.class)
                    .creationTime().toMillis();
            if (createdAt > 0) {
                return createdAt;
            }
        } catch (IOException | SecurityException ignored) {
            // 创建时间不可用时仍可按修改时间找出最新文件。
        }
        return file.lastModified();
    }

    private static Color blend(Color background, Color accent) {
        return new Color(
                (background.getRed() * 4 + accent.getRed()) / 5,
                (background.getGreen() * 4 + accent.getGreen()) / 5,
                (background.getBlue() * 4 + accent.getBlue()) / 5);
    }

    private boolean accepts(File file) {
        if (acceptedExtensions.isEmpty()) {
            return true;
        }
        String lowerCaseName = file.getName().toLowerCase(Locale.ROOT);
        for (String extension : acceptedExtensions) {
            if (lowerCaseName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void updateSelection() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow < 0) {
            selectButton.setEnabled(false);
            return;
        }
        File file = tableModel.getFile(fileTable.convertRowIndexToModel(viewRow));
        addressField.setText(file.getAbsolutePath());
        selectButton.setEnabled(file.isFile() && accepts(file));
    }

    private void openSelectedEntry() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        File file = tableModel.getFile(fileTable.convertRowIndexToModel(viewRow));
        if (file.isDirectory()) {
            loadDirectory(file);
        } else {
            approveSelection();
        }
    }

    private void approveSelection() {
        int viewRow = fileTable.getSelectedRow();
        File candidate = viewRow < 0
                ? new File(addressField.getText().trim())
                : tableModel.getFile(fileTable.convertRowIndexToModel(viewRow));
        if (candidate.isDirectory()) {
            loadDirectory(candidate);
        } else if (candidate.isFile() && accepts(candidate)) {
            selectedFile = candidate;
            dispose();
        } else {
            showPathError("请选择符合文件类型要求的文件");
        }
    }

    private void selectFile(File file) {
        int modelRow = tableModel.indexOf(file);
        if (modelRow < 0) {
            return;
        }
        int viewRow = fileTable.convertRowIndexToView(modelRow);
        fileTable.setRowSelectionInterval(viewRow, viewRow);
        fileTable.scrollRectToVisible(fileTable.getCellRect(viewRow, NAME_COLUMN, true));
    }

    /** Sizes columns from headers and rendered values; horizontal scrolling preserves full content. */
    private void fitColumnsToContent() {
        JTableHeader header = fileTable.getTableHeader();
        int totalWidth = 0;
        for (int viewColumn = 0; viewColumn < fileTable.getColumnCount(); viewColumn++) {
            TableColumn column = fileTable.getColumnModel().getColumn(viewColumn);
            int width = preferredHeaderWidth(header, column);
            for (int viewRow = 0; viewRow < fileTable.getRowCount(); viewRow++) {
                TableCellRenderer renderer = fileTable.getCellRenderer(viewRow, viewColumn);
                Component component = fileTable.prepareRenderer(renderer, viewRow, viewColumn);
                width = Math.max(width, component.getPreferredSize().width + CELL_PADDING);
            }
            column.setPreferredWidth(width);
            totalWidth += width;
        }

        if (fileTable.getParent() instanceof JViewport) {
            int availableWidth = fileTable.getParent().getWidth();
            if (availableWidth > totalWidth) {
                TableColumn nameColumn = fileTable.getColumnModel().getColumn(NAME_COLUMN);
                nameColumn.setPreferredWidth(nameColumn.getPreferredWidth() + availableWidth - totalWidth);
            }
        }
    }
    private static int preferredHeaderWidth(JTableHeader header, TableColumn column) {
        TableCellRenderer renderer = column.getHeaderRenderer();
        if (renderer == null) {
            renderer = header.getDefaultRenderer();
        }
        Component component = renderer.getTableCellRendererComponent(
                header.getTable(), column.getHeaderValue(), false, false, -1, column.getModelIndex());
        return component.getPreferredSize().width + CELL_PADDING;
    }

    private void showPathError(String message) {
        JOptionPane.showMessageDialog(this, message, "路径错误", JOptionPane.WARNING_MESSAGE);
        addressField.requestFocusInWindow();
        addressField.selectAll();
    }

    private static List<String> normalizeExtensions(String[] extensions) {
        List<String> normalized = new ArrayList<>();
        Arrays.stream(extensions)
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .map(extension -> extension.startsWith(".") ? extension : "." + extension)
                .forEach(normalized::add);
        return normalized;
    }

    private static final class FileTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"名称", "大小", "类型", "修改日期"};
        private static final Class<?>[] COLUMN_TYPES = {File.class, Long.class, String.class, Date.class};
        private final FileSystemView fileSystemView;
        private List<File> files = Collections.emptyList();

        private FileTableModel(FileSystemView fileSystemView) {
            this.fileSystemView = fileSystemView;
        }

        private void setFiles(List<File> files) {
            this.files = new ArrayList<>(files);
            fireTableDataChanged();
        }

        private File getFile(int row) {
            return files.get(row);
        }

        private int indexOf(File file) {
            return files.indexOf(file);
        }

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return COLUMN_NAMES.length; }
        @Override public String getColumnName(int column) { return COLUMN_NAMES[column]; }
        @Override public Class<?> getColumnClass(int column) { return COLUMN_TYPES[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            File file = files.get(row);
            switch (column) {
                case NAME_COLUMN: return file;
                case SIZE_COLUMN: return file.isDirectory() ? null : file.length();
                case TYPE_COLUMN: return fileSystemView.getSystemTypeDescription(file);
                case MODIFIED_COLUMN: return new Date(file.lastModified());
                default: throw new IllegalArgumentException("Unknown column: " + column);
            }
        }
    }

    private static final class FileNameRenderer extends DefaultTableCellRenderer {
        private final FileSystemView fileSystemView;
        private final Set<String> historyPathKeys;

        private FileNameRenderer(FileSystemView fileSystemView, Set<String> historyPathKeys) {
            this.fileSystemView = fileSystemView;
            this.historyPathKeys = historyPathKeys;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            File file = (File) value;
            String name = fileSystemView.getSystemDisplayName(file);
            setText(name == null || name.isEmpty() ? file.getName() : name);
            setIcon(fileSystemView.getSystemIcon(file));
            boolean hasSigningHistory = historyPathKeys.contains(
                    SigningHistoryStore.normalizePathKey(file.getAbsolutePath()));

            setToolTipText(hasSigningHistory ? "该文件有签名历史，仍可继续选择" : null);
            return this;
        }
    }
    private static final class FileSizeRenderer extends DefaultTableCellRenderer {
        private FileSizeRenderer() { setHorizontalAlignment(RIGHT); }

        @Override
        protected void setValue(Object value) {
            setText(value == null ? "" : formatSize((Long) value));
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            double size = bytes;
            String[] units = {"KB", "MB", "GB", "TB"};
            int unit = -1;
            do {
                size /= 1024;
                unit++;
            } while (size >= 1024 && unit < units.length - 1);
            return String.format(Locale.ROOT, "%.1f %s", size, units[unit]);
        }
    }

    private static final class DateRenderer extends DefaultTableCellRenderer {
        private final DateFormat formatter = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.MEDIUM);

        @Override
        protected void setValue(Object value) {
            setText(value == null ? "" : formatter.format((Date) value));
        }
    }
}
