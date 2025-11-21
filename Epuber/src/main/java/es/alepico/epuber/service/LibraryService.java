package es.alepico.epuber.service;

import es.alepico.epuber.model.ConversionConfig;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LibraryService {

    public static class ScanResult {
        public int found, copied, skipped;
        public Exception error;
        public boolean cancelled;
    }

    public interface LibraryListener {
        void onProgress(int current, int total, String message);
        void onLog(String message);
    }

    public List<Path> scanFiles(ConversionConfig cfg) {
        try (Stream<Path> s = Files.walk(cfg.source)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        for (String ext : cfg.extensions) if (n.endsWith(ext)) return true;
                        return false;
                    })
                    .filter(p -> checkDate(p, cfg.fromDate, cfg.toDate))
                    .filter(p -> checkKeyword(p, cfg.keyword))
                    .filter(p -> checkSize(p, cfg.minSizeBytes))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            return List.of();
        }
    }

    public ScanResult copyFiles(List<Path> files, ConversionConfig cfg, LibraryListener listener) {
        ScanResult res = new ScanResult();
        res.found = files.size();
        AtomicInteger count = new AtomicInteger(0);
        
        try {
            if (cfg.target != null) Files.createDirectories(cfg.target);
        } catch (IOException e) { res.error = e; return res; }

        for (Path p : files) {
            // Simulación de chequeo de cancelación (se debería manejar desde fuera con thread interruption)
            if (Thread.currentThread().isInterrupted()) { res.cancelled = true; break; }

            try {
                Path dest = cfg.target.resolve(p.getFileName());
                boolean exists = Files.exists(dest);
                
                if (cfg.onlyNew && exists) {
                    res.skipped++; listener.onLog("Omitido (existe): " + p.getFileName());
                } else if (!cfg.overwrite && exists) {
                    res.skipped++; listener.onLog("Omitido (existe): " + p.getFileName());
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    res.copied++; listener.onLog("Copiado: " + p.getFileName());
                }
            } catch (Exception e) {
                listener.onLog("Error: " + e.getMessage());
            }
            listener.onProgress(count.incrementAndGet(), res.found, "Procesando...");
        }
        return res;
    }

    private boolean checkDate(Path p, LocalDate from, LocalDate to) {
        try {
            LocalDate d = LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault()).toLocalDate();
            if (from != null && d.isBefore(from)) return false;
            if (to != null && d.isAfter(to)) return false;
            return true;
        } catch (Exception e) { return true; }
    }
    
    private boolean checkKeyword(Path p, String k) {
        if (k == null || k.isBlank()) return true;
        return p.getFileName().toString().toLowerCase().contains(k.toLowerCase());
    }
    private boolean checkSize(Path p, long min) {
        try { return Files.size(p) >= min; } catch(Exception e){ return true; }
    }
}