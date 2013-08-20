package org.motechproject.server.config.domain;

import org.apache.commons.lang.StringUtils;
import org.motechproject.server.config.bootstrap.MotechConfigurationException;

public class BootstrapConfig {

    public static final String DEFAULT_TENANT_ID = "DEFAULT";
    private DBConfig dbConfig;
    private String tenantId = DEFAULT_TENANT_ID;
    private ConfigSource configSource;

    public BootstrapConfig(DBConfig dbConfig, String tenantId, ConfigSource configSource) {
        if (dbConfig == null) {
            throw new MotechConfigurationException("DB configuration cannot be null.");
        }
        this.dbConfig = dbConfig;
        this.tenantId = (StringUtils.isNotBlank(tenantId)) ? tenantId : DEFAULT_TENANT_ID;
        this.configSource = (configSource != null) ? configSource : ConfigSource.UI;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BootstrapConfig that = (BootstrapConfig) o;

        if (!configSource.equals(that.configSource)) {
            return false;
        }

        if (!dbConfig.equals(that.dbConfig)) {
            return false;
        }
        if (!tenantId.equals(that.tenantId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = dbConfig.hashCode();
        result = 31 * result + tenantId.hashCode();
        result = 31 * result + configSource.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BootstrapConfig{");
        sb.append("dbConfig=").append(dbConfig);
        sb.append(", tenantId='").append(tenantId).append('\'');
        sb.append(", configSource=").append(configSource);
        sb.append('}');
        return sb.toString();
    }

    public String getTenantId() {
        return tenantId;
    }

    public ConfigSource getConfigSource() {
        return configSource;
    }
}
