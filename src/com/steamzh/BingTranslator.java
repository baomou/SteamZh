package com.steamzh;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 必应翻译客户端（网页版 ttranslatev3 接口，免 API Key）。
 *
 * 踩过的坑：
 *   - www.bing.com 会被 302 到 cn.bing.com，POST 必须发到重定向后的域名；
 *   - 请求要带抓页面时拿到的 Cookie，否则返回 200 但 body 为空。
 *
 * 并发：内部用 4 个线程并行请求，批量翻译时吞吐量约为串行的 4 倍。
 */
public class BingTranslator {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String PAGE = "https://www.bing.com/translator?from=en&to=zh-Hans";
    private static final long TOKEN_TTL = 45L * 60 * 1000;

    private String base = "https://cn.bing.com";
    private String key;
    private String token;
    private String ig;
    private String iid;
    private String cookie = "";
    private long fetchedAt;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private String cacheLang = Config.targetLang;
    private int requestCount;
    private int cacheHits;
    private int failures;

    /** 翻译一段文本；失败返回 null */
    public String translate(String text) {
        if (text == null) return null;
        String src = text.trim().replaceAll("\\s+", " ");
        if (src.isEmpty()) return null;

        // 目标语言变了，旧缓存作废
        if (!Config.targetLang.equals(cacheLang)) {
            cache.clear();
            cacheLang = Config.targetLang;
        }

        String hit = cache.get(src);
        if (hit != null) {
            cacheHits++;
            return hit;
        }

        try {
            String out = callOnce(src);
            if (out == null) {
                refreshToken();
                out = callOnce(src);
            }
            if (out != null) {
                if (cache.size() > 6000) cache.clear();
                cache.put(src, out);
            } else {
                failures++;
            }
            return out;
        } catch (Exception e) {
            failures++;
            Log.debug("[翻译] 失败: " + e.getMessage());
            return null;
        }
    }

    /** 并发批量翻译，保持输入顺序 */
    public List<String> translateMany(List<String> texts) {
        String[] out = new String[texts.size()];
        java.util.concurrent.atomic.AtomicInteger cursor = new java.util.concurrent.atomic.AtomicInteger(0);
        int workers = Math.max(1, Math.min(Config.concurrency, texts.size()));

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            Thread t = new Thread(() -> {
                int idx;
                while ((idx = cursor.getAndIncrement()) < texts.size()) {
                    out[idx] = translate(texts.get(idx));
                }
            });
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }
        return java.util.Arrays.asList(out);
    }

    private String callOnce(String text) throws Exception {
        if (key == null || System.currentTimeMillis() - fetchedAt > TOKEN_TTL) {
            refreshToken();
        }
        requestCount++;

        String body = "fromLang=auto-detect"
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&to=" + Config.targetLang
                + "&token=" + token
                + "&key=" + key;

        HttpResponse<String> resp;
        synchronized (this) {
            resp = Http.postForm(
                    base + "/ttranslatev3?isVertical=1&IG=" + ig + "&IID=" + iid, body,
                    "User-Agent", UA,
                    "Referer", base + "/translator",
                    "Origin", base,
                    "Accept", "application/json, text/plain, */*",
                    "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                    "Cookie", cookie);
        }

        if (resp.statusCode() >= 400) return null;
        String out = JsonUtil.extractString(resp.body(), "text");
        if (out == null) {
            refreshToken();
        }
        return out;
    }

    private synchronized void refreshToken() throws Exception {
        HttpResponse<String> resp = Http.getResponse(PAGE,
                "User-Agent", UA,
                "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        String html = resp.body();
        URI finalUri = resp.uri();
        base = finalUri.getScheme() + "://" + finalUri.getHost();

        List<String> rawCookies = new ArrayList<>(resp.headers().allValues("set-cookie"));
        cookie = String.join("; ", rawCookies.stream().map(c -> c.split(";")[0]).toList());

        Matcher abuse = Pattern.compile("params_AbusePreventionHelper\\s*=\\s*\\[([^\\]]+)\\]").matcher(html);
        Matcher igm = Pattern.compile("IG:\"([0-9A-F]+)\"").matcher(html);
        Matcher iidm = Pattern.compile("data-iid=\"(translator\\.\\d+)\"").matcher(html);

        if (!abuse.find() || !igm.find()) {
            throw new RuntimeException("必应令牌解析失败（页面结构可能变了）");
        }
        String[] parts = abuse.group(1).split(",");
        key = parts[0].trim().replace("\"", "");
        token = parts[1].trim().replace("\"", "");
        ig = igm.group(1);
        iid = iidm.find() ? iidm.group(1) : "translator.5023";
        fetchedAt = System.currentTimeMillis();
    }

    public String stats() {
        return "请求 " + requestCount + " 次 / 缓存命中 " + cacheHits
                + " 次 / 失败 " + failures + " 次 / 缓存 " + cache.size() + " 条";
    }
}
