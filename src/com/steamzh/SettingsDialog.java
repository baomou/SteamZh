package com.steamzh;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * 设置窗口（Swing）。从托盘菜单打开。
 */
public class SettingsDialog {

    private static JFrame frame;

    public static void show() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null && frame.isDisplayable()) {
                frame.toFront();
                frame.requestFocus();
                return;
            }

            frame = new JFrame("Steam 翻译 · 设置");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(true);

            JPanel root = new JPanel(new BorderLayout(12, 12));
            root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

            JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));

            JLabel title1 = new JLabel("翻译范围");
            title1.setFont(title1.getFont().deriveFont(java.awt.Font.BOLD));
            form.add(title1);

            JCheckBox store = new JCheckBox("商店页面 (store.steampowered.com)", Config.translateStore);
            JCheckBox community = new JCheckBox("社区页面 (steamcommunity.com)", Config.translateCommunity);
            JCheckBox help = new JCheckBox("帮助页面 (help.steampowered.com)", Config.translateHelp);
            form.add(store);
            form.add(community);
            form.add(help);

            JLabel title2 = new JLabel("显示");
            title2.setFont(title2.getFont().deriveFont(java.awt.Font.BOLD));
            form.add(title2);

            JCheckBox showSource = new JCheckBox("鼠标悬停时显示英文原文", Config.showSource);
            form.add(showSource);

            JLabel title3 = new JLabel("翻译");
            title3.setFont(title3.getFont().deriveFont(java.awt.Font.BOLD));
            form.add(title3);

            JComboBox<String> lang = new JComboBox<>(new String[]{"简体中文", "繁体中文"});
            lang.setSelectedIndex("zh-Hant".equals(Config.targetLang) ? 1 : 0);
            form.add(lang);

            JComboBox<String> conc = new JComboBox<>(new String[]{"2", "4", "6", "8"});
            conc.setSelectedItem(String.valueOf(Config.concurrency));
            form.add(new JLabel("并发请求数（越大越快，也越容易被限流）"));
            form.add(conc);

            root.add(form, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
            JButton cancel = new JButton("取消");
            JButton save = new JButton("保存");
            buttons.add(cancel);
            buttons.add(save);
            root.add(buttons, BorderLayout.SOUTH);

            cancel.addActionListener(e -> frame.dispose());
            save.addActionListener(e -> {
                Config.translateStore = store.isSelected();
                Config.translateCommunity = community.isSelected();
                Config.translateHelp = help.isSelected();
                Config.showSource = showSource.isSelected();
                Config.targetLang = lang.getSelectedIndex() == 1 ? "zh-Hant" : "zh-Hans";
                Config.concurrency = Integer.parseInt((String) conc.getSelectedItem());
                Config.save();
                frame.dispose();
            });

            frame.setContentPane(root);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
