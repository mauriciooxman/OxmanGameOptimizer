package cl.oxman.oxmangameoptimizer.ui;

public record ClientModeViewState(ApplicationMode mode, boolean performanceLabVisible,
        boolean experimentsVisible, boolean technicalLogVisible) {
    public static ClientModeViewState initial() { return forMode(ApplicationMode.CLIENT); }
    public static ClientModeViewState forMode(ApplicationMode mode) {
        boolean advanced = mode == ApplicationMode.ADVANCED;
        return new ClientModeViewState(mode, advanced, advanced, advanced);
    }
}
