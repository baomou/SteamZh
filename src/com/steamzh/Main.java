package com.steamzh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 程序入口。
 *
 * 循环做四件事：
 *   1. 每 1 秒扫一次 Steam 的页面列表，找出商店/社区页面；
 *   2. 给每个页面建立（或复用）翻译会话；
 *   3. 会话内部：注入脚本 → 取待翻译文本 → 并发翻译 → 写回页面；
 *   4. 页面关掉就自动清理。
 *
 * 托盘菜单可以暂停 / 恢复 / 退出。
 */
public class Main {

    public static void main(String[] args) {
        Config.load();

        // --settings 直接打开设置窗口；--verbose 输出调试细节
        for (String a : args) {
            if ("--verbose".equals(a)) Log.verbose = true;
        }
        if (args.length > 0 && args[0].equals("--settings")) {
            SettingsDialog.show();
            return;
        }

        Log.info("=== Steam 界面翻译 启动 ===");

        Log.debug("[设置] 商店=" + Config.translateStore
                + " 社区=" + Config.translateCommunity
                + " 帮助=" + Config.translateHelp
                + " 悬停原文=" + Config.showSource
                + " 语言=" + Config.targetLang
                + " 并发=" + Config.concurrency);

        if (!portAlive()) {
            Log.warn("!! 连不上 Steam 的调试端口 8080。");
            Log.warn("!! 请先完全退出 Steam，再用「启动Steam(调试模式).bat」重新打开。");
            Log.warn("!! 详细说明见 README.md");
            showError("连不上 Steam 的调试端口 8080。\n\n"
                    + "请先完全退出 Steam（托盘右键 → 退出），\n"
                    + "再用项目里的「启动Steam(调试模式).bat」重新打开 Steam，\n"
                    + "最后重新启动本程序。");
            return;
        }

        Log.info("调试端口正常，在 Steam 里打开商店或社区页面即可自动翻译。");
        Tray.install();

        BingTranslator translator = new BingTranslator();
        Map<String, Session> sessions = new HashMap<>();

        long lastScan = 0;
        long lastStats = System.currentTimeMillis();

        while (true) {
            try {
                if (Tray.paused) {
                    Thread.sleep(500);
                    continue;
                }

                long now = System.currentTimeMillis();

                if (now - lastScan > 1000) {
                    lastScan = now;
                    Set<String> alive = new HashSet<>();

                    for (Target t : Target.list()) {
                        if (!t.translatable()) continue;
                        alive.add(t.id);

                        Session s = sessions.get(t.id);
                        if (s == null) {
                            s = new Session(t);
                            sessions.put(t.id, s);
                        }
                        try {
                            s.tick(translator);
                        } catch (Exception e) {
                            sessions.remove(t.id);
                            Log.debug("[页面] 断开: " + t.describe() + " (" + e.getMessage() + ")");
                        }
                    }

                    sessions.keySet().retainAll(alive);
                }

                if (now - lastStats > 30000) {
                    lastStats = now;
                    Log.debug("[状态] " + sessions.size() + " 个页面 / " + translator.stats());
                }

                Thread.sleep(300);
            } catch (Exception e) {
                Log.warn("[主循环] " + e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static boolean portAlive() {
        try {
            Http.get("http://127.0.0.1:8080/json/version");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 打包成 exe 后没有控制台，用弹窗提示 */
    private static void showError(String message) {
        try {
            javax.swing.JOptionPane.showMessageDialog(null, message,
                    "Steam 翻译", javax.swing.JOptionPane.WARNING_MESSAGE);
        } catch (Exception ignored) {
        }
    }
}
