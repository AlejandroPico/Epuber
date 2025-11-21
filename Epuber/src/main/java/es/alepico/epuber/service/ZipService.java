package es.alepico.epuber.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Servicio de utilidades para compresión y descompresión.
 * Especializado para cumplir con los requisitos del formato EPUB (mimetype sin comprimir).
 */
public class ZipService {

    /**
     * Descomprime un archivo ZIP (o EPUB) en un directorio de destino.
     * @param zipFilePath Ruta al archivo zip.
     * @param destDir Directorio donde se extraerá el contenido.
     */
    public void unzip(Path zipFilePath, Path destDir) throws IOException {
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }

        try (ZipFile zip = new ZipFile(zipFilePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                Path outPath = destDir.resolve(ze.getName()).normalize();
                
                // Seguridad: evitar Zip Slip (rutas que salen del directorio destino)
                if (!outPath.startsWith(destDir)) {
                    continue; 
                }

                if (ze.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    if (outPath.getParent() != null) {
                        Files.createDirectories(outPath.getParent());
                    }
                    try (InputStream is = zip.getInputStream(ze)) {
                        Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /**
     * Empaqueta un directorio en un archivo EPUB válido.
     * Asegura que el archivo 'mimetype' sea el primero y no esté comprimido (STORED).
     * * @param sourceDir Directorio raíz que contiene OEBPS, META-INF y mimetype.
     * @param outFile Ruta del archivo .epub resultante.
     */
    public void packEpub(Path sourceDir, Path outFile) throws IOException {
        if (Files.exists(outFile)) {
            Files.delete(outFile);
        }

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outFile))) {
            // 1. Escribir mimetype (SIEMPRE primero y STORED/Sin comprimir)
            Path mimePath = sourceDir.resolve("mimetype");
            if (Files.exists(mimePath)) {
                byte[] mimeBytes = Files.readAllBytes(mimePath);
                
                ZipEntry mimeEntry = new ZipEntry("mimetype");
                mimeEntry.setMethod(ZipEntry.STORED);
                mimeEntry.setSize(mimeBytes.length);
                mimeEntry.setCompressedSize(mimeBytes.length);
                mimeEntry.setCrc(calculateCrc(mimeBytes));
                
                zos.putNextEntry(mimeEntry);
                zos.write(mimeBytes);
                zos.closeEntry();
            } else {
                // Si no existe el archivo físico, lo creamos al vuelo por seguridad
                byte[] mimeBytes = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
                ZipEntry mimeEntry = new ZipEntry("mimetype");
                mimeEntry.setMethod(ZipEntry.STORED);
                mimeEntry.setSize(mimeBytes.length);
                mimeEntry.setCompressedSize(mimeBytes.length);
                mimeEntry.setCrc(calculateCrc(mimeBytes));
                
                zos.putNextEntry(mimeEntry);
                zos.write(mimeBytes);
                zos.closeEntry();
            }

            // 2. Comprimir el resto del contenido
            addDirToZip(zos, sourceDir, sourceDir);
        }
    }

    /**
     * Recorre recursivamente un directorio y añade archivos al ZipOutputStream.
     * Ignora el archivo 'mimetype' porque ya se procesó manualmente.
     */
    private void addDirToZip(ZipOutputStream zos, Path folder, Path base) throws IOException {
        try (var stream = Files.walk(folder)) {
            stream.filter(p -> !Files.isDirectory(p))
                  .filter(p -> !p.getFileName().toString().equals("mimetype")) // Ignorar mimetype
                  .forEach(path -> {
                      String zipEntryName = base.relativize(path).toString().replace('\\', '/');
                      try {
                          zos.putNextEntry(new ZipEntry(zipEntryName));
                          Files.copy(path, zos);
                          zos.closeEntry();
                      } catch (IOException e) {
                          throw new RuntimeException("Error zippeando: " + path, e);
                      }
                  });
        }
    }

    /**
     * Calcula el CRC32 necesario para entradas STORED.
     */
    private long calculateCrc(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
    
    /**
     * Utilidad para borrar un directorio temporal completo.
     */
    public void deleteDirectory(Path root) {
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.delete(p); } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
    }
}