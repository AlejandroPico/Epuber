package es.alepico.epuber.ui.common;

public class Styles {
    public static final String MAIN_CSS = """
            data:text/css,
            :root { -fx-font-family: "Inter", "Segoe UI", system-ui; }
            .root { -fx-background-color: linear-gradient(to bottom, #f7f8fb, #eef1f6); }
            .app-title { -fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill:#0f172a; }
            .app-subtitle { -fx-opacity: 0.85; -fx-text-fill:#334155; }
            .panel { -fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10,0,0,2); -fx-padding: 12; }
            .text-field, .combo-box, .date-picker { -fx-background-radius: 8; -fx-padding: 6; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 8; }
            .button { -fx-background-radius: 8; -fx-padding: 8 16; -fx-font-weight:600; -fx-background-color: #3b82f6; -fx-text-fill:white; -fx-cursor: hand; }
            .button:hover { -fx-background-color: #2563eb; }
            .button-danger { -fx-background-color: #ef4444; }
            .thumb { -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1),5,0,0,1); -fx-background-radius: 8; -fx-background-color:white; -fx-padding:4; }
            .thumb:hover { -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.4),8,0,0,1); }
            .thumb-selected { -fx-border-color:#3b82f6; -fx-border-width:2; }
            """;
}