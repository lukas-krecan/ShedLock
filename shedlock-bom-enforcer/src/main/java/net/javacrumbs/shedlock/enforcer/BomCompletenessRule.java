package net.javacrumbs.shedlock.enforcer;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;

/**
 * Custom Maven Enforcer Rule to validate that all modules from the parent POM
 * are included in the BOM's dependencyManagement section.
 */
@Named("bomCompleteness")
public class BomCompletenessRule extends AbstractEnforcerRule {

    private final MavenSession session;

    @Inject
    public BomCompletenessRule(MavenProject project, MavenSession session, RuntimeInformation runtimeInformation) {
        this.session = Objects.requireNonNull(session);
        Objects.requireNonNull(runtimeInformation);
    }

    @Override
    public void execute() throws EnforcerRuleException {
        try {
            // Use injected MavenProject to get the correct paths
            File projectBasedir = new File(session.getExecutionRootDirectory());

            File parentPomFile = new File(projectBasedir, "pom.xml");
            File bomPomFile = new File(projectBasedir, "shedlock-bom/pom.xml");

            if (!parentPomFile.exists()) {
                throw new EnforcerRuleException("Parent POM not found at: " + parentPomFile.getAbsolutePath());
            }

            getLog().info("🔍 Verifying BOM completeness using native Maven APIs...");

            // Get modules from parent project
            Set<String> parentModules = getParentModules(parentPomFile);
            getLog().info("📁 Found " + parentModules.size()
                    + " modules in parent project (excluding tests and support)");

            // Get artifacts from current BOM project
            Set<String> bomArtifacts = getBomArtifacts(bomPomFile);
            getLog().info("📦 Found " + bomArtifacts.size() + " artifacts in BOM");

            // Find missing modules
            Set<String> missingModules = new HashSet<>(parentModules);
            missingModules.removeAll(bomArtifacts);

            // Find extra artifacts (optional warning)
            Set<String> extraArtifacts = new HashSet<>(bomArtifacts);
            extraArtifacts.removeAll(parentModules);

            // Log detailed information
            if (getLog().isDebugEnabled()) {
                getLog().debug("Parent modules: " + parentModules);
                getLog().debug("BOM artifacts: " + bomArtifacts);
            }

            // Report results
            if (!missingModules.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("❌ BOM validation failed: The following modules are missing from the BOM:\n");
                for (String module : missingModules) {
                    errorMessage.append("  - ").append(module).append("\n");
                }
                throw new EnforcerRuleException(errorMessage.toString());
            }

            if (!extraArtifacts.isEmpty()) {
                getLog().warn("⚠️  The following artifacts in BOM don't correspond to modules:");
                for (String artifact : extraArtifacts) {
                    getLog().warn("  - " + artifact);
                }
            }

            getLog().info("✅ BOM verification passed: All modules are properly included in the BOM");

        } catch (Exception e) {
            if (e instanceof EnforcerRuleException) {
                throw (EnforcerRuleException) e;
            }
            throw new EnforcerRuleException("BOM validation failed: " + e.getMessage(), e);
        }
    }

    private Set<String> getParentModules(File parentPomFile) throws Exception {
        // Read parent POM to get modules
        MavenXpp3Reader reader = new MavenXpp3Reader();

        try (FileReader fileReader = new FileReader(parentPomFile)) {
            Model parentModel = reader.read(fileReader);

            return parentModel.getModules().stream()
                    .filter(module -> !module.contains("test/")
                            && !module.contains("shedlock-test-support")
                            && !module.equals("shedlock-bom")
                            && !module.equals("shedlock-bom-enforcer"))
                    .map(module -> module.contains("/") ? module.substring(module.lastIndexOf('/') + 1) : module)
                    .collect(Collectors.toSet());
        }
    }

    private Set<String> getBomArtifacts(File bomPomFile) throws Exception {
        // Read BOM POM to get dependency management artifacts
        MavenXpp3Reader reader = new MavenXpp3Reader();

        try (FileReader fileReader = new FileReader(bomPomFile)) {
            Model bomModel = reader.read(fileReader);

            if (bomModel.getDependencyManagement() == null
                    || bomModel.getDependencyManagement().getDependencies() == null) {
                return Collections.emptySet();
            }

            return bomModel.getDependencyManagement().getDependencies().stream()
                    .filter(dep -> "net.javacrumbs.shedlock".equals(dep.getGroupId()))
                    .map(Dependency::getArtifactId)
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public String toString() {
        return "BomCompletenessRule[]";
    }
}
