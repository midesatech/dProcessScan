package com.example.mdt.infrastructure.adapter.mariadb.repository;

import com.example.mdt.infrastructure.adapter.mariadb.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionJpaRepository extends JpaRepository<LocationEntity, Long> { }
