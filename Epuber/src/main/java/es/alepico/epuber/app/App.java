package es.alepico.epuber.app;

import es.alepico.epuber.ui.common.Styles;
import es.alepico.epuber.ui.tabs.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("EPUBER - Gestor Profesional");

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
            new LibraryTab(stage),
            new ConverterTab(stage),
            new CoversTab(),
            new DuplicatesTab(stage)
        );

        Scene scene = new Scene(tabs, 1000, 700);
        scene.getStylesheets().add(Styles.MAIN_CSS);
        
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}