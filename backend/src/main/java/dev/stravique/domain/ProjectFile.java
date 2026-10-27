package dev.stravique.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_file")
public class ProjectFile {

    @Id
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "relative_path", nullable = false, length = 4096)
    private String relativePath;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 30)
    private FileType fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(length = 80)
    private String encoding;

    @Column(name = "last_modified_at")
    private Instant lastModifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 30)
    private FileScanStatus scanStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 30)
    private ParseStatus parseStatus;

    @Column(name = "parse_error", length = 4000)
    private String parseError;

    @Column(name = "framework_api_count", nullable = false)
    private int frameworkApiCount;

    @Column(nullable = false)
    private boolean generated;

    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private FileChangeType changeType;

    @Column(name = "structure_role", length = 40)
    private String structureRole;

    public static ProjectFile discovered(UUID scanId, UUID projectId, UUID moduleId, String relativePath,
                                         String fileName, FileType fileType, long sizeBytes, Instant lastModifiedAt,
                                         String sha256, boolean generated) {
        ProjectFile file = new ProjectFile();
        file.id = UUID.randomUUID();
        file.scanId = scanId;
        file.projectId = projectId;
        file.moduleId = moduleId;
        file.relativePath = relativePath;
        file.fileName = fileName;
        file.fileType = fileType;
        file.sizeBytes = sizeBytes;
        file.lastModifiedAt = lastModifiedAt;
        file.scanStatus = FileScanStatus.DISCOVERED;
        file.parseStatus = ParseStatus.NOT_APPLICABLE;
        file.changeType = FileChangeType.ADDED;
        file.sha256 = sha256;
        file.generated = generated;
        return file;
    }

    public static ProjectFile deleted(ProjectFile previous, UUID scanId, UUID projectId) {
        ProjectFile file = new ProjectFile();
        file.id = UUID.randomUUID();
        file.scanId = scanId;
        file.projectId = projectId;
        file.relativePath = previous.relativePath;
        file.fileName = previous.fileName;
        file.fileType = previous.fileType;
        file.sizeBytes = previous.sizeBytes;
        file.encoding = previous.encoding;
        file.lastModifiedAt = previous.lastModifiedAt;
        file.scanStatus = FileScanStatus.SKIPPED;
        file.parseStatus = ParseStatus.NOT_APPLICABLE;
        file.parseError = "File was present in the previous completed scan and is now absent.";
        file.changeType = FileChangeType.DELETED;
        file.structureRole = previous.structureRole;
        file.generated = previous.generated;
        file.sha256 = previous.sha256;
        return file;
    }

    public void setModuleId(UUID moduleId) {
        this.moduleId = moduleId;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setScanStatus(FileScanStatus scanStatus) {
        this.scanStatus = scanStatus;
    }

    public void setParseStatus(ParseStatus parseStatus) {
        this.parseStatus = parseStatus;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }

    public void setFrameworkApiCount(int frameworkApiCount) {
        this.frameworkApiCount = frameworkApiCount;
    }

    public void setChangeType(FileChangeType changeType) {
        this.changeType = changeType;
    }

    public void setStructureRole(String structureRole) {
        this.structureRole = structureRole;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanId() {
        return scanId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFileName() {
        return fileName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getEncoding() {
        return encoding;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public FileScanStatus getScanStatus() {
        return scanStatus;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public int getFrameworkApiCount() {
        return frameworkApiCount;
    }

    public boolean isGenerated() {
        return generated;
    }

    public String getSha256() {
        return sha256;
    }

    public FileChangeType getChangeType() {
        return changeType;
    }

    public String getStructureRole() {
        return structureRole;
    }
}
