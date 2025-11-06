package es.alepico.epuber;

import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
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
    private List<String> spineHrefs = new ArrayList<>();
    private int currentIndex = 0;

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
                new Label("Fuente:"), fontCombo,
                new Label("Tamaño:"), fontSizeSlider,
                new Label("Interlineado:"), lineHeightSlider,
                new Label("Márgenes:"), marginSlider,
                new Label("Alineación:"), new HBox(6, alignLeftBtn, alignJustBtn),
                darkTheme
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));
        toolbar.setStyle("-fx-background-color: -fx-base;");

        // Navegación
        prevBtn.setOnAction(e -> goTo(currentIndex - 1));
        nextBtn.setOnAction(e -> goTo(currentIndex + 1));
        HBox nav = new HBox(10, prevBtn, nextBtn);
        nav.setAlignment(Pos.CENTER);
        nav.setPadding(new Insets(8));

        // Panel de capítulos (spine simple)
        ListView<String> chapterList = new ListView<>();
        chapterList.setPrefWidth(260);
        chapterList.getSelectionModel().selectedIndexProperty().addListener((obs, oldv, idx) -> {
            if (idx != null && idx.intValue() >= 0 && idx.intValue() < spineHrefs.size()) {
                goTo(idx.intValue());
            }
        });

        // Unzip + parse OPF
        unzipToTemp(epubFile);
        parseOpf();

        chapterList.getItems().setAll(prettyNames(spineHrefs));

        // Contenido
        BorderPane content = new BorderPane();
        content.setTop(toolbar);
        content.setCenter(webView);
        content.setBottom(nav);

        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().addAll(chapterList, content);
        split.setDividerPositions(0.22);

        Scene scene = new Scene(split, 1100, 800);
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
    }

    private void updateNavButtons() {
        prevBtn.setDisable(currentIndex<=0);
        nextBtn.setDisable(currentIndex>=spineHrefs.size()-1);
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
}
