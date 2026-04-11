package hue.captains.singapura.tao.http.rps2.remote;

/**
 * Launcher that avoids the "JavaFX runtime components are missing" error
 * when running from the classpath (non-modular). This class does NOT extend
 * {@code Application}, so the JVM doesn't check for the JavaFX module at startup.
 */
public class RpsGuiLauncher {
    public static void main(String[] args) {
        RpsGuiClient.main(args);
    }
}
