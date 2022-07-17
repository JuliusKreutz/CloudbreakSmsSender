package dev.kreutz.cloudbreaksmssenderlauncher;

import com.formdev.flatlaf.FlatLightLaf;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Launcher {

    public static void main(String[] args) {
        String localVersion = getLocalVersion();
        String remoteVersion = getRemoteVersion();

        if (localVersion.equals(remoteVersion)) {
            execute();
            return;
        }

        FlatLightLaf.setup();
        int result = JOptionPane.showConfirmDialog(null, "Do you want to update?", "Update available", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            update(remoteVersion);
        } else {
            execute();
        }
    }

    private static String getLocalVersion() {
        try {
            return Files.readAllLines(Paths.get("version.txt")).get(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getRemoteVersion() {
        try {
            URL url = new URL("https://api.github.com/repos/JuliusKreutz/CloudbreakSmsSender/releases/latest");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));

            String response = reader.readLine();
            JSONObject object = new JSONObject(response);

            return object.getString("tag_name");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void execute() {
        try {
            Runtime.getRuntime().exec("CloudbreakSmsSender.exe");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void update(String remoteVersion) {
        try {
            String exeName = "CloudbreakSmsSenderInstaller.exe";
            URL url = new URL("https://github.com/JuliusKreutz/CloudbreakSmsSender/releases/download/" + remoteVersion + "/" + exeName);
            Path path = Paths.get(System.getProperty("java.io.tmpdir")).resolve(exeName);
            Files.copy(url.openStream(), path, StandardCopyOption.REPLACE_EXISTING);
            Runtime.getRuntime().exec(String.valueOf(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
