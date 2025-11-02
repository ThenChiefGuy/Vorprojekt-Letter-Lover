// letter-lover-client/pom.xml
/*
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.letterlover</groupId>
        <artifactId>letter-lover-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>letter-lover-client</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>com.letterlover</groupId>
            <artifactId>letter-lover-common</artifactId>
            <version>1.0.0</version>
        </dependency>
        
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-messaging</artifactId>
        </dependency>
        <dependency>
            <groupId>org.java-websocket</groupId>
            <artifactId>Java-WebSocket</artifactId>
            <version>1.5.6</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.letterlover.client.LetterLoverApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
*/

// src/main/java/com/letterlover/client/LetterLoverApplication.java
package com.letterlover.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class LetterLoverApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        
        primaryStage.setTitle("Letter Lover - Multiplayer Card Game");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(768);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// src/main/java/com/letterlover/client/network/WebSocketClient.java
package com.letterlover.client.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.letterlover.common.dto.*;
import com.letterlover.common.model.GameState;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.function.Consumer;

@Slf4j
public class GameWebSocketClient extends WebSocketClient {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Consumer<GameState> gameStateHandler;
    private Consumer<ChatMessage> chatMessageHandler;

    public GameWebSocketClient(String serverUrl) throws Exception {
        super(new URI(serverUrl + "/ws"));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("WebSocket connection opened");
    }

    @Override
    public void onMessage(String message) {
        try {
            if (message.contains("\"phase\"")) {
                GameState gameState = objectMapper.readValue(message, GameState.class);
                if (gameStateHandler != null) {
                    Platform.runLater(() -> gameStateHandler.accept(gameState));
                }
            } else if (message.contains("\"timestamp\"")) {
                ChatMessage chatMessage = objectMapper.readValue(message, ChatMessage.class);
                if (chatMessageHandler != null) {
                    Platform.runLater(() -> chatMessageHandler.accept(chatMessage));
                }
            }
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("WebSocket connection closed: {}", reason);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error", ex);
    }

    public void setGameStateHandler(Consumer<GameState> handler) {
        this.gameStateHandler = handler;
    }

    public void setChatMessageHandler(Consumer<ChatMessage> handler) {
        this.chatMessageHandler = handler;
    }

    public void sendAction(GameAction action) {
        try {
            String json = objectMapper.writeValueAsString(action);
            send(json);
        } catch (Exception e) {
            log.error("Error sending action", e);
        }
    }

    public void sendChatMessage(ChatMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            send(json);
        } catch (Exception e) {
            log.error("Error sending chat message", e);
        }
    }
}

// src/main/java/com/letterlover/client/controller/MainController.java
package com.letterlover.client.controller;

