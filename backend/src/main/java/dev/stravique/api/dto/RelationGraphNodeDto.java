package dev.stravique.api.dto;

public record RelationGraphNodeDto(
        String id,
        String path,
        String fileName,
        String fileType,
        int incoming,
        int outgoing
) {
}
