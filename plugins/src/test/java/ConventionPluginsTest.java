import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConventionPluginsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path fixture(String settings, String build) throws IOException {
        Path root = temporary.newFolder().toPath();
        write(root, "settings.gradle", settings);
        write(root, "build.gradle", build);
        write(root, "gradle/libs.versions.toml", Files.readString(Path.of(System.getProperty("buildConfig.catalogFile"))));
        // RFG downloads the version index during plugin application. Configuration-only
        // tests seed an isolated index and never execute Minecraft download/decompile tasks.
        write(root, "offline.init.gradle", """
                gradle.beforeProject {
                    def index = new File(gradle.gradleUserHomeDir, 'caches/retro_futura_gradle/mc-vanilla/all_versions_manifest.json')
                    if (!index.exists()) { index.parentFile.mkdirs(); index.text = '{"versions":[]}' }
                }
                """);
        return root;
    }

    private void write(Path root, String file, String content) throws IOException {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private BuildResult run(Path root, String... arguments) {
        ArrayList<String> args = new ArrayList<>(Arrays.asList(arguments));
        args.addAll(Arrays.asList("--stacktrace", "--console=plain", "--offline", "--init-script", root.resolve("offline.init.gradle").toString()));
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("VERSION", "1.2.3");
        env.remove("RELEASE_VERSION");
        env.remove("ARTIFACT_GROUP_ID");
        env.remove("ARTIFACT_ID");
        env.put("IMPACT_MAVEN_USER", "impact-test-user");
        env.put("IMPACT_MAVEN_PASSWORD", "impact-test-password");
        env.put("MAVEN_USER", "ignored-old-user");
        env.put("MAVEN_PASSWORD", "ignored-old-password");
        return GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withEnvironment(env).withArguments(args).forwardOutput().build();
    }

    @Test public void librariesCompileWithIndependentTargetsAndPublishSources() throws Exception {
        Path root = fixture("rootProject.name='fixture'\ninclude 'api', 'consumer'", "");
        write(root, "api/build.gradle", """
                plugins { id 'publish-setup' }
                impactMod { modGroup.set('space.test'); javaRelease.set(8) }
                tasks.register('verifyCompiler') {
                    doLast {
                        assert !java.toolchain.languageVersion.present
                        assert !plugins.hasPlugin('org.jetbrains.kotlin.jvm')
                        assert !plugins.hasPlugin('com.gtnewhorizons.retrofuturagradle')
                        assert compileJava.javaCompiler.get().metadata.languageVersion.asInt() >= 17
                        assert version == '1.2.3'
                    }
                }
                """);
        write(root, "consumer/build.gradle", """
                plugins { id 'publish-setup' }
                impactMod { modGroup.set('space.test'); javaRelease.set(17) }
                dependencies { implementation project(':api') }
                """);
        write(root, "api/src/main/java/example/Api.java", "package example; public class Api { public static int value() { return 1; } }");
        write(root, "consumer/src/main/java/example/Consumer.java", "package example; public record Consumer(int value) { public static Consumer make() { return new Consumer(Api.value()); } }");
        run(root, ":consumer:build", ":api:verifyCompiler", ":api:generatePomFileForMavenPublication", ":consumer:generatePomFileForMavenPublication");
        byte[] api = Files.readAllBytes(root.resolve("api/build/classes/java/main/example/Api.class"));
        byte[] consumer = Files.readAllBytes(root.resolve("consumer/build/classes/java/main/example/Consumer.class"));
        assertEquals(52, api[7] & 255);
        assertEquals(61, consumer[7] & 255);
        assertTrue(Files.exists(root.resolve("consumer/build/libs/consumer-1.2.3-sources.jar")));
        String pom = Files.readString(root.resolve("consumer/build/publications/maven/pom-default.xml"));
        assertTrue(pom.contains("<artifactId>api</artifactId>"));
        assertTrue(pom.contains("<groupId>space.test</groupId>"));
    }

    @Test public void minecraftKeepsLegacyMetadataWithoutGlobalToolchains() throws Exception {
        Path root = fixture("rootProject.name='legacy'", """
                plugins { id 'minecraft-setup'; id 'publish-setup' }
                tasks.register('verify') {
                    doLast {
                        assert !plugins.hasPlugin('com.gtnewhorizons.gtnhconvention')
                        assert !java.toolchain.languageVersion.present
                        assert java.toolchain.vendor.get().toString() in ['any', 'any vendor']
                        assert compileJava.options.release.get() == 8
                        assert compileKotlin.compilerOptions.jvmTarget.get().target == '1.8'
                        assert !compilePatchedMcKotlin.enabled
                        assert jar.manifest.attributes.FMLCorePlugin == 'space.test.loader.Plugin'
                        assert jar.manifest.attributes.FMLAT == 'legacy_at.cfg'
                        assert mcpTasks.deobfuscationATs.files*.name.contains('legacy_at.cfg')
                        assert jar.archiveClassifier.get() == 'dev'
                        assert publishing.publications.maven.groupId == 'space.publish'
                        assert publishing.publications.maven.version == '1.2.3'
                        assert publishing.repositories.size() == 1
                        assert publishing.repositories.impact.credentials.username == 'impact-test-user'
                        assert publishing.repositories.impact.credentials.password == 'impact-test-password'
                        assert !configurations.runtimeElements.allDependencies.any { it.name == 'dev-only' }
                        assert configurations.runtimeClasspath.allDependencies.any { it.name == 'dev-only' }
                    }
                }
                dependencies { runtimeOnlyNonPublishable 'example:dev-only:1.0' }
                """);
        write(root, "gradle.properties", "modId=legacy\nmodGroup=space.test\nmavenGroupId=space.publish\ncoreModClass=loader.Plugin\nmavenPublishUrl=https://example.invalid/releases\n");
        write(root, "src/main/resources/META-INF/legacy_at.cfg", "public net.minecraft.block.Block field_149781_w\n");
        write(root, "src/main/resources/mcmod.info", "[{\"modid\":\"${modId}\",\"version\":\"${modVersion}\"}]");
        run(root, "verify", "processResources", "generatePomFileForMavenPublication");
        assertTrue(Files.readString(root.resolve("build/resources/main/mcmod.info")).contains("1.2.3"));
        assertFalse(Files.readString(root.resolve("build/publications/maven/pom-default.xml")).contains("dev-only"));
    }

    @Test public void publicationNamesLeaveRfgDevelopmentArchivesDistinct() throws Exception {
        for (String plugins : new String[] {
                "id 'minecraft-setup'; id 'publish-setup'",
                "id 'publish-setup'; id 'minecraft-setup'"
        }) {
            Path root = fixture("rootProject.name='dev-fixture'", "plugins { " + plugins + " }\n" + """
                    impactMod { artifactId.set('published-mod'); archiveName.set('legacy-mod') }
                    tasks.register('toolsJar', Jar) {
                        archiveBaseName.set('internal-tools')
                        archiveVersion.set('42')
                    }
                    tasks.register('verify') {
                        doLast {
                            assert packageMcLauncher.archiveFileName.get() == 'mclauncher-1.7.10.jar'
                            assert packagePatchedMc.archiveFileName.get() == 'recompiled_minecraft-1.7.10.jar'
                            assert packageMcLauncher.archiveFile.get().asFile != packagePatchedMc.archiveFile.get().asFile
                            assert reobfJar.recompMcJar.get().asFile == packagePatchedMc.archiveFile.get().asFile
                            assert toolsJar.archiveFileName.get() == 'internal-tools-42.jar'
                            assert jar.archiveFileName.get() == 'published-mod-1.2.3-dev.jar'
                            assert shadowJar.archiveFileName.get() == 'published-mod-1.2.3-dev-shadow.jar'
                            assert reobfJar.archiveFileName.get() == 'published-mod-1.2.3.jar'
                            assert sourcesJar.archiveFileName.get() == 'published-mod-1.2.3-sources.jar'
                            assert publishing.publications.maven.artifactId == 'published-mod'
                            assert publishing.publications.maven.version == '1.2.3'
                        }
                    }
                    """);
            run(root, "verify");
        }
    }

    @Test public void mixinsRegisterRefmapBeforeRfgFinalizesConfiguration() throws Exception {
        Path root = fixture("rootProject.name='mixins'", """
                plugins { id 'minecraft-setup' }
                impactMod {
                    modId.set('mixins'); modGroup.set('space.mixins')
                    usesMixins.set(true); coreModClass.set('loader.Early')
                    mixinConfigs.set(['mixins.mixins.json', 'mixins.mixins.early.json'])
                }
                tasks.register('verify') {
                    doLast {
                        assert modUtils.mixinRefMap.get() == 'mixins.mixins.refmap.json'
                        assert compileJava.options.compilerArgumentProviders.any {
                            it instanceof com.gtnewhorizons.retrofuturagradle.modutils.ModUtils.SrgCommandLineArgs &&
                                it.outRefMapFile.get().asFile.name == 'mixins.mixins.refmap.json'
                        }
                        assert configurations.annotationProcessor.allDependencies.any {
                            it.name == 'unimixins' && it.version == '0.3.2' && it.artifacts.any { it.classifier == 'dev' }
                        }
                        assert configurations.annotationProcessor.allDependencies.any { it.name == 'asm-debug-all' && it.version == '5.1' }
                        assert jar.manifest.attributes.MixinConfigs == 'mixins.mixins.json,mixins.mixins.early.json'
                        assert jar.manifest.attributes.FMLCorePlugin == 'space.mixins.loader.Early'
                    }
                }
                """);
        write(root, "src/main/resources/mixins.mixins.json", "{\"package\":\"space.mixins\",\"refmap\":\"mixins.mixins.refmap.json\"}");
        write(root, "src/main/resources/mixins.mixins.early.json", "{\"package\":\"space.mixins.early\",\"refmap\":\"mixins.mixins.refmap.json\"}");
        Path catalog = root.resolve("gradle/libs.versions.toml");
        Files.writeString(catalog, Files.readString(catalog)
                .replace("unimixins = \"0.3.1\"", "unimixins = \"0.3.2\"")
                .replace("mixin-asm = \"5.0.4\"", "mixin-asm = \"5.1\""));
        run(root, "verify");
    }

    @Test public void shadowFeedsReobfAndCarriesCoremodManifest() throws Exception {
        Path root = fixture("rootProject.name='forgelin'", """
                plugins { id 'minecraft-setup'; id 'publish-setup' }
                impactMod {
                    modGroup.set('net.shadowfacts.forgelin')
                    coreModClass.set('preloader.ForgelinPlugin')
                    minimizeShadedDependencies.set(false)
                    relocateShadedDependencies.set(true)
                }
                dependencies { shadowImplementation files('bundled.jar') }
                tasks.register('verify') {
                    doLast {
                        assert reobfJar.inputJar.get().asFile == shadowJar.archiveFile.get().asFile
                        assert configurations.apiElements.outgoing.artifacts.size() == 1
                        assert configurations.apiElements.outgoing.artifacts.iterator().next().file == shadowJar.archiveFile.get().asFile
                        assert shadowJar.manifest.effectiveManifest.attributes.FMLCorePlugin == 'net.shadowfacts.forgelin.preloader.ForgelinPlugin'
                        assert jar.archiveClassifier.get() == 'dev-preshadow'
                        assert shadowJar.archiveClassifier.get() == 'dev'
                        assert shadowJar.enableAutoRelocation.get()
                        assert shadowJar.relocationPrefix.get() == 'net.shadowfacts.forgelin.shadow'
                    }
                }
                """);
        run(root, "verify");
    }

    @Test public void settingsDoNotApplyFoojayOrGtnhToEveryProject() throws Exception {
        Path root = fixture("""
                plugins { id 'settings-setup' }
                assert !plugins.hasPlugin('org.gradle.toolchains.foojay-resolver-convention')
                assert !plugins.hasPlugin('com.gtnewhorizons.gtnhsettingsconvention')
                assert dependencyResolutionManagement.repositories.find { it.name == 'Modrinth' }.url.toString() == 'https://api.modrinth.com/maven'
                rootProject.name='settings-fixture'
                """, "tasks.register('verify') { doLast { assert !plugins.hasPlugin('java'); assert !plugins.hasPlugin('org.jetbrains.kotlin.jvm'); assert !plugins.hasPlugin('com.gtnewhorizons.gtnhconvention') } }");
        run(root, "verify");
    }

    @Test public void siblingModsResolveSrgProjectArtifactsWithoutMavenPublication() throws Exception {
        Path root = fixture("rootProject.name='multi'\ninclude 'api', 'mod', 'shared'", "");
        write(root, "gradle.properties", "kotlin.stdlib.default.dependency=false\norg.gradle.configureondemand=true\n");
        String setup = """
                plugins { id 'minecraft-setup'; id 'publish-setup' }
                impactMod { modGroup.set('space.local') }
                minecraft { applyMcDependencies.set(false) }
                """;
        write(root, "api/build.gradle", setup + "impactMod { modId.set('api_mod') }\n");
        write(root, "shared/build.gradle", "plugins { id 'jvm-setup' }; group='space.shared'\n");
        write(root, "mod/build.gradle", setup + """
                dependencies { implementation project(':api'); implementation project(':shared') }
                tasks.register('verify') {
                    doLast {
                        def result = configurations.reobfJarConfiguration.incoming.resolutionResult
                        assert result.allComponents.any { it.id instanceof org.gradle.api.artifacts.component.ProjectComponentIdentifier && it.id.projectPath == ':api' }
                        assert !result.allDependencies.any { it instanceof org.gradle.api.artifacts.result.UnresolvedDependencyResult }
                        assert configurations.reobfJarConfiguration.incoming.artifacts.artifacts.any {
                            it.id.componentIdentifier instanceof org.gradle.api.artifacts.component.ProjectComponentIdentifier && it.file.name == 'api-1.2.3.jar'
                        }
                        assert configurations.reobfJarConfiguration.incoming.artifacts.artifacts.any { it.file.name == 'shared-1.2.3.jar' }
                        assert configurations.obfuscatedRuntimeClasspath.incoming.artifacts.artifacts.any { it.file.name == 'api-1.2.3.jar' }
                        assert configurations.obfuscatedRuntimeClasspath.incoming.artifacts.artifacts.any { it.file.name == 'shared-1.2.3.jar' }
                    }
                }
                """);
        run(root, ":mod:verify");
    }
}
