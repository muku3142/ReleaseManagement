package com.evision.releasemgmt.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_snapshot_logo")
public class DocumentSnapshotLogoEntity {

    @Id
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public DocumentSnapshotLogoEntity() {
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(UUID snapshotId) {
        this.snapshotId = snapshotId;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getByteSize() {
        return byteSize;
    }

    public void setByteSize(int byteSize) {
        this.byteSize = byteSize;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
