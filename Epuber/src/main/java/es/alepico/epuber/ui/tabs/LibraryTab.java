package es.alepico.epuber.ui.tabs;

import es.alepico.epuber.model.ConversionConfig;
import es.alepico.epuber.service.LibraryService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class LibraryTab extends Tab {
    private final Stage stage;
    private final TextField sourceField, targetField, keywordField;
    private final CheckBox extEpub, extPdf, extMobi, overwriteCheck, onlyNewCheck;
    private final DatePicker fromDate, toDate;
    private final TextArea logArea;
    private final ProgressBar progressBar;
    private final Button startBtn, saveListBtn, scanBtn, toggleLogBtn;
    private final VBox logContainer;
    private final List<Path> scannedFiles = new ArrayList<>();
    private final Label statusLabel;
    private final LibraryService service = new LibraryService();
    private Task<?> currentTask;
    private Consumer<List<Path>> scanFinishedListener;

    public LibraryTab(Stage stage) {
        super("Biblioteca");
        this.stage = stage;
        setClosable(false);

        // Controles
        sourceField = new TextField(); sourceField.setPromptText("Origen...");
        targetField = new TextField(); targetField.setPromptText("Destino...");
        Button btnSrc = new Button("..."); btnSrc.setOnAction(e-> chooseDirAndScan(stage, sourceField));
        Button btnDst = new Button("..."); btnDst.setOnAction(e-> chooseDir(stage, targetField));

        extEpub = new CheckBox(".epub"); extEpub.setSelected(true);
        extPdf = new CheckBox(".pdf"); extPdf.setSelected(true);
        extMobi = new CheckBox(".mobi");
        keywordField = new TextField(); keywordField.setPromptText("Buscar nombre...");

        fromDate = new DatePicker(); toDate = new DatePicker();
        overwriteCheck = new CheckBox("Sobrescribir");
        onlyNewCheck = new CheckBox("Solo nuevos");

        scanBtn = new Button("Escanear");
        scanBtn.setOnAction(e -> startScan());

        startBtn = new Button("Iniciar Copia");
        startBtn.setDisable(true);
        startBtn.setOnAction(e -> toggleCopyProcess());
        saveListBtn = new Button("Guardar listado");
        saveListBtn.setDisable(true);
        saveListBtn.setOnAction(e -> saveListToFile());

        statusLabel = new Label("Sin escanear");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(10);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-control-inner-background: black; -fx-text-fill: #39ff14; -fx-font-family: 'Consolas', 'Ubuntu Mono', monospace;");
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        toggleLogBtn = new Button("Mostrar registro");
        toggleLogBtn.setOnAction(e -> toggleLogVisibility());
        logContainer = new VBox(new Label("Registro"), logArea);
        logContainer.setVisible(false);
        logContainer.setManaged(false);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox.setVgrow(logContainer, Priority.ALWAYS);

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Origen:"), 0, 0); grid.add(sourceField, 1, 0); grid.add(btnSrc, 2, 0);
        grid.add(new Label("Destino:"), 0, 1); grid.add(targetField, 1, 1); grid.add(btnDst, 2, 1);
        grid.add(new HBox(10, new Label("Tipos:"), extEpub, extPdf, extMobi), 1, 2);
        grid.add(new HBox(10, new Label("Filtros:"), keywordField, fromDate, toDate), 1, 3);
        grid.add(new HBox(10, overwriteCheck, onlyNewCheck), 1, 4);

        HBox actions = new HBox(10, scanBtn, startBtn, saveListBtn, statusLabel);

        VBox content = new VBox(15,
            new Label("Gestión Masiva de Biblioteca"),
            grid,
            actions,
            progressBar,
            toggleLogBtn,
            logContainer
        );
        content.setPadding(new Insets(20));
        content.getStyleClass().add("panel");

        BorderPane root = new BorderPane(content);
        root.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setContent(root);

        setupAutoScan();
    }

    public void setOnScanFinished(Consumer<List<Path>> listener) {
        this.scanFinishedListener = listener;
    }

    private void chooseDir(Stage s, TextField f) {
        DirectoryChooser dc = new DirectoryChooser();
        File d = dc.showDialog(s);
        if(d != null) f.setText(d.getAbsolutePath());
    }

    private void chooseDirAndScan(Stage s, TextField f) {
        chooseDir(s, f);
        if(!f.getText().isBlank()) startScan();
    }

    private void setupAutoScan() {
        PauseTransition debounce = new PauseTransition(Duration.millis(500));
        sourceField.textProperty().addListener((obs, oldV, newV) -> {
            if(newV == null || newV.isBlank()) return;
            debounce.setOnFinished(e -> startScan());
            debounce.playFromStart();
        });
    }

    private void startScan() {
        if(currentTask != null && currentTask.isRunning()) {
            log("Ya hay un proceso en marcha.");
            return;
        }

        ConversionConfig cfg = buildScanConfig();
        if(cfg == null) return;

        scanBtn.setDisable(true);
        startBtn.setDisable(true);
        saveListBtn.setDisable(true);
        logArea.clear();

        Task<List<Path>> scanTask = new Task<>() {
            @Override protected List<Path> call() {
                updateMessage("Escaneando...");
                updateProgress(-1, 1);
                List<Path> files = service.scanFiles(cfg);
                updateMessage("Encontrados: " + files.size());
                return files;
            }
        };

        currentTask = scanTask;
        statusLabel.textProperty().bind(scanTask.messageProperty());
        progressBar.progressProperty().bind(scanTask.progressProperty());

        scanTask.setOnSucceeded(e -> finishScan(scanTask.getValue(), "Escaneo completado."));
        scanTask.setOnCancelled(e -> finishScan(List.of(), "Escaneo cancelado."));
        scanTask.setOnFailed(e -> {
            log("Error al escanear: " + scanTask.getException().getMessage());
            finishScan(List.of(), "Error al escanear.");
        });

        new Thread(scanTask).start();
    }

    private ConversionConfig buildScanConfig() {
        if(sourceField.getText().isBlank()) { log("¡Falta origen!"); return null; }

        ConversionConfig cfg = new ConversionConfig();
        cfg.source = Path.of(sourceField.getText());
        cfg.extensions = new HashSet<>();
        if(extEpub.isSelected()) cfg.extensions.add(".epub");
        if(extPdf.isSelected()) cfg.extensions.add(".pdf");
        if(extMobi.isSelected()) cfg.extensions.add(".mobi");
        if(cfg.extensions.isEmpty()) { log("Selecciona al menos una extensión de archivo."); return null; }
        cfg.keyword = keywordField.getText();
        cfg.fromDate = fromDate.getValue();
        cfg.toDate = toDate.getValue();
        return cfg;
    }

    private ConversionConfig buildCopyConfig() {
        if(sourceField.getText().isBlank()) { log("¡Falta origen!"); return null; }
        if(targetField.getText().isBlank()) { log("¡Falta destino!"); return null; }
        ConversionConfig cfg = buildScanConfig();
        if(cfg == null) return null;
        cfg.target = Path.of(targetField.getText());
        cfg.overwrite = overwriteCheck.isSelected();
        cfg.onlyNew = onlyNewCheck.isSelected();
        return cfg;
    }

    private void toggleCopyProcess() {
        if(currentTask != null && currentTask.isRunning()) {
            log("Cancelando tarea actual...");
            currentTask.cancel();
            return;
        }

        if(scannedFiles.isEmpty()) {
            log("Escanea primero para habilitar la copia.");
            return;
        }

        ConversionConfig cfg = buildCopyConfig();
        if(cfg == null) return;

        LibraryCopyTask task = new LibraryCopyTask(cfg, List.copyOf(scannedFiles));
        currentTask = task;

        startBtn.setText("Cancelar");
        startBtn.getStyleClass().add("button-danger");
        saveListBtn.setDisable(true);
        scanBtn.setDisable(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> finishCopy(task.getValue(), "Proceso terminado."));
        task.setOnCancelled(e -> finishCopy(task.getValue(), "Cancelado."));
        task.setOnFailed(e -> finishCopy(task.getValue(), "Error: " + task.getException().getMessage()));

        new Thread(task).start();
    }

    private void finishScan(List<Path> files, String msg) {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        currentTask = null;

        scannedFiles.clear();
        scannedFiles.addAll(files);

        if (scanFinishedListener != null) {
            scanFinishedListener.accept(List.copyOf(scannedFiles));
        }

        boolean hasResults = !scannedFiles.isEmpty();
        startBtn.setDisable(!hasResults);
        saveListBtn.setDisable(!hasResults);
        scanBtn.setDisable(false);

        log(msg);
        log("Documentos encontrados: " + files.size());
        statusLabel.setText(msg);
    }

    private void finishCopy(LibraryService.ScanResult res, String msg) {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        startBtn.setText("Iniciar Copia");
        startBtn.getStyleClass().remove("button-danger");
        currentTask = null;

        if(res == null) res = new LibraryService.ScanResult();
        saveListBtn.setDisable(scannedFiles.isEmpty());
        scanBtn.setDisable(false);

        log(msg);
        log(String.format("Encontrados: %d | Copiados: %d | Omitidos: %d", res.found, res.copied, res.skipped));
        if(res.error != null) log("Error: " + res.error.getMessage());
        if(res.cancelled) log("Proceso cancelado por el usuario.");

        statusLabel.setText(msg);
    }

    private void toggleLogVisibility() {
        boolean show = !logContainer.isVisible();
        logContainer.setVisible(show);
        logContainer.setManaged(show);
        toggleLogBtn.setText(show ? "Ocultar registro" : "Mostrar registro");
    }

    private void log(String t) { logArea.appendText(t + "\n"); }

    private void saveListToFile() {
        if(scannedFiles.isEmpty()) {
            log("No hay archivos para guardar.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar listado de libros");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt"));
        chooser.setInitialFileName("listado.txt");
        File dest = chooser.showSaveDialog(stage);
        if(dest == null) return;

        try {
            List<String> lines = scannedFiles.stream()
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(this::formatTitleAndAuthor)
                    .toList();
            Files.write(dest.toPath(), lines);
            log("Listado guardado en: " + dest.getAbsolutePath());
        } catch (IOException e) {
            log("No se pudo guardar el listado: " + e.getMessage());
        }
    }

    private String formatTitleAndAuthor(String fileName) {
        String baseName = fileName.replaceFirst("\\.[^.]+$", "");
        String[] parts = baseName.split("\\s+-\\s+", 2);
        String title = parts[0].trim();
        String author = parts.length > 1 ? parts[1].trim() : "Autor desconocido";
        return title + " - " + author;
    }

    private class LibraryCopyTask extends Task<LibraryService.ScanResult> {
        private final ConversionConfig cfg;
        private final List<Path> files;

        private LibraryCopyTask(ConversionConfig cfg, List<Path> files) {
            this.cfg = cfg;
            this.files = files;
        }

        @Override protected LibraryService.ScanResult call() {
            updateMessage("Copiando...");
            updateProgress(0, Math.max(files.size(), 1));
            return service.copyFiles(files, cfg, new LibraryService.LibraryListener() {
                @Override public void onProgress(int c, int t, String m) { updateProgress(c, t); updateMessage(m); }
                @Override public void onLog(String m) { logFromTask(m); }
            });
        }

        private void logFromTask(String msg) { Platform.runLater(() -> log(msg)); }
    }
}
