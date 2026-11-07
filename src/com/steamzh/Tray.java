package com.steamzh;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * 系统托盘图标：显示运行状态，提供「暂停 / 恢复 / 设置 / 退出」。
 *
 * 菜单没有用 AWT PopupMenu（打包后中文变方块），也没有用 JPopupMenu
 * （PopupFactory 会自己改位置，导致贴边被裁），而是用一个无边框 JWindow，
 * 自己算屏幕坐标，位置完全可控。
 */
public class Tray {

    public static volatile boolean paused = false;

    private static TrayIcon icon;
    private static JWindow menuWindow;
    private static JButton toggleButton;
    private static long shownAt;

    public static void install() {
        if (!SystemTray.isSupported()) return;
        try {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(35, 120, 200));
            g.fillRoundRect(0, 0, 16, 16, 4, 4);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
            g.drawString("译", 1, 13);
            g.dispose();

            icon = new TrayIcon(img, "Steam 翻译运行中");
            icon.setImageAutoSize(true);
            icon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isRightClick(e)) showMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isRightClick(e)) showMenu(e);
                }
            });

            SystemTray.getSystemTray().add(icon);
            Log.info("[托盘] 已启动，右键图标可暂停 / 设置 / 退出");
        } catch (Exception e) {
            Log.warn("[托盘] 不可用: " + e.getMessage());
        }
    }

    private static void ensureMenu() {
        if (menuWindow != null) return;

        Runnable create = () -> {
                Font f = new Font("Microsoft YaHei", Font.PLAIN, 12);

                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBackground(Color.WHITE);
                panel.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));

                toggleButton = item("暂停翻译", f, () -> {
                    paused = !paused;
                    refreshLabel();
                    hideMenu();
                });
                JButton settings = item("设置...", f, () -> {
                    hideMenu();
                    SettingsDialog.show();
                });
                JButton exit = item("退出", f, () -> {
                    hideMenu();
                    System.exit(0);
                });

                panel.add(toggleButton);
                panel.add(settings);
                panel.add(new JSeparator());
                panel.add(exit);

                menuWindow = new JWindow();
                menuWindow.setContentPane(panel);
                menuWindow.setAlwaysOnTop(true);
                menuWindow.setFocusableWindowState(true);
                menuWindow.addWindowFocusListener(new WindowAdapter() {
                    @Override
                    public void windowLostFocus(WindowEvent e) {
                        // 刚弹出时的焦点抖动不算
                        if (System.currentTimeMillis() - shownAt > 250) hideMenu();
                    }
                });
                menuWindow.pack();
        };

        // TrayIcon 的事件本来就在 EDT 上，此时不能再用 invokeAndWait
        if (SwingUtilities.isEventDispatchThread()) {
            create.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(create);
            } catch (Exception e) {
                Log.warn("[托盘] 菜单创建失败: " + e.getMessage());
            }
        }
    }

    private static JButton item(String text, Font f, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(f);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBackground(Color.WHITE);
        b.setMargin(new java.awt.Insets(2, 12, 2, 24));
        b.setPreferredSize(new Dimension(150, 30));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.addActionListener(e -> action.run());
        b.getModel().addChangeListener(e -> {
            boolean rollover = b.getModel().isRollover();
            b.setBackground(rollover ? new Color(229, 241, 251) : Color.WHITE);
        });
        return b;
    }

    /**
     * 判断是不是右键。Windows 上 TrayIcon 合成的 MouseEvent 里
     * isPopupTrigger() 并不可靠，必须同时看 button。
     */
    private static boolean isRightClick(MouseEvent e) {
        return e.getButton() == MouseEvent.BUTTON3 || e.isPopupTrigger();
    }

    private static void showMenu(MouseEvent e) {
        showMenuAt(e.getXOnScreen(), e.getYOnScreen());
    }

    /** 在指定的屏幕坐标弹出菜单（抽出来便于测试） */
    static void showMenuAt(int rawX, int rawY) {
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        int anchorX = rawX;
        int anchorY = rawY;

        // HiDPI 陷阱：TrayIcon 的事件给的是物理像素，而 AWT 窗口坐标是逻辑像素。
        // 坐标落在逻辑屏幕之外时，按 DPI 缩放换算回来。
        try {
            java.awt.geom.AffineTransform t = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform();
            double sx = t.getScaleX() > 0 ? t.getScaleX() : 1.0;
            double sy = t.getScaleY() > 0 ? t.getScaleY() : 1.0;
            if (rawX >= screenBounds.width || rawY >= screenBounds.height) {
                anchorX = (int) Math.round(rawX / sx);
                anchorY = (int) Math.round(rawY / sy);
            }
        } catch (Exception ignored) {
        }

        Log.debug("[托盘] 右键锚点 " + rawX + "," + rawY
                + " -> 换算后 " + anchorX + "," + anchorY);

        final int fx = anchorX;
        final int fy = anchorY;

        ensureMenu();
        SwingUtilities.invokeLater(() -> {
            if (menuWindow == null) return;

            menuWindow.pack();
            Dimension d = menuWindow.getSize();
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

            int x = fx;
            int y = fy;

            // 贴右边就往左收，贴底边就往上弹
            if (x + d.width > screen.x + screen.width) {
                x = screen.x + screen.width - d.width;
            }
            if (y + d.height > screen.y + screen.height) {
                y = y - d.height;
            }
            x = Math.max(screen.x, x);
            y = Math.max(screen.y, y);

            menuWindow.setBounds(x, y, d.width, d.height);
            shownAt = System.currentTimeMillis();
            menuWindow.setVisible(true);
            menuWindow.validate();
            menuWindow.toFront();
        });
    }

    private static void hideMenu() {
        SwingUtilities.invokeLater(() -> {
            if (menuWindow != null) menuWindow.setVisible(false);
        });
    }

    /** 更新菜单文字和鼠标悬停提示 */
    public static void refreshLabel() {
        SwingUtilities.invokeLater(() -> {
            if (toggleButton != null) {
                toggleButton.setText(paused ? "恢复翻译" : "暂停翻译");
            }
            if (icon != null) {
                icon.setToolTip(paused ? "Steam 翻译已暂停" : "Steam 翻译运行中");
            }
        });
    }
}
