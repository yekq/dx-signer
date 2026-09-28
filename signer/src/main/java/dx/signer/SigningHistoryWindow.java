package dx.signer;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.Component;
import java.awt.Dimension;
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
import java.util.List;

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
    private final int[] contentWidths = new int[]{180, 180, 70};

    SigningHistoryWindow(JFrame owner, WindowStateStore states) {
        super(owner, "签名历史", false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(420, 280));
        setContentPane(scrollPane);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(Math.max(24, table.getFontMetrics(table.getFont()).getHeight() + 8));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Date.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value == null ? "" : TIME_FORMAT.format(Instant.ofEpochMilli(((Date) value).getTime())));
            }
        });
        TableRowSorter<HistoryTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
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
        model.fireTableDataChanged();
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
        int total = contentWidths[0] + contentWidths[1] + contentWidths[2];
        int extra = Math.max(0, scrollPane.getViewport().getWidth() - total);
        for (int column = 0; column < contentWidths.length; column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int width = contentWidths[column] + (column == 0 ? extra : 0);
            tableColumn.setPreferredWidth(width);
            tableColumn.setWidth(width);
        }
    }

    private static final class HistoryTableModel extends AbstractTableModel {
        private final String[] columns = {"文件名", "时间", "状态"};
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
            return column == 1 ? Date.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            SigningHistoryStore.Record record = records.get(row);
            switch (column) {
                case 0:
                    return record.fileName();
                case 1:
                    return new Date(record.timestamp());
                case 2:
                    return record.successful() ? "成功" : "失败";
                default:
                    throw new IndexOutOfBoundsException("不存在的历史记录列：" + column);
            }
        }
    }
}
