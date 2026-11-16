package com.quizsphere;

import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Entry point:  java -cp "out;lib\*" com.quizsphere.Main   (run from the project folder) */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.load();
        } catch (Config.ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }
        Path frontend = Path.of("frontend");
        if (!Files.isDirectory(frontend)) {
            System.err.println("Cannot find the 'frontend' folder. Run this command from the quizsphere project folder.");
            System.exit(1);
            return;
        }
        DatabaseManager db = new DatabaseManager(config);
        try {
            db.checkConnection();
        } catch (DatabaseException e) {
            System.err.println("Cannot start: " + e.getMessage());
            System.exit(1);
            return;
        }
        try {
            WebServer server = new WebServer(config, new Application(config, db), frontend);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            System.out.println("QuizSphere is running.  Open  http://" + config.serverHost() + ":" + config.serverPort() + "  in your browser.");
            System.out.println("Press Ctrl+C to stop.");
        } catch (BindException e) {
            System.err.println("Port " + config.serverPort() + " is already in use. Change SERVER_PORT in .env or stop the other program.");
            System.exit(1);
        } catch (java.io.IOException e) {
            System.err.println("Could not start the web server: " + e.getMessage());
            System.exit(1);
        }
    }
}
