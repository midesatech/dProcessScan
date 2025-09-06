package com.example.mdt.infrastructure.adapter.mariadb;

import com.example.mdt.domain.gateway.MetadataGateway;
import com.example.mdt.infrastructure.adapter.mariadb.entity.UHFReaderEntity;
import com.example.mdt.infrastructure.adapter.mariadb.repository.LectorUhfJpaRepository;
import com.example.mdt.infrastructure.adapter.mariadb.repository.UbicacionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MetadataGatewayJpaAdapter implements MetadataGateway {

    private final LectorUhfJpaRepository lectorRepo;
    private final UbicacionJpaRepository ubicRepo;

    public MetadataGatewayJpaAdapter(LectorUhfJpaRepository lectorRepo, UbicacionJpaRepository ubicRepo) {
        this.lectorRepo = lectorRepo;
        this.ubicRepo = ubicRepo;
    }

    @Override
    public Optional<Long> findLectorIdByCodigo(String codigo) {
        return lectorRepo.findByCodigo(codigo).map(UHFReaderEntity::getId);
    }

    @Override
    public boolean existsUbicacionId(Long id) {
        return ubicRepo.existsById(id);
    }
}