package cl.oxman.oxmangameoptimizer.performance.benchmark;

public enum ExperimentInterpretation {
    LIKELY_IMPROVEMENT("El resultado experimental sugiere una mejora."),
    NO_CLEAR_DIFFERENCE("No se observa una diferencia clara."),
    LIKELY_REGRESSION("El resultado experimental sugiere una regresión."),
    HIGH_VARIABILITY("Alta variabilidad entre runs. Repite el benchmark en condiciones más consistentes."),
    NO_CHANGE("HighQoS ya estaba activo en el proceso.\nNo se aplicó ninguna modificación experimental.\nEl experimento no puede evaluar un efecto de rendimiento."),
    CONFIGURATION_DRIFT("La prioridad del proceso fue modificada externamente durante la prueba. El resultado no puede utilizarse para evaluar ABOVE_NORMAL."),
    INSUFFICIENT_DATA("Datos insuficientes para interpretar el experimento.");
    private final String message;
    ExperimentInterpretation(String message) { this.message = message; }
    public String message() { return message; }
}
