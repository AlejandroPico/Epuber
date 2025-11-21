package es.alepico.epuber.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo de datos que representa los metadatos completos de un libro.
 * Incluye campos estándar (Dublin Core) y extensiones específicas (Calibre/EPUB3).
 * Unifica la antigua clase estática 'PdfFixedEpubConverter.Metadata' y 'TitleAuthor'.
 */
public class BookMetadata {

    // === Dublin Core Básico ===
    public String title = "(Sin título)";
    public List<String> authors = new ArrayList<>();
    public String publisher;
    public LocalDate date;         // dc:date
    public List<String> languages = new ArrayList<>(List.of("es"));
    public String synopsis;        // dc:description

    // === Metadatos Extendidos (Calibre / Series / Clasificación) ===
    public String series;          // calibre:series
    public Double seriesIndex;     // calibre:series_index
    public LocalDate issued;       // dcterms:issued
    public List<String> tags = new ArrayList<>(); // dc:subject
    public Map<String, String> ids = new LinkedHashMap<>(); // isbn, doi, google, amazon, etc.
    public Double rating;          // calibre:rating

    // === Archivos asociados ===
    public Path coverImage;        // Ruta local a la imagen de portada para conversión

    // === Constructores ===

    public BookMetadata() {
        // Constructor vacío requerido
    }

    /**
     * Constructor de conveniencia para inicialización rápida (ej. desde nombre de archivo).
     */
    public BookMetadata(String title, String author) {
        this.title = (title == null || title.isBlank()) ? "(Sin título)" : title.trim();
        if (author != null && !author.isBlank()) {
            this.authors.add(author.trim());
        }
    }

    // === Métodos de Utilidad ===

    /**
     * Devuelve los autores como una sola cadena separada por punto y coma.
     * Útil para mostrar en tablas o etiquetas de la UI.
     */
    public String getAuthorString() {
        if (authors == null || authors.isEmpty()) return "";
        return String.join("; ", authors);
    }

    /**
     * Devuelve el primer idioma de la lista o "es" por defecto.
     */
    public String getPrimaryLanguage() {
        if (languages != null && !languages.isEmpty()) {
            return languages.get(0);
        }
        return "es";
    }

    @Override
    public String toString() {
        return "BookMetadata{" +
                "title='" + title + '\'' +
                ", authors=" + authors +
                '}';
    }
}