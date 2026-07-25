package dx.signer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
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
 * File selector backed only by public Swing and {@link FileSystemView} APIs.
 * The address field accepts pasted directory or file paths and navigates on Enter.
 */
public final class FileChooserDialog extends JDialog {
    private static final int NAME_COLUMN = 0;
    private static final int SIZE_COLUMN = 1;
    private static final int TYPE_COLUMN = 2;
    private static final int MODIFIED_COLUMN = 3;
    private static final int CELL_PADDING = 18;

    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final FileTableModel tableModel = new FileTableModel(fileSystemView);
    private final JTable fileTable = new JTable(tableModel);
    private final JTextField addressField = new JTextField();
    private final JButton selectButton = new JButton("选择");
    private final List<String> acceptedExtensions;

    private File currentDirectory;
    private File selectedFile;

    private FileChooserDialog(Window owner, File initialPath, String filterDescription,
                              String... acceptedExtensions) {
        super(owner, "选择文件", ModalityType.APPLICATION_MODAL);
        this.acceptedExtensions = normalizeExtensions(acceptedExtensions);
        initializeUi(filterDescription);
        navigateToInitialPath(initialPath);
    }

    /** Opens the modal selector and returns {@code null} when canceled. */
    public static File chooseFile(Component parent, File initialPath, String filterDescription,
                                  String... acceptedExtensions) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        FileChooserDialog dialog = new FileChooserDialog(
                owner, initialPath, filterDescription, acceptedExtensions);
        dialog.setVisible(true);
        return dialog.selectedFile;
    }

    private void initializeUi(String filterDescription) {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(createAddressBar(), BorderLayout.NORTH);
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
        fileTable.setDefaultRenderer(File.class, new FileNameRenderer(fileSystemView));
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
        } else if (accepts(target)) {
            loadDirectory(target.getParentFile());
            selectFile(target);
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
            if (file.isDirectory() || accepts(file)) {
                visibleFiles.add(file);
            }
        }
        currentDirectory = directory;
        selectedFile = null;
        selectButton.setEnabled(false);
        addressField.setText(directory.getAbsolutePath());
        tableModel.setFiles(visibleFiles);
        SwingUtilities.invokeLater(this::fitColumnsToContent);
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

        private FileNameRenderer(FileSystemView fileSystemView) {
            this.fileSystemView = fileSystemView;
        }

        @Override
        protected void setValue(Object value) {
            File file = (File) value;
            String name = fileSystemView.getSystemDisplayName(file);
            setText(name == null || name.isEmpty() ? file.getName() : name);
            setIcon(fileSystemView.getSystemIcon(file));
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