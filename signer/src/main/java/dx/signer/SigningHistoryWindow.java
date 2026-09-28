package dx.signer;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/22 11:53
 * description: 在主窗口右侧以可排序列表展示签名记录，并独立记住窗口位置。
 */
final class SigningHistoryWindow extends JDialog {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final HistoryTableModel model = new HistoryTableModel();
    private final JTable table = new JTable(model) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            return row < 0 ? null : model.records.get(convertRowIndexToModel(row)).path();
        }
    };
    private final JScrollPane scrollPane = new JScrollPane(table);
    private final JLabel summaryLabel = new JLabel();
    private final int[] contentWidths = new int[]{180, 110, 85, 180, 70};
    private Map<String, Color> projectColors = Collections.emptyMap();

    SigningHistoryWindow(JFrame owner, WindowStateStore states) {
        super(owner, "签名历史", false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(420, 280));
        Color surface = themeColor("Signer.surface", table.getBackground());
        Color foreground = themeColor("Signer.foreground", table.getForeground());
        Color muted = themeColor("Signer.muted", foreground);
        JPanel content = new JPanel(new BorderLayout());
        JPanel summary = new JPanel(new BorderLayout());
        summary.setBackground(surface);
        summary.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        summaryLabel.setForeground(muted);
        summary.add(summaryLabel, BorderLayout.WEST);
        content.add(summary, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        setContentPane(content);
        table.setBackground(themeColor("Signer.background", table.getBackground()));
        table.setForeground(foreground);
        scrollPane.getViewport().setBackground(table.getBackground());
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setShowVerticalLines(false);
        table.setGridColor(blend(table.getBackground(), foreground, 0.1f));
        table.setRowHeight(Math.max(30, table.getFontMetrics(table.getFont()).getHeight() + 12));
        table.getTableHeader().setBackground(surface);
        table.getTableHeader().setForeground(foreground);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(String.class, new HistoryCellRenderer());
        table.setDefaultRenderer(Date.class, new HistoryCellRenderer());
        TableRowSorter<HistoryTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(3, SortOrder.DESCENDING)));
        table.setRowSorter(sorter);
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyColumnWidths();
            }
        });
        Rectangle fallback = new Rectangle(owner.getX() + owner.getWidth() + 8, owner.getY(),
                owner.getWidth(), owner.getHeight());
        if (states == null) {
            setBounds(WindowStateStore.fitToBounds(fallback, getMinimumSize(),
                    WindowStateStore.usableBounds(owner.getGraphicsConfiguration())));
        } else {
            states.track(this, "history", fallback);
        }
    }

    void showHistory() {
        setVisible(true);
        toFront();
    }

    /** 接收历史快照，所有表格更新都在事件分发线程执行。 */
    void setRecords(List<SigningHistoryStore.Record> records) {
        List<SigningHistoryStore.Record> snapshot = new ArrayList<>(records);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setRecords(snapshot));
            return;
        }
        model.records = snapshot;
        projectColors = createProjectColors(snapshot, isDark(table.getBackground()));
        model.fireTableDataChanged();
        long successful = snapshot.stream().filter(SigningHistoryStore.Record::successful).count();
        summaryLabel.setText("共 " + snapshot.size() + " 条记录  ·  成功 " + successful
                + "  ·  失败 " + (snapshot.size() - successful));
        measureColumnWidths();
        applyColumnWidths();
    }

    private void measureColumnWidths() {
        for (int column = 0; column < table.getColumnCount(); column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            TableCellRenderer header = table.getTableHeader().getDefaultRenderer();
            int width = header.getTableCellRendererComponent(table, tableColumn.getHeaderValue(),
                    false, false, -1, column).getPreferredSize().width + 30;
            for (int row = 0; row < table.getRowCount(); row++) {
                Component cell = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                width = Math.max(width, cell.getPreferredSize().width + 24);
            }
            contentWidths[column] = width;
        }
    }

    private void applyColumnWidths() {
        int total = 0;
        for (int width : contentWidths) {
            total += width;
        }
        int extra = Math.max(0, scrollPane.getViewport().getWidth() - total);
        for (int column = 0; column < contentWidths.length; column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int width = contentWidths[column] + (column == 0 ? extra : 0);
            tableColumn.setPreferredWidth(width);
            tableColumn.setWidth(width);
        }
    }

    private static Color themeColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private static Color blend(Color base, Color tint, float amount) {
        return new Color(
                Math.round(base.getRed() * (1 - amount) + tint.getRed() * amount),
                Math.round(base.getGreen() * (1 - amount) + tint.getGreen() * amount),
                Math.round(base.getBlue() * (1 - amount) + tint.getBlue() * amount));
    }

    static Map<String, Color> createProjectColors(List<SigningHistoryStore.Record> records,
                                                   boolean darkBackground) {
        Set<String> names = new TreeSet<>();
        for (SigningHistoryStore.Record record : records) {
            if (!record.projectName().isEmpty()) {
                names.add(record.projectName());
            }
        }
        Map<String, Color> colors = new HashMap<>();
        Set<Integer> usedRgb = new HashSet<>();
        Object configured = UIManager.get("Signer.projectColors");
        if (configured instanceof Map) {
            Map<?, ?> overrides = (Map<?, ?>) configured;
            for (String name : names) {
                Object color = overrides.get(name);
                if (color instanceof Color) {
                    colors.put(name, (Color) color);
                    usedRgb.add(((Color) color).getRGB());
                }
            }
        }
        int index = 0;
        for (String name : names) {
            if (colors.containsKey(name)) {
                index++;
                continue;
            }
            float hue = (index + 0.5f) / names.size();
            Color color;
            do {
                color = Color.getHSBColor(hue, darkBackground ? 0.55f : 0.65f,
                        darkBackground ? 0.82f : 0.62f);
                hue = (hue + 0.002f) % 1f;
            } while (!usedRgb.add(color.getRGB()));
            colors.put(name, color);
            index++;
        }
        return colors;
    }

    private static boolean isDark(Color color) {
        return (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) < 128000;
    }

    private final class HistoryCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                        boolean focused, int viewRow, int viewColumn) {
            Object displayValue = value instanceof Date
                    ? TIME_FORMAT.format(Instant.ofEpochMilli(((Date) value).getTime())) : value;
            super.getTableCellRendererComponent(source, displayValue, selected, focused, viewRow, viewColumn);
            SigningHistoryStore.Record record = model.records.get(source.convertRowIndexToModel(viewRow));
            String project = record.projectName();
            Color base = source.getBackground();
            Color accent = project.isEmpty() ? themeColor("Signer.muted", source.getForeground())
                    : projectColors.get(project);
            if (!selected) {
                setBackground(project.isEmpty() ? base : blend(base, accent, 0.12f));
                if (viewColumn == 1 && !project.isEmpty()) {
                    setForeground(accent);
                } else if (viewColumn == 4) {
                    setForeground(themeColor(record.successful() ? "Signer.success" : "Signer.failure",
                            source.getForeground()));
                } else {
                    setForeground(source.getForeground());
                }
            }
            setFont(source.getFont().deriveFont(viewColumn == 1 || viewColumn == 4
                    ? Font.BOLD : Font.PLAIN));
            setBorder(viewColumn == 0
                    ? BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8))
                    : BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    private static final class HistoryTableModel extends AbstractTableModel {
        private final String[] columns = {"文件名", "项目", "签名类型", "时间", "状态"};
        private List<SigningHistoryStore.Record> records = Collections.emptyList();

        @Override
        public int getRowCount() {
            return records.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 3 ? Date.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            SigningHistoryStore.Record record = records.get(row);
            switch (column) {
                case 0:
                    return record.fileName();
                case 1:
                    return record.projectName().isEmpty() ? "未记录" : record.projectName();
                case 2:
                    return SigningHistoryStore.signingTypeForFileName(record.fileName());
                case 3:
                    return new Date(record.timestamp());
                case 4:
                    return record.successful() ? "成功" : "失败";
                default:
                    throw new IndexOutOfBoundsException("不存在的历史记录列：" + column);
            }
        }
    }
}
