/**
 * This file is part of Miroa Updater.
 * Copyright (C) 2016 David Cachau <dev@safranil.fr>
 *
 * Miroa Updater is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Miroa Updater is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Miroa Updater.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.safranil.minecraft.miroa;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;

public class Updater extends Application {

    private UpdaterController controller;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(this.getClass().getResource("updater.fxml"));
        AnchorPane root = loader.load();
        primaryStage.setTitle("Miroa");
        primaryStage.setScene(new Scene(root, 490, 90));
        primaryStage.show();
        primaryStage.setMinHeight(primaryStage.getHeight());
        primaryStage.setOnCloseRequest(event -> System.exit(0));
        primaryStage.setMinWidth(primaryStage.getWidth());

        controller = loader.getController();
        controller.progress.setProgress(-1);

        Thread t = new Thread(() -> {
            File launcher = new File(getWorkingDir(), "launcher.jar");
            String lHash = "", rHash = "";

            try {
                if (launcher.exists()) {
                    lHash = getLocalChecksum(launcher);
                }
            } catch (IOException e) {
                displayExceptionAndExit("Erreur launcher local",
                        "Une erreur est survenue lors de la préparation de la mise à jour.",
                        "Impossible de calculer la version local",
                        e);
            }

            try {
                rHash = getRemoteChecksum();
            } catch (IOException e) {
                displayExceptionAndExit("Erreur launcher",
                        "Une erreur est survenue lors de la préparation de la mise à jour.",
                        "Impossible de récupérer la version de la mise à jour du launcher",
                        e);
            }

            assert lHash != null : "Hash is empty, but you can't reach this code if is null !";
            if (!lHash.equals(rHash)) {
                System.out.println("Starting updating...");
                try {
                    downloadJar(launcher);
                } catch (Exception e) {
                    displayExceptionAndExit("Erreur de mise à jour",
                            "Une erreur est survenue lors de la mise à jour.",
                            "Impossible de récupérer la nouvelle du launcher",
                            e);
                }
            }

            ProcessBuilder pb = new ProcessBuilder();
            pb.command("java", "-jar", "launcher.jar");
            pb.directory(getWorkingDir());

            File logFile = new File(getWorkingDir(), "launcher.log");
            pb.redirectError(logFile);
            pb.redirectOutput(logFile);

            try {
                Process p = pb.start();

                PlatformImpl.runLater(primaryStage::hide);

                p.waitFor();
            } catch (IOException e) {
                displayExceptionAndExit("Erreur launcher",
                        "Une erreur est survenue lors du lancement du launcher.",
                        "Impossible de lancer correctement le lancher.",
                        e);
            } catch (InterruptedException ignored) {
            }

            PlatformImpl.exit();
        });
        t.start();
    }

    /**
     * Get the miroa working directory
     *
     * @return File directory
     */
    private File getWorkingDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String appData = System.getenv("APPDATA");
        String userHome = System.getProperty("user.home");

        if (os.contains("win") && appData != null) {
            return new File(appData, ".miroa");
        } else if (os.contains("mac")) {
            return new File(userHome, "Library/Application Support/.miroa");
        } else {
            return new File(userHome, ".miroa");
        }
    }

    /**
     * Get the next launcher version
     *
     * @return The next launcher version
     */
    private String getRemoteChecksum() throws IOException {
        URL url = new URL("http://static.safranil.fr/minecraft/launcher.php");
        URLConnection httpURLConnection = url.openConnection();
        InputStream inputStream = httpURLConnection.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = bufferedReader.readLine();
        StringBuilder response = new StringBuilder();

        // Only read the first line containing the hash
        if (line != null) {
            response = response.append(line);
        }
        inputStream.close();
        return response.toString();
    }

    private String getLocalChecksum(File launcher) throws IOException {
        DigestInputStream digestInputStream = null;
        try {
            digestInputStream = new DigestInputStream(new FileInputStream(launcher), MessageDigest.getInstance("SHA-1"));
            byte[] buffer = new byte[65536];

            int readed = digestInputStream.read(buffer);
            while (readed >= 1)
                readed = digestInputStream.read(buffer);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (digestInputStream != null) {
                digestInputStream.close();
            }
        }

        return String.format("%1$032x", new BigInteger(1, digestInputStream.getMessageDigest().digest()));
    }

    private void downloadJar(File destination) throws Exception {
        File parentFile = new File(destination.getParent());
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw new Exception("Unable to create launcher folder");
            }
        }
        if (destination.exists() && !destination.delete()) {
            throw new Exception("Couldn't delete previous launcher");
        }
        if (!destination.createNewFile()) {
            throw new Exception("Couldn't create the new launcher JAR");
        }

        URL url = new URL("http://static.safranil.fr/minecraft/launcher.jar");
        URLConnection connection = url.openConnection();

        BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destination));

        final int len = connection.getContentLength();

        int seek = 0, readed;
        byte[] buffer;

        if (len > 0) {
            while (seek < len) {
                buffer = new byte[8192];
                readed = inputStream.read(buffer);
                if (readed > 0)
                    outputStream.write(buffer, 0, readed);
                seek += readed;
                int finalSeek = seek;
                PlatformImpl.runAndWait(() -> controller.progress.setProgress(finalSeek/len));
            }
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    /**
     * Show a dialog with an exception inside
     *
     * @param title     title of the dialog
     * @param header    Header part
     * @param content   content part
     * @param throwable exception to display
     */
    private void displayExceptionAndExit(String title, String header, String content, Throwable throwable) {
        throwable.printStackTrace();

        PlatformImpl.runAndWait(() -> {
            // Create the alert
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content +
                    "\n\nVous pouvez reporter cette erreur par mail à dev@safranil.fr avec le contenu de la boite ci-dessous (Afficher les détails pour voir la boite).");

            // Create the exception area
            TextArea textArea = new TextArea(ExceptionUtils.getStackTrace(throwable));
            textArea.setEditable(false);
            textArea.setWrapText(false);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            textArea.setStyle("-fx-font-size: 11px");
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            // Add the exception area to the alert
            GridPane pane = new GridPane();
            pane.setMaxWidth(Double.MAX_VALUE);
            pane.add(textArea, 0, 0);
            alert.getDialogPane().setExpandableContent(pane);
            alert.getDialogPane().setMinWidth(650);

            // And finally, show the alert
            alert.showAndWait();

            PlatformImpl.exit();
        });
    }
}
