package es.alepico.epuber.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

public class ConversionConfig {
    public Path source;
    public Path target;
    public Set<String> extensions;
    public LocalDate fromDate;
    public LocalDate toDate;
    public boolean overwrite;
    public boolean onlyNew;
    public String keyword;
    public long minSizeBytes;
    
    public ConversionConfig() {
		// TODO Auto-generated constructor stub
	}
}