package com.djay.touchmenot;

/**
 * Lightweight diagnostic logger to capture class and method calls during runtime.
 * Writes to the same shared TouchMeNot log file as Logger.
 */
public final class DiagnosticLogger {
    private DiagnosticLogger() {}

    public static void log(String category, String message) {
        Logger.log(category, message);
    }
}
