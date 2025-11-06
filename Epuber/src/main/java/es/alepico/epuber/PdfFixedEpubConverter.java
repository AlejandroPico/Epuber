package es.alepico.epuber;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Conversor PDF -> EPUB 3 (maquetación fija).
 * - Renderiza cada página a imagen (JPEG) con PDFBox.
 * - Opción de partir dobles (ancho/alto > 1.3).
 * - Inserta portada, sinopsis y metadatos tipo Calibre.
 */
public class PdfFixedEpubConverter {

    public interface ProgressListener {
        default void onMessage(String msg) {}
        default void onProgress(long done, long total) {}
    }

    public static class Metadata {
        public String title = "(Sin título)";
        public List<String> authors = new ArrayList<>();
        public String series;
        public Double seriesIndex;
        public String publisher;
        public LocalDate date;
        public LocalDate issued;
        public List<String> languages = new ArrayList<>(List.of("es"));
        public List<String> tags = new ArrayList<>();
        public Map<String,String> ids = new LinkedHashMap<>();
        public Double rating;
        public String synopsis;
        public Path coverImage;
    }

    private static class PageImage {
        final String name; final int width; final int height; final String mediaType;
        PageImage(String n, int w, int h, String mt){ name=n; width=w; height=h; mediaType=mt; }
    }

    public static Path convert(Path pdf, Path outEpub, Metadata meta,
                               boolean splitSpreads, int dpi,
                               ProgressListener listener) throws IOException {

        Objects.requireNonNull(pdf); Objects.requireNonNull(outEpub);
        if (dpi < 90) dpi = 90; if (dpi > 450) dpi = 450;

        Path work = Files.createTempDirectory("epub_fx_");
        Path OEBPS = work.resolve("OEBPS");
        Path IMAGES = OEBPS.resolve("images");
        Path XHTML  = OEBPS.resolve("xhtml");
        Files.createDirectories(work.resolve("META-INF"));
        Files.createDirectories(OEBPS); Files.createDirectories(IMAGES); Files.createDirectories(XHTML);

        // 1) Render PDF
        List<PageImage> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int total = doc.getNumberOfPages();
            for (int i=0; i<total; i++) {
                if (listener != null) listener.onMessage("Renderizando página " + (i+1) + "/" + total);
                BufferedImage img = renderer.renderImageWithDPI(i, dpi);
                boolean isSpread = splitSpreads && (img.getWidth() / (double) img.getHeight()) > 1.30;

                if (isSpread) {
                    int mid = img.getWidth() / 2;
                    BufferedImage left  = img.getSubimage(0, 0, mid, img.getHeight());
                    BufferedImage right = img.getSubimage(mid, 0, img.getWidth()-mid, img.getHeight());
                    String ln = String.format("p%03d_L.jpg", i+1);
                    String rn = String.format("p%03d_R.jpg", i+1);
                    ImageIO.write(left, "jpg", IMAGES.resolve(ln).toFile());
                    ImageIO.write(right,"jpg", IMAGES.resolve(rn).toFile());
                    pages.add(new PageImage(ln, left.getWidth(), left.getHeight(), "image/jpeg"));
                    pages.add(new PageImage(rn, right.getWidth(), right.getHeight(), "image/jpeg"));
                } else {
                    String name = String.format("p%03d.jpg", i+1);
                    ImageIO.write(img, "jpg", IMAGES.resolve(name).toFile());
                    pages.add(new PageImage(name, img.getWidth(), img.getHeight(), "image/jpeg"));
                }
                if (listener != null) listener.onProgress(i+1, total);
            }
        }
        if (pages.isEmpty()) throw new IOException("No se pudo renderizar ninguna página del PDF.");

        // 2) Portada
        String coverHref;
        if (meta != null && meta.coverImage != null && Files.isRegularFile(meta.coverImage)) {
            coverHref = "cover.jpg";
            BufferedImage cimg = ImageIO.read(meta.coverImage.toFile());
            ImageIO.write(cimg, "jpg", IMAGES.resolve(coverHref).toFile());
        } else coverHref = pages.get(0).name;

        // 3) Front matter: cover.xhtml + sinopsis.xhtml (si hay)
        writeText(XHTML.resolve("cover.xhtml"), xhtmlFixed("Portada", "images/" + coverHref,
                pages.get(0).width, pages.get(0).height));
        boolean hasSynopsis = meta != null && meta.synopsis != null && !meta.synopsis.isBlank();
        if (hasSynopsis) {
            writeText(XHTML.resolve("sinopsis.xhtml"),
                    xhtmlFlow("Sinopsis", "<section id=\"sinopsis\"><h2>Sinopsis</h2><p>"
                            + esc(meta.synopsis) + "</p></section>"));
        }

        // 4) Páginas
        List<String> xhtmlFiles = new ArrayList<>();
        for (int i=0; i<pages.size(); i++) {
            PageImage p = pages.get(i);
            String fn = String.format("page_%04d.xhtml", i+1);
            writeText(XHTML.resolve(fn), xhtmlFixed("Página " + (i+1), "images/" + p.name, p.width, p.height));
            xhtmlFiles.add(fn);
        }

        // 5) nav.xhtml
        StringBuilder nav = new StringBuilder();
        nav.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        nav.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n");
        nav.append("<head><meta charset=\"utf-8\"/><title>Índice</title></head><body>\n");
        nav.append("<nav epub:type=\"toc\" id=\"toc\"><h1>Índice</h1><ol>\n");
        nav.append("  <li><a href=\"xhtml/cover.xhtml\">Portada</a></li>\n");
        if (hasSynopsis) nav.append("  <li><a href=\"xhtml/sinopsis.xhtml\">Sinopsis</a></li>\n");
        for (String f : xhtmlFiles) nav.append("  <li><a href=\"xhtml/").append(f).append("\">").append(f).append("</a></li>\n");
        nav.append("</ol></nav></body></html>");
        writeText(OEBPS.resolve("nav.xhtml"), nav.toString());

        // 6) content.opf + container.xml + mimetype
        String opf = buildOpf(meta, xhtmlFiles, pages, coverHref, hasSynopsis);
        writeText(OEBPS.resolve("content.opf"), opf);
        writeText(work.resolve("META-INF").resolve("container.xml"),
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """);
        writeText(work.resolve("mimetype"), "application/epub+zip");

        // 7) Empaquetar EPUB (mimetype primero sin compresión)
        if (Files.exists(outEpub)) Files.delete(outEpub);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(outEpub))) {
            ZipEntry mime = new ZipEntry("mimetype");
            mime.setMethod(ZipEntry.STORED);
            byte[] mt = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            mime.setSize(mt.length); mime.setCompressedSize(mt.length); mime.setCrc(crc32(mt));
            z.putNextEntry(mime); z.write(mt); z.closeEntry();

            addDirToZip(z, work.resolve("META-INF"), work);
            addDirToZip(z, OEBPS, work);
        }

        // Limpieza
        try { deleteTree(work); } catch (Exception ignored) {}
        return outEpub;
    }

    // ===== Helpers =====

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private static String xhtmlFixed(String title, String imgHref, int w, int h) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head>
                    <meta charset="utf-8"/>
                    <title>%s</title>
                    <meta name="viewport" content="width=%d, height=%d"/>
                    <style>
                      html,body{margin:0;padding:0;background:#fff}
                      img{width:100%%;height:100%%;object-fit:contain;display:block}
                    </style>
                  </head>
                  <body>
                    <img src="%s" alt="%s"/>
                  </body>
                </html>
                """.formatted(esc(title), w, h, esc(imgHref), esc(title));
    }

