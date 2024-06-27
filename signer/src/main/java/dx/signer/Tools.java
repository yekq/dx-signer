package dx.signer;

import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

/**
 * create by yekangqi
 * <hr>
 * time: 2024/6/25 10:14
 * <hr>
 * description:
 */
public class Tools {

    public static void openDir(Component parentComponent, String folderPath) {
        File folder = new File(folderPath);
        // 检查文件夹是否存在
        if (folder.exists() && folder.isDirectory()) {
            try {
                // 使用 Desktop 类打开文件夹
                Desktop desktop = Desktop.getDesktop();
                desktop.open(folder);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parentComponent, "打开路径错误: " + folderPath + "," + e.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(parentComponent, "路径不存在: " + folderPath);
        }
    }
} 