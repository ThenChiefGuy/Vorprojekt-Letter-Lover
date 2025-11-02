// letter-lover-common/pom.xml
/*
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.letterlover</groupId>
        <artifactId>letter-lover-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>letter-lover-common</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
*/

// src/main/java/com/letterlover/common/model/CardType.java
package com.letterlover.common.model;

import lombok.Getter;

@Getter
public enum CardType {
    GUARD(1, "Guard", 5, "🛡️", "#8B4513", 
          "Rate eine Karte (außer Guard). Wenn richtig, ist der Spieler ausgeschieden."),
    PRIEST(2, "Priest", 2, "👁️", "#4169E1", 
           "Sieh dir die Hand eines anderen Spielers an."),
    BARON(3, "Baron", 2, "⚔️", "#8B008B", 
          "Vergleiche Hände mit einem Spieler. Der niedrigere Wert scheidet aus."),
    HANDMAID(4, "Handmaid", 2, "🌸", "#FF69B4", 
             "Bis zu deinem nächsten Zug bist du geschützt."),
    PRINCE(5, "Prince", 2, "👑", "#FF6347", 
           "Ein Spieler (auch du) wirft seine Hand ab und zieht eine neue Karte."),
    KING(6, "King", 1, "♚", "#FFD700", 
         "Tausche deine Hand mit einem anderen Spieler."),
    COUNTESS(7, "Countess", 1, "👸", "#DDA0DD", 
             "Muss abgeworfen werden, wenn du King oder Prince hast."),
    PRINCESS(8, "Princess", 1, "💝", "#FF1493", 
             "Wenn du diese Karte abwirfst, scheidest du aus.");

    private final int value;
    private final String name;
    private final int count;
    private final String icon;
    private final String color;
    private final String ability;

    CardType(int value, String name, int count, String icon, String color, String ability) {
        this.value = value;
        this.name = name;
        this.count = count;
        this.icon = icon;
        this.color = color;
        this.ability = ability;
    }

    public boolean requiresTarget() {
        return this != HANDMAID && this != COUNTESS && this != PRINCESS;
    }

    public boolean requiresGuess() {
        return this == GUARD;
    }
}

// src/main/java/com/letterlover/common/model/Card.java
package com.letterlover.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Card {
    private String id;
    private CardType type;

    public Card(CardType type) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
    }

    public int getValue() {
        return type.getValue();
    }

    public String getName() {
        return type.getName();
    }
}

// src/main/java/com/letterlover/common/model/Player.java
package com.letterlover.common.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Player {
    private String id;
    private String name;
    private Card currentCard;
    private List<Card> discardedCards = new ArrayList<>();
    private boolean isProtected;
    private boolean isEliminated;
    private int tokens;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.tokens = 0;
        this.isProtected = false;
        this.isEliminated = false;
    }

    public void drawCard(Card card) {
        this.currentCard = card;
    }

    public void discardCard(Card card) {
        this.discardedCards.add(card);
        if (this.currentCard != null && this.currentCard.getId().equals(card.getId())) {
            this.currentCard = null;
        }
    }

    public void resetForNewRound() {
        this.currentCard = null;
        this.discardedCards.clear();
        this.isProtected = false;
        this.isEliminated = false;
    }

    public void addToken() {
        this.tokens++;
    }
}

// src/main/java/com/letterlover/common/model/GameState.java
package com.letterlover.common.model;

import lombok.Data;
import java.util.*;

@Data
public class GameState {
    private String roomCode;
    private List<Player> players = new ArrayList<>();
    private List<Card> deck = new ArrayList<>();
    private Card burnedCard;
    private int currentPlayerIndex;
    private GamePhase phase;
    private int roundNumber;
    private String lastAction;
    private List<String> gameLog = new ArrayList<>();

    public enum GamePhase {
        WAITING, PLAYING, ROUND_END, GAME_END
    }

    public Player getCurrentPlayer() {
        if (players.isEmpty() || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        } while (getCurrentPlayer().isEliminated() && getActivePlayers().size() > 1);
    }

    public List<Player> getActivePlayers() {
        return players.stream()
                .filter(p -> !p.isEliminated())
                .toList();
    }

    public void addLogEntry(String entry) {
        gameLog.add("[Runde " + roundNumber + "] " + entry);
    }
}

// src/main/java/com/letterlover/common/dto/GameAction.java
package com.letterlover.common.dto;

import com.letterlover.common.model.CardType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameAction {
    private ActionType type;
    private String playerId;
    private String cardId;
    private CardType cardType;
    private String targetPlayerId;
    private CardType guessedCard;

    public enum ActionType {
        PLAY_CARD, DRAW_CARD, START_GAME, JOIN_ROOM, LEAVE_ROOM, CHAT_MESSAGE
    }
}

// src/main/java/com/letterlover/common/dto/ChatMessage.java
package com.letterlover.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String playerId;
    private String playerName;
    private String message;
    private long timestamp;
}

// src/main/java/com/letterlover/common/dto/RoomInfo.java
package com.letterlover.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfo {
    private String roomCode;
    private String hostId;
    private int playerCount;
    private int maxPlayers = 4;
    private boolean isGameStarted;
}