    private static String xhtmlFlow(String title, String body) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="es" lang="es">
                  <head>
                    <meta charset="utf-8"/>
                    <title>%s</title>
                    <style>body{font-family:serif;line-height:1.6;margin:1.2em}h1,h2{line-height:1.25}</style>
                  </head>
                  <body>
                    %s
                  </body>
                </html>
                """.formatted(esc(title), body);
    }

    private static String buildOpf(Metadata m, List<String> xhtmlFiles, List<PageImage> pages, String coverHref, boolean hasSynopsis) {
        String lang = (m!=null && m.languages!=null && !m.languages.isEmpty()) ? m.languages.get(0) : "es";
        String uuid = UUID.randomUUID().toString();
        String today = java.time.LocalDate.now().toString();

        StringBuilder md = new StringBuilder();
        md.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\" xmlns:dcterms=\"http://purl.org/dc/terms/\">\n");
        md.append("    <dc:identifier id=\"bookid\">urn:uuid:").append(uuid).append("</dc:identifier>\n");
        md.append("    <dc:title>").append(esc(m != null ? m.title : "(Sin título)")).append("</dc:title>\n");
        if (m!=null && m.authors!=null) for (String a : m.authors) md.append("    <dc:creator>").append(esc(a)).append("</dc:creator>\n");
        if (m!=null && m.languages!=null && !m.languages.isEmpty()) for (String lg : m.languages) md.append("    <dc:language>").append(esc(lg.trim())).append("</dc:language>\n");
        else md.append("    <dc:language>es</dc:language>\n");
        if (m != null) {
            if (m.publisher != null && !m.publisher.isBlank()) md.append("    <dc:publisher>").append(esc(m.publisher)).append("</dc:publisher>\n");
            if (m.date != null) md.append("    <dc:date>").append(m.date).append("</dc:date>\n");
            if (m.issued != null) md.append("    <meta property=\"dcterms:issued\">").append(m.issued).append("</meta>\n");
            if (m.tags != null) for (String s : m.tags) if (!s.isBlank()) md.append("    <dc:subject>").append(esc(s.trim())).append("</dc:subject>\n");
            if (m.ids != null) for (Map.Entry<String,String> e : m.ids.entrySet())
                if (e.getValue()!=null && !e.getValue().isBlank())
                    md.append("    <dc:identifier>").append(esc(e.getKey())).append(":").append(esc(e.getValue())).append("</dc:identifier>\n");
            if (m.rating != null) md.append("    <meta name=\"calibre:rating\" content=\"").append(m.rating).append("\"/>\n");
            if (m.series != null && !m.series.isBlank()) {
                md.append("    <meta name=\"calibre:series\" content=\"").append(esc(m.series)).append("\"/>\n");
                if (m.seriesIndex != null) md.append("    <meta name=\"calibre:series_index\" content=\"").append(m.seriesIndex).append("\"/>\n");
                String seriesId = "c"+UUID.randomUUID().toString().replace("-", "");
                md.append("    <meta property=\"belongs-to-collection\" id=\"").append(seriesId).append("\">").append(esc(m.series)).append("</meta>\n");
                md.append("    <meta refines=\"#").append(seriesId).append("\" property=\"collection-type\">series</meta>\n");
                if (m.seriesIndex != null) md.append("    <meta refines=\"#").append(seriesId).append("\" property=\"group-position\">").append(m.seriesIndex).append("</meta>\n");
            }
        }
        md.append("    <meta property=\"dcterms:modified\">").append(today).append("T00:00:00Z</meta>\n");
        md.append("    <meta property=\"rendition:layout\">pre-paginated</meta>\n");
        md.append("    <meta property=\"rendition:spread\">auto</meta>\n");
        md.append("    <meta property=\"rendition:orientation\">auto</meta>\n");
        md.append("  </metadata>\n");

        StringBuilder man = new StringBuilder();
        man.append("  <manifest>\n");
        man.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
        man.append("    <item id=\"cover\" href=\"xhtml/cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
        if (hasSynopsis) man.append("    <item id=\"sinopsis\" href=\"xhtml/sinopsis.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
        man.append("    <item id=\"cover-img\" href=\"images/").append(esc(coverHref)).append("\" media-type=\"image/jpeg\" properties=\"cover-image\"/>\n");
        for (int i=0;i<pages.size();i++) {
            PageImage p = pages.get(i);
            man.append("    <item id=\"img").append(i+1).append("\" href=\"images/").append(esc(p.name)).append("\" media-type=\"").append(p.mediaType).append("\"/>\n");
        }
        for (int i=0;i<xhtmlFiles.size();i++) man.append("    <item id=\"p").append(i+1).append("\" href=\"xhtml/").append(xhtmlFiles.get(i)).append("\" media-type=\"application/xhtml+xml\"/>\n");
        man.append("  </manifest>\n");

        StringBuilder sp = new StringBuilder();
        sp.append("  <spine page-progression-direction=\"ltr\">\n");
        sp.append("    <itemref idref=\"cover\"/>\n");
        if (hasSynopsis) sp.append("    <itemref idref=\"sinopsis\"/>\n");
        for (int i=0;i<xhtmlFiles.size();i++) sp.append("    <itemref idref=\"p").append(i+1).append("\"/>\n");
        sp.append("  </spine>\n");

        return """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0" xml:lang="%s" prefix="rendition: http://www.idpf.org/vocab/rendition/#">
                %s
                %s
                %s
                </package>
                """.formatted(esc(lang), md.toString(), man.toString(), sp.toString());
    }

    private static void addDirToZip(ZipOutputStream z, Path dir, Path base) throws IOException {
        try (var s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isDirectory(p)) continue;
                String rel = base.relativize(p).toString().replace('\\','/');
                z.putNextEntry(new ZipEntry(rel));
                Files.copy(p, z);
                z.closeEntry();
            }
        }
    }

    private static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static long crc32(byte[] data) {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        c.update(data);
        return c.getValue();
    }

    private static void deleteTree(Path root) throws IOException {
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}
