package com.cryptwolf.cryptwolf;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;

public class PrimaryController {

    private static final int ITERATION_COUNT = 65536; // Iteration count

    private File sourceDirectory;
    private File destinationDirectory;
    private SecretKey secretKey;
    private boolean isEncryptMode = true;
    private double xOffset = 0;
    private double yOffset = 0;
    private byte[] keyBytes; // To store the generated key bytes

    @FXML
    private ToggleButton toggleButton;

    @FXML
    private Button actionButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private ChoiceBox<String> keyLengthChoiceBox;

    @FXML
    private AnchorPane draggableRegion;

    @FXML
    private void handleCloseButtonAction(ActionEvent event) {
        clearSensitiveData();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleSelectSourceFolder(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Source Folder");
        sourceDirectory = directoryChooser.showDialog(((Node) event.getSource()).getScene().getWindow());

        if (sourceDirectory != null) {
            showAlert("Source Folder Selected", sourceDirectory.getAbsolutePath());
        } else {
            showAlert("No Source Folder Selected", "Please select a source folder.");
        }
    }

    @FXML
    private void handleSelectDestinationFolder(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Destination Folder");
        destinationDirectory = directoryChooser.showDialog(((Node) event.getSource()).getScene().getWindow());

        if (destinationDirectory != null) {
            showAlert("Destination Folder Selected", destinationDirectory.getAbsolutePath());
        } else {
            showAlert("No Destination Folder Selected", "Please select a destination folder.");
        }
    }

    @FXML
    private void handleEncryptAndMoveFiles(ActionEvent event) {
        if (sourceDirectory != null && destinationDirectory != null) {
            String keyLengthStr = keyLengthChoiceBox.getValue();
            if (keyLengthStr == null) {
                showAlert("Error", "Please select a key length.");
                return;
            }

            int keyLength = 256;
            if (keyLengthStr.equals("128 bits")) {
                keyLength = 128;
            } else if (keyLengthStr.equals("192 bits")) {
                keyLength = 192;
            }

            try {
                if (isEncryptMode) {
                    secretKey = generateKey(keyLength);
                    keyBytes = secretKey.getEncoded();
                } else {
                    if (keyBytes == null) {
                        showAlert("Error", "No key found for decryption.");
                        return;
                    }
                    secretKey = new SecretKeySpec(keyBytes, "AES");
                }

                Task<Void> task = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        try {
                            List<Path> files = Files.walk(sourceDirectory.toPath())
                                    .filter(Files::isRegularFile)
                                    .toList();

                            int totalFiles = files.size();
                            for (int i = 0; i < totalFiles; i++) {
                                Path file = files.get(i);
                                Path destFile = destinationDirectory.toPath().resolve(sourceDirectory.toPath().relativize(file));
                                Files.createDirectories(destFile.getParent());
                                processFile(file, destFile, isEncryptMode ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE);
                                updateProgress(i + 1, totalFiles);
                            }

                            Platform.runLater(() -> showAlert("Success", "Files processed successfully."));
                        } catch (Exception e) {
                            e.printStackTrace();
                            Platform.runLater(() -> showAlert("Error", "An error occurred: " + e.getMessage()));
                        }
                        return null;
                    }
                };

                progressBar.progressProperty().bind(task.progressProperty());
                new Thread(task).start();
            } catch (Exception e) {
                showAlert("Error", "Failed to generate key: " + e.getMessage());
            }
        } else {
            showAlert("Error", "Please select both source and destination folders.");
        }
    }

    @FXML
    private void toggleMode(ActionEvent event) {
        isEncryptMode = !isEncryptMode;
        actionButton.setText(isEncryptMode ? "Encrypt" : "Decrypt");
    }

    private SecretKey generateKey(int keyLength) throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(keyLength, new SecureRandom());
        return keyGen.generateKey();
    }

    private void processFiles(Path sourcePath, Path destinationPath, int cipherMode) throws Exception {
        Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Path destFile = destinationPath.resolve(sourcePath.relativize(file));
                        Files.createDirectories(destFile.getParent());
                        processFile(file, destFile, cipherMode);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    private void processFile(Path sourceFile, Path destFile, int cipherMode) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(cipherMode, secretKey);

        try (FileInputStream fis = new FileInputStream(sourceFile.toFile());
             FileOutputStream fos = new FileOutputStream(destFile.toFile());
             CipherInputStream cis = (cipherMode == Cipher.DECRYPT_MODE) ? new CipherInputStream(fis, cipher) : null;
             CipherOutputStream cos = (cipherMode == Cipher.ENCRYPT_MODE) ? new CipherOutputStream(fos, cipher) : null) {

            byte[] buffer = new byte[4096];
            int bytesRead;

            if (cipherMode == Cipher.ENCRYPT_MODE) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    cos.write(buffer, 0, bytesRead);
                }
            } else {
                while ((bytesRead = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    public void clearSensitiveData() {
        if (secretKey != null) {
            byte[] keyBytes = secretKey.getEncoded();
            for (int i = 0; i < keyBytes.length; i++) {
                keyBytes[i] = 0;
            }
            secretKey = null;
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    public void initialize() {
        keyLengthChoiceBox.setItems(FXCollections.observableArrayList(
                "128 bits",
                "192 bits",
                "256 bits"
        ));
        keyLengthChoiceBox.setValue("256 bits");

        draggableRegion.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        draggableRegion.setOnMouseDragged(event -> {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }
}
