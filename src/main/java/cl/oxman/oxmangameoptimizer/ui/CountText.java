package cl.oxman.oxmangameoptimizer.ui;

/** Spanish count labels: only one takes the singular form. */
final class CountText {
    private CountText() { }

    static String format(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
