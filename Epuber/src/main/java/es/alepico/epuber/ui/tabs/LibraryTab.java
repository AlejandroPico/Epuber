package es.alepico.epuber.ui.tabs;

import es.alepico.epuber.model.ConversionConfig;
import es.alepico.epuber.service.LibraryService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class LibraryTab extends Tab {
    private final Stage stage;
    private final TextField sourceField, targetField, keywordField;
    private final CheckBox extEpub, extPdf, extMobi, overwriteCheck, onlyNewCheck;
    private final DatePicker fromDate, toDate;
    private final TextArea logArea;
    private final ProgressBar progressBar;
    private final Button startBtn, saveListBtn;
    private final TableView<Path> fileTable;
    private final ObservableList<Path> scannedFiles = FXCollections.observableArrayList();
    private final Label statusLabel;
    private final LibraryService service = new LibraryService();
    private Task<LibraryService.ScanResult> currentTask;

    public LibraryTab(Stage stage) {
        super("Biblioteca");
        this.stage = stage;
        setClosable(false);

        // Controles
        sourceField = new TextField(); sourceField.setPromptText("Origen...");
        targetField = new TextField(); targetField.setPromptText("Destino...");
        Button btnSrc = new Button("..."); btnSrc.setOnAction(e-> chooseDir(stage, sourceField));
        Button btnDst = new Button("..."); btnDst.setOnAction(e-> chooseDir(stage, targetField));

        extEpub = new CheckBox(".epub"); extEpub.setSelected(true);
        extPdf = new CheckBox(".pdf"); extPdf.setSelected(true);
        extMobi = new CheckBox(".mobi");
        keywordField = new TextField(); keywordField.setPromptText("Buscar nombre...");

        fromDate = new DatePicker(); toDate = new DatePicker();
        overwriteCheck = new CheckBox("Sobrescribir");
        onlyNewCheck = new CheckBox("Solo nuevos");

        startBtn = new Button("Iniciar Copia");
        startBtn.setOnAction(e -> toggleProcess());
        saveListBtn = new Button("Guardar listado");
        saveListBtn.setDisable(true);
        saveListBtn.setOnAction(e -> saveListToFile());

        statusLabel = new Label("Sin escanear");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);
        logArea.setWrapText(true);
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        fileTable = createTable();

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Origen:"), 0, 0); grid.add(sourceField, 1, 0); grid.add(btnSrc, 2, 0);
        grid.add(new Label("Destino:"), 0, 1); grid.add(targetField, 1, 1); grid.add(btnDst, 2, 1);
        grid.add(new HBox(10, new Label("Tipos:"), extEpub, extPdf, extMobi), 1, 2);
        grid.add(new HBox(10, new Label("Filtros:"), keywordField, fromDate, toDate), 1, 3);
        grid.add(new HBox(10, overwriteCheck, onlyNewCheck), 1, 4);

        HBox actions = new HBox(10, startBtn, saveListBtn, statusLabel);

        VBox content = new VBox(15,
            new Label("Gestión Masiva de Biblioteca"),
            grid,
            actions,
            progressBar,
            fileTable,
            new Label("Registro"),
            logArea
        );
        content.setPadding(new Insets(20));
        content.getStyleClass().add("panel");
        VBox.setVgrow(fileTable, Priority.ALWAYS);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        BorderPane root = new BorderPane(content);
        root.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setContent(root);
    }

    private TableView<Path> createTable() {
        TableView<Path> table = new TableView<>();
        table.setItems(scannedFiles);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Sin resultados aún"));

        TableColumn<Path, String> nameCol = new TableColumn<>("Archivo");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFileName().toString()));

        TableColumn<Path, String> folderCol = new TableColumn<>("Carpeta");
        folderCol.setCellValueFactory(cd -> new SimpleStringProperty(
            Optional.ofNullable(cd.getValue().getParent()).map(Path::toString).orElse("")
        ));

        TableColumn<Path, String> dateCol = new TableColumn<>("Modificado");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(formatDate(cd.getValue())));

        table.getColumns().addAll(nameCol, folderCol, dateCol);
        return table;
    }

    private void chooseDir(Stage s, TextField f) {
        DirectoryChooser dc = new DirectoryChooser();
        File d = dc.showDialog(s);
        if(d != null) f.setText(d.getAbsolutePath());
    }

    private void toggleProcess() {
        if(currentTask != null && currentTask.isRunning()) {
            log("Cancelando tarea actual...");
            currentTask.cancel();
            return;
        }

        logArea.clear();
        ConversionConfig cfg = buildConfig();
        if(cfg == null) return;

        LibraryTask task = new LibraryTask(cfg);
        currentTask = task;

        startBtn.setText("Cancelar");
        startBtn.getStyleClass().add("button-danger");
        saveListBtn.setDisable(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> finish(task.getValue(), task.getFiles(), "Proceso terminado."));
        task.setOnCancelled(e -> finish(task.getValue(), task.getFiles(), "Cancelado."));
        task.setOnFailed(e -> finish(task.getValue(), task.getFiles(), "Error: " + task.getException().getMessage()));

        new Thread(task).start();
    }

    private ConversionConfig buildConfig() {
        if(sourceField.getText().isBlank()) { log("¡Falta origen!"); return null; }
        if(targetField.getText().isBlank()) { log("¡Falta destino!"); return null; }

        ConversionConfig cfg = new ConversionConfig();
        cfg.source = Path.of(sourceField.getText());
        cfg.target = Path.of(targetField.getText());
        cfg.extensions = new HashSet<>();
        if(extEpub.isSelected()) cfg.extensions.add(".epub");
        if(extPdf.isSelected()) cfg.extensions.add(".pdf");
        if(extMobi.isSelected()) cfg.extensions.add(".mobi");
        if(cfg.extensions.isEmpty()) { log("Selecciona al menos una extensión de archivo."); return null; }
        cfg.overwrite = overwriteCheck.isSelected();
        cfg.onlyNew = onlyNewCheck.isSelected();
        cfg.keyword = keywordField.getText();
        cfg.fromDate = fromDate.getValue();
        cfg.toDate = toDate.getValue();
        return cfg;
    }

    private void finish(LibraryService.ScanResult res, List<Path> files, String msg) {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        startBtn.setText("Iniciar Copia");
        startBtn.getStyleClass().remove("button-danger");
        currentTask = null;

        if(res == null) res = new LibraryService.ScanResult();
        scannedFiles.setAll(files);
        saveListBtn.setDisable(scannedFiles.isEmpty());

        log(msg);
        log(String.format("Encontrados: %d | Copiados: %d | Omitidos: %d", res.found, res.copied, res.skipped));
        if(res.error != null) log("Error: " + res.error.getMessage());
        if(res.cancelled) log("Proceso cancelado por el usuario.");

        statusLabel.setText(msg);
    }

    private void log(String t) { logArea.appendText(t + "\n"); }

    private void saveListToFile() {
        if(scannedFiles.isEmpty()) {
            log("No hay archivos para guardar.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar listado de archivos");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt"));
        chooser.setInitialFileName("listado.txt");
        File dest = chooser.showSaveDialog(stage);
        if(dest == null) return;

        try {
            Files.write(dest.toPath(), scannedFiles.stream().map(Path::toString).toList());
            log("Listado guardado en: " + dest.getAbsolutePath());
        } catch (IOException e) {
            log("No se pudo guardar el listado: " + e.getMessage());
        }
    }

    private String formatDate(Path path) {
        try {
            var time = Files.getLastModifiedTime(path).toInstant();
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault()).format(time);
        } catch (IOException e) {
            return "-";
        }
    }

    private class LibraryTask extends Task<LibraryService.ScanResult> {
        private final ConversionConfig cfg;
        private List<Path> files = List.of();

        private LibraryTask(ConversionConfig cfg) { this.cfg = cfg; }

        @Override protected LibraryService.ScanResult call() {
            updateMessage("Escaneando...");
            files = service.scanFiles(cfg);
            updateMessage("Encontrados: " + files.size());
            logFromTask("Escaneo completado. Archivos encontrados: " + files.size());

            if(isCancelled()) {
                LibraryService.ScanResult cancelled = new LibraryService.ScanResult();
                cancelled.found = files.size();
                cancelled.cancelled = true;
                return cancelled;
            }

            updateProgress(0, Math.max(files.size(), 1));
            return service.copyFiles(files, cfg, new LibraryService.LibraryListener() {
                @Override public void onProgress(int c, int t, String m) { updateProgress(c, t); updateMessage(m); }
                @Override public void onLog(String m) { logFromTask(m); }
            });
        }

        public List<Path> getFiles() { return files; }

        private void logFromTask(String msg) { Platform.runLater(() -> log(msg)); }
    }
}