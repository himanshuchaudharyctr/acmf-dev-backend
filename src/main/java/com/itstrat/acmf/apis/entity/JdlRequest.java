package com.itstrat.acmf.apis.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.constraints.NotBlank;
import java.util.List;

public class JdlRequest {
    @NotBlank(message = "Base name is required")
    private String baseName;

    @NotBlank(message = "Application type is required")
    private String applicationType;
    @NotBlank(message = "authenticationType type is required")
    private String authenticationType;
    @NotBlank(message = "packageName type is required")
    private String packageName;

    @NotBlank(message = "Database type is required")
    private String databaseType;

    @NotBlank(message = "Production database type is required")
    private String prodDatabaseType;

    @NotBlank(message = "Build tool is required")
    private String buildTool;

    private String clientFramework;
    private String cacheProvider;
    private List<String> testFrameworks;
    private String serverPort;
    private boolean enableHibernateCache;
    private String jwtSecretKey;
    private String serviceDiscoveryType;
    private boolean reactive;
    private String nativeLanguage;
    private List<String> otherLanguages;
    private boolean enableTranslation;
    private boolean microfrontend;
    private String websocket;
    private String clientPackageManager;
    private List<String> clientTheme;

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getProdDatabaseType(){return prodDatabaseType;}

    public void setProdDatabaseType(String prodDatabaseType){ this.prodDatabaseType = prodDatabaseType;}

    public String getBuildTool() {
        return buildTool;
    }

    public void setBuildTool(String buildTool) {
        this.buildTool = buildTool;
    }

    public String getClientFramework() {
        return clientFramework;
    }

    public void setClientFramework(String clientFramework) {
        this.clientFramework = clientFramework;
    }

    public String getCacheProvider() {
        return cacheProvider;
    }

    public void setCacheProvider(String cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public List<String> getTestFrameworks() {
        return testFrameworks;
    }

    public void setTestFrameworks(List<String> testFrameworks) {
        this.testFrameworks = testFrameworks;
    }

    public boolean isEnableHibernateCache() {
        return enableHibernateCache;
    }

    public void setEnableHibernateCache(boolean enableHibernateCache) {
        this.enableHibernateCache = enableHibernateCache;
    }

    public String getJwtSecretKey() {
        return jwtSecretKey;
    }

    public void setJwtSecretKey(String jwtSecretKey) {
        this.jwtSecretKey = jwtSecretKey;
    }

    public String getServiceDiscoveryType() {
        return serviceDiscoveryType;
    }

    public void setServiceDiscoveryType(String serviceDiscoveryType) {
        this.serviceDiscoveryType = serviceDiscoveryType;
    }

    public boolean isReactive() {
        return reactive;
    }

    public void setReactive(boolean reactive) {
        this.reactive = reactive;
    }

    public String getNativeLanguage() {
        return nativeLanguage;
    }

    public void setNativeLanguage(String nativeLanguage) {
        this.nativeLanguage = nativeLanguage;
    }

    public List<String> getOtherLanguages() {
        return otherLanguages;
    }

    public void setOtherLanguages(List<String> otherLanguages) {
        this.otherLanguages = otherLanguages;
    }

    public boolean isEnableTranslation() {
        return enableTranslation;
    }

    public void setEnableTranslation(boolean enableTranslation) {
        this.enableTranslation = enableTranslation;
    }

    public boolean isMicrofrontend() {
        return microfrontend;
    }

    public void setMicrofrontend(boolean microfrontend) {
        this.microfrontend = microfrontend;
    }

    public String getWebsocket() {
        return websocket;
    }

    public void setWebsocket(String websocket) {
        this.websocket = websocket;
    }

    public String getClientPackageManager() {
        return clientPackageManager;
    }

    public void setClientPackageManager(String clientPackageManager) {
        this.clientPackageManager = clientPackageManager;
    }

    public List<String> getClientTheme() {
        return clientTheme;
    }

    public void setClientTheme(List<String> clientTheme) {
        this.clientTheme = clientTheme;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public String toJsonString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{}"; // return an empty JSON object if an error occurs
        }
    }
}
