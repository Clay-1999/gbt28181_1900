package com.example.gbt28181.domain.repository;

import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Ivs1900InterconnectConfigRepository extends JpaRepository<Ivs1900InterconnectConfig, Long> {

    /** 查找第一条配置（系统只支持单实例） */
    Optional<Ivs1900InterconnectConfig> findFirstByOrderByIdAsc();

    Optional<Ivs1900InterconnectConfig> findBySipId(String sipId);
}
