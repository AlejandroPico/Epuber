package es.alepico.epuber.ui.tabs;

import es.alepico.epuber.model.BookMetadata;
import es.alepico.epuber.service.PdfService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Path;

public class ConverterTab extends Tab {
    private TextField pdfPath, epubPath, titleField, authorField;
    private TextArea logArea;
    private ProgressBar prog;
    private PdfService service = new PdfService();

    public ConverterTab(Stage stage) {
        super("Conversor PDF");
        setClosable(false);

        pdfPath = new TextField(); pdfPath.setPromptText("Archivo PDF...");
        epubPath = new TextField(); epubPath.setPromptText("Salida EPUB...");
        titleField = new TextField(); titleField.setPromptText("Título del libro");
        authorField = new TextField(); authorField.setPromptText("Autor(es)");

        Button bPdf = new Button("Abrir PDF"); bPdf.setOnAction(e-> chooseFile(stage, pdfPath, true));
        Button bEpub = new Button("Guardar Como"); bEpub.setOnAction(e-> chooseFile(stage, epubPath, false));
        Button bConv = new Button("Convertir"); bConv.setOnAction(e-> runConvert());

        logArea = new TextArea(); logArea.setPrefHeight(100);
        prog = new ProgressBar(0); prog.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10);
        form.add(new Label("PDF:"),0,0); form.add(pdfPath,1,0); form.add(bPdf,2,0);
        form.add(new Label("EPUB:"),0,1); form.add(epubPath,1,1); form.add(bEpub,2,1);
        form.add(new Label("Meta:"),0,2); form.add(titleField,1,2); form.add(authorField,2,2);

        VBox root = new VBox(15, new Label("Conversor Fijo (Fixed Layout)"), form, bConv, prog, logArea);
        root.setPadding(new Insets(20));
        setContent(new ScrollPane(root));
    }

    private void chooseFile(Stage s, TextField tf, boolean open) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(open?"PDF":"EPUB", open?"*.pdf":"*.epub"));
        File f = open ? fc.showOpenDialog(s) : fc.showSaveDialog(s);
        if(f!=null) tf.setText(f.getAbsolutePath());
    }

    private void runConvert() {
        if(pdfPath.getText().isBlank() || epubPath.getText().isBlank()) return;
        
        BookMetadata meta = new BookMetadata(titleField.getText(), authorField.getText());
        Path src = Path.of(pdfPath.getText());
        Path dst = Path.of(epubPath.getText());

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                service.convert(src, dst, meta, true, 300, new PdfService.ProgressListener() {
                    @Override public void onMessage(String msg) { updateMessage(msg); }
                    @Override public void onProgress(long d, long t) { updateProgress(d,t); }
                });
                return null;
            }
        };
        
        prog.progressProperty().bind(task.progressProperty());
        logArea.textProperty().bind(task.messageProperty());
        new Thread(task).start();
    }
}