package es.alepico.epuber;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Epuber extends Application {

    private TextField sourceField;
    private TextField targetField;
    private CheckBox overwriteCheck;
    private CheckBox onlyNewCheck;
    private CheckBox extEpubCheck, extMobiCheck, extPdfCheck;

    private DatePicker fromDatePicker, toDatePicker;

    private Button startStopButton, listButton;
    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea logArea;

    // Portadas + paginación
    private CheckBox showCoversCheck;
    private FlowPane coversPane;
    private Button prevPageBtn, nextPageBtn;
    private Label pageInfoLabel;
    private ComboBox<Integer> pageSizeCombo;

    private Task<?> currentTask;
    private Task<?> currentCoversTask;

    private List<Path> lastFileList = List.of();
    private int currentPage = 1;
    private int pageSize = 36;

    // ===== Conversor PDF -> EPUB fijo =====
    private TextField pdfInField, epubOutField, coverImgField;
    private Button browsePdfBtn, browseEpubBtn, browseCoverBtn, convertBtn, openEpubBtn;
    private CheckBox splitSpreadsCheck;
    private Spinner<Integer> dpiSpinner;
    private TextField metaTitleField, metaAuthorsField, metaSeriesField, metaSeriesIndexField;
    private TextField metaPublisherField, metaLangsField, metaTagsField, metaIdsField;
    private DatePicker metaDatePicker, metaIssuedPicker;
    private Slider metaRatingSlider;
    private TextArea metaSynopsisArea;
    private ProgressBar convProgress;
    private Label convLabel;

    @Override
    public void start(Stage stage) {
        stage.setTitle("EPUBER - gestor de EPUB - MOBI - PDF)");

        // Origen/destino
        sourceField = new TextField();
        sourceField.setPromptText("Carpeta de origen (subcarpetas incluidas)");
        sourceField.setEditable(false);
        Button browseSource = new Button("Elegir origen…");
        browseSource.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Selecciona carpeta de origen");
            var dir = dc.showDialog(stage);
            if (dir != null) sourceField.setText(dir.getAbsolutePath());
        });

        targetField = new TextField();
        targetField.setPromptText("Carpeta de destino (plana)");
        targetField.setEditable(false);
        Button browseTarget = new Button("Elegir destino…");
        browseTarget.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Selecciona carpeta de destino");
            var dir = dc.showDialog(stage);
            if (dir != null) targetField.setText(dir.getAbsolutePath());
        });

        overwriteCheck = new CheckBox("Sobrescribir si ya existe");
        onlyNewCheck = new CheckBox("Añadir nuevos (ignorar existentes)");
        onlyNewCheck.selectedProperty().addListener((obs, o, v) -> {
            overwriteCheck.setDisable(v);
            if (v) overwriteCheck.setSelected(false);
        });

        // Extensiones
        extEpubCheck = new CheckBox(".epub"); extEpubCheck.setSelected(true);
        extMobiCheck = new CheckBox(".mobi"); extMobiCheck.setSelected(true);
        extPdfCheck  = new CheckBox(".pdf");  extPdfCheck.setSelected(true);
        HBox extBox = new HBox(12, new Label("Extensiones:"), extEpubCheck, extMobiCheck, extPdfCheck);
        extBox.setAlignment(Pos.CENTER_LEFT);

        // Fechas
        fromDatePicker = new DatePicker();
        toDatePicker   = new DatePicker();
        HBox datesBox = new HBox(10, new Label("Desde:"), fromDatePicker, new Label("Hasta:"), toDatePicker);
        datesBox.setAlignment(Pos.CENTER_LEFT);

        // Botones
        startStopButton = new Button("Iniciar copia");
        startStopButton.setDefaultButton(true);
        startStopButton.setOnAction(e -> onStartStop());

        listButton = new Button("Listar y guardar…");
        listButton.setOnAction(e -> onListAndSave(stage));

        Button copyLogBtn = new Button("Copiar registro");
        copyLogBtn.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(logArea.getText());
            Clipboard.getSystemClipboard().setContent(c);
        });

        progressBar = new ProgressBar(0); progressBar.setPrefWidth(340);
        progressLabel = new Label("Listo.");

        logArea = new TextArea(); logArea.setEditable(false); logArea.setPrefRowCount(12);

        // Form
        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(10);
        int r=0;
        form.add(new Label("Origen:"), 0, r); form.add(sourceField, 1, r); form.add(browseSource, 2, r++);
        form.add(new Label("Destino:"), 0, r); form.add(targetField, 1, r); form.add(browseTarget, 2, r++);

        form.add(extBox, 1, r++, 2, 1);
        form.add(overwriteCheck, 1, r++);
        form.add(onlyNewCheck, 1, r++);
        form.add(new Label("Filtro por fechas (modificación):"), 1, r++);
        form.add(datesBox, 1, r++);

        Label note = new Label("Destino plano: solo archivos seleccionados, sin subcarpetas.");
        note.setStyle("-fx-opacity:0.8;-fx-font-size:12px;");

        HBox actions = new HBox(10, startStopButton, listButton, copyLogBtn);
        HBox progressBox = new HBox(10, progressBar, progressLabel);

        // Conversor PDF->EPUB (TitledPane con scroll interno)
        TitledPane pdfConvPane = buildPdfConverterPane(stage);

        // Columna izquierda
        VBox leftPane = new VBox(16,
                header(), form, note,
                pdfConvPane,                // ⬅️ aquí insertamos el conversor
                actions,
                new Label("Registro:"),
                new VBox(logArea),
                progressBox,
                footer()
        );
        leftPane.setPadding(new Insets(18));
        leftPane.setFillWidth(true);

        // Scroll para la columna izquierda
        ScrollPane leftScroll = new ScrollPane(leftPane);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setStyle("-fx-background-color:transparent; -fx-background-insets: 0;");

        // Carátulas + paginación (columna derecha)
        showCoversCheck = new CheckBox("Mostrar carátulas (EPUB/PDF)");
        showCoversCheck.selectedProperty().addListener((obs,o,v) -> { if(v) renderCoversPage(); else clearCovers(); });

        pageSizeCombo = new ComboBox<>();
        pageSizeCombo.getItems().addAll(24, 36, 60); pageSizeCombo.setValue(pageSize);
        pageSizeCombo.valueProperty().addListener((obs,o,v)->{ pageSize = (v==null?36:v); currentPage=1; renderCoversPage(); });

        prevPageBtn = new Button("◀ Anterior");
        nextPageBtn = new Button("Siguiente ▶");
        pageInfoLabel = new Label("Página 0/0");
        prevPageBtn.setOnAction(e-> { if(currentPage>1){ currentPage--; renderCoversPage(); }});
        nextPageBtn.setOnAction(e-> { if(currentPage<getTotalPages()){ currentPage++; renderCoversPage(); }});

        HBox pagerBar = new HBox(10, showCoversCheck, new Label("Por página:"), pageSizeCombo, prevPageBtn, pageInfoLabel, nextPageBtn);
        pagerBar.setAlignment(Pos.CENTER_LEFT);

        coversPane = new FlowPane(); coversPane.setHgap(14); coversPane.setVgap(14); coversPane.setPrefWrapLength(300);
        ScrollPane coversScroll = new ScrollPane(coversPane); coversScroll.setFitToWidth(true);

        VBox rightPane = new VBox(10, pagerBar, coversScroll);
        rightPane.setPadding(new Insets(10)); rightPane.setMinWidth(360); rightPane.setPrefWidth(390);

        SplitPane split = new SplitPane(leftScroll, rightPane);
        split.setDividerPositions(0.66);

        Scene scene = new Scene(split, 1180, 700);
        scene.getStylesheets().add(inlineCss());
        stage.setScene(scene);
        stage.show();
    }

    private Node header() {
        Label t = new Label("Copiador y lector");
        t.getStyleClass().add("app-title");
        Label s = new Label("EPUB/MOBI/PDF · filtros fecha/extensión · destino plano · miniaturas paginadas · lector EPUB integrado");
        s.getStyleClass().add("app-subtitle");
        return new VBox(4, t, s);
    }
    private Node footer() {
        Label small = new Label("JavaFX • PDFBox • UI minimalista");
        small.getStyleClass().add("footer");
        return small;
    }
    private String inlineCss() {
        return """
            data:text/css,
            :root { -fx-font-family: "Inter", "Segoe UI", system-ui; }
            .root { -fx-background-color: linear-gradient(to bottom, #f7f8fb, #eef1f6); }
            .app-title { -fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill:#0f172a; }
            .app-subtitle { -fx-opacity: 0.85; -fx-text-fill:#334155; }
            .text-field { -fx-background-radius: 12; -fx-padding: 8 12; -fx-background-color:white; }
            .text-field:focused { -fx-effect: dropshadow(gaussian, rgba(76,139,245,0.4),12,0,0,4); }
            .scroll-pane { -fx-background-color: transparent; }
            .button { -fx-background-radius: 12; -fx-padding: 8 14; -fx-font-weight:600; -fx-background-color: linear-gradient(to right,#4C8BF5,#5fa4ff); -fx-text-fill:white; }
            .button:disabled { -fx-opacity:0.7; }
            .titled-pane > .title { -fx-background-radius: 12; }
            .text-area { -fx-background-radius: 12; }
            .footer { -fx-opacity: .6; -fx-font-size: 11px; }
            .progress-bar { -fx-accent: #4C8BF5; -fx-background-radius: 10; }
            .btn-danger { -fx-background-color: #d84545; -fx-text-fill: white; }
            .thumb { -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18),10,0,0,2); -fx-background-radius: 12; -fx-padding:6; -fx-background-color:white; }
        """;
    }

    // ================= Acciones principales =================
    private void onStartStop() {
        if (currentTask != null && currentTask.isRunning()) { currentTask.cancel(true); log("Cancelación solicitada…"); return; }
        Params params = readParams(); if (params == null) return;

        setRunningUi(true); clearCovers();
        logArea.clear();
        log("Inicio copia: " + LocalDateTime.now());
        log("Origen: " + params.source);
        log("Destino: " + params.target);
        log("Extensiones: " + String.join(", ", params.extensions));
        if (params.fromDate!=null || params.toDate!=null) log("Fechas: " + (params.fromDate==null?"∞":params.fromDate) + " → " + (params.toDate==null?"∞":params.toDate));
        log("Añadir nuevos: " + (params.onlyNew ? "Sí" : "No") + " | Sobrescribir: " + (params.overwrite ? "Sí" : "No"));

        currentTask = new Task<Result>() {
            @Override protected Result call() {
                List<Path> files = collectFilteredFiles(params);
                Platform.runLater(() -> setCoverSource(files));
                int found = files.size(); updateProgress(0, Math.max(found,1)); updateMessage("Encontrados: " + found);

                AtomicInteger copied = new AtomicInteger(0), skipped = new AtomicInteger(0); int processed=0;
                try { Files.createDirectories(params.target); } catch (IOException e) { return new Result(found,0,0,true,e); }

                for (Path p : files) {
                    if (isCancelled()) return new Result(found,copied.get(),skipped.get(),true,null);
                    try {
                        Path dest = params.target.resolve(p.getFileName());
                        boolean exists = Files.exists(dest);
                        if (params.onlyNew) {
                            if (exists) { skipped.incrementAndGet(); logLater("Omitido (ya existe): " + dest.getFileName()); }
                            else { Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES); copied.incrementAndGet(); logLater("Copiado (nuevo): " + p.getFileName()); }
                        } else {
                            if (!params.overwrite && exists) { skipped.incrementAndGet(); logLater("Omitido (ya existe): " + dest.getFileName()); }
                            else {
                                Files.copy(p, dest, params.overwrite ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES}
                                                                     : new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES});
                                copied.incrementAndGet(); logLater((params.overwrite?"Reemplazado: ":"Copiado: ") + p.getFileName());
                            }
                        }
                    } catch (FileAlreadyExistsException faee) {
                        skipped.incrementAndGet(); logLater("Omitido (ya existe): " + p.getFileName());
                    } catch (IOException io) {
                        logLater("Error copiando " + p.getFileName() + " : " + io.getMessage());
                    } finally {
                        processed++; updateProgress(processed, Math.max(found,1)); updateMessage("Procesados: " + processed + " / " + found);
                    }
                }
                return new Result(found, copied.get(), skipped.get(), false, null);
            }
        };

        bindTask(currentTask);
        new Thread(currentTask, "copy-task").start();
    }

    private void onListAndSave(Stage stage) {
        if (currentTask != null && currentTask.isRunning()) { showAlert(Alert.AlertType.INFORMATION, "Hay un proceso en curso. Deténlo antes de listar."); return; }
        Params params = readParams(); if (params == null) return;

        setRunningUi(true); clearCovers();
        logArea.clear(); log("Inicio listado: " + LocalDateTime.now());

        currentTask = new Task<Result>() {
            List<Path> files;
            @Override protected Result call() {
                files = collectFilteredFiles(params);
                Platform.runLater(() -> setCoverSource(files));
                int found = files.size();
                updateProgress(found, Math.max(found,1));
                updateMessage("Encontrados: " + found);
                files.forEach(f -> logLater(f.getFileName().toString()));
                return new Result(found, 0, 0, false, null);
            }
            @Override protected void succeeded() {
                super.succeeded(); unbindTask(); setRunningUi(false);
                FileChooser fc = new FileChooser();
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto (*.txt)", "*.txt"));
                fc.setInitialFileName("listado-libros.txt");
                File out = fc.showSaveDialog(stage);
                if (out != null) {
                    try (BufferedWriter w = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
                        for (Path p : files) {
                            TitleAuthor ta = getTitleAuthor(p);
                            if (ta.author == null || ta.author.isBlank()) w.write(ta.title);
                            else w.write(ta.title + " - " + ta.author);
                            w.newLine();
                        }
                    } catch (IOException ex) {
                        showAlert(Alert.AlertType.ERROR, "No se pudo guardar el listado:\n" + ex.getMessage());
                        return;
                    }
                    showAlert(Alert.AlertType.INFORMATION, "Listado guardado correctamente.");
                } else {
                    showAlert(Alert.AlertType.INFORMATION, "Listado generado. (Guardado cancelado)");
                }
            }
            @Override protected void failed() { super.failed(); unbindTask(); setRunningUi(false);
                Throwable ex = getException();
                showAlert(Alert.AlertType.ERROR, "Error al listar:\n" + (ex==null?"desconocido":ex.getMessage()));
            }
            @Override protected void cancelled() { super.cancelled(); unbindTask(); setRunningUi(false);
                showAlert(Alert.AlertType.WARNING, "Listado cancelado."); }
        };

        bindTask(currentTask);
        new Thread(currentTask, "list-task").start();
    }

    // ================= Paginación de carátulas =================
    private void setCoverSource(List<Path> files) { lastFileList = files; currentPage = 1; renderCoversPage(); }
    private int getTotalPages() { if (lastFileList==null || lastFileList.isEmpty()) return 1; return Math.max(1, (int)Math.ceil(lastFileList.size()/(double)pageSize)); }

    private void renderCoversPage() {
        clearCovers();
        if (!showCoversCheck.isSelected()) return;

        int total = getTotalPages();
        currentPage = Math.min(Math.max(1, currentPage), total);
        int from = (currentPage-1)*pageSize, to = Math.min(from+pageSize, lastFileList.size());
        pageInfoLabel.setText("Página " + currentPage + " / " + total);
        prevPageBtn.setDisable(currentPage<=1); nextPageBtn.setDisable(currentPage>=total);

        if (currentCoversTask != null && currentCoversTask.isRunning()) currentCoversTask.cancel();
        List<Path> pageItems = lastFileList.subList(from, to);

        currentCoversTask = new Task<Void>() {
            @Override protected Void call() {
                for (Path p : pageItems) {
                    if (isCancelled()) break;
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".epub")) {
                        Image img = loadEpubCover(p);
                        if (img != null) addThumb(img, displayName(p), p);
                        else addThumbPlaceholder("EPUB\n(sin portada)", displayName(p), p);
                    } else if (name.endsWith(".pdf")) {
                        Image img = renderPdfFirstPage(p);
                        if (img != null) addThumb(img, displayName(p), p);
                        else addThumbPlaceholder("PDF", displayName(p), p);
                    } else if (name.endsWith(".mobi")) {
                        addThumbPlaceholder("MOBI", displayName(p), p);
                    }
                }
                return null;
            }
        };
        new Thread(currentCoversTask, "covers-page-task").start();
    }

    private void clearCovers() { if (currentCoversTask!=null && currentCoversTask.isRunning()) currentCoversTask.cancel(); coversPane.getChildren().clear(); }

    private String displayName(Path p) {
        TitleAuthor ta = getTitleAuthorQuick(p);
        if (ta.author==null || ta.author.isBlank()) return ta.title;
        return ta.title + " — " + ta.author;
    }

    private void addThumb(Image img, String title, Path file) {
        Platform.runLater(() -> {
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(true);
            iv.setFitHeight(320); // grande
            VBox box = new VBox(6, iv, new Label(title));
            box.setAlignment(Pos.TOP_CENTER);
            box.getStyleClass().add("thumb");
            box.setPadding(new Insets(6));
            // Doble clic para abrir
            box.setOnMouseClicked(evt -> {
                if (evt.getButton()== MouseButton.PRIMARY && evt.getClickCount()==2) openFile(file);
            });
            coversPane.getChildren().add(box);
        });
    }
    private void addThumbPlaceholder(String text, String title, Path file) {
        Platform.runLater(() -> {
            Label l = new Label(text);
            l.setMinSize(200, 280);
            l.setAlignment(Pos.CENTER);
            l.setStyle("-fx-border-color:#bbb;-fx-border-radius:10;-fx-background-radius:10;-fx-background-color:rgba(0,0,0,0.03);-fx-padding:12;");
            VBox box = new VBox(6, l, new Label(title));
            box.setAlignment(Pos.TOP_CENTER);
            box.getStyleClass().add("thumb");
            box.setPadding(new Insets(6));
            box.setOnMouseClicked(evt -> {
                if (evt.getButton()== MouseButton.PRIMARY && evt.getClickCount()==2) openFile(file);
            });
            coversPane.getChildren().add(box);
        });
    }

    private void openFile(Path file) {
        String low = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (low.endsWith(".epub")) {
                ReaderWindow.openEpub(file); // Lector integrado
            } else {
                // PDF y MOBI: abrir con app del sistema
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file.toFile());
                else showAlert(Alert.AlertType.INFORMATION, "No se puede abrir con el sistema en esta plataforma.");
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "No se pudo abrir el archivo:\n" + ex.getMessage());
        }
    }

    // ================= Parámetros / filtros / listado =================
    private static class Params {
        Path source, target; Set<String> extensions; LocalDate fromDate, toDate; boolean overwrite, onlyNew;
    }
    private Params readParams() {
        String src = sourceField.getText(), dst = targetField.getText();
        if (src==null || src.isBlank()) { showAlert(Alert.AlertType.WARNING, "Selecciona carpeta de ORIGEN."); return null; }
        if (dst==null || dst.isBlank()) { showAlert(Alert.AlertType.WARNING, "Selecciona carpeta de DESTINO."); return null; }
        Path source = Paths.get(src), target = Paths.get(dst);
        if (!Files.isDirectory(source)) { showAlert(Alert.AlertType.ERROR, "La carpeta de ORIGEN no es válida."); return null; }
        if (!Files.isDirectory(target)) { showAlert(Alert.AlertType.ERROR, "La carpeta de DESTINO no es válida."); return null; }
        try { if (Files.isSameFile(source, target)) { showAlert(Alert.AlertType.ERROR, "ORIGEN y DESTINO no pueden ser la misma carpeta."); return null; } } catch (IOException ignored){}

        Set<String> exts = new HashSet<>();
        if (extEpubCheck.isSelected()) exts.add(".epub");
        if (extMobiCheck.isSelected()) exts.add(".mobi");
        if (extPdfCheck.isSelected())  exts.add(".pdf");
        if (exts.isEmpty()) { showAlert(Alert.AlertType.WARNING, "Selecciona al menos una extensión."); return null; }

        LocalDate from = fromDatePicker.getValue(), to = toDatePicker.getValue();
        if (from!=null && to!=null && from.isAfter(to)) { showAlert(Alert.AlertType.WARNING, "La fecha 'desde' no puede ser posterior a 'hasta'."); return null; }

        Params p = new Params();
        p.source=source; p.target=target; p.extensions=exts; p.fromDate=from; p.toDate=to;
        p.overwrite = overwriteCheck.isSelected(); p.onlyNew = onlyNewCheck.isSelected();
        return p;
    }

    private List<Path> collectFilteredFiles(Params params) {
        try (Stream<Path> s = Files.walk(params.source)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        for (String ext : params.extensions) if (name.endsWith(ext)) return true;
                        return false;
                    })
                    .filter(p -> passesDateFilter(p, params.fromDate, params.toDate))
                    .sorted(Comparator.comparing(Path::toString, String::compareToIgnoreCase))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            logLater("Error escaneando: " + ex.getMessage());
            return List.of();
        }
    }
    private boolean passesDateFilter(Path p, LocalDate from, LocalDate to) {
        if (from==null && to==null) return true;
        LocalDate d = getFileDate(p);
        if (from!=null && d.isBefore(from)) return false;
        if (to!=null && d.isAfter(to)) return false;
        return true;
    }
    private LocalDate getFileDate(Path p) {
        try {
            var t = Files.getLastModifiedTime(p).toInstant();
            return LocalDateTime.ofInstant(t, ZoneId.systemDefault()).toLocalDate();
        } catch (IOException e) { return LocalDate.MIN; }
    }

    // ================= Metadatos: Título/Autor =================
    private static class TitleAuthor { final String title, author; TitleAuthor(String t,String a){ this.title=(t==null||t.isBlank())?"(Sin título)":t.trim(); this.author=a==null?"":a.trim(); } }
    private TitleAuthor getTitleAuthorQuick(Path p) {
        String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".epub")) {
            TitleAuthor ta = readEpubTitleAuthor(p); if (ta!=null) return ta;
        } else if (lower.endsWith(".pdf")) {
            TitleAuthor ta = readPdfTitleAuthor(p); if (ta!=null) return ta;
        }
        // MOBI / fallback: "Titulo - Autor.ext"
        String base = p.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String[] parts = base.split("\\s+-\\s+", 2);
        if (parts.length==2) return new TitleAuthor(parts[0], parts[1]);
        return new TitleAuthor(base, "");
    }
    private TitleAuthor getTitleAuthor(Path p) {
        String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".epub")) { TitleAuthor ta = readEpubTitleAuthor(p); if (ta!=null) return ta; }
        if (lower.endsWith(".pdf"))  { TitleAuthor ta = readPdfTitleAuthor(p);  if (ta!=null) return ta; }
        return getTitleAuthorQuick(p);
    }
    private TitleAuthor readPdfTitleAuthor(Path pdf) {
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            PDDocumentInformation info = doc.getDocumentInformation();
            String title = info.getTitle(), author = info.getAuthor();
            if ((title!=null && !title.isBlank()) || (author!=null && !author.isBlank())) return new TitleAuthor(title, author);
        } catch (IOException ignored) {}
        return null;
    }

    // ================= Miniaturas: EPUB y PDF =================
    private Image loadEpubCover(Path epubPath) {
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            String opfPath = findOpfPath(zip); if (opfPath==null) return null;
            String coverHref = findCoverHref(zip, opfPath); if (coverHref==null) return null;
            String baseDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/')+1) : "";
            String coverPath = normalizeZipPath(baseDir + coverHref);
            ZipEntry imgEntry = zip.getEntry(coverPath);
            if (imgEntry==null) imgEntry = zip.getEntry(coverHref);
            if (imgEntry==null) return null;
            try (InputStream is = zip.getInputStream(imgEntry)) { return new Image(is, 0, 320, true, true); }
        } catch (Exception e) { return null; }
    }
    private Image renderPdfFirstPage(Path pdfPath) {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(0, 130f); // ~320px alto aprox
            return SwingFXUtils.toFXImage(img, null);
        } catch (IOException e) { return null; }
    }

    // ================= EPUB: OPF & metadatos =================
    private TitleAuthor readEpubTitleAuthor(Path epubPath) {
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            String opfPath = findOpfPath(zip); if (opfPath==null) return null;
            ZipEntry opfEntry = zip.getEntry(opfPath); if (opfEntry==null) return null;
            try (InputStream is = zip.getInputStream(opfEntry)) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                doc.getDocumentElement().normalize();
                String title = firstText(doc, "dc:title"); if (title==null || title.isBlank()) title = firstText(doc, "title");
                List<String> creators = nodeTexts(doc, "dc:creator"); if (creators.isEmpty()) creators = nodeTexts(doc, "creator");
                String author = creators.stream().filter(s->s!=null && !s.isBlank()).collect(Collectors.joining("; "));
                return new TitleAuthor(title, author);
            }
        } catch (Exception e) { return null; }
    }
    private String findOpfPath(ZipFile zip) throws Exception {
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container==null) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) { ZipEntry z=en.nextElement(); if (!z.isDirectory() && z.getName().toLowerCase(Locale.ROOT).endsWith(".opf")) return z.getName(); }
            return null;
        }
        try (InputStream is = zip.getInputStream(container)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            NodeList n = doc.getElementsByTagName("rootfile");
            for (int i=0;i<n.getLength();i++){
                var node = n.item(i);
                var attr = node.getAttributes()!=null ? node.getAttributes().getNamedItem("full-path") : null;
                if (attr!=null) return attr.getNodeValue();
            }
        }
        return null;
    }
    private String findCoverHref(ZipFile zip, String opfPath) throws Exception {
        ZipEntry opfEntry = zip.getEntry(opfPath); if (opfEntry==null) return null;
        try (InputStream is = zip.getInputStream(opfEntry)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();
            NodeList meta = doc.getElementsByTagName("meta"); String coverId = null;
            for (int i=0;i<meta.getLength();i++){
                var m=meta.item(i); var attrs=m.getAttributes(); if (attrs==null) continue;
                var nameAttr=attrs.getNamedItem("name"); var contentAttr=attrs.getNamedItem("content");
                if (nameAttr!=null && "cover".equalsIgnoreCase(nameAttr.getNodeValue()) && contentAttr!=null) { coverId = contentAttr.getNodeValue(); break; }
            }
            NodeList items = doc.getElementsByTagName("item");
            String hrefById=null, hrefByProp=null, hrefByGuess=null;
            for (int i=0;i<items.getLength();i++){
                var it=items.item(i); var attrs=it.getAttributes(); if (attrs==null) continue;
                var idAttr=attrs.getNamedItem("id"); var hrefAttr=attrs.getNamedItem("href");
                var propsAttr=attrs.getNamedItem("properties"); var mtAttr=attrs.getNamedItem("media-type");
                String id=idAttr!=null?idAttr.getNodeValue():null;
                String href=hrefAttr!=null?hrefAttr.getNodeValue():null;
                String props=propsAttr!=null?propsAttr.getNodeValue():"";
                String mt=mtAttr!=null?mtAttr.getNodeValue():"";
                if (coverId!=null && coverId.equals(id) && href!=null) hrefById=href;
                if (props!=null && props.toLowerCase(Locale.ROOT).contains("cover-image") && href!=null) hrefByProp=href;
                if (href!=null && mt!=null && mt.startsWith("image/") && href.toLowerCase(Locale.ROOT).contains("cover")) hrefByGuess=href;
            }
            if (hrefById!=null) return hrefById; if (hrefByProp!=null) return hrefByProp; if (hrefByGuess!=null) return hrefByGuess;
        }
        return null;
    }
    private String normalizeZipPath(String p) {
        Deque<String> stack = new ArrayDeque<>();
        for (String part : p.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) { if (!stack.isEmpty()) stack.removeLast(); } else stack.addLast(part);
        }
        return String.join("/", stack);
    }
    private String firstText(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        for (int i=0;i<nl.getLength();i++) { var n = nl.item(i); if (n!=null && n.getTextContent()!=null) { String s=n.getTextContent().trim(); if (!s.isBlank()) return s; } }
        return null;
    }
    private List<String> nodeTexts(Document doc, String tag) {
        List<String> list = new ArrayList<>(); NodeList nl = doc.getElementsByTagName(tag);
        for (int i=0;i<nl.getLength();i++){ var n=nl.item(i); if (n!=null && n.getTextContent()!=null){ String s=n.getTextContent().trim(); if (!s.isBlank()) list.add(s); } }
        return list;
    }

    // ================= Utilidades de UI/tareas =================
    private void bindTask(Task<?> t) {
        progressBar.progressProperty().bind(t.progressProperty());
        progressLabel.textProperty().bind(t.messageProperty());
        t.setOnSucceeded(e-> { unbindTask(); Result r=(Result)((Task<?>)e.getSource()).getValue(); showSummary(r); setRunningUi(false); });
        t.setOnCancelled(e-> { unbindTask(); Result r=null; try{ r=(Result)((Task<?>)e.getSource()).getValue(); }catch(Exception ignored){} if (r==null) r=new Result(0,0,0,true,null); showSummary(r); setRunningUi(false); });
        t.setOnFailed(e-> { unbindTask(); Throwable ex=((Task<?>)e.getSource()).getException(); log("Error: " + (ex==null?"desconocido":ex.getMessage())); showAlert(Alert.AlertType.ERROR, "Se produjo un error.\n" + (ex==null?"":ex.getMessage())); setRunningUi(false); });
    }
    private void unbindTask(){ progressBar.progressProperty().unbind(); progressLabel.textProperty().unbind(); }
    private void setRunningUi(boolean running) {
        if (running) { startStopButton.setText("Detener"); if (!startStopButton.getStyleClass().contains("btn-danger")) startStopButton.getStyleClass().add("btn-danger"); listButton.setDisable(true); }
        else { startStopButton.setText("Iniciar copia"); startStopButton.getStyleClass().remove("btn-danger"); listButton.setDisable(false); progressLabel.setText("Listo."); progressBar.setProgress(0); }
    }
    private void showSummary(Result r) {
        log("Fin: " + LocalDateTime.now());
        String base = (r.cancelled?"Proceso CANCELADO\n":"Proceso completado\n") + "Encontrados: " + r.found + "\nCopiados: " + r.copied + "\nOmitidos: " + r.skipped;
        if (r.error != null) { log("Error: " + r.error.getMessage()); showAlert(Alert.AlertType.ERROR, base + "\n\nError: " + r.error.getMessage()); }
        else showAlert(r.cancelled?Alert.AlertType.WARNING:Alert.AlertType.INFORMATION, base);
    }
    private void log(String t){ logArea.appendText(t + System.lineSeparator()); }
    private void logLater(String t){ Platform.runLater(() -> log(t)); }

    // Alert seguro (desde cualquier hilo)
    private void showAlert(Alert.AlertType type, String msg) {
        Runnable r = () -> { Alert a = new Alert(type, msg, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }

    public static void main(String[] args) { launch(); }

    // Resultado
    private static class Result { final int found,copied,skipped; final boolean cancelled; final Exception error;
        Result(int f,int c,int s,boolean k,Exception e){found=f;copied=c;skipped=s;cancelled=k;error=e;} }

    // ===== Conversor: UI =====
    private TitledPane buildPdfConverterPane(Stage stage) {
        // Entradas
        pdfInField = new TextField(); pdfInField.setPromptText("PDF de entrada"); pdfInField.setEditable(false);
        browsePdfBtn = new Button("Elegir PDF…");
        browsePdfBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
            File f = fc.showOpenDialog(stage);
            if (f != null) pdfInField.setText(f.getAbsolutePath());
        });

        epubOutField = new TextField(); epubOutField.setPromptText("EPUB de salida"); epubOutField.setEditable(false);
        browseEpubBtn = new Button("Guardar como…");
        browseEpubBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("EPUB (*.epub)", "*.epub"));
            fc.setInitialFileName("convertido.epub");
            File f = fc.showSaveDialog(stage);
            if (f != null) epubOutField.setText(f.getAbsolutePath());
        });

        coverImgField = new TextField(); coverImgField.setPromptText("Portada (opcional)"); coverImgField.setEditable(false);
        browseCoverBtn = new Button("Elegir portada…");
        browseCoverBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen (*.jpg, *.png)", "*.jpg","*.jpeg","*.png"));
            File f = fc.showOpenDialog(stage);
            if (f != null) coverImgField.setText(f.getAbsolutePath());
        });

        splitSpreadsCheck = new CheckBox("Partir dobles automáticamente");
        splitSpreadsCheck.setSelected(true);
        dpiSpinner = new Spinner<>(100, 450, 300, 25);
        dpiSpinner.setEditable(true);

        // Metadatos (estilo Calibre)
        metaTitleField = new TextField();    metaTitleField.setPromptText("Título");
        metaAuthorsField = new TextField();  metaAuthorsField.setPromptText("Autor(es) — separados por ;");
        metaSeriesField = new TextField();   metaSeriesField.setPromptText("Serie");
        metaSeriesIndexField = new TextField(); metaSeriesIndexField.setPromptText("N.º en la serie");
        metaPublisherField = new TextField(); metaPublisherField.setPromptText("Editorial");
        metaLangsField = new TextField();    metaLangsField.setPromptText("Idiomas — ej: es, en");
        metaTagsField = new TextField();     metaTagsField.setPromptText("Etiquetas — separadas por ,");
        metaIdsField = new TextField();      metaIdsField.setPromptText("IDs — ej: isbn:978..., google:XYZ");
        metaDatePicker = new DatePicker();   metaIssuedPicker = new DatePicker();
        metaRatingSlider = new Slider(0, 5, 0);
        metaRatingSlider.setMajorTickUnit(1); metaRatingSlider.setMinorTickCount(1);
        metaRatingSlider.setShowTickMarks(true); metaRatingSlider.setShowTickLabels(true);

        metaSynopsisArea = new TextArea();
        metaSynopsisArea.setPromptText("Sinopsis");
        metaSynopsisArea.setPrefRowCount(5);
        metaSynopsisArea.setMaxHeight(Region.USE_PREF_SIZE);

        // Botones
        convertBtn = new Button("Convertir PDF → EPUB (fijo)");
        openEpubBtn = new Button("Abrir EPUB");
        openEpubBtn.setDisable(true);

        convProgress = new ProgressBar(0); convLabel = new Label("Listo.");

        HBox io1 = new HBox(8, new Label("PDF:"), pdfInField, browsePdfBtn);
        HBox io2 = new HBox(8, new Label("EPUB:"), epubOutField, browseEpubBtn);
        HBox io3 = new HBox(8, new Label("Portada:"), coverImgField, browseCoverBtn);
        io1.setAlignment(Pos.CENTER_LEFT); io2.setAlignment(Pos.CENTER_LEFT); io3.setAlignment(Pos.CENTER_LEFT);

        GridPane meta = new GridPane(); meta.setHgap(10); meta.setVgap(8);
        int r=0;
        meta.add(new Label("Título:"), 0,r); meta.add(metaTitleField,1,r++,3,1);
        meta.add(new Label("Autor(es):"),0,r); meta.add(metaAuthorsField,1,r++,3,1);
        meta.add(new Label("Serie:"),0,r); meta.add(metaSeriesField,1,r); meta.add(new Label("N.º:"),3,r); meta.add(metaSeriesIndexField,4,r++);

        meta.add(new Label("Editorial:"),0,r); meta.add(metaPublisherField,1,r++ ,3,1);
        meta.add(new Label("Fecha:"),0,r); meta.add(metaDatePicker,1,r); meta.add(new Label("Publicado:"),3,r); meta.add(metaIssuedPicker,4,r++);

        meta.add(new Label("Idiomas:"),0,r); meta.add(metaLangsField,1,r++ ,3,1);
        meta.add(new Label("Etiquetas:"),0,r); meta.add(metaTagsField,1,r++ ,3,1);
        meta.add(new Label("IDs:"),0,r); meta.add(metaIdsField,1,r++ ,3,1);
        meta.add(new Label("Valoración:"),0,r); meta.add(metaRatingSlider,1,r++ ,3,1);

        VBox box = new VBox(10,
                new Label("Conversor PDF → EPUB (maquetación fija)"),
                io1, io2, io3,
                new HBox(10, splitSpreadsCheck, new Label("DPI:"), dpiSpinner),
                new Label("Metadatos"),
                meta,
                new Label("Sinopsis:"),
                metaSynopsisArea,
                new HBox(10, convertBtn, openEpubBtn),
                new HBox(10, convProgress, convLabel)
        );
        box.setPadding(new Insets(10));
        box.setFillWidth(true);

        convertBtn.setOnAction(e -> onConvertPdfToEpub());
        openEpubBtn.setOnAction(e -> {
            String path = epubOutField.getText();
            if (path != null && !path.isBlank()) {
                try { ReaderWindow.openEpub(Paths.get(path)); } catch (Exception ex) { showAlert(Alert.AlertType.ERROR, "No se pudo abrir el EPUB:\n"+ex.getMessage()); }
            }
        });

        // ⬇️ scroll interno del conversor
        ScrollPane convScroll = new ScrollPane(box);
        convScroll.setFitToWidth(true);
        convScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        convScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        convScroll.setPrefViewportHeight(560);

        TitledPane tp = new TitledPane("Conversor PDF → EPUB", convScroll);
        tp.setExpanded(false);
        return tp;
    }

    private void onConvertPdfToEpub() {
        if (currentTask != null && currentTask.isRunning()) { showAlert(Alert.AlertType.INFORMATION, "Hay otro proceso en curso."); return; }
        String in  = pdfInField.getText();
        String out = epubOutField.getText();
        if (in==null || in.isBlank()) { showAlert(Alert.AlertType.WARNING, "Selecciona un PDF de entrada."); return; }
        if (out==null || out.isBlank()) { showAlert(Alert.AlertType.WARNING, "Elige un archivo EPUB de salida."); return; }

        Path pdfPath = Paths.get(in);
        Path epubPath = Paths.get(out);
        boolean split = splitSpreadsCheck.isSelected();
        int dpi = dpiSpinner.getValue();

        PdfFixedEpubConverter.Metadata md = new PdfFixedEpubConverter.Metadata();
        md.title = valOr(metaTitleField.getText(), "(Sin título)");
        md.authors = parseList(metaAuthorsField.getText(), ";");
        md.series  = blankToNull(metaSeriesField.getText());
        md.seriesIndex = parseDouble(metaSeriesIndexField.getText());
        md.publisher = blankToNull(metaPublisherField.getText());
        md.date = metaDatePicker.getValue();
        md.issued = metaIssuedPicker.getValue();
        md.languages = parseList(metaLangsField.getText(), ",");
        if (md.languages.isEmpty()) md.languages = new ArrayList<>(List.of("es"));
        md.tags = parseList(metaTagsField.getText(), ",");
        md.ids  = parseKeyValue(metaIdsField.getText());
        md.rating = (double)Math.round(metaRatingSlider.getValue()*2)/2.0;
        md.synopsis = metaSynopsisArea.getText();
        if (coverImgField.getText()!=null && !coverImgField.getText().isBlank()) {
            Path c = Paths.get(coverImgField.getText());
            if (Files.exists(c)) md.coverImage = c;
        }

        convProgress.setProgress(0); convLabel.setText("Preparando…");
        convertBtn.setDisable(true); openEpubBtn.setDisable(true);

        currentTask = new javafx.concurrent.Task<>() {
            @Override protected Object call() throws Exception {
                return PdfFixedEpubConverter.convert(pdfPath, epubPath, md, split, dpi, new PdfFixedEpubConverter.ProgressListener() {
                    @Override public void onMessage(String msg) { updateMessage(msg); }
                    @Override public void onProgress(long done, long total) {
                        updateProgress(done, Math.max(total,1));
                    }
                });
            }
            @Override protected void succeeded() {
                super.succeeded();
                convLabel.textProperty().unbind(); convProgress.progressProperty().unbind();
                convLabel.setText("Conversión completada.");
                convProgress.setProgress(1.0);
                convertBtn.setDisable(false); openEpubBtn.setDisable(false);
                showAlert(Alert.AlertType.INFORMATION, "EPUB creado:\n" + epubPath);
            }
            @Override protected void failed() {
                super.failed();
                convLabel.textProperty().unbind(); convProgress.progressProperty().unbind();
                convertBtn.setDisable(false); openEpubBtn.setDisable(true);
                Throwable ex = getException();
                showAlert(Alert.AlertType.ERROR, "Error en la conversión:\n" + (ex==null? "desconocido" : ex.getMessage()));
            }
        };
        convLabel.textProperty().bind(currentTask.messageProperty());
        convProgress.progressProperty().bind(currentTask.progressProperty());
        new Thread(currentTask, "pdf2epub-task").start();
    }

    private static String valOr(String s, String d){ return (s==null || s.isBlank()) ? d : s.trim(); }
    private static String blankToNull(String s){ return (s==null || s.isBlank()) ? null : s.trim(); }
    private static Double parseDouble(String s){
        try { if (s==null || s.isBlank()) return null; return Double.parseDouble(s.trim().replace(',','.')); }
        catch (Exception e){ return null; }
    }
    private static List<String> parseList(String s, String sep){
        if (s==null || s.isBlank()) return new ArrayList<>();
        String[] parts = s.split("\\s*"+java.util.regex.Pattern.quote(sep)+"\\s*");
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!p.isBlank()) out.add(p.trim());
        return out;
    }
    private static Map<String,String> parseKeyValue(String s){
        Map<String,String> m = new LinkedHashMap<>();
        if (s==null || s.isBlank()) return m;
        for (String part : s.split("\\s*,\\s*")) {
            int i = part.indexOf(':'); if (i>0 && i<part.length()-1) m.put(part.substring(0,i).trim(), part.substring(i+1).trim());
        }
        return m;
    }
}
