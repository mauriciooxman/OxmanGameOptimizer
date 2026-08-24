package cl.oxman.oxmangameoptimizer.game;

import cl.oxman.oxmangameoptimizer.optimizer.BoostOptimizer;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.ui.ClientSessionStatus;
import cl.oxman.oxmangameoptimizer.ui.LogManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class GamingSessionManager {

    private static final AtomicBoolean SESSION_ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean RESTORING = new AtomicBoolean(false);

    private GamingSessionManager() {
    }

    public static boolean start(GameProfile profile, Consumer<String> statusCallback) {
        if (!SystemOperationGuard.acquire(SystemOperationGuard.Operation.GAMING_SESSION)) {
            LogManager.addLog("⚠ Hay otra operación activa: " + SystemOperationGuard.active());
            return false;
        }
        if (!SESSION_ACTIVE.compareAndSet(false, true)) {
            SystemOperationGuard.release(SystemOperationGuard.Operation.GAMING_SESSION);
            LogManager.addLog("⚠ Ya existe una sesión de juego activa.");
            return false;
        }

        Thread sessionThread = new Thread(() -> runSession(profile, statusCallback), "game-session");
        sessionThread.setDaemon(true);
        sessionThread.start();
        return true;
    }

    public static void finishManually(Consumer<String> statusCallback) {
        Thread restoreThread = new Thread(() -> restore(statusCallback), "manual-game-restore");
        restoreThread.setDaemon(true);
        restoreThread.start();
    }

    public static boolean isSessionActive() {
        return SESSION_ACTIVE.get();
    }

    public static void finishBeforeExit() {
        if (SESSION_ACTIVE.get()) {
            restore(status -> { });
        }
    }

    private static void runSession(GameProfile profile, Consumer<String> statusCallback) {
        statusCallback.accept("Aplicando perfil para " + profile);
        OptimizationReport report = BoostOptimizer.applyBoost(profile.toString());
        statusCallback.accept(ClientSessionStatus.afterOptimization(report));

        statusCallback.accept(ClientSessionStatus.starting(profile.toString()));
        LogManager.addClientLog("Abriendo " + profile + "...");
        boolean launched = profile.launch();
        if (!launched) {
            LogManager.addClientLog("Abre el juego manualmente; Oxman lo detectará automáticamente.");
        }

        if (!waitForStart(profile, 120)) {
            if (profile.equals(GameProfile.VALORANT)) {
                LogManager.addLog("⚠ Riot Client no respondió. Ciérralo desde la bandeja");
                LogManager.addLog("  o reinicia Windows y vuelve a intentar.");
            } else {
                LogManager.addLog("⚠ No se detectó el juego.");
            }
            LogManager.addLog("Restaurando Windows...");
            restore(statusCallback);
            return;
        }

        LogManager.addClientLog(profile + " detectado. El sistema está listo para jugar.");
        statusCallback.accept(ClientSessionStatus.running(profile.toString()));

        while (SESSION_ACTIVE.get() && profile.isRunning()) {
            if (!sleepSeconds(3)) {
                if (SESSION_ACTIVE.get()) restore(statusCallback);
                return;
            }
        }

        if (SESSION_ACTIVE.get()) {
            LogManager.addClientLog("Juego cerrado. Restaurando el sistema automáticamente.");
            restore(statusCallback);
        }
    }

    private static boolean waitForStart(GameProfile profile, int timeoutSeconds) {
        int attempts = timeoutSeconds / 2;
        for (int i = 0; i < attempts && SESSION_ACTIVE.get(); i++) {
            if (profile.isRunning()) {
                return true;
            }
            if (i == 10) {
                profile.retryLaunch();
            }
            if (!sleepSeconds(2)) {
                return false;
            }
        }
        return false;
    }

    private static void restore(Consumer<String> statusCallback) {
        if (!RESTORING.compareAndSet(false, true)) {
            return;
        }

        try {
            statusCallback.accept("Restaurando Windows...");
            boolean restored = BoostOptimizer.restoreDefaults();
            SESSION_ACTIVE.set(false);
            SystemOperationGuard.release(SystemOperationGuard.Operation.GAMING_SESSION);
            statusCallback.accept(restored ? "Windows restaurado" : "Restauración pendiente");
        } finally {
            RESTORING.set(false);
        }
    }

    public static boolean recoverIncompleteSession() {
        return BoostOptimizer.recoverIncompleteSession();
    }

    private static boolean sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
