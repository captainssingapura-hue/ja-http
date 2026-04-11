package hue.captains.singapura.tao.http.rps2.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hue.captains.singapura.tao.http.rps2.Choice;
import hue.captains.singapura.tao.http.rps2.GameMessage;
import hue.captains.singapura.tao.http.rps2.PlayerMessage;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * JavaFX GUI client for the RPS remote game.
 * <p>
 * Screens:
 * <ol>
 *   <li>Login — enter name, connect</li>
 *   <li>Waiting — waiting for opponent</li>
 *   <li>Play — pick ROCK / PAPER / SCISSORS</li>
 *   <li>Result — round outcome, auto-advances</li>
 *   <li>Game Over — final score, play again</li>
 * </ol>
 */
public class RpsGuiClient extends Application {

    private final PlayerMessageCodec playerCodec = new PlayerMessageCodec();
    private final GameMessageCodec gameCodec = new GameMessageCodec();
    private final ObjectMapper mapper = new ObjectMapper();

    private Vertx vertx;
    private WebSocket ws;
    private String playerName;

    // UI components
    private VBox root;
    private Label titleLabel;
    private Label statusLabel;
    private Label scoreLabel;
    private Label roundLabel;
    private HBox choiceBox;
    private Button rockBtn, paperBtn, scissorsBtn;

    @Override
    public void start(Stage stage) {
        root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);

        titleLabel = new Label("Rock Paper Scissors");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));

        statusLabel = new Label();
        statusLabel.setFont(Font.font(14));
        statusLabel.setWrapText(true);

        scoreLabel = new Label();
        scoreLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        roundLabel = new Label();
        roundLabel.setFont(Font.font(14));

        rockBtn = makeChoiceButton("ROCK", Choice.ROCK);
        paperBtn = makeChoiceButton("PAPER", Choice.PAPER);
        scissorsBtn = makeChoiceButton("SCISSORS", Choice.SCISSORS);

        choiceBox = new HBox(15, rockBtn, paperBtn, scissorsBtn);
        choiceBox.setAlignment(Pos.CENTER);

        showLoginScreen();

        var scene = new Scene(root, 450, 350);
        stage.setTitle("RPS Client");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
    }

    // ---- Screens ----

    private void showLoginScreen() {
        var nameField = new TextField();
        nameField.setPromptText("Enter your name");
        nameField.setMaxWidth(200);

        var connectBtn = new Button("Connect");
        connectBtn.setDefaultButton(true);

        statusLabel.setText("");

        connectBtn.setOnAction(e -> {
            var name = nameField.getText().trim();
            if (name.isEmpty()) return;
            playerName = name;
            connectBtn.setDisable(true);
            nameField.setDisable(true);
            statusLabel.setText("Connecting...");
            connect();
        });

        root.getChildren().setAll(titleLabel, nameField, connectBtn, statusLabel);
    }

    private void showWaitingScreen(String extra) {
        statusLabel.setText(extra != null ? extra : "Waiting for opponent...");
        scoreLabel.setText("");
        roundLabel.setText("");
        root.getChildren().setAll(titleLabel, statusLabel);
    }

    private void showPlayScreen(int round) {
        roundLabel.setText("Round " + round);
        statusLabel.setText("Pick your move!");
        setChoiceButtonsDisabled(false);
        root.getChildren().setAll(titleLabel, scoreLabel, roundLabel, statusLabel, choiceBox);
    }

    private void showRoundResultScreen(PlayerMessage.RoundResult result) {
        String winnerText;
        if (result.winner().equals("draw")) {
            winnerText = "Draw!";
        } else if (result.winner().equals(playerName)) {
            winnerText = "You won this round!";
        } else {
            winnerText = result.winner() + " won this round.";
        }

        roundLabel.setText("Round " + result.round() + " Result");
        statusLabel.setText(String.format("%s (%s)  vs  %s (%s)\n%s",
                result.playerA(), result.choiceA(),
                result.playerB(), result.choiceB(),
                winnerText));
        setChoiceButtonsDisabled(true);
        root.getChildren().setAll(titleLabel, scoreLabel, roundLabel, statusLabel, choiceBox);
    }

    private void showGameOverScreen(PlayerMessage.GameOver result) {
        boolean won = result.winner().equals(playerName);
        titleLabel.setText(won ? "You Win!" : "You Lose!");

        statusLabel.setText(String.format("Final score: %s %d  -  %s %d",
                result.playerA(), result.scoreA(),
                result.playerB(), result.scoreB()));

        var playAgainBtn = new Button("Play Again");
        playAgainBtn.setOnAction(e -> {
            titleLabel.setText("Rock Paper Scissors");
            if (ws != null) {
                ws.close();
            }
            showLoginScreen();
        });

        root.getChildren().setAll(titleLabel, statusLabel, playAgainBtn);
    }

    // ---- Network ----

    private void connect() {
        vertx = Vertx.vertx();

        var options = new WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(8082)
                .setURI("/rps");

        vertx.createHttpClient()
                .webSocket(options)
                .onSuccess(socket -> {
                    ws = socket;
                    sendHandshake();
                    ws.textMessageHandler(this::handleMessage);
                    ws.closeHandler(v -> Platform.runLater(() ->
                            statusLabel.setText("Disconnected from server.")));
                    Platform.runLater(() -> showWaitingScreen(null));
                })
                .onFailure(err -> Platform.runLater(() -> {
                    statusLabel.setText("Connection failed: " + err.getMessage());
                    showLoginScreen();
                }));
    }

    private void sendHandshake() {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", playerName);
            ws.writeTextMessage(mapper.writeValueAsString(node));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send handshake", e);
        }
    }

    private void handleMessage(String frame) {
        var msg = playerCodec.decode(frame);

        Platform.runLater(() -> {
            switch (msg) {
                case PlayerMessage.GameAssigned assigned -> {
                        showWaitingScreen("Matched against " + assigned.opponentName() + "! Get ready...");
                        var ready = new GameMessage.PlayerReady(null, null);
                        ws.writeTextMessage(gameCodec.encode(ready));
                }

                case PlayerMessage.RoundStart roundStart ->
                        showPlayScreen(roundStart.round());

                case PlayerMessage.RoundResult result ->
                        showRoundResultScreen(result);

                case PlayerMessage.GameOver gameOver ->
                        showGameOverScreen(gameOver);
            }
        });
    }

    private void sendChoice(Choice choice) {
        if (ws == null) return;
        setChoiceButtonsDisabled(true);
        statusLabel.setText("Waiting for opponent's move...");
        var move = new GameMessage.PlayerMove(null, null, choice);
        ws.writeTextMessage(gameCodec.encode(move));
    }

    // ---- Helpers ----

    private Button makeChoiceButton(String label, Choice choice) {
        var btn = new Button(label);
        btn.setPrefWidth(110);
        btn.setPrefHeight(40);
        btn.setFont(Font.font("System", FontWeight.BOLD, 13));
        btn.setOnAction(e -> sendChoice(choice));
        return btn;
    }

    private void setChoiceButtonsDisabled(boolean disabled) {
        rockBtn.setDisable(disabled);
        paperBtn.setDisable(disabled);
        scissorsBtn.setDisable(disabled);
    }

    private void shutdown() {
        if (ws != null) ws.close();
        if (vertx != null) vertx.close();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
