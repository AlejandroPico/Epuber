package es.alepico.epuber.ui.common;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Métodos de utilidad para componentes visuales reusables.
 */
public class UIHelpers {

    /**
     * Crea la cabecera estándar de las pestañas.
     * @param title Título principal (ej. "Biblioteca")
     * @param subtitle Subtítulo descriptivo.
     * @return Un VBox formateado con las clases CSS correspondientes.
     */
    public static Node createHeader(String title, String subtitle) {
        Label t = new Label(title);
        t.getStyleClass().add("app-title");
        
        Label s = new Label(subtitle);
        s.getStyleClass().add("app-subtitle");
        s.setWrapText(true);
        
        VBox box = new VBox(5, t, s);
        // Pequeño margen inferior
        box.setStyle("-fx-padding: 0 0 15 0;"); 
        return box;
    }

    /**
     * Muestra una alerta de forma segura, independientemente del hilo desde el que se llame.
     * Si se llama desde un Task (hilo de fondo), se envuelve en Platform.runLater.
     */
    public static void showAlert(Alert.AlertType type, String title, String message) {
        Runnable r = () -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
        };

        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}