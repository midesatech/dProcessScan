package com.example.mdt.infrastructure.adapter.mariadb;

import com.example.mdt.domain.gateway.DeteccionesGateway;
import com.example.mdt.domain.model.Deteccion;
import com.example.mdt.infrastructure.adapter.mariadb.entity.DeteccionTagEntity;
import com.example.mdt.infrastructure.adapter.mariadb.repository.DeteccionTagJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeteccionesGatewayJpaAdapter implements DeteccionesGateway {
    private static final Logger log = LoggerFactory.getLogger(DeteccionesGatewayJpaAdapter.class);
    private final DeteccionTagJpaRepository repo;

    public DeteccionesGatewayJpaAdapter(DeteccionTagJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void save(Deteccion d) {
        var e = new DeteccionTagEntity();
        e.setLectorId(d.lectorId());
        e.setUbicacionId(d.ubicacionId());
        e.setEpc(d.epc());
        e.setRssi(d.rssi());
        e.setMachine(d.machine());
        e.setCreatedAt(d.createdAt());
        repo.save(e);
        log.debug("Persisted detection epc={} lector_id={} ubicacion_id={}", d.epc(), d.lectorId(), d.ubicacionId());
    }
}
