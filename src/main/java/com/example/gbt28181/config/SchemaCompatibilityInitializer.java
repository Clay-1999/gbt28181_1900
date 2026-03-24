package com.example.gbt28181.config;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import com.example.gbt28181.sip.CatalogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaCompatibilityInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Ivs1900InterconnectConfigRepository ivs1900ConfigRepository;
    private final CatalogQueryService catalogQueryService;

    @Override
    public void run(ApplicationArguments args) {
        boolean localPtzColumnAdded = ensureColumn("ivs1900_camera_mapping", "ptz_type", "varchar(20)");
        ensureColumn("remote_device", "ptz_type", "varchar(20)");

        if (localPtzColumnAdded || hasLocalMappingsMissingPtzType()) {
            ivs1900ConfigRepository.findFirstByOrderByIdAsc().ifPresent(config -> {
                log.info("检测到本端设备 PTZ 类型缺失，启动后主动补发 Catalog Query");
                catalogQueryService.queryCatalog(toInterconnectConfig(config));
            });
        }

        ensureTestCamera();
    }

    /**
     * 确保 SIP 测试套件（套件2）所需的本端测试相机存在于数据库。
     * 仅在该 gbDeviceId 不存在时插入，不影响生产数据。
     */
    private void ensureTestCamera() {
        String testGbDeviceId = "34020000001320000034";
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ivs1900_camera_mapping where gb_device_id = ?",
                Integer.class, testGbDeviceId);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "insert into ivs1900_camera_mapping (ivs_camera_id, gb_device_id, name, status) values (?, ?, ?, ?)",
                    "test_cfg_cam_01", testGbDeviceId, "TestConfigCamera", "ON");
            log.info("已插入测试相机 gbDeviceId={}", testGbDeviceId);
        }
    }

    private boolean hasLocalMappingsMissingPtzType() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ivs1900_camera_mapping where ptz_type is null or trim(ptz_type) = ''",
                Integer.class
        );
        return count != null && count > 0;
    }

    private boolean ensureColumn(String tableName, String columnName, String columnDefinition) {
        try {
            if (columnExists(tableName, columnName)) {
                return false;
            }
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
            log.info("补齐数据库列: {}.{} {}", tableName, columnName, columnDefinition);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("补齐数据库列失败: " + tableName + "." + columnName, e);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(
                    connection.getCatalog(),
                    null,
                    tableName.toUpperCase(Locale.ROOT),
                    columnName.toUpperCase(Locale.ROOT))) {
                return rs.next();
            }
        }
    }

    private InterconnectConfig toInterconnectConfig(Ivs1900InterconnectConfig ivs1900) {
        InterconnectConfig config = new InterconnectConfig();
        config.setId(ivs1900.getId());
        config.setRemoteSipId(ivs1900.getSipId());
        config.setRemoteIp(ivs1900.getIp());
        config.setRemotePort(ivs1900.getPort());
        config.setRemoteDomain(ivs1900.getDomain());
        config.setPassword(ivs1900.getPassword());
        config.setEnabled(true);
        return config;
    }
}
