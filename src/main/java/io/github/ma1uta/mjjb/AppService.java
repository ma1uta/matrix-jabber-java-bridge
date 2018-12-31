package io.github.ma1uta.mjjb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.ma1uta.mjjb.config.AppConfig;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AppService.
 */
@CommandLine.Command(name = "java -jar mjjb.jar")
public class AppService {

    @CommandLine.Option(names = {"-f", "--file"}, description = "configuration file", defaultValue = "mjjb.yml")
    private String configFile = "mjjb.yml";

    @CommandLine.Option(names = {"-c", "--config"}, description = "configuration files location")
    private String configLocation;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    /**
     * Main entry point.
     *
     * @param args command line arguments.
     * @throws Exception when cannot run the bridge.
     */
    public static void main(String[] args) throws Exception {
        CommandLine.populateCommand(new AppService(), args).run();
    }

    /**
     * Run AppService.
     *
     * @throws Exception when cannot run the bridge.
     */
    public void run() throws Exception {
        if (helpRequested) {
            CommandLine.usage(this, System.out);
            return;
        }

        Path configPath;
        if (configLocation != null) {
            configPath = Paths.get(configLocation, configFile);
        } else {
            configPath = Paths.get(configFile);
        }

        ObjectMapper configMapper = new ObjectMapper(new YAMLFactory());
        configMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AppConfig appConfig = configMapper.readValue(configPath.toFile(), AppConfig.class);

        new Bridge().run(appConfig);
    }
}
