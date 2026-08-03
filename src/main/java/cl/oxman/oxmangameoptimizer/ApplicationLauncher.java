package cl.oxman.oxmangameoptimizer;

/**
 * Entry point for native packaging. Keeping this class independent from
 * JavaFX prevents the JVM launcher from requiring JavaFX on the boot path.
 */
public final class ApplicationLauncher {

    private ApplicationLauncher() {
    }

    public static void main(String[] args) {
        OxmanApplication.main(args);
    }
}
