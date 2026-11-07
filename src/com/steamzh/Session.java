package com.steamzh;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个页面目标的翻译会话：
 *   连接 -> 注入脚本 -> 取出待翻译文本 -> 并发翻译 -> 写回页面。
 */
public class Session {

    /** 每轮最多处理的条数 */
    private static final int BATCH = 20;

    private final Target target;
    private CdpClient cdp;
    private boolean installed;
    private int translatedCount;
    private boolean lastShowSource = Config.showSource;

    public Session(Target target) {
        this.target = target;
    }

    public void tick(BingTranslator translator) throws Exception {
        if (cdp == null) {
            cdp = CdpClient.connect(target.wsUrl);
            Log.debug("[连接] " + target.describe());

            call("window.__stzh = undefined");
            call(PageScript.INIT);
            installed = true;
            Log.debug("[注入] " + target.describe());
        }

        if (!installed) {
            call(PageScript.INIT);
            installed = true;
            Log.debug("[注入] " + target.describe());
        }

        // 设置里改了「悬停显示原文」就同步给页面
        if (lastShowSource != Config.showSource) {
            call("window.__stzh.setShowSource(" + Config.showSource + ")");
            lastShowSource = Config.showSource;
        }

        String drainedResp = call("window.__stzh ? window.__stzh.drain(" + BATCH + ") : 'NO_SCRIPT'");
        String drainedJson = JsonUtil.extractString(drainedResp, "value");

        // 页面导航后脚本会丢，补注入
        if ("NO_SCRIPT".equals(drainedJson)) {
            call(PageScript.INIT);
            return;
        }
        if (drainedJson == null || drainedJson.isEmpty() || drainedJson.equals("[]")) {
            return;
        }

        List<Long> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        Matcher m = Pattern.compile("\"id\":(\\d+),\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(drainedJson);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
            texts.add(JsonUtil.unescape(m.group(2)));
        }
        if (ids.isEmpty()) return;

        List<String> results = translator.translateMany(texts);

        StringBuilder applied = new StringBuilder("[");
        int okCount = 0;
        for (int i = 0; i < ids.size(); i++) {
            String tr = results.get(i);
            if (applied.length() > 1) applied.append(',');
            applied.append("{\"id\":").append(ids.get(i))
                    .append(",\"translated\":")
                    .append(tr == null ? "null" : JsonUtil.escape(tr))
                    .append('}');
            if (tr != null) okCount++;
        }
        applied.append(']');

        call("window.__stzh.apply(" + JsonUtil.escape(applied.toString()) + ")");

        translatedCount += okCount;
        if (okCount > 0) {
            Log.debug("[翻译] " + target.describe()
                    + " 本轮 " + okCount + " 条，累计 " + translatedCount);
        }
    }

    /** 在页面里执行一段 JS，返回完整的 CDP 响应 */
    private String call(String expression) throws Exception {
        String params = "{\"expression\":" + JsonUtil.escape(expression)
                + ",\"returnByValue\":true,\"awaitPromise\":false}";
        return cdp.send("Runtime.evaluate", params);
    }

    public String id() {
        return target.id;
    }
}
