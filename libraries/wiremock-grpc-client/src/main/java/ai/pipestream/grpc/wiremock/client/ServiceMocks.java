package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Centralized service mocks factory for easy testing.
 * Provides ready-to-use mocks for all pipeline services.
 */
public class ServiceMocks {
    
    // Note: In the client-server model, we don't hold the WireMockServer instance directly.
    // The WireMock client is static (WireMock.stubFor).
    // However, if consumers expect to pass a server instance, we can support that for API compatibility
    // but it won't be used for stubbing if we assume the static client is configured.
    // BETTER: We adapt this to work with the static client configuration assumed by the new architecture.
    
    private PlatformRegistrationMock platformRegistrationMock;
    private AccountManagerMock accountManagerMock;
    private ConnectorServiceMock connectorServiceMock;
    private SimpleServiceMock mappingServiceMock;
    private SimpleServiceMock engineServiceMock;
    private SimpleServiceMock designModeServiceMock;
    
    /**
     * Creates a service mocks factory.
     * Assumes WireMock.configureFor(...) has been called.
     */
    public ServiceMocks() {
    }
    
    /**
     * Get or create the Platform Registration Service mock.
     *
     * @return the PlatformRegistrationMock instance
     */
    public PlatformRegistrationMock platformRegistration() {
        if (platformRegistrationMock == null) {
            platformRegistrationMock = new PlatformRegistrationMock();
        }
        return platformRegistrationMock;
    }
    
    /**
     * Get or create the Account Manager Service mock.
     *
     * @return the AccountManagerMock instance
     */
    public AccountManagerMock accountManager() {
        if (accountManagerMock == null) {
            accountManagerMock = new AccountManagerMock();
        }
        return accountManagerMock;
    }

    /**
     * Get or create the Connector Service mock.
     *
     * @return the ConnectorServiceMock instance
     */
    public ConnectorServiceMock connector() {
        if (connectorServiceMock == null) {
            connectorServiceMock = new ConnectorServiceMock();
        }
        return connectorServiceMock;
    }
    
    /**
     * Get or create the Mapping Service mock.
     *
     * @return the SimpleServiceMock instance for mapping service
     */
    public SimpleServiceMock mapping() {
        if (mappingServiceMock == null) {
            mappingServiceMock = new SimpleServiceMock("ai.pipestream.mapping.MappingService");
        }
        return mappingServiceMock;
    }
    
    /**
     * Get or create the Engine Service mock.
     *
     * @return the SimpleServiceMock instance for engine service
     */
    public SimpleServiceMock engine() {
        if (engineServiceMock == null) {
            engineServiceMock = new SimpleServiceMock("ai.pipestream.engine.v1.EngineV1Service");
        }
        return engineServiceMock;
    }
    
    /**
     * Get or create the Design Mode Service mock.
     *
     * @return the SimpleServiceMock instance for design mode service
     */
    public SimpleServiceMock designMode() {
        if (designModeServiceMock == null) {
            designModeServiceMock = new SimpleServiceMock("ai.pipestream.design.v1.DesignModev1Service");
        }
        return designModeServiceMock;
    }
    
    /**
     * Setup default mocks for all services (useful for basic testing).
     * 
     * <p>Configures mocks for platform registration, account manager, and all service mocks.
     *
     * @return this instance for method chaining
     */
    public ServiceMocks setupDefaults() {
        platformRegistration()
            .mockServiceRegistration()
            .mockModuleRegistration();
            
        accountManager()
            .mockCreateAccount("default-account", "Default Account", "Default test account")
            .mockGetAccount("default-account", "Default Account", "Default test account", true);

        connector()
            .mockValidateApiKey("default-connector", "default-api-key", "default-account");
            
        mapping()
            .setupDefaults();
            
        engine()
            .setupDefaults();
            
        designMode()
            .setupDefaults();
            
        return this;
    }
    
    /**
     * Reset all mocks to clean state.
     * 
     * <p>Clears all cached mock instances so they will be recreated on next access.
     * Does NOT reset WireMock server state (use WireMock.reset() for that).
     *
     * @return this instance for method chaining
     */
    public ServiceMocks reset() {
        // Recreate mocks to reset state
        platformRegistrationMock = null;
        accountManagerMock = null;
        connectorServiceMock = null;
        mappingServiceMock = null;
        engineServiceMock = null;
        designModeServiceMock = null;
        return this;
    }
}