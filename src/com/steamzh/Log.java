package com.steamzh;

/**
 * 轻量日志。
 *
 * 默认只输出启动信息和错误，避免刷屏。
 * 命令行加 --verbose 时输出全部调试细节（连接、注入、每批翻译等）。
 */
public class Log {

    public static volatile boolean verbose = false;

    /** 始终输出：启动信息、正常状态 */
    public static void info(String msg) {
        System.out.println(msg);
    }

    /** 仅 --verbose 时输出：调试细节 */
    public static void debug(String msg) {
        if (verbose) System.out.println(msg);
    }

    /** 始终输出：错误和异常 */
    public static void warn(String msg) {
        System.out.println(msg);
    }
}
