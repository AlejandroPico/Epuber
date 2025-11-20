package es.alepico.epuber;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ReaderWindow {

    private final Stage stage = new Stage();
    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();

    private final ComboBox<String> fontCombo = new ComboBox<>();
    private final Slider fontSizeSlider = new Slider(12, 28, 18);
    private final Slider lineHeightSlider = new Slider(1.2, 2.0, 1.5);
    private final Slider marginSlider = new Slider(0, 80, 24);
    private final ToggleGroup alignGroup = new ToggleGroup();
    private final ToggleButton alignLeftBtn = new ToggleButton("Izq.");
    private final ToggleButton alignJustBtn = new ToggleButton("Justif.");
    private final CheckBox darkTheme = new CheckBox("Tema oscuro");

    private final Button prevBtn = new Button("◀ Anterior");
    private final Button nextBtn = new Button("Siguiente ▶");

    private Path tempRoot;
    private Path opfDir;
    private final List<String> spineHrefs = new ArrayList<>();
    private int currentIndex = 0;

    private final ListView<ChapterEntry> chapterList = new ListView<>();
    private final TextField chapterSearch = new TextField();
    private final Label progressLabel = new Label();
    private ObservableList<ChapterEntry> chapterEntries = FXCollections.observableArrayList();
    private FilteredList<ChapterEntry> filteredEntries;

    private ReaderWindow() {}

    public static void openEpub(Path epubFile) throws Exception {
        ReaderWindow w = new ReaderWindow();
        w.init(epubFile);
        w.stage.show();
    }

    private void init(Path epubFile) throws Exception {
        stage.setTitle("Lector EPUB — " + epubFile.getFileName());

        // UI top
        fontCombo.getItems().addAll("Serif", "Sans", "Monospace");
        fontCombo.setValue("Serif");

        alignLeftBtn.setToggleGroup(alignGroup);
        alignJustBtn.setToggleGroup(alignGroup);
        alignJustBtn.setSelected(true);

        HBox toolbar = new HBox(10,
                labeledBox("Fuente", fontCombo),
                labeledBox("Tamaño", fontSizeSlider),
                labeledBox("Interlineado", lineHeightSlider),
                labeledBox("Márgenes", marginSlider),
                labeledBox("Alineación", new HBox(6, alignLeftBtn, alignJustBtn)),
                darkTheme
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14));
        toolbar.getStyleClass().add("control-bar");

        // Navegación
        prevBtn.setOnAction(e -> goTo(currentIndex - 1));
        nextBtn.setOnAction(e -> goTo(currentIndex + 1));
        HBox nav = new HBox(10, prevBtn, nextBtn, progressLabel);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(12, 14, 12, 14));
        nav.getStyleClass().add("control-bar");

        // Panel de capítulos (spine simple)
        chapterList.setPrefWidth(260);
        chapterList.getStyleClass().add("panel");
        chapterList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ChapterEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        chapterList.getSelectionModel().selectedItemProperty().addListener((obs, oldv, entry) -> {
            if (entry != null) goTo(entry.spineIndex());
        });

        chapterSearch.setPromptText("Buscar capítulo…");
        chapterSearch.textProperty().addListener((obs, ov, nv) -> {
            if (filteredEntries != null) {
                String query = nv == null ? "" : nv.trim().toLowerCase(Locale.ROOT);
                filteredEntries.setPredicate(entry -> query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query));
                chapterList.getSelectionModel().clearSelection();
            }
        });

        // Unzip + parse OPF
        unzipToTemp(epubFile);
        parseOpf();

        buildToc(prettyNames(spineHrefs));

        // Contenido
        BorderPane content = new BorderPane();
        content.setTop(toolbar);
        content.setCenter(webView);
        content.setBottom(nav);
        content.getStyleClass().add("panel");

        VBox tocPane = new VBox(10,
                new Label("Contenido"),
                chapterSearch,
                chapterList
        );
        tocPane.setPadding(new Insets(12));
        tocPane.getStyleClass().add("panel");

        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().addAll(tocPane, content);
        split.setDividerPositions(0.22);

        Scene scene = new Scene(split, 1100, 800);
        scene.getStylesheets().add(readerCss());
        scene.setOnKeyPressed(evt -> switch (evt.getCode()) {
            case RIGHT, PAGE_DOWN -> goTo(currentIndex + 1);
            case LEFT, PAGE_UP -> goTo(currentIndex - 1);
            default -> { }
        });
        stage.setScene(scene);

        // Reaplicar CSS tras cada carga
        ChangeListener<Worker.State> stateListener = (obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) injectUserCss();
        };
        engine.getLoadWorker().stateProperty().addListener(stateListener);

        // Listeners de ajustes
        fontCombo.valueProperty().addListener((o,ov,nv)-> injectUserCss());
        fontSizeSlider.valueProperty().addListener((o,ov,nv)-> injectUserCss());
        lineHeightSlider.valueProperty().addListener((o,ov,nv)-> injectUserCss());
        marginSlider.valueProperty().addListener((o,ov,nv)-> injectUserCss());
        alignGroup.selectedToggleProperty().addListener((o,ov,nv)-> injectUserCss());
        darkTheme.selectedProperty().addListener((o,ov,nv)-> injectUserCss());

        // Cargar primer capítulo
        if (!spineHrefs.isEmpty()) goTo(0);
    }

    private void goTo(int idx) {
        if (idx<0 || idx>=spineHrefs.size()) return;
        currentIndex = idx;
        String href = spineHrefs.get(idx);
        try {
            Path target = opfDir.resolve(href).normalize();
            engine.load(target.toUri().toString());
        } catch (Exception ex) {
            // fallback a leer como texto
            try {
                String html = Files.readString(opfDir.resolve(href), StandardCharsets.UTF_8);
                engine.loadContent(html);
            } catch (IOException ignored) {}
        }
        updateNavButtons();
        progressLabel.setText("Capítulo " + (currentIndex+1) + " de " + spineHrefs.size());
        selectCurrentInList();
    }

    private void updateNavButtons() {
        prevBtn.setDisable(currentIndex<=0);
        nextBtn.setDisable(currentIndex>=spineHrefs.size()-1);
    }

    private void selectCurrentInList() {
        if (filteredEntries == null) return;
        for (ChapterEntry entry : filteredEntries) {
            if (entry.spineIndex() == currentIndex) {
                chapterList.getSelectionModel().select(entry);
                chapterList.scrollTo(entry);
                break;
            }
        }
    }

    private void injectUserCss() {
        String family = switch (fontCombo.getValue()) {
            case "Sans" -> "system-ui, Segoe UI, Roboto, Arial, sans-serif";
            case "Monospace" -> "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";
            default -> "Georgia, 'Times New Roman', serif";
        };
        double fs = fontSizeSlider.getValue();
        double lh = lineHeightSlider.getValue();
        double mg = marginSlider.getValue();
        boolean justify = alignGroup.getSelectedToggle() == alignJustBtn;
        boolean dark = darkTheme.isSelected();

        String fg = dark ? "#eaeaea" : "#111";
        String bg = dark ? "#0e0f12" : "#ffffff";
        String link = dark ? "#8bb4ff" : "#1a66ff";

        String css = """
            body, html { color: %s !important; background:%s !important;
                        font-family:%s !important; font-size: %spx !important; line-height:%s !important; }
            body { margin-left:%spx !important; margin-right:%spx !important; }
            p, li, div, td, th { text-align:%s !important; }
            a { color:%s !important; }
            img, svg, video, canvas { max-width: 100%% !important; height: auto !important; }
            """.formatted(fg, bg, family, (int)fs, String.format(java.util.Locale.US, "%.2f", lh),
                          (int)mg, (int)mg, (justify?"justify":"left"), link);

        // Inserta/actualiza <style id="usercss"> en el documento actual
        String js = """
            (function(){
                const head = document.head || document.getElementsByTagName('head')[0];
                let style = document.getElementById('usercss');
                if (!style) {
                  style = document.createElement('style');
                  style.id = 'usercss';
                  head.appendChild(style);
                }
                style.textContent = %s;
            })();
            """.formatted(toJsString(css));
        try { engine.executeScript(js); } catch (Exception ignored) {}
    }

    private static String toJsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n","\\n") + "\"";
    }

    private void unzipToTemp(Path epub) throws IOException {
        tempRoot = Files.createTempDirectory("epubreader_");
        tempRoot.toFile().deleteOnExit();
        try (ZipFile zip = new ZipFile(epub.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                Path out = tempRoot.resolve(ze.getName());
                if (ze.isDirectory()) { Files.createDirectories(out); }
                else {
                    Files.createDirectories(out.getParent());
                    try (InputStream is = zip.getInputStream(ze)) { Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        }
    }

    private void parseOpf() throws Exception {
        // Busca container.xml -> OPF
        Path container = tempRoot.resolve("META-INF/container.xml");
        String opfPath = null;
        if (Files.exists(container)) {
            try (InputStream is = Files.newInputStream(container)) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                NodeList n = doc.getElementsByTagName("rootfile");
                for (int i=0;i<n.getLength();i++){
                    var node=n.item(i);
                    var attr=node.getAttributes()!=null? node.getAttributes().getNamedItem("full-path"):null;
                    if (attr!=null){ opfPath = attr.getNodeValue(); break; }
                }
            }
        }
        if (opfPath==null) {
            // fallback: primera .opf encontrada
            try (Stream<Path> s = Files.walk(tempRoot)) {
                Optional<Path> any = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".opf")).findFirst();
                if (any.isPresent()) opfPath = tempRoot.relativize(any.get()).toString().replace('\\','/');
            }
        }
        if (opfPath==null) throw new IOException("No se encontró el archivo OPF.");

        Path opf = tempRoot.resolve(opfPath);
        opfDir = opf.getParent();

        // Parse OPF: manifest + spine
        Map<String,String> manifest = new HashMap<>();
        try (InputStream is = Files.newInputStream(opf)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            for (int i=0;i<items.getLength();i++){
                var it=items.item(i); var attrs=it.getAttributes(); if (attrs==null) continue;
                var idAttr=attrs.getNamedItem("id"); var hrefAttr=attrs.getNamedItem("href");
                if (idAttr!=null && hrefAttr!=null) manifest.put(idAttr.getNodeValue(), hrefAttr.getNodeValue());
            }

            NodeList itemrefs = doc.getElementsByTagName("itemref");
            spineHrefs.clear();
            for (int i=0;i<itemrefs.getLength();i++){
                var ref=itemrefs.item(i); var attrs=ref.getAttributes(); if (attrs==null) continue;
                var idref=attrs.getNamedItem("idref"); if (idref==null) continue;
                String href = manifest.get(idref.getNodeValue());
                if (href!=null && isHtmlLike(href)) spineHrefs.add(normalize(href));
            }
        }
        if (spineHrefs.isEmpty()) throw new IOException("Spine vacío o no soportado.");
    }

    private void buildToc(List<String> names) {
        chapterEntries = FXCollections.observableArrayList();
        for (int i = 0; i < names.size(); i++) {
            chapterEntries.add(new ChapterEntry(names.get(i), i));
        }
        filteredEntries = new FilteredList<>(chapterEntries, c -> true);
        chapterList.setItems(filteredEntries);
    }

    private static boolean isHtmlLike(String href) {
        String l = href.toLowerCase(Locale.ROOT);
        return l.endsWith(".xhtml") || l.endsWith(".html") || l.endsWith(".htm");
    }

    private String normalize(String href) {
        // Limpia ./ y ../
        Deque<String> st = new ArrayDeque<>();
        for (String p : href.split("/")) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) { if (!st.isEmpty()) st.removeLast(); } else st.addLast(p);
        }
        return String.join("/", st);
    }

    private List<String> prettyNames(List<String> hrefs) {
        List<String> out = new ArrayList<>();
        for (String h : hrefs) {
            String n = h;
            int slash = n.lastIndexOf('/'); if (slash>=0) n = n.substring(slash+1);
            n = URLDecoder.decode(n, StandardCharsets.UTF_8);
            out.add(n);
        }
        return out;
    }

    private HBox labeledBox(String label, Node content) {
        Label l = new Label(label);
        VBox box = new VBox(4, l, content);
        box.getStyleClass().add("field-group");
        return new HBox(box);
    }

    private String readerCss() {
        return """
            data:text/css,
            :root { -fx-font-family: "Inter", "Segoe UI", system-ui; }
            .split-pane { -fx-background-color: linear-gradient(to bottom,#f7f8fb,#eef1f6); }
            .panel { -fx-background-color: white; -fx-background-radius: 18; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(16,24,40,0.08), 14, 0.12, 0, 4); }
            .control-bar { -fx-background-color: transparent; }
            .button { -fx-background-radius: 12; -fx-padding: 9 14; -fx-font-weight: 600; }
            .toggle-button { -fx-background-radius: 10; -fx-padding: 8 10; }
            .text-field, .combo-box, .slider { -fx-background-radius: 12; }
            .list-view { -fx-background-radius: 12; -fx-padding: 6; }
            .field-group > .label { -fx-text-fill: #3b4452; -fx-opacity: 0.9; -fx-font-size: 11px; -fx-font-weight: 600; }
            .label { -fx-text-fill: #1f2937; }
        """;
    }

    private record ChapterEntry(String name, int spineIndex) { }
}
