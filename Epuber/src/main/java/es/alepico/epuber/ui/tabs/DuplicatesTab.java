package es.alepico.epuber.ui.tabs;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DuplicatesTab extends Tab {
    public DuplicatesTab(Stage stage) {
        super("Duplicados");
        setClosable(false);
        TableView<?> table = new TableView<>();
        Button scanBtn = new Button("Buscar Duplicados");
        VBox root = new VBox(10, new Label("Buscador de archivos repetidos"), scanBtn, table);
        root.setPadding(new Insets(20));
        setContent(root);
    }
}