package app.java;

import org.owasp.encoder.Encode;

public final class InputSanitizer {
    private InputSanitizer() {}

    // Remove script/style blocks and any HTML tags, preserving textual content
    public static String stripTags(String input) {
        if (input == null) return null;
        // Remove <script>...</script> and <style>...</style> blocks (case-insensitive)
        String cleaned = input.replaceAll("(?i)<script.*?>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?i)<style.*?>.*?</style>", "");
        // Remove any remaining tags
        cleaned = cleaned.replaceAll("<[^>]*>", "");
        return cleaned.trim();
    }

    // Encode for safe HTML output — uses OWASP Encoder
    public static String encodeForHtml(String input) {
        if (input == null) return null;
        return Encode.forHtml(input);
    }

    // Encode for HTML attribute contexts
    public static String encodeForHtmlAttribute(String input) {
        if (input == null) return null;
        return Encode.forHtmlAttribute(input);
    }

    // Encode for JavaScript contexts
    public static String encodeForJavaScript(String input) {
        if (input == null) return null;
        return Encode.forJavaScript(input);
    }
}
