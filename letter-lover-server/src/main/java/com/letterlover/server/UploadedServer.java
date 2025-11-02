// letter-lover-server/pom.xml
/*
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.letterlover</groupId>
        <artifactId>letter-lover-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>letter-lover-server</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>com.letterlover</groupId>
            <artifactId>letter-lover-common</artifactId>
            <version>1.0.0</version>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
*/

// src/main/java/com/letterlover/server/LetterLoverServerApplication.java
package com.letterlover.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LetterLoverServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LetterLoverServerApplication.class, args);
    }
}

// src/main/java/com/letterlover/server/config/WebSocketConfig.java
package com.letterlover.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

// src/main/java/com/letterlover/server/service/GameService.java
package com.letterlover.server.service;

import com.letterlover.common.model.*;
import com.letterlover.common.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GameService {
    
    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();

    public RoomInfo createRoom(String hostId, String hostName) {
        String roomCode = generateRoomCode();
        RoomInfo room = new RoomInfo(roomCode, hostId, 1, 4, false);
        rooms.put(roomCode, room);
        
        GameState game = new GameState();
        game.setRoomCode(roomCode);
        game.setPhase(GameState.GamePhase.WAITING);
        game.setRoundNumber(0);
        game.getPlayers().add(new Player(hostId, hostName));
        games.put(roomCode, game);
        
        log.info("Room created: {} by {}", roomCode, hostName);
        return room;
    }

    public GameState joinRoom(String roomCode, String playerId, String playerName) {
        GameState game = games.get(roomCode);
        RoomInfo room = rooms.get(roomCode);
        
        if (game == null || room == null) {
            throw new IllegalStateException("Room not found");
        }
        
        if (room.isGameStarted()) {
            throw new IllegalStateException("Game already started");
        }
        
        if (game.getPlayers().size() >= room.getMaxPlayers()) {
            throw new IllegalStateException("Room is full");
        }
        
        game.getPlayers().add(new Player(playerId, playerName));
        room.setPlayerCount(game.getPlayers().size());
        
        log.info("Player {} joined room {}", playerName, roomCode);
        return game;
    }

    public GameState startGame(String roomCode) {
        GameState game = games.get(roomCode);
        RoomInfo room = rooms.get(roomCode);
        
        if (game == null) {
            throw new IllegalStateException("Game not found");
        }
        
        if (game.getPlayers().size() < 2) {
            throw new IllegalStateException("Need at least 2 players");
        }
        
        room.setGameStarted(true);
        startNewRound(game);
        
        log.info("Game started in room {}", roomCode);
        return game;
    }

    private void startNewRound(GameState game) {
        game.setRoundNumber(game.getRoundNumber() + 1);
        game.setPhase(GameState.GamePhase.PLAYING);
        game.setCurrentPlayerIndex(0);
        
        // Reset players
        game.getPlayers().forEach(Player::resetForNewRound);
        
        // Initialize deck
        game.setDeck(initializeDeck());
        Collections.shuffle(game.getDeck());
        
        // Burn one card
        game.setBurnedCard(game.getDeck().remove(0));
        
        // Deal initial cards
        for (Player player : game.getPlayers()) {
            player.drawCard(game.getDeck().remove(0));
        }
        
        game.addLogEntry("Neue Runde gestartet! " + game.getPlayers().size() + " Spieler.");
    }

    private List<Card> initializeDeck() {
        List<Card> deck = new ArrayList<>();
        for (CardType type : CardType.values()) {
            for (int i = 0; i < type.getCount(); i++) {
                deck.add(new Card(type));
            }
        }
        return deck;
    }

    public GameState playCard(String roomCode, GameAction action) {
        GameState game = games.get(roomCode);
        Player player = game.getPlayers().stream()
                .filter(p -> p.getId().equals(action.getPlayerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player not found"));
        
        if (!game.getCurrentPlayer().getId().equals(player.getId())) {
            throw new IllegalStateException("Not your turn");
        }
        
        Card playedCard = player.getCurrentCard();
        if (!playedCard.getType().equals(action.getCardType())) {
            throw new IllegalStateException("Invalid card");
        }
        
        // Check for forced Countess play
        if (hasCountessConstraint(player)) {
            if (playedCard.getType() != CardType.COUNTESS) {
                throw new IllegalStateException("Must play Countess when holding King or Prince");
            }
        }
        
        player.discardCard(playedCard);
        executeCardEffect(game, player, action);
        
        // Draw new card
        if (!game.getDeck().isEmpty()) {
            player.drawCard(game.getDeck().remove(0));
        }
        
        // Remove protection from all players
        game.getPlayers().forEach(p -> p.setProtected(false));
        
        // Set protection if Handmaid was played
        if (playedCard.getType() == CardType.HANDMAID) {
            player.setProtected(true);
        }
        
        // Check for round end
        checkRoundEnd(game);
        
        if (game.getPhase() == GameState.GamePhase.PLAYING) {
            game.nextPlayer();
        }
        
        return game;
    }

    private boolean hasCountessConstraint(Player player) {
        Card card = player.getCurrentCard();
        return card != null && (card.getType() == CardType.KING || card.getType() == CardType.PRINCE);
    }

    private void executeCardEffect(GameState game, Player player, GameAction action) {
        CardType cardType = action.getCardType();
        Player target = null;
        
        if (action.getTargetPlayerId() != null) {
            target = game.getPlayers().stream()
                    .filter(p -> p.getId().equals(action.getTargetPlayerId()))
                    .findFirst()
                    .orElse(null);
            
            if (target != null && target.isProtected()) {
                game.addLogEntry(player.getName() + " versuchte " + target.getName() + 
                                " anzugreifen, aber sie ist geschützt!");
                return;
            }
        }
        
        switch (cardType) {
            case GUARD -> executeGuard(game, player, target, action.getGuessedCard());
            case PRIEST -> executePriest(game, player, target);
            case BARON -> executeBaron(game, player, target);
            case HANDMAID -> executeHandmaid(game, player);
            case PRINCE -> executePrince(game, player, target);
            case KING -> executeKing(game, player, target);
            case COUNTESS -> executeCountess(game, player);
            case PRINCESS -> executePrincess(game, player);
        }
    }

    private void executeGuard(GameState game, Player player, Player target, CardType guess) {
        if (target == null || guess == null) return;
        
        if (target.getCurrentCard().getType() == guess) {
            target.setEliminated(true);
            game.addLogEntry(player.getName() + " hat richtig geraten! " + 
                           target.getName() + " hatte " + guess.getName() + " und scheidet aus!");
        } else {
            game.addLogEntry(player.getName() + " hat falsch geraten. " + 
                           target.getName() + " hatte nicht " + guess.getName() + ".");
        }
    }

    private void executePriest(GameState game, Player player, Player target) {
        if (target == null) return;
        game.addLogEntry(player.getName() + " hat die Karte von " + 
                       target.getName() + " angesehen: " + target.getCurrentCard().getName());
    }

    private void executeBaron(GameState game, Player player, Player target) {
        if (target == null) return;
        
        int playerValue = player.getCurrentCard().getValue();
        int targetValue = target.getCurrentCard().getValue();
        
        if (playerValue > targetValue) {
            target.setEliminated(true);
            game.addLogEntry(player.getName() + " (" + playerValue + ") hat " + 
                           target.getName() + " (" + targetValue + ") im Duell besiegt!");
        } else if (targetValue > playerValue) {
            player.setEliminated(true);
            game.addLogEntry(target.getName() + " (" + targetValue + ") hat " + 
                           player.getName() + " (" + playerValue + ") im Duell besiegt!");
        } else {
            game.addLogEntry(player.getName() + " und " + target.getName() + 
                           " haben beide " + playerValue + ". Unentschieden!");
        }
    }

    private void executeHandmaid(GameState game, Player player) {
        game.addLogEntry(player.getName() + " ist bis zum nächsten Zug geschützt!");
    }

    private void executePrince(GameState game, Player player, Player target) {
        if (target == null) target = player;
        
        Card discarded = target.getCurrentCard();
        target.discardCard(discarded);
        
        if (discarded.getType() == CardType.PRINCESS) {
            target.setEliminated(true);
            game.addLogEntry(target.getName() + " musste die Princess abwerfen und scheidet aus!");
        } else {
            if (!game.getDeck().isEmpty()) {
                target.drawCard(game.getDeck().remove(0));
            } else {
                target.drawCard(game.getBurnedCard());
            }
            game.addLogEntry(target.getName() + " hat " + discarded.getName() + 
                           " abgeworfen und eine neue Karte gezogen.");
        }
    }

    private void executeKing(GameState game, Player player, Player target) {
        if (target == null) return;
        
        Card temp = player.getCurrentCard();
        player.drawCard(target.getCurrentCard());
        target.drawCard(temp);
        
        game.addLogEntry(player.getName() + " hat Karten mit " + target.getName() + " getauscht!");
    }

    private void executeCountess(GameState game, Player player) {
        game.addLogEntry(player.getName() + " hat die Countess abgeworfen.");
    }

    private void executePrincess(GameState game, Player player) {
        player.setEliminated(true);
        game.addLogEntry(player.getName() + " hat die Princess abgeworfen und scheidet aus!");
    }

    private void checkRoundEnd(GameState game) {
        List<Player> active = game.getActivePlayers();
        
        // Only one player left or deck empty
        if (active.size() == 1 || game.getDeck().isEmpty()) {
            Player winner = determineRoundWinner(active);
            winner.addToken();
            
            game.setPhase(GameState.GamePhase.ROUND_END);
            game.addLogEntry("Runde beendet! " + winner.getName() + " gewinnt und erhält einen Token!");
            
            // Check for game winner
            if (winner.getTokens() >= getRequiredTokens(game.getPlayers().size())) {
                game.setPhase(GameState.GamePhase.GAME_END);
                game.addLogEntry("SPIEL BEENDET! " + winner.getName() + " gewinnt das Spiel!");
            }
        }
    }

    private Player determineRoundWinner(List<Player> active) {
        return active.stream()
                .max(Comparator.comparingInt(p -> p.getCurrentCard().getValue()))
                .orElse(active.get(0));
    }

    private int getRequiredTokens(int playerCount) {
        return switch (playerCount) {
            case 2 -> 7;
            case 3 -> 5;
            default -> 4;
        };
    }

    private String generateRoomCode() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    public GameState getGame(String roomCode) {
        return games.get(roomCode);
    }

    public RoomInfo getRoom(String roomCode) {
        return rooms.get(roomCode);
    }
}

// src/main/java/com/letterlover/server/controller/GameController.java
package com.letterlover.server.controller;

import com.letterlover.common.dto.*;
import com.letterlover.common.model.GameState;
import com.letterlover.server.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/game.createRoom")
    public void createRoom(@Payload Map<String, String> request) {
        RoomInfo room = gameService.createRoom(
            request.get("playerId"), 
            request.get("playerName")
        );
        GameState game = gameService.getGame(room.getRoomCode());
        messagingTemplate.convertAndSend("/topic/room." + room.getRoomCode(), game);
    }

    @MessageMapping("/game.joinRoom")
    public void joinRoom(@Payload Map<String, String> request) {
        GameState game = gameService.joinRoom(
            request.get("roomCode"),
            request.get("playerId"),
            request.get("playerName")
        );
        messagingTemplate.convertAndSend("/topic/room." + game.getRoomCode(), game);
    }

    @MessageMapping("/game.startGame")
    public void startGame(@Payload Map<String, String> request) {
        GameState game = gameService.startGame(request.get("roomCode"));
        messagingTemplate.convertAndSend("/topic/room." + game.getRoomCode(), game);
    }

    @MessageMapping("/game.playCard")
    public void playCard(@Payload GameAction action) {
        GameState game = gameService.playCard(action.getCardId(), action);
        messagingTemplate.convertAndSend("/topic/room." + game.getRoomCode(), game);
    }

    @MessageMapping("/chat.sendMessage")
    public void sendChatMessage(@Payload ChatMessage message) {
        messagingTemplate.convertAndSend("/topic/chat." + message.getPlayerId(), message);
    }
}

// src/main/resources/application.yml
/*
server:
  port: 8080

spring:
  application:
    name: letter-lover-server
  datasource:
    url: jdbc:h2:mem:letterlover
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true

logging:
  level:
    com.letterlover: DEBUG
*/