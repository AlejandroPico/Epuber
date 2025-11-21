package es.alepico.epuber.ui.tabs;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.FlowPane;

public class CoversTab extends Tab {
    public CoversTab() {
        super("Carátulas");
        setClosable(false);
        FlowPane flow = new FlowPane();
        flow.setHgap(20); flow.setVgap(20);
        flow.setPadding(new Insets(20));
        // Lógica futura de carga de imágenes...
        flow.getChildren().add(new Label("Selecciona una carpeta en Biblioteca para ver portadas."));
        setContent(new ScrollPane(flow));
    }
}