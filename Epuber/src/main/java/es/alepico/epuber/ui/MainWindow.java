package es.alepico.epuber.ui;

import es.alepico.epuber.ui.common.Styles;
import es.alepico.epuber.ui.tabs.ConverterTab;
import es.alepico.epuber.ui.tabs.CoversTab;
import es.alepico.epuber.ui.tabs.DuplicatesTab;
import es.alepico.epuber.ui.tabs.LibraryTab;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Ventana principal de la aplicación.
 * Ensambla las distintas pestañas funcionales.
 */
public class MainWindow {

    private final Stage stage;
    private final BorderPane rootLayout;

    public MainWindow(Stage stage) {
        this.stage = stage;
        this.rootLayout = new BorderPane();
        initLayout();
    }

    private void initLayout() {
        // Crear el contenedor de pestañas
        TabPane tabPane = new TabPane();
        
        // Instanciar y añadir cada módulo funcional
        // Pasamos el 'stage' a aquellos que necesiten abrir diálogos (Library y Converter)
        tabPane.getTabs().addAll(
            new LibraryTab(stage),
            new ConverterTab(stage),
            new CoversTab(),        // CoversTab gestiona su propia lógica de visualización
            new DuplicatesTab(stage)
        );

        // Configurar estilos para que las pestañas no se cierren
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        rootLayout.setCenter(tabPane);
    }

    /**
     * Construye y devuelve la escena lista para mostrar.
     */
    public Scene buildScene() {
        Scene scene = new Scene(rootLayout, 1240, 760);
        
        // Cargar los estilos CSS definidos en la clase Styles
        scene.getStylesheets().add(Styles.MAIN_CSS);
        
        return scene;
    }
    
    /**
     * Muestra la ventana.
     */
    public void show() {
        stage.setScene(buildScene());
        stage.show();
    }
}