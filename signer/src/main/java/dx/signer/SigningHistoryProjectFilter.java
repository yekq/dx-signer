package dx.signer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/10/29 10:00
 * description: 按项目切换签名历史，展示各组记录数量并在刷新时保留当前选择。
 */
final class SigningHistoryProjectFilter extends JPanel {
    private static final String ALL_PROJECTS_LABEL = "全部项目";
    private static final String UNRECORDED_PROJECT_LABEL = "未记录项目";

    private final DefaultListModel<ProjectGroup> groups = new DefaultListModel<>();
    private final JList<ProjectGroup> projectList = new JList<ProjectGroup>(groups) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            return index >= 0 && getCellBounds(index, index).contains(event.getPoint())
                    ? groups.get(index).toString() : null;
        }
    };
    private Map<String, Color> projectColors = Collections.emptyMap();
    private boolean updating;

    SigningHistoryProjectFilter(Runnable onSelectionChanged) {
        super(new BorderLayout());
        setPreferredSize(new Dimension(164, 0));
        setMinimumSize(new Dimension(120, 0));
        Color surface = themeColor("Signer.surface", projectList.getBackground());
        Color foreground = themeColor("Signer.foreground", projectList.getForeground());
        Color muted = themeColor("Signer.muted", foreground);
        setBackground(surface);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                themeColor("Signer.border", muted)));
        JLabel title = new JLabel("项目");
        title.setForeground(muted);
        title.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        add(title, BorderLayout.NORTH);

        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.setBackground(surface);
        projectList.setForeground(foreground);
        projectList.setSelectionBackground(themeColor("Signer.selection", projectList.getSelectionBackground()));
        projectList.setSelectionForeground(foreground);
        projectList.setFixedCellHeight(Math.max(36,
                projectList.getFontMetrics(projectList.getFont()).getHeight() + 16));
        projectList.setCellRenderer(new ProjectGroupRenderer());
        projectList.setToolTipText("");
        groups.addElement(ProjectGroup.all(0));
        projectList.setSelectedIndex(0);
        projectList.addListSelectionListener(event -> {
            if (!updating && !event.getValueIsAdjusting()) {
                onSelectionChanged.run();
            }
        });
        JScrollPane scroll = new JScrollPane(projectList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(surface);
        add(scroll, BorderLayout.CENTER);
    }

    /** 更新分组但不触发切换回调，避免刷新中的临时选中状态影响表格。 */
    void setRecords(List<SigningHistoryStore.Record> records, Map<String, Color> colors) {
        ProjectGroup previous = selectedGroup();
        Map<String, Integer> counts = new TreeMap<>();
        int unrecorded = 0;
        for (SigningHistoryStore.Record record : records) {
            String projectName = record.projectName();
            if (projectName.isEmpty()) {
                unrecorded++;
            } else {
                counts.put(projectName, counts.getOrDefault(projectName, 0) + 1);
            }
        }
        projectColors = new HashMap<>(colors);
        updating = true;
        try {
            groups.clear();
            groups.addElement(ProjectGroup.all(records.size()));
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                groups.addElement(ProjectGroup.project(entry.getKey(), entry.getValue()));
            }
            if (unrecorded > 0) {
                groups.addElement(ProjectGroup.unrecorded(unrecorded));
            }
            int selectedIndex = 0;
            for (int index = 1; index < groups.size(); index++) {
                if (groups.get(index).sameProject(previous)) {
                    selectedIndex = index;
                    break;
                }
            }
            projectList.setSelectedIndex(selectedIndex);
            projectList.ensureIndexIsVisible(selectedIndex);
        } finally {
            updating = false;
        }
    }

    boolean matches(SigningHistoryStore.Record record) {
        ProjectGroup selected = selectedGroup();
        return selected.kind == GroupKind.ALL
                || (selected.kind == GroupKind.UNRECORDED && record.projectName().isEmpty())
                || (selected.kind == GroupKind.PROJECT && selected.name.equals(record.projectName()));
    }

    String selectedGroupLabel() {
        return selectedGroup().label();
    }

    private ProjectGroup selectedGroup() {
        ProjectGroup selected = projectList.getSelectedValue();
        return selected == null ? ProjectGroup.all(0) : selected;
    }

    private static Color themeColor(String key, Color fallback) {
        Color configured = UIManager.getColor(key);
        return configured == null ? fallback : configured;
    }

    private final class ProjectGroupRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            ProjectGroup group = (ProjectGroup) value;
            // 项目颜色来自全量历史，切换筛选时不会重新分配颜色。
            Color accent = group.kind == GroupKind.PROJECT ? projectColors.get(group.name) : null;
            if (accent == null) {
                accent = themeColor(group.kind == GroupKind.ALL ? "Signer.accent" : "Signer.muted",
                        list.getForeground());
            }
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                    BorderFactory.createEmptyBorder(0, 9, 0, 8)));
            if (!selected) {
                setForeground(group.kind == GroupKind.UNRECORDED
                        ? themeColor("Signer.muted", list.getForeground()) : list.getForeground());
            }
            setFont(list.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
            return this;
        }
    }

    private enum GroupKind {
        ALL, PROJECT, UNRECORDED
    }

    /** 特殊分组使用独立类型，避免与同名的真实项目混淆。 */
    private static final class ProjectGroup {
        private final GroupKind kind;
        private final String name;
        private final int count;

        private ProjectGroup(GroupKind kind, String name, int count) {
            this.kind = kind;
            this.name = name;
            this.count = count;
        }

        private static ProjectGroup all(int count) {
            return new ProjectGroup(GroupKind.ALL, "", count);
        }

        private static ProjectGroup project(String name, int count) {
            return new ProjectGroup(GroupKind.PROJECT, name, count);
        }

        private static ProjectGroup unrecorded(int count) {
            return new ProjectGroup(GroupKind.UNRECORDED, "", count);
        }

        private boolean sameProject(ProjectGroup other) {
            return kind == other.kind && name.equals(other.name);
        }

        private String label() {
            if (kind == GroupKind.ALL) {
                return ALL_PROJECTS_LABEL;
            }
            if (kind == GroupKind.UNRECORDED) {
                return UNRECORDED_PROJECT_LABEL;
            }
            return ALL_PROJECTS_LABEL.equals(name) || UNRECORDED_PROJECT_LABEL.equals(name)
                    ? name + "（项目）" : name;
        }

        @Override
        public String toString() {
            return label() + "（" + count + "）";
        }
    }
}
