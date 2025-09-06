package com.example.mdt.infrastructure.adapter.mariadb.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lectores_uhf")
public class UHFReaderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String codigo;

    @Column(length = 255)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private LocationEntity ubicacion;

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocationEntity getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(LocationEntity ubicacion) {
        this.ubicacion = ubicacion;
    }
}
