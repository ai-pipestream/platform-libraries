package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Centralized service mocks factory for easy testing.
 * Provides ready-to-use mocks for all pipeline services.
 */
public class ServiceMocks {
    
    private final WireMockServer wireMockServer;
    private PlatformRegistrationMock platformRegistrationMock;
    private AccountManagerMock accountManagerMock;
    private SimpleServiceMock repositoryServiceMock;
    private SimpleServiceMock mappingServiceMock;
    private SimpleServiceMock engineServiceMock;
    private SimpleServiceMock designModeServiceMock;
    
    /**
     * Creates a service mocks factory using the given WireMock server.
     *
     * @param wireMockServer The WireMock server instance
     */
    public ServiceMocks(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }
    
    /**
     * Get or create the Platform Registration Service mock.
     *
     * @return the PlatformRegistrationMock instance
     */
    public PlatformRegistrationMock platformRegistration() {
        if (platformRegistrationMock == null) {
            platformRegistrationMock = new PlatformRegistrationMock(wireMockServer);
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
            accountManagerMock = new AccountManagerMock(wireMockServer.port());
        }
        return accountManagerMock;
    }
    
    /**
     * Get or create the Repository Service mock.
     *
     * @return the SimpleServiceMock instance for repository service
     */
    public SimpleServiceMock repository() {
        if (repositoryServiceMock == null) {
            repositoryServiceMock = new SimpleServiceMock(wireMockServer.port());
        }
        return repositoryServiceMock;
    }
    
    /**
     * Get or create the Mapping Service mock.
     *
     * @return the SimpleServiceMock instance for mapping service
     */
    public SimpleServiceMock mapping() {
        if (mappingServiceMock == null) {
            mappingServiceMock = new SimpleServiceMock(wireMockServer.port());
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
            engineServiceMock = new SimpleServiceMock(wireMockServer.port());
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
            designModeServiceMock = new SimpleServiceMock(wireMockServer.port());
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
            
        repository()
            .setupDefaults();
            
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
     *
     * @return this instance for method chaining
     */
    public ServiceMocks reset() {
        // Recreate mocks to reset state
        platformRegistrationMock = null;
        accountManagerMock = null;
        repositoryServiceMock = null;
        mappingServiceMock = null;
        engineServiceMock = null;
        designModeServiceMock = null;
        return this;
    }
}
