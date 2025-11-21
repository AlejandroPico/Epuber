package es.alepico.epuber.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Representa un archivo de libro (EPUB, PDF, MOBI) en el sistema de archivos.
 * Encapsula la ruta física y proporciona métodos para obtener información básica
 * sin necesidad de realizar una lectura profunda de metadatos.
 */
public class BookFile {

    private final Path path;
    private final long size;
    private final String extension;
    
    // Metadatos inferidos del nombre de archivo (rápido)
    private String simpleTitle;
    private String simpleAuthor;

    public BookFile(Path path) {
        this.path = path;
        this.size = calculateSize();
        this.extension = extractExtension();
        parseFilename();
    }

    private long calculateSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private String extractExtension() {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    /**
     * Intenta adivinar Título y Autor basándose en el patrón "Título - Autor.ext".
     * Lógica migrada de 'getTitleAuthorQuick' del proyecto original.
     */
    private void parseFilename() {
        String fileName = path.getFileName().toString();
        // Quitar extensión
        String baseName = fileName.replaceFirst("\\.[^.]+$", "");
        
        // Intentar separar por guion rodeado de espacios " - "
        String[] parts = baseName.split("\\s+-\\s+", 2);
        
        if (parts.length == 2) {
            this.simpleTitle = parts[0].trim();
            this.simpleAuthor = parts[1].trim();
        } else {
            this.simpleTitle = baseName.trim();
            this.simpleAuthor = ""; // Autor desconocido por nombre de archivo
        }
    }

    // ===== Getters =====

    public Path getPath() {
        return path;
    }

    public String getFileName() {
        return path.getFileName().toString();
    }

    public long getSize() {
        return size;
    }
    
    /**
     * Devuelve el tamaño formateado (ej. "1.5 MB").
     */
    public String getSizeFormatted() {
        return String.format(Locale.ROOT, "%.2f MB", size / 1_048_576.0);
    }

    public String getExtension() {
        return extension;
    }

    public String getSimpleTitle() {
        return simpleTitle;
    }

    public String getSimpleAuthor() {
        return simpleAuthor;
    }

    /**
     * Devuelve una clave normalizada para detectar duplicados.
     * Combina título y autor limpiando puntuación.
     */
    public String getNormalizationKey() {
        String raw = (simpleTitle + " " + simpleAuthor).toLowerCase(Locale.ROOT);
        // Eliminar puntuación y espacios extra
        return raw.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
    }

    @Override
    public String toString() {
        return getFileName();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookFile bookFile = (BookFile) o;
        return path.equals(bookFile.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}