import com.letterlover.client.network.GameWebSocketClient;
import com.letterlover.common.dto.*;
import com.letterlover.common.model.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MainController {

    @FXML private StackPane rootPane;
    @FXML private VBox menuScreen;
    @FXML private VBox lobbyScreen;
    @FXML private BorderPane gameScreen;
    
    @FXML private TextField playerNameField;
    @FXML private TextField roomCodeField;
    @FXML private Label roomCodeLabel;
    @FXML private ListView<String> playerListView;
    @FXML private Button startGameButton;
    
    @FXML private HBox playerHandBox;
    @FXML private VBox gameLogBox;
    @FXML private ListView<String> chatListView;
    @FXML private TextField chatInputField;
    @FXML private FlowPane otherPlayersPane;
    @FXML private Label currentPlayerLabel;
    @FXML private Label deckCountLabel;
    
    private GameWebSocketClient webSocketClient;
    private String playerId;
    private String currentRoomCode;
    private GameState currentGameState;

    @FXML
    public void initialize() {
        playerId = UUID.randomUUID().toString();
        connectToServer();
        setupStyles();
    }

    private void connectToServer() {
        try {
            webSocketClient = new GameWebSocketClient("ws://localhost:8080");
            webSocketClient.setGameStateHandler(this::updateGameState);
            webSocketClient.setChatMessageHandler(this::addChatMessage);
            webSocketClient.connect();
        } catch (Exception e) {
            log.error("Failed to connect to server", e);
            showError("Verbindung zum Server fehlgeschlagen");
        }
    }

    private void setupStyles() {
        // Apply medieval/romantic theme
        rootPane.setStyle("-fx-background-color: linear-gradient(to bottom, #2c1810, #1a0f0a);");
    }

    @FXML
    private void onCreateRoom() {
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            showError("Bitte gib einen Spielernamen ein");
            return;
        }

        Map<String, String> request = Map.of(
            "playerId", playerId,
            "playerName", playerName
        );
        
        // Send create room request
        // In real implementation, this would go through WebSocket
        showLobbyScreen();
    }

    @FXML
    private void onJoinRoom() {
        String playerName = playerNameField.getText().trim();
        String roomCode = roomCodeField.getText().trim();
        
        if (playerName.isEmpty() || roomCode.isEmpty()) {
            showError("Bitte fülle alle Felder aus");
            return;
        }

        currentRoomCode = roomCode;
        Map<String, String> request = Map.of(
            "playerId", playerId,
            "playerName", playerName,
            "roomCode", roomCode
        );
        
        showLobbyScreen();
    }

    @FXML
    private void onStartGame() {
        if (currentGameState != null && currentGameState.getPlayers().size() >= 2) {
            Map<String, String> request = Map.of("roomCode", currentRoomCode);
            // Send start game request
            showGameScreen();
        }
    }

    @FXML
    private void onSendChat() {
        String message = chatInputField.getText().trim();
        if (!message.isEmpty()) {
            ChatMessage chatMsg = new ChatMessage(
                playerId,
                playerNameField.getText(),
                message,
                System.currentTimeMillis()
            );
            webSocketClient.sendChatMessage(chatMsg);
            chatInputField.clear();
        }
    }

    private void updateGameState(GameState gameState) {
        this.currentGameState = gameState;
        
        // Update UI based on game state
        if (gameState.getPhase() == GameState.GamePhase.WAITING) {
            updateLobby(gameState);
        } else {
            updateGame(gameState);
        }
    }

    private void updateLobby(GameState gameState) {
        playerListView.getItems().clear();
        gameState.getPlayers().forEach(p -> 
            playerListView.getItems().add(p.getName() + " (" + p.getTokens() + " Tokens)")
        );
        
        startGameButton.setDisable(gameState.getPlayers().size() < 2);
    }

    private void updateGame(GameState gameState) {
        // Update current player indicator
        Player currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer != null) {
            currentPlayerLabel.setText("Am Zug: " + currentPlayer.getName());
            currentPlayerLabel.setStyle(currentPlayer.getId().equals(playerId) 
                ? "-fx-text-fill: gold; -fx-font-weight: bold;" 
                : "-fx-text-fill: white;");
        }
        
        // Update deck count
        deckCountLabel.setText("Deck: " + gameState.getDeck().size() + " Karten");
        
        // Update player hand
        updatePlayerHand(gameState);
        
        // Update other players
        updateOtherPlayers(gameState);
        
        // Update game log
        updateGameLog(gameState);
    }

    private void updatePlayerHand(GameState gameState) {
        playerHandBox.getChildren().clear();
        
        Player player = gameState.getPlayers().stream()
            .filter(p -> p.getId().equals(playerId))
            .findFirst()
            .orElse(null);
        
        if (player != null && player.getCurrentCard() != null) {
            VBox cardBox = createCardView(player.getCurrentCard(), true);
            playerHandBox.getChildren().add(cardBox);
            
            // Add play button if it's player's turn
            if (gameState.getCurrentPlayer().getId().equals(playerId)) {
                Button playButton = new Button("Spielen");
                playButton.setOnAction(e -> onPlayCard(player.getCurrentCard()));
                playerHandBox.getChildren().add(playButton);
            }
        }
    }

    private VBox createCardView(Card card, boolean showDetails) {
        VBox cardBox = new VBox(10);
        cardBox.setAlignment(Pos.CENTER);
        cardBox.setPrefSize(150, 220);
        cardBox.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 15; " +
            "-fx-border-color: gold; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 15; " +
            "-fx-padding: 15; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 5);",
            card.getType().getColor()
        ));
        
        // Card icon
        Label iconLabel = new Label(card.getType().getIcon());
        iconLabel.setStyle("-fx-font-size: 48px;");
        
        // Card name
        Label nameLabel = new Label(card.getName());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Card value
        Label valueLabel = new Label("Wert: " + card.getValue());
        valueLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        
        cardBox.getChildren().addAll(iconLabel, nameLabel, valueLabel);
        
        if (showDetails) {
            Text abilityText = new Text(card.getType().getAbility());
            abilityText.setWrappingWidth(120);
            abilityText.setFill(Color.WHITE);
            abilityText.setStyle("-fx-font-size: 11px;");
            cardBox.getChildren().add(abilityText);
        }
        
        // Animation
        ScaleTransition st = new ScaleTransition(Duration.millis(200), cardBox);
        cardBox.setOnMouseEntered(e -> {
            st.setToX(1.1);
            st.setToY(1.1);
            st.play();
        });
        cardBox.setOnMouseExited(e -> {
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        return cardBox;
    }

    private void updateOtherPlayers(GameState gameState) {
        otherPlayersPane.getChildren().clear();
        
        gameState.getPlayers().stream()
            .filter(p -> !p.getId().equals(playerId))
            .forEach(p -> {
                VBox playerBox = new VBox(5);
                playerBox.setAlignment(Pos.CENTER);
                playerBox.setPrefSize(120, 160);
                playerBox.setStyle(
                    "-fx-background-color: rgba(139, 69, 19, 0.7); " +
                    "-fx-background-radius: 10; " +
                    "-fx-border-color: " + (p.isProtected() ? "pink" : "gold") + "; " +
                    "-fx-border-width: 2; " +
                    "-fx-border-radius: 10; " +
                    "-fx-padding: 10;"
                );
                
                if (p.isEliminated()) {
                    playerBox.setOpacity(0.5);
                }
                
                Label nameLabel = new Label(p.getName());
                nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                
                Label tokensLabel = new Label("🏆 " + p.getTokens());
                tokensLabel.setStyle("-fx-text-fill: gold;");
                
                Label statusLabel = new Label(
                    p.isEliminated() ? "❌ Ausgeschieden" : 
                    p.isProtected() ? "🛡️ Geschützt" : "✓ Aktiv"
                );
                statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
                
                playerBox.getChildren().addAll(nameLabel, tokensLabel, statusLabel);
                otherPlayersPane.getChildren().add(playerBox);
            });
    }

    private void updateGameLog(GameState gameState) {
        gameLogBox.getChildren().clear();
        gameState.getGameLog().forEach(entry -> {
            Label logEntry = new Label(entry);
            logEntry.setStyle(
                "-fx-text-fill: white; " +
                "-fx-padding: 5; " +
                "-fx-background-color: rgba(0,0,0,0.3); " +
                "-fx-background-radius: 5;"
            );
            logEntry.setWrapText(true);
            gameLogBox.getChildren().add(logEntry);
        });
    }

    private void onPlayCard(Card card) {
        // Show target selection dialog if needed
        if (card.getType().requiresTarget()) {
            showTargetSelectionDialog(card);
        } else {
            GameAction action = new GameAction();
            action.setType(GameAction.ActionType.PLAY_CARD);
            action.setPlayerId(playerId);
            action.setCardId(card.getId());
            action.setCardType(card.getType());
            webSocketClient.sendAction(action);
        }
    }

    private void showTargetSelectionDialog(Card card) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Ziel auswählen");
        dialog.setHeaderText("Wähle einen Spieler für " + card.getName());
        
        VBox content = new VBox(10);
        ToggleGroup group = new ToggleGroup();
        
        currentGameState.getPlayers().stream()
            .filter(p -> !p.getId().equals(playerId) && !p.isEliminated() && !p.isProtected())
            .forEach(p -> {
                RadioButton rb = new RadioButton(p.getName());
                rb.setToggleGroup(group);
                rb.setUserData(p.getId());
                content.getChildren().add(rb);
            });
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK && group.getSelectedToggle() != null) {
                return (String) group.getSelectedToggle().getUserData();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(targetId -> {
            if (card.getType().requiresGuess()) {
                showGuessDialog(card, targetId);
            } else {
                sendPlayCardAction(card, targetId, null);
            }
        });
    }

    private void showGuessDialog(Card card, String targetId) {
        ChoiceDialog<CardType> dialog = new ChoiceDialog<>(
            CardType.PRIEST,
            Arrays.stream(CardType.values())
                .filter(ct -> ct != CardType.GUARD)
                .toList()
        );
        dialog.setTitle("Karte raten");
        dialog.setHeaderText("Welche Karte hat der Spieler?");
        
        dialog.showAndWait().ifPresent(guess -> 
            sendPlayCardAction(card, targetId, guess)
        );
    }

    private void sendPlayCardAction(Card card, String targetId, CardType guess) {
        GameAction action = new GameAction();
        action.setType(GameAction.ActionType.PLAY_CARD);
        action.setPlayerId(playerId);
        action.setCardId(card.getId());
        action.setCardType(card.getType());
        action.setTargetPlayerId(targetId);
        action.setGuessedCard(guess);
        webSocketClient.sendAction(action);
    }

    private void addChatMessage(ChatMessage message) {
        String formatted = String.format("[%s] %s", message.getPlayerName(), message.getMessage());
        chatListView.getItems().add(formatted);
        chatListView.scrollTo(chatListView.getItems().size() - 1);
    }

    private void showLobbyScreen() {
        menuScreen.setVisible(false);
        lobbyScreen.setVisible(true);
        gameScreen.setVisible(false);
    }

    private void showGameScreen() {
        menuScreen.setVisible(false);
        lobbyScreen.setVisible(false);
        gameScreen.setVisible(true);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Fehler");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}