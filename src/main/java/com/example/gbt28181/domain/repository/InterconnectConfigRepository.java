package com.example.gbt28181.domain.repository;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterconnectConfigRepository extends JpaRepository<InterconnectConfig, Long> {
}
