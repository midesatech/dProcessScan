package com.example.mdt.infrastructure.adapter.mariadb.entity;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="detecciones_tags")
public class DeteccionTagEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="lector_id", nullable=false) private Long lectorId;
    @Column(name="ubicacion_id") private Long ubicacionId;
    @Column(name="epc", nullable=false, length=64) private String epc;
    @Column(name="rssi") private Integer rssi;
    @Column(name="machine", length=100) private String machine;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist public void onPersist(){ var now = LocalDateTime.now(); if (createdAt==null) createdAt=now; updatedAt=now; }
    @PreUpdate public void onUpdate(){ updatedAt = LocalDateTime.now(); }
    public Long getId(){ return id; }
    public Long getLectorId(){ return lectorId; } public void setLectorId(Long v){ lectorId=v; }
    public Long getUbicacionId(){ return ubicacionId; } public void setUbicacionId(Long v){ ubicacionId=v; }
    public String getEpc(){ return epc; } public void setEpc(String v){ epc=v; }
    public Integer getRssi(){ return rssi; } public void setRssi(Integer v){ rssi=v; }
    public String getMachine(){ return machine; } public void setMachine(String v){ machine=v; }
    public LocalDateTime getCreatedAt(){ return createdAt; } public void setCreatedAt(LocalDateTime v){ createdAt=v; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; } public void setUpdatedAt(LocalDateTime v){ updatedAt=v; }
}
