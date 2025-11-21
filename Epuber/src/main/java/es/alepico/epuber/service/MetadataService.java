package es.alepico.epuber.service;

import es.alepico.epuber.model.BookMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio encargado de buscar información de libros en internet (OpenLibrary)
 * y de guardar ficheros de metadatos auxiliares (sidecars).
 */
public class MetadataService {

    private final HttpClient client;

    public MetadataService() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Busca metadatos en OpenLibrary basado en un título.
     *
     * @param title El título del libro a buscar.
     * @return Un Optional conteniendo los metadatos si se encontraron, o vacío si no.
     * @throws IOException Si hay error de red.
     * @throws InterruptedException Si la petición es interrumpida.
     */
    public Optional<BookMetadata> fetchFromOpenLibrary(String title) throws IOException, InterruptedException {
        if (title == null || title.isBlank()) return Optional.empty();

        String query = URLEncoder.encode(title, StandardCharsets.UTF_8);
        String url = "https://openlibrary.org/search.json?limit=1&title=" + query;

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            String json = resp.body();
            
            // Extraemos los datos usando Regex para no depender de librerías externas (Gson/Jackson)
            // tal como estaba en el proyecto original.
            String foundTitle = extractJsonField(json, "title");
            
            if (foundTitle != null) {
                BookMetadata meta = new BookMetadata();
                meta.title = foundTitle;
                
                String author = extractJsonArrayFirst(json, "author_name");
                if (author != null) meta.authors.add(author);
                
                String sentence = extractJsonField(json, "first_sentence");
                if (sentence != null) {
                    meta.synopsis = sentence.replaceAll("^\"|\"$", "");
                }
                
                // Intentamos sacar el año si existe
                String year = extractJsonField(json, "first_publish_year");
                if (year != null) {
                    // Podríamos parsearlo a LocalDate si fuera necesario, 
                    // aquí lo dejamos simple o lo asignamos si extendemos el modelo.
                }

                return Optional.of(meta);
            }
        }
        
        return Optional.empty();
    }

    /**
     * Guarda un archivo de texto simple (.metadata.txt) junto al archivo del libro.
     */
    public void saveSidecar(Path bookFile, BookMetadata meta) throws IOException {
        if (bookFile == null || meta == null) return;

        Path sidecar = bookFile.resolveSibling(bookFile.getFileName().toString() + ".metadata.txt");
        
        String tagsStr = (meta.tags != null) ? String.join(", ", meta.tags) : "";
        String synopsisStr = (meta.synopsis != null) ? meta.synopsis : "";

        List<String> lines = List.of(
                "Título: " + (meta.title != null ? meta.title : ""),
                "Autor: " + meta.getAuthorString(),
                "Etiquetas: " + tagsStr,
                "Sinopsis:",
                synopsisStr
        );

        Files.write(sidecar, lines, StandardCharsets.UTF_8, 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // ===== Métodos privados de parsing (Regex) extraídos de Epuber.java =====

    private String extractJsonField(String json, String field) {
        // Busca: "field": "valor"
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        
        // Fallback para números (sin comillas): "field": 1234
        Pattern pNum = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([0-9]+)");
        Matcher mNum = pNum.matcher(json);
        if (mNum.find()) return mNum.group(1);
        
        return null;
    }

    private String extractJsonArrayFirst(String json, String field) {
        // Busca: "field": ["valor1", "valor2"]
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String content = m.group(1);
            String[] parts = content.split(",");
            if (parts.length > 0) {
                return parts[0].replaceAll("^\"|\"$", "").trim();
            }
        }
        return null;
    }
}