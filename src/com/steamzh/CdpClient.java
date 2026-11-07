package com.steamzh;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 一个页面的 CDP 调试客户端。
 *
 * 用法：
 *   CdpClient cdp = CdpClient.connect(wsUrl);
 *   String resp = cdp.send("Runtime.evaluate", "{\"expression\":\"1+1\"}");
 */
public class CdpClient implements WebSocket.Listener {

    private WebSocket socket;

    // 流水号 -> 等待结果的盒子
    private final Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    // 一条消息可能被拆成多段到达，先攒在缓冲区
    private final StringBuilder buffer = new StringBuilder();

    private long nextId = 1;

    public static CdpClient connect(String wsUrl) throws Exception {
        CdpClient client = new CdpClient();
        client.socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), client)
                .get(10, TimeUnit.SECONDS);   // 最多等 10 秒握手
        return client;
    }

    /** 发一条请求，等它的回信 */
    public String send(String method, String paramsJson) throws Exception {
        long id = nextId++;
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(id, future);

        String message = "{\"id\":" + id
                + ",\"method\":\"" + method + "\""
                + ",\"params\":" + paramsJson + "}";
        socket.sendText(message, true).get(10, TimeUnit.SECONDS);

        return future.get(15, TimeUnit.SECONDS);   // 最多等 15 秒回信
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        Log.debug("[CdpClient] WebSocket 已连接");
        webSocket.request(1);   // 关键：不请求就收不到消息
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {                       // last=true 表示这一条消息收完整了
            String message = buffer.toString();
            buffer.setLength(0);
            handle(message);
        }
        webSocket.request(1);             // 关键：每收一条，再请求下一条
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        Log.debug("[CdpClient] 连接关闭: " + statusCode + " " + reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        Log.warn("[CdpClient] 出错: " + error);
    }

    /** 收到消息：取出 id，找到对应的盒子，把结果塞进去 */
    private void handle(String message) {
        Long id = JsonUtil.extractLong(message, "id");
        if (id == null) return;           // 没有 id 的是事件通知，暂时忽略
        CompletableFuture<String> future = pending.remove(id);
        if (future != null) future.complete(message);
    }

    public void close() {
        try { socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Exception e) { }
    }
}
