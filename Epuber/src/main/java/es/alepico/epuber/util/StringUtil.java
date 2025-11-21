package es.alepico.epuber.util;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utilidades estáticas para manipulación de cadenas, parseo de metadatos y limpieza de texto.
 */
public class StringUtil {

    private StringUtil() {
        // Evitar instanciación
    }

    /**
     * Devuelve el valor por defecto si la cadena es nula o está vacía.
     * Equivalente al antiguo 'valOr'.
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return (str == null || str.isBlank()) ? defaultValue : str.trim();
    }

    /**
     * Devuelve null si la cadena está vacía o solo tiene espacios.
     * Útil para limpiar campos opcionales antes de guardarlos.
     */
    public static String blankToNull(String str) {
        return (str == null || str.isBlank()) ? null : str.trim();
    }

    /**
     * Escapa caracteres especiales para XML/HTML.
     * Vital para generar el archivo .opf y los xhtml del EPUB sin romper la estructura.
     */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Convierte una cadena separada por un delimitador en una lista limpia.
     * Ej: "autor1; autor2 ; autor3" -> ["autor1", "autor2", "autor3"]
     */
    public static List<String> parseList(String text, String separator) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        
        String[] parts = text.split("\\s*" + Pattern.quote(separator) + "\\s*");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    /**
     * Parsea cadenas de tipo clave-valor usadas en IDs.
     * Ej: "isbn:978123, google:XYZ" -> Map{isbn=978123, google=XYZ}
     */
    public static Map<String, String> parseKeyValue(String text) {
        Map<String, String> m = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return m;

        for (String part : text.split("\\s*,\\s*")) {
            int i = part.indexOf(':');
            if (i > 0 && i < part.length() - 1) {
                String key = part.substring(0, i).trim();
                String val = part.substring(i + 1).trim();
                m.put(key, val);
            }
        }
        return m;
    }

    /**
     * Normaliza una cadena para comparaciones "fuzzy" (búsqueda de duplicados).
     * Elimina puntuación, convierte a minúsculas y colapsa espacios.
     */
    public static String normalizeForComparison(String text) {
        if (text == null) return "";
        String raw = text.toLowerCase(Locale.ROOT);
        // Eliminar puntuación
        raw = raw.replaceAll("[\\p{Punct}]+", " ");
        // Colapsar espacios múltiples
        return raw.replaceAll("\\s+", " ").trim();
    }

    /**
     * Intenta parsear un Double de un String de forma segura, soportando coma y punto.
     */
    public static Double parseDoubleSafe(String text) {
        try {
            if (text == null || text.isBlank()) return null;
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }
}