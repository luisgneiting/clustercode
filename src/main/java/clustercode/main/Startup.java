package clustercode.main;

import clustercode.database.CouchDbVerticle;
import clustercode.healthcheck.HealthCheckVerticle;
import clustercode.impl.util.PackagingUtil;
import clustercode.main.config.AnnotatedCli;
import clustercode.main.config.Configuration;
import clustercode.main.config.converter.LogFormat;
import clustercode.main.config.converter.LogLevel;
import clustercode.messaging.RabbitMqVerticle;
import clustercode.scheduling.SchedulingVerticle;
import clustercode.transcoding.TranscodingVerticle;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

public class Startup {

    private static Logger log;

    public static void main(String[] args) throws Exception {
        var cli = CLI.create(AnnotatedCli.class);
        var flags = new AnnotatedCli();
        Path configPath = null;
        try {
            var parsed = cli.parse(Arrays.asList(args), true);
            CLIConfigurator.inject(parsed, flags);
            if (!parsed.isValid() || flags.isHelp()) {
                printUsageAndExit(cli);
            }

            System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

            configPath = flags.getConfigFile();

            // override default log properties from ENV if given.
            trySetPropertyFromEnv("CC_LOG_LEVEL", "log.level", cli, LogLevel::valueOf);
            trySetPropertyFromEnv("CC_LOG_FORMAT", "log.format", cli, LogFormat::valueOf);
            trySetPropertyFromEnv("CC_LOG_CONFIG", "log4j.configurationFile");

            // override Log ENVs from CLI if given.
            if (flags.getLogLevel() != null) System.setProperty("log.level", flags.getLogLevel().name());
            if (flags.getLogFormat() != null) System.setProperty("log.format", flags.getLogFormat().name());
            if (flags.getLogConfig() != null) System.setProperty("log4j.configurationFile", flags
                .getLogConfig()
                .toAbsolutePath()
                .toString());

        } catch (CLIException ex) {
            System.err.println(ex.getMessage());
            printUsageAndExit(cli);
        }

        log = LoggerFactory.getLogger(Startup.class);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Normally the expectable exceptions should be caught, but to debug any unexpected ones we log them.
            log.error("Application-wide uncaught exception:", throwable);
            System.exit(2);
        });

        MDC.put("version", getApplicationVersion().orElse("unknown"));
        MDC.put("work_dir", new File("").getAbsolutePath());
        log.info("Starting clustercode.");
        MDC.remove("work_dir");
        MDC.remove("version");

        var props = loadPropertiesFromFile(configPath);

        ConfigRetriever retriever = ConfigRetriever.create(Vertx.vertx(),
            new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                    .setType("json")
                    .setFormat("json")
                    .setConfig(Configuration.createFromDefault())
                )
                .addStore(new ConfigStoreOptions()
                    .setType("json")
                    .setFormat("json")
                    .setConfig(Configuration.createFromProperties(props)))
                .addStore(new ConfigStoreOptions()
                    .setType("json")
                    .setFormat("json")
                    .setConfig(Configuration.createFromEnvMap(System.getenv())))
                .addStore(new ConfigStoreOptions()
                    .setType("json")
                    .setFormat("json")
                    .setConfig(Configuration.createFromFlags(flags)))
        );

        retriever.getConfig(json -> {
            var config = json.result();
            var v = Vertx.vertx(new VertxOptions().setMetricsOptions(
                new MicrometerMetricsOptions()
                    .setPrometheusOptions(new VertxPrometheusOptions()
                        .setEnabled(config.getBoolean(Configuration.prometheus_enabled.key()))
                        .setPublishQuantiles(config.getBoolean(Configuration.prometheus_publishQuantiles.key()))
                    )
                    .setEnabled(true)
                )
            );
            var router = Router.router(v);

            v.exceptionHandler(ex -> log.error("Unhandled Vertx exception:", ex));
            var healthCheckVerticle = new HealthCheckVerticle(router);
            v.deployVerticle(
                new HttpVerticle(router),
                new DeploymentOptions().setConfig(config));
            v.deployVerticle(
                healthCheckVerticle,
                new DeploymentOptions().setConfig(config));
            v.deployVerticle(
                new CouchDbVerticle()
                    .withLivenessChecks(healthCheckVerticle::registerLivenesschecks)
                    .withReadinessChecks(healthCheckVerticle::registerReadinessChecks),
                new DeploymentOptions().setConfig(config));
            v.deployVerticle(
                new RabbitMqVerticle()
                    .withLivenessChecks(healthCheckVerticle::registerLivenesschecks)
                    .withReadinessChecks(healthCheckVerticle::registerReadinessChecks),
                new DeploymentOptions().setConfig(config));
            v.deployVerticle(
                new TranscodingVerticle(),
                new DeploymentOptions().setConfig(config));
            v.deployVerticle(
                new SchedulingVerticle(),
                new DeploymentOptions().setConfig(config));
        });

    }

    private static void trySetPropertyFromEnv(
        String key,
        String prop) {
        var value = System.getenv(key);
        if (value != null) System.setProperty(prop, value);
    }

    private static <T extends Enum> void trySetPropertyFromEnv(
        String key,
        String prop,
        CLI cli,
        Function<String, T> enumSupplier) {
        var value = System.getenv(key);
        try {
            if (value != null) System.setProperty(prop, enumSupplier.apply(value).name());
        } catch (IllegalArgumentException ex) {
            System.err.println(String.format("The value '%1$s' is not accepted by '%2$s'", value, prop));
            printUsageAndExit(cli);
        }
    }

    private static Properties loadPropertiesFromFile(Path path) {
        var props = new Properties();
        if (path == null) return props;
        try (var input = new FileReader(path.toFile())) {
            MDC.put("path", path.toString());
            log.info("Reading config file.");
            props.load(input);
            return props;
        } catch (IOException | NullPointerException ex) {
            MDC.put("error", ex.toString());
            MDC.put("help", "Ignoring config file.");
            log.warn("Could not read config file.");
            return props;
        } finally {
            MDC.remove("error");
            MDC.remove("path");
            MDC.remove("help");
        }
    }

    private static void printUsageAndExit(CLI cli) {
        StringBuilder builder = new StringBuilder();
        cli.usage(builder);
        System.out.print(builder.toString());
        System.exit(1);
    }

    private static Optional<String> getApplicationVersion() {
        return PackagingUtil.getManifestAttribute("Implementation-Version");
    }

}