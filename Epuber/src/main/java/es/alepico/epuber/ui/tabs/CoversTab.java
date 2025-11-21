package es.alepico.epuber.ui.tabs;

import es.alepico.epuber.ui.reader.ReaderWindow;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CoversTab extends Tab {

    private final FlowPane coversPane = new FlowPane();
    private final Label pageInfo = new Label("Página 0/0");
    private final Button prevBtn = new Button("◀ Anterior");
    private final Button nextBtn = new Button("Siguiente ▶");
    private final ComboBox<Integer> pageSizeCombo = new ComboBox<>();

    private List<Path> files = List.of();
    private boolean hasScanned = false;
    private int pageSize = 30;
    private int currentPage = 1;
    private javafx.concurrent.Task<?> currentTask;

    public CoversTab() {
        super("Carátulas");
        setClosable(false);

        pageSizeCombo.getItems().addAll(20, 30, 50, 60);
        pageSizeCombo.setValue(pageSize);
        pageSizeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            pageSize = newV == null ? 30 : newV;
            currentPage = 1;
            renderPage();
        });

        prevBtn.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                renderPage();
            }
        });
        nextBtn.setOnAction(e -> {
            if (currentPage < getTotalPages()) {
                currentPage++;
                renderPage();
            }
        });

        coversPane.setHgap(18);
        coversPane.setVgap(18);
        coversPane.setPadding(new Insets(18));

        HBox pager = new HBox(10,
                new Label("Por página:"),
                pageSizeCombo,
                prevBtn,
                pageInfo,
                nextBtn
        );
        pager.setAlignment(Pos.CENTER_LEFT);
        pager.setPadding(new Insets(12, 18, 6, 18));

        BorderPane layout = new BorderPane();
        layout.setTop(pager);
        layout.setCenter(new ScrollPane(coversPane));

        setContent(layout);
        renderPage();
    }

    public void updateFiles(List<Path> newFiles) {
        Runnable update = () -> {
            files = new ArrayList<>(Optional.ofNullable(newFiles).orElse(List.of()));
            hasScanned = true;
            currentPage = 1;
            renderPage();
        };
        if (Platform.isFxApplicationThread()) update.run(); else Platform.runLater(update);
    }

    private void renderPage() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
        coversPane.getChildren().clear();

        if (files.isEmpty()) {
            pageInfo.setText("Página 0/0");
            prevBtn.setDisable(true);
            nextBtn.setDisable(true);
            String msg = hasScanned
                    ? "No se encontraron libros en el último escaneo."
                    : "Selecciona una carpeta en Biblioteca y escanea para ver carátulas.";
            coversPane.getChildren().add(new Label(msg));
            return;
        }

        int totalPages = getTotalPages();
        if (currentPage > totalPages) currentPage = totalPages;

        pageInfo.setText("Página " + currentPage + "/" + totalPages);
        prevBtn.setDisable(currentPage <= 1);
        nextBtn.setDisable(currentPage >= totalPages);

        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, files.size());
        List<Path> page = files.subList(start, end);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                for (Path p : page) {
                    if (isCancelled()) break;
                    Node thumb = buildThumb(p);
                    Platform.runLater(() -> coversPane.getChildren().add(thumb));
                }
                return null;
            }
        };
        currentTask = task;
        new Thread(task, "covers-page-loader").start();
    }

    private int getTotalPages() {
        if (files.isEmpty()) return 0;
        return (int) Math.ceil(files.size() / (double) pageSize);
    }

    private Node buildThumb(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Image cover = null;
        if (name.endsWith(".epub")) cover = loadEpubCover(file);
        else if (name.endsWith(".pdf")) cover = renderPdfFirstPage(file);

        Label title = new Label(prettyTitle(file));
        title.setWrapText(true);
        title.setMaxWidth(220);

        VBox box;
        if (cover != null) {
            ImageView iv = new ImageView(cover);
            iv.setPreserveRatio(true);
            iv.setFitHeight(260);
            box = new VBox(8, iv, title);
        } else {
            Label placeholder = new Label(name.endsWith(".mobi") ? "MOBI" : "Sin portada");
            placeholder.setMinSize(180, 240);
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setStyle("-fx-border-color:#cbd5e1; -fx-border-radius:10; -fx-padding:10; -fx-background-radius:10; -fx-background-color: rgba(0,0,0,0.02);");
            box = new VBox(8, placeholder, title);
        }

        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("thumb");
        box.setOnMouseClicked(evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) openFile(file);
        });
        return box;
    }

    private String prettyTitle(Path file) {
        String base = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String[] parts = base.split("\\s+-\\s+", 2);
        if (parts.length == 2) return parts[0].trim() + " — " + parts[1].trim();
        return base;
    }

    private void openFile(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".epub")) {
                ReaderWindow.openEpub(file);
            } else if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No se puede abrir el archivo en esta plataforma.");
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "No se pudo abrir el archivo:\n" + ex.getMessage());
        }
    }

    private Image loadEpubCover(Path epubPath) {
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            String opfPath = findOpfPath(zip);
            if (opfPath == null) return null;
            String coverHref = findCoverHref(zip, opfPath);
            if (coverHref == null) return null;
            String baseDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            String coverPath = normalizeZipPath(baseDir + coverHref);
            ZipEntry imgEntry = zip.getEntry(coverPath);
            if (imgEntry == null) imgEntry = zip.getEntry(coverHref);
            if (imgEntry == null) return null;
            try (InputStream is = zip.getInputStream(imgEntry)) {
                return new Image(is, 0, 320, true, true);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Image renderPdfFirstPage(Path pdfPath) {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(0, 130f);
            return SwingFXUtils.toFXImage(img, null);
        } catch (IOException e) {
            return null;
        }
    }

    private String findOpfPath(ZipFile zip) throws Exception {
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container == null) {
            var en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry z = en.nextElement();
                if (!z.isDirectory() && z.getName().toLowerCase(Locale.ROOT).endsWith(".opf")) return z.getName();
            }
            return null;
        }
        try (InputStream is = zip.getInputStream(container)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            NodeList n = doc.getElementsByTagName("rootfile");
            for (int i = 0; i < n.getLength(); i++) {
                var node = n.item(i);
                var attr = node.getAttributes() != null ? node.getAttributes().getNamedItem("full-path") : null;
                if (attr != null) return attr.getNodeValue();
            }
        }
        return null;
    }

    private String findCoverHref(ZipFile zip, String opfPath) throws Exception {
        ZipEntry opfEntry = zip.getEntry(opfPath);
        if (opfEntry == null) return null;
        try (InputStream is = zip.getInputStream(opfEntry)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();
            NodeList meta = doc.getElementsByTagName("meta");
            String coverId = null;
            for (int i = 0; i < meta.getLength(); i++) {
                var m = meta.item(i);
                var attrs = m.getAttributes();
                if (attrs == null) continue;
                var nameAttr = attrs.getNamedItem("name");
                var contentAttr = attrs.getNamedItem("content");
                if (nameAttr != null && "cover".equalsIgnoreCase(nameAttr.getNodeValue()) && contentAttr != null) {
                    coverId = contentAttr.getNodeValue();
                    break;
                }
            }
            NodeList items = doc.getElementsByTagName("item");
            String hrefById = null, hrefByProp = null, hrefByGuess = null;
            for (int i = 0; i < items.getLength(); i++) {
                var it = items.item(i);
                var attrs = it.getAttributes();
                if (attrs == null) continue;
                var idAttr = attrs.getNamedItem("id");
                var hrefAttr = attrs.getNamedItem("href");
                var propsAttr = attrs.getNamedItem("properties");
                var mtAttr = attrs.getNamedItem("media-type");
                String id = idAttr != null ? idAttr.getNodeValue() : null;
                String href = hrefAttr != null ? hrefAttr.getNodeValue() : null;
                String props = propsAttr != null ? propsAttr.getNodeValue() : "";
                String mt = mtAttr != null ? mtAttr.getNodeValue() : "";
                if (coverId != null && coverId.equals(id) && href != null) hrefById = href;
                if (props != null && props.toLowerCase(Locale.ROOT).contains("cover-image") && href != null) hrefByProp = href;
                if (href != null && mt != null && mt.startsWith("image/") && href.toLowerCase(Locale.ROOT).contains("cover")) hrefByGuess = href;
            }
            if (hrefById != null) return hrefById;
            if (hrefByProp != null) return hrefByProp;
            return hrefByGuess;
        }
    }

    private String normalizeZipPath(String p) {
        Deque<String> stack = new ArrayDeque<>();
        for (String part : p.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else stack.addLast(part);
        }
        return String.join("/", stack);
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Runnable r = () -> {
            Alert a = new Alert(type, msg, ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }
}