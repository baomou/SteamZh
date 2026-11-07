package com.steamzh;

import java.util.ArrayList;
import java.util.List;

/** 一个 CEF 页面目标（/json/list 里的一条记录）。 */
public class Target {

    public final String id;
    public final String title;
    public final String url;
    public final String wsUrl;

    public Target(String id, String title, String url, String wsUrl) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.wsUrl = wsUrl;
    }

    /** 拉取当前所有页面目标 */
    public static List<Target> list() throws Exception {
        String json = Http.get("http://127.0.0.1:8080/json/list");

        List<String> ids = JsonUtil.extractAll(json, "id");
        List<String> titles = JsonUtil.extractAll(json, "title");
        List<String> urls = JsonUtil.extractAll(json, "url");
        List<String> wss = JsonUtil.extractAll(json, "webSocketDebuggerUrl");

        List<Target> out = new ArrayList<>();
        int n = Math.min(ids.size(), wss.size());
        for (int i = 0; i < n; i++) {
            out.add(new Target(
                    ids.get(i),
                    i < titles.size() ? titles.get(i) : "",
                    i < urls.size() ? urls.get(i) : "",
                    wss.get(i)));
        }
        return out;
    }

    /** 只处理设置里勾选的页面类型 */
    public boolean translatable() {
        if (url == null || url.isEmpty()) return false;
        if (url.contains("store.steampowered.com")) return Config.translateStore;
        if (url.contains("steamcommunity.com")) return Config.translateCommunity;
        if (url.contains("help.steampowered.com")) return Config.translateHelp;
        return Config.translateStore && url.contains("steampowered.com");
    }

    public String describe() {
        if (title != null && !title.isEmpty()) return title;
        if (url != null && !url.isEmpty()) return url;
        return id;
    }
}
