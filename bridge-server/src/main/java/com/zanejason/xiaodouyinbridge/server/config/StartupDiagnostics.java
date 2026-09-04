package com.zanejason.xiaodouyinbridge.server.config;

import com.zanejason.xiaodouyinbridge.server.service.BindingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Component
public class StartupDiagnostics implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final BindingService bindingService;
    private final String datasourceUrl;
    private final String datasourceUser;
    private final String bridgeApiKey;
    private final String douyinAppId;
    private final String douyinAppSecret;
    private final String douyinDataSecret;
    private final int serverPort;

    public StartupDiagnostics(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            BindingService bindingService,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUser,
            @Value("${bridge.api-key}") String bridgeApiKey,
            @Value("${douyin.app-id:}") String douyinAppId,
            @Value("${douyin.app-secret:}") String douyinAppSecret,
            @Value("${douyin.data-secret:default}") String douyinDataSecret,
            @Value("${server.port:8765}") int serverPort) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.bindingService = bindingService;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUser = datasourceUser;
        this.bridgeApiKey = bridgeApiKey;
        this.douyinAppId = douyinAppId;
        this.douyinAppSecret = douyinAppSecret;
        this.douyinDataSecret = douyinDataSecret;
        this.serverPort = serverPort;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("============================================================");
        log.info(" XiaoDouyinBridge Bridge Server is ready");
        log.info(" HTTP port              : {}", serverPort);
        log.info(" MariaDB URL            : {}", datasourceUrl);
        log.info(" MariaDB user           : {}", datasourceUser);
        log.info(" Douyin AppID configured: {}", configured(douyinAppId));
        log.info(" Douyin AppSecret set   : {}", configured(douyinAppSecret));
        log.info(" Douyin DataSecret set  : {}", configuredNonDefault(douyinDataSecret, "default"));
        log.info(" Bridge API key set     : {}", configuredNonDefault(bridgeApiKey, "change-me"));

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            log.info(" [DB] Connection OK: {} {}", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
        }

        Integer bindingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM xdb_binding", Integer.class);
        Integer pendingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM xdb_pending_binding", Integer.class);
        log.info(" [DB] schema.sql OK: xdb_binding={} row(s), xdb_pending_binding={} row(s)",
                bindingCount == null ? 0 : bindingCount,
                pendingCount == null ? 0 : pendingCount);
        log.info(" [DB] Repository read check: {} persisted binding(s)", bindingService.allBindings().size());

        if (!configuredNonDefault(bridgeApiKey, "change-me")) {
            log.warn(" [CONFIG] XIAODOUYINBRIDGE_API_KEY is still using the default value 'change-me'");
        }
        if (!configured(douyinAppId) || !configured(douyinAppSecret)) {
            log.warn(" [CONFIG] Douyin AppID/AppSecret is incomplete; real Douyin API calls will not work yet");
        }
        if (!configuredNonDefault(douyinDataSecret, "default")) {
            log.warn(" [CONFIG] DOUYIN_DATA_SECRET is still 'default'; replace it before production callback testing");
        }

        log.info(" Callback endpoint       : /api/douyin/live-data/callback");
        log.info(" Minecraft bindings API : /api/bindings");
        log.info(" Douyin session API      : /api/douyin/session");
        log.info("============================================================");
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private boolean configuredNonDefault(String value, String defaultValue) {
        return configured(value) && !defaultValue.equals(value);
    }
}
