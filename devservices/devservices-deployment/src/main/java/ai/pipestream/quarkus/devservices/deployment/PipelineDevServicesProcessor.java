package ai.pipestream.quarkus.devservices.deployment;

import ai.pipestream.quarkus.devservices.PipelineDevServicesConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ConfigurationBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

class PipelineDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(PipelineDevServicesProcessor.class);
    private static final String FEATURE = "pipeline-devservices";
    private static final String COMPOSE_FILE_RESOURCE = "compose-devservices.yml";
    private static final String INIT_SCRIPT_RESOURCE = "init-mysql.sql";

    private static final String VERSION_FILE = ".version";

    @BuildStep(onlyIf = IsDevelopment.class)
    FeatureBuildItem feature() {
        LOG.info("========================================");
        LOG.info("Pipeline Dev Services extension - feature build step executing");
        LOG.info("========================================");
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    FeatureBuildItem setupComposeFile(PipelineDevServicesConfig config,
                                      ConfigurationBuildItem configuration) {
        LOG.info("========================================");
        LOG.info("Pipeline Dev Services extension - setupComposeFile build step executing");
        LOG.info("Extension enabled: " + config.enabled());
        LOG.info("========================================");
        if (!config.enabled()) {
            LOG.warn("Pipeline Dev Services extension is disabled");
            return new FeatureBuildItem(FEATURE);
        }

        try {
            String targetDir = config.targetDir();
            Path targetPath = Paths.get(targetDir);
            Path composeFile = targetPath.resolve(COMPOSE_FILE_RESOURCE);
            Path versionFile = targetPath.resolve(VERSION_FILE);

            // Create target directory if it doesn't exist
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
                LOG.info("Created target directory: " + targetPath);
            }

            // Read compose file from classpath (from runtime module resources)
            InputStream composeResource = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(COMPOSE_FILE_RESOURCE);
            
            if (composeResource == null) {
                LOG.warn("Compose file not found in classpath: " + COMPOSE_FILE_RESOURCE);
                return new FeatureBuildItem(FEATURE);
            }

            // Calculate SHA of the resource
            byte[] resourceBytes = composeResource.readAllBytes();
            String resourceSha = calculateSHA(resourceBytes);
            composeResource.close();

            // Check if file exists
            if (Files.exists(composeFile)) {
                // Read existing version info
                VersionInfo existingVersion = readVersionInfo(versionFile);
                
                if (existingVersion != null && existingVersion.sha().equals(resourceSha)) {
                    // Same version, no update needed
                    LOG.debug("Compose file is up to date");
                } else if (existingVersion != null && !existingVersion.sha().equals(resourceSha)) {
                    // Version mismatch
                    if (config.forceUpdate()) {
                        LOG.info("Force update enabled, updating compose file");
                        updateComposeFile(composeFile, versionFile, resourceBytes, resourceSha, config);
                    } else if (config.autoUpdate()) {
                        // Check if file was edited (SHA differs from stored SHA)
                        byte[] existingBytes = Files.readAllBytes(composeFile);
                        String existingSha = calculateSHA(existingBytes);
                        
                        if (existingVersion.sha().equals(existingSha)) {
                            // File wasn't edited, safe to auto-update
                            LOG.info("Auto-updating compose file (version changed)");
                            updateComposeFile(composeFile, versionFile, resourceBytes, resourceSha, config);
                        } else {
                            // File was edited, warn and don't update
                            LOG.warn("Compose file was manually edited. Skipping auto-update. " +
                                    "Set quarkus.pipeline-devservices.force-update=true to force update.");
                        }
                    } else {
                        LOG.debug("Auto-update disabled, using existing compose file");
                    }
                } else {
                    // Version file doesn't exist, treat as new
                    LOG.info("Extracting compose file (first time)");
                    extractComposeFile(composeFile, versionFile, resourceBytes, resourceSha);
                }
            } else {
                // File doesn't exist, extract it
                LOG.info("Extracting compose file to: " + composeFile);
                extractComposeFile(composeFile, versionFile, resourceBytes, resourceSha);
            }

            // Validate YAML
            validateYaml(composeFile);

            // Extract init script if it exists
            Path initScriptFile = targetPath.resolve(INIT_SCRIPT_RESOURCE);
            try (InputStream initScriptResource = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(INIT_SCRIPT_RESOURCE)) {
                if (initScriptResource != null) {
                    LOG.debug("Extracting init script to: " + initScriptFile);
                    byte[] initScriptBytes = initScriptResource.readAllBytes();
                    Files.write(initScriptFile, initScriptBytes);
                } else {
                    LOG.debug("Init script not found in classpath: " + INIT_SCRIPT_RESOURCE);
                }
            }

            // Set Quarkus compose devservices properties
            String composeFileAbsolutePath = composeFile.toAbsolutePath().toString();
            String projectName = config.projectName()
                    .filter(s -> !s.isEmpty())
                    .orElse("pipeline-shared-devservices");
            
            // Set the compose file path
            LOG.info("Pipeline Dev Services configured: " + composeFileAbsolutePath);
            LOG.info("To enable Compose Dev Services, add the following properties to your application.properties:");
            LOG.info("  %dev.quarkus.compose.devservices.enabled=true");
            LOG.info("  %dev.quarkus.compose.devservices.files=" + composeFileAbsolutePath);
            LOG.info("  %dev.quarkus.compose.devservices.project-name=" + projectName);
            LOG.info("  %dev.quarkus.compose.devservices.start-services=true");
            LOG.info("  %dev.quarkus.compose.devservices.stop-services=false");
            LOG.info("  %dev.quarkus.compose.devservices.reuse-project-for-tests=true");


        } catch (Exception e) {
            LOG.error("Failed to setup compose file", e);
            throw new RuntimeException("Failed to setup Pipeline Dev Services compose file", e);
        }

        return new FeatureBuildItem(FEATURE + "-compose");
    }

    private void extractComposeFile(Path composeFile, Path versionFile, byte[] resourceBytes, String resourceSha) 
            throws IOException {
        Files.write(composeFile, resourceBytes);
        writeVersionInfo(versionFile, resourceSha);
        LOG.info("Compose file extracted and version info written");
    }

    private void updateComposeFile(Path composeFile, Path versionFile, byte[] resourceBytes, String resourceSha,
                                   PipelineDevServicesConfig config) throws IOException {
        // Check if file was edited
        VersionInfo existingVersion = readVersionInfo(versionFile);
        if (existingVersion != null && Files.exists(composeFile)) {
            byte[] existingBytes = Files.readAllBytes(composeFile);
            String existingSha = calculateSHA(existingBytes);
            
            if (!existingVersion.sha().equals(existingSha)) {
                // File was edited, create backup
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Path backupFile = Paths.get(composeFile.toString() + ".backup." + timestamp);
                Files.copy(composeFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Created backup of edited compose file: " + backupFile);
            }
        }
        
        // Update the file
        Files.write(composeFile, resourceBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        writeVersionInfo(versionFile, resourceSha);
        LOG.info("Compose file updated");
    }

    private void validateYaml(Path composeFile) {
        try {
            // Simple validation - check if file is readable and has content
            if (!Files.exists(composeFile) || Files.size(composeFile) == 0) {
                throw new RuntimeException("Compose file is empty or doesn't exist");
            }
            
            // Basic YAML validation - check for key markers
            String content = Files.readString(composeFile);
            if (!content.contains("services:") && !content.contains("version:") && !content.contains("name:")) {
                LOG.warn("Compose file may not be valid YAML - missing expected markers");
            }
            
            LOG.debug("YAML validation passed");
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate compose file", e);
        }
    }

    private String calculateSHA(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA", e);
        }
    }

    private void writeVersionInfo(Path versionFile, String sha) throws IOException {
        String content = "sha=" + sha + "\n";
        Files.write(versionFile, content.getBytes());
    }

    private VersionInfo readVersionInfo(Path versionFile) {
        try {
            if (!Files.exists(versionFile)) {
                return null;
            }
            
            String content = Files.readString(versionFile);
            String sha = null;
            for (String line : content.split("\n")) {
                if (line.startsWith("sha=")) {
                    sha = line.substring(4).trim();
                    break;
                }
            }
            
            return sha != null ? new VersionInfo(sha) : null;
        } catch (IOException e) {
            LOG.warn("Failed to read version info", e);
            return null;
        }
    }

    private record VersionInfo(String sha) {
    }
}
