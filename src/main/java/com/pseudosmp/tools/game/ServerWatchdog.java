package com.pseudosmp.tools.game;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerWatchdog {
    private final MatrixSpigotBridge plugin;

    private BukkitTask tickTask;
    private ScheduledExecutorService watchdogExecutor;

    private volatile long lastTickTimestamp;
    private volatile boolean onFire = false;
    private volatile long freezeStartTimestamp;
    private volatile int notificationsSentCount = 0;

    public ServerWatchdog(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        stop();

        lastTickTimestamp = System.currentTimeMillis();
        onFire = false;
        notificationsSentCount = 0;

        // Synchronous tick task updating lastTickTimestamp every 10 ticks (0.5 seconds)
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                onTick();
            }
        }.runTaskTimer(plugin, 10L, 10L);

        // Async monitor running every 1 second
        watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MSB-ServerWatchdog");
            t.setDaemon(true);
            return t;
        });
        watchdogExecutor.scheduleAtFixedRate(this::checkWatchdog, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (tickTask != null) {
            try {
                tickTask.cancel();
            } catch (IllegalStateException ignored) {}
            tickTask = null;
        }
        if (watchdogExecutor != null) {
            try {
                watchdogExecutor.shutdownNow();
            } catch (Exception ignored) {}
            watchdogExecutor = null;
        }
        onFire = false;
        notificationsSentCount = 0;
    }

    public synchronized void reload() {
        stop();
        start();
    }

    private void onTick() {
        lastTickTimestamp = System.currentTimeMillis();

        if (onFire) {
            onFire = false;
            notificationsSentCount = 0;

            boolean notifyRecovery = MatrixSpigotBridge.config.getFormatSettingBool("watchdog.notify_recovery");
            if (notifyRecovery && plugin.getMatrix() != null) {
                long frozenSec = Math.max(1, (System.currentTimeMillis() - freezeStartTimestamp) / 1000);
                int timeout = MatrixSpigotBridge.config.getFormatSettingInt("watchdog.timeout", 30);
                String recoveryFormat = MatrixSpigotBridge.config.getFormat("watchdog.recovery");

                if (recoveryFormat != null && !recoveryFormat.isEmpty()) {
                    String msg = recoveryFormat
                            .replace("{LAG_TIME}", String.valueOf(frozenSec))
                            .replace("{TIMEOUT}", String.valueOf(timeout));
                    plugin.sendMessageToMatrix(msg, "", null);
                }
            }
        }
    }

    private void checkWatchdog() {
        boolean enabled = MatrixSpigotBridge.config.getFormatSettingBool("watchdog.enabled");
        if (!enabled || plugin.getMatrix() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsedSec = (now - lastTickTimestamp) / 1000;
        int timeout = MatrixSpigotBridge.config.getFormatSettingInt("watchdog.timeout", 30);

        if (timeout <= 0) {
            return;
        }

        if (elapsedSec >= timeout) {
            int repeatNotification = MatrixSpigotBridge.config.getFormatSettingInt("watchdog.repeat_notification", 1);
            String freezeFormat = MatrixSpigotBridge.config.getFormat("watchdog.freeze");

            if (freezeFormat == null || freezeFormat.isEmpty()) {
                return;
            }

            String msg = freezeFormat.replace("{TIMEOUT}", String.valueOf(timeout));
            if (MatrixSpigotBridge.config.canUsePapi && MatrixSpigotBridge.formatter != null) {
                msg = MatrixSpigotBridge.formatter.replacePlaceholderAPI(null, msg);
            }
            if (MatrixSpigotBridge.formatter != null) {
                msg = MatrixSpigotBridge.formatter.replaceTimePlaceholders(msg);
            }

            if (!onFire) {
                onFire = true;
                freezeStartTimestamp = lastTickTimestamp;
                notificationsSentCount = 1;

                sendDirectMatrixMessage(msg);
            } else if (notificationsSentCount < repeatNotification) {
                notificationsSentCount++;
                sendDirectMatrixMessage(msg);
            }
        }
    }

    private void sendDirectMatrixMessage(String msg) {
        try {
            if (plugin.getMatrix() != null) {
                plugin.getMatrix().postMessage(msg);
            }
        } catch (Exception ignored) {}
    }
}
