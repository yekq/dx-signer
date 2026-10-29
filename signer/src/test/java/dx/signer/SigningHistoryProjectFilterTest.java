package dx.signer;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/10/29 10:00
 * description: 在事件分发线程验证项目分组切换、记录计数、刷新保留和同名特殊分组隔离。
 */
public final class SigningHistoryProjectFilterTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("history-project-filter-test-");
        try {
            SigningHistoryStore store = new SigningHistoryStore(directory.resolve("signing-history.json"));
            List<String> names = Arrays.asList("广东", "四川", "广东", "", "全部项目", "未记录项目", "广东[试用].*");
            for (int index = 0; index < names.size(); index++) {
                store.add(directory.resolve("client-" + index + ".apk"), index % 2 == 0, names.get(index));
            }
            List<SigningHistoryStore.Record> records = store.records();
            SwingUtilities.invokeAndWait(() -> verifyOnEventThread(records));
            System.out.println("签名历史项目分组验证通过：切换、数量、特殊同名、刷新保留与项目颜色");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyOnEventThread(List<SigningHistoryStore.Record> records) {
        UiTheme.apply(null);
        int[] changes = {0};
        SigningHistoryProjectFilter filter = new SigningHistoryProjectFilter(() -> changes[0]++);
        JList<?> list = findProjectList(filter);
        assertEquals(0, changes[0]);
        assertEquals("全部项目", filter.selectedGroupLabel());
        filter.setRecords(records, Collections.singletonMap("广东", Color.BLUE));
        assertEquals(0, changes[0]);
        assertEquals("全部项目（7）", list.getModel().getElementAt(0).toString());
        assertMatching(filter, records, null);
        verifyProjectOrder(list, records);

        list.setSelectedIndex(indexOf(list, "广东（2）"));
        assertEquals(1, changes[0]);
        assertEquals("广东", filter.selectedGroupLabel());
        assertMatching(filter, records, "广东");
        assertProjectColor(list, Color.BLUE);

        List<SigningHistoryStore.Record> updated = new ArrayList<>(records);
        updated.add(findRecord(records, "广东"));
        Map<String, Color> colors = new HashMap<>();
        colors.put("广东", Color.RED);
        filter.setRecords(updated, colors);
        assertEquals(1, changes[0]);
        assertEquals("广东", filter.selectedGroupLabel());
        assertEquals("广东（3）", list.getSelectedValue().toString());
        assertMatching(filter, updated, "广东");
        assertProjectColor(list, Color.RED);

        List<SigningHistoryStore.Record> remaining = records.stream()
                .filter(record -> !record.projectName().equals("广东")).collect(Collectors.toList());
        filter.setRecords(remaining, colors);
        assertEquals(1, changes[0]);
        assertEquals(0, list.getSelectedIndex());
        assertMatching(filter, remaining, null);

        filter.setRecords(records, colors);
        list.setSelectedIndex(indexOf(list, "全部项目（项目）（1）"));
        assertEquals("全部项目（项目）", filter.selectedGroupLabel());
        assertMatching(filter, records, "全部项目");
        list.setSelectedIndex(indexOf(list, "未记录项目（项目）（1）"));
        assertEquals("未记录项目（项目）", filter.selectedGroupLabel());
        assertMatching(filter, records, "未记录项目");
        list.setSelectedIndex(indexOf(list, "未记录项目（1）"));
        assertEquals("未记录项目", filter.selectedGroupLabel());
        assertMatching(filter, records, "");
        filter.setRecords(records, colors);
        assertEquals("未记录项目", filter.selectedGroupLabel());
        assertMatching(filter, records, "");

        list.setSelectedIndex(indexOf(list, "广东[试用].*（1）"));
        assertMatching(filter, records, "广东[试用].*");
        list.setSize(164, 400);
        Rectangle cell = list.getCellBounds(list.getSelectedIndex(), list.getSelectedIndex());
        MouseEvent hover = new MouseEvent(list, MouseEvent.MOUSE_MOVED, 0L, 0,
                cell.x + 4, cell.y + 4, 0, false);
        assertEquals("广东[试用].*（1）", list.getToolTipText(hover));

        int countBeforeRefresh = changes[0];
        filter.setRecords(Collections.emptyList(), Collections.emptyMap());
        assertEquals(countBeforeRefresh, changes[0]);
        assertEquals(1, list.getModel().getSize());
        assertEquals("全部项目（0）", list.getSelectedValue().toString());
        assertMatching(filter, records, null);
    }

    private static void verifyProjectOrder(JList<?> list, List<SigningHistoryStore.Record> records) {
        TreeSet<String> sortedNames = new TreeSet<>();
        for (SigningHistoryStore.Record record : records) {
            if (!record.projectName().isEmpty()) {
                sortedNames.add(record.projectName());
            }
        }
        int index = 1;
        for (String name : sortedNames) {
            String expectedLabel = "全部项目".equals(name) || "未记录项目".equals(name)
                    ? name + "（项目）" : name;
            long count = records.stream().filter(record -> record.projectName().equals(name)).count();
            assertEquals(expectedLabel + "（" + count + "）", list.getModel().getElementAt(index++).toString());
        }
        assertEquals("未记录项目（1）", list.getModel().getElementAt(index).toString());
    }

    /** 验证真实列表渲染使用最新项目颜色，刷新不遗留之前的配色。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertProjectColor(JList<?> list, Color expected) {
        Component rendered = ((JList) list).getCellRenderer().getListCellRendererComponent(
                list, list.getSelectedValue(), list.getSelectedIndex(), false, false);
        CompoundBorder border = (CompoundBorder) ((JLabel) rendered).getBorder();
        assertEquals(expected, ((MatteBorder) border.getOutsideBorder()).getMatteColor());
    }

    private static void assertMatching(SigningHistoryProjectFilter filter,
                                       List<SigningHistoryStore.Record> records, String project) {
        for (SigningHistoryStore.Record record : records) {
            boolean expected = project == null || project.equals(record.projectName());
            if (filter.matches(record) != expected) {
                throw new AssertionError("项目筛选结果错误：" + filter.selectedGroupLabel()
                        + "，记录项目：" + record.projectName());
            }
        }
    }

    private static SigningHistoryStore.Record findRecord(List<SigningHistoryStore.Record> records,
                                                         String project) {
        return records.stream().filter(record -> record.projectName().equals(project)).findFirst().get();
    }

    private static JList<?> findProjectList(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JList) {
                return (JList<?>) child;
            }
            if (child instanceof Container) {
                JList<?> found = findProjectList((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int indexOf(JList<?> list, String label) {
        for (int index = 0; index < list.getModel().getSize(); index++) {
            if (label.equals(list.getModel().getElementAt(index).toString())) {
                return index;
            }
        }
        throw new AssertionError("未找到项目分组：" + label);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("预期 " + expected + "，实际 " + actual);
        }
    }
}
