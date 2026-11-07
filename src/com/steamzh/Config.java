package com.steamzh;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 全局设置，保存在程序目录下的 config.properties。
 * 静态字段直接改，改完调用 save()。
 */
public class Config {

    public static boolean translateStore = true;
    public static boolean translateCommunity = true;
    public static boolean translateHelp = false;
    public static boolean showSource = true;
    public static String targetLang = "zh-Hans";
    public static int concurrency = 4;

    private static final Path FILE = Paths.get("config.properties");

    /** 打包成 exe 后，配置写在 exe 同目录；开发模式下写在当前目录 */
    private static Path configPath() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isEmpty()) {
            Path dir = Paths.get(appPath).getParent();
            if (dir != null) return dir.resolve("config.properties");
        }
        return FILE;
    }

    public static void load() {
        Path file = configPath();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception e) {
            Log.warn("[设置] 读取失败，使用默认值: " + e.getMessage());
            return;
        }
        translateStore = bool(p, "translateStore", translateStore);
        translateCommunity = bool(p, "translateCommunity", translateCommunity);
        translateHelp = bool(p, "translateHelp", translateHelp);
        showSource = bool(p, "showSource", showSource);
        targetLang = p.getProperty("targetLang", targetLang);
        try {
            concurrency = Math.max(1, Math.min(8, Integer.parseInt(p.getProperty("concurrency", "4"))));
        } catch (NumberFormatException ignored) {
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("translateStore", String.valueOf(translateStore));
        p.setProperty("translateCommunity", String.valueOf(translateCommunity));
        p.setProperty("translateHelp", String.valueOf(translateHelp));
        p.setProperty("showSource", String.valueOf(showSource));
        p.setProperty("targetLang", targetLang);
        p.setProperty("concurrency", String.valueOf(concurrency));
        try (Writer w = Files.newBufferedWriter(configPath(), StandardCharsets.UTF_8)) {
            p.store(w, "Steam translator settings");
            Log.debug("[设置] 已保存到 " + configPath().toAbsolutePath());
        } catch (Exception e) {
            Log.warn("[设置] 保存失败: " + e.getMessage());
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }
}
