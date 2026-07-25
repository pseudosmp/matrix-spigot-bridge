package com.pseudosmp.tools.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SchedulerAdapter {
    private static final boolean IS_FOLIA;
    private static Method getAsyncSchedulerMethod;
    private static Method getGlobalRegionSchedulerMethod;
    private static Method asyncRunNowMethod;
    private static Method asyncRunAtFixedRateMethod;
    private static Method globalRunMethod;
    private static Method globalRunAtFixedRateMethod;

    static {
        boolean isFolia = false;
        try {
            Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");

            getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");
            getGlobalRegionSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");

            asyncRunNowMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            asyncRunAtFixedRateMethod = asyncSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

            globalRunMethod = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
            globalRunAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

            isFolia = true;
        } catch (Throwable ignored) {
            isFolia = false;
        }
        IS_FOLIA = isFolia;
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static TaskWrapper runAsync(JavaPlugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
                Consumer<Object> consumer = s -> task.run();
                Object scheduledTask = asyncRunNowMethod.invoke(asyncScheduler, plugin, consumer);
                return () -> {
                    try {
                        scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
                    } catch (Exception ignored) {}
                };
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to run Folia async task", e);
                return () -> {};
            }
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return bt::cancel;
        }
    }

    public static TaskWrapper runGlobal(JavaPlugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
                Consumer<Object> consumer = s -> task.run();
                Object scheduledTask = globalRunMethod.invoke(globalScheduler, plugin, consumer);
                return () -> {
                    try {
                        scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
                    } catch (Exception ignored) {}
                };
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to run Folia global task", e);
                return () -> {};
            }
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
            return bt::cancel;
        }
    }

    public static TaskWrapper runAsyncTimer(JavaPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (IS_FOLIA) {
            try {
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
                Consumer<Object> consumer = s -> task.run();
                long delayMs = initialDelayTicks * 50;
                long periodMs = periodTicks * 50;
                Object scheduledTask = asyncRunAtFixedRateMethod.invoke(asyncScheduler, plugin, consumer, delayMs, Math.max(1, periodMs), TimeUnit.MILLISECONDS);
                return () -> {
                    try {
                        scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
                    } catch (Exception ignored) {}
                };
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to run Folia async timer task", e);
                return () -> {};
            }
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
            return bt::cancel;
        }
    }

    public static TaskWrapper runGlobalTimer(JavaPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (IS_FOLIA) {
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
                Consumer<Object> consumer = s -> task.run();
                Object scheduledTask = globalRunAtFixedRateMethod.invoke(globalScheduler, plugin, consumer, Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
                return () -> {
                    try {
                        scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
                    } catch (Exception ignored) {}
                };
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to run Folia global timer task", e);
                return () -> {};
            }
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
            return bt::cancel;
        }
    }

    @FunctionalInterface
    public interface TaskWrapper {
        void cancel();
    }
}

