package es.alepico.epuber.ui.tabs;

import es.alepico.epuber.model.ConversionConfig;
import es.alepico.epuber.service.LibraryService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;

public class LibraryTab extends Tab {
    private TextField sourceField, targetField, keywordField;
    private CheckBox extEpub, extPdf, extMobi, overwriteCheck, onlyNewCheck;
    private DatePicker fromDate, toDate;
    private TextArea logArea;
    private ProgressBar progressBar;
    private Button startBtn;
    private LibraryService service = new LibraryService();
    private Task<LibraryService.ScanResult> currentTask;

    public LibraryTab(Stage stage) {
        super("Biblioteca");
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

        logArea = new TextArea(); logArea.setEditable(false); logArea.setPrefHeight(150);
        progressBar = new ProgressBar(0); progressBar.setMaxWidth(Double.MAX_VALUE);

        // Layout
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Origen:"), 0, 0); grid.add(sourceField, 1, 0); grid.add(btnSrc, 2, 0);
        grid.add(new Label("Destino:"), 0, 1); grid.add(targetField, 1, 1); grid.add(btnDst, 2, 1);
        grid.add(new HBox(10, new Label("Tipos:"), extEpub, extPdf, extMobi), 1, 2);
        grid.add(new HBox(10, new Label("Filtros:"), keywordField, fromDate, toDate), 1, 3);
        grid.add(new HBox(10, overwriteCheck, onlyNewCheck), 1, 4);

        VBox content = new VBox(15, 
            new Label("Gestión Masiva de Biblioteca"),
            grid, 
            new Separator(),
            startBtn, 
            progressBar, 
            logArea
        );
        content.setPadding(new Insets(20));
        content.getStyleClass().add("panel");

        setContent(new ScrollPane(content));
    }

    private void chooseDir(Stage s, TextField f) {
        DirectoryChooser dc = new DirectoryChooser();
        File d = dc.showDialog(s);
        if(d != null) f.setText(d.getAbsolutePath());
    }

    private void toggleProcess() {
        if(currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            return;
        }
        
        logArea.clear();
        ConversionConfig cfg = new ConversionConfig();
        if(sourceField.getText().isBlank()) { log("¡Falta origen!"); return; }
        cfg.source = Path.of(sourceField.getText());
        cfg.target = targetField.getText().isBlank() ? null : Path.of(targetField.getText());
        cfg.extensions = new HashSet<>();
        if(extEpub.isSelected()) cfg.extensions.add(".epub");
        if(extPdf.isSelected()) cfg.extensions.add(".pdf");
        if(extMobi.isSelected()) cfg.extensions.add(".mobi");
        cfg.overwrite = overwriteCheck.isSelected();
        cfg.onlyNew = onlyNewCheck.isSelected();
        
        currentTask = new Task<>() {
            @Override protected LibraryService.ScanResult call() {
                var files = service.scanFiles(cfg);
                updateMessage("Encontrados: " + files.size());
                return service.copyFiles(files, cfg, new LibraryService.LibraryListener() {
                    @Override public void onProgress(int c, int t, String m) { updateProgress(c, t); }
                    @Override public void onLog(String m) { Platform.runLater(()-> log(m)); }
                });
            }
        };
        
        startBtn.setText("Cancelar");
        startBtn.getStyleClass().add("button-danger");
        progressBar.progressProperty().bind(currentTask.progressProperty());
        
        currentTask.setOnSucceeded(e -> { finish("Proceso terminado."); });
        currentTask.setOnCancelled(e -> { finish("Cancelado."); });
        currentTask.setOnFailed(e -> { finish("Error: " + currentTask.getException().getMessage()); });
        
        new Thread(currentTask).start();
    }

    private void finish(String msg) {
        log(msg);
        startBtn.setText("Iniciar Copia");
        startBtn.getStyleClass().remove("button-danger");
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
    }

    private void log(String t) { logArea.appendText(t + "\n"); }
}