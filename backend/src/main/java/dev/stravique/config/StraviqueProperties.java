package dev.stravique.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "stravique")
public class StraviqueProperties {

    private String analyzerVersion = "1.0.0";
    private String defaultLocale = "en-US";
    private final Scan scan = new Scan();
    private final Cors cors = new Cors();
    private final Transformation transformation = new Transformation();

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public void setAnalyzerVersion(String analyzerVersion) {
        this.analyzerVersion = analyzerVersion;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public Scan getScan() {
        return scan;
    }

    public Cors getCors() {
        return cors;
    }

    public Transformation getTransformation() {
        return transformation;
    }

    public static class Transformation {
        private String backupDirectory = "";

        public String getBackupDirectory() {
            return backupDirectory;
        }

        public void setBackupDirectory(String backupDirectory) {
            this.backupDirectory = backupDirectory == null ? "" : backupDirectory;
        }
    }

    public static class Scan {
        private List<String> allowedRoots = new ArrayList<>();
        private long maxFileBytes = 2_097_152;
        private int maxFiles = 100_000;

        public List<String> getAllowedRoots() {
            return allowedRoots;
        }

        public void setAllowedRoots(List<String> allowedRoots) {
            this.allowedRoots = allowedRoots == null ? new ArrayList<>() : allowedRoots;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
        }
    }
}
