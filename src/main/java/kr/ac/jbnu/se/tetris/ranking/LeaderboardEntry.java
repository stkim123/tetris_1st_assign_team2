package kr.ac.jbnu.se.tetris.ranking;

public final class LeaderboardEntry {
    private final int rank;
    private final String userId;
    private final String displayName;
    private final int rating;
    private final int wins;
    private final int losses;
    private final int games;

    public LeaderboardEntry(int rank, String userId, String displayName, int rating,
            int wins, int losses, int games) {
        this.rank = rank;
        this.userId = userId;
        this.displayName = displayName;
        this.rating = rating;
        this.wins = wins;
        this.losses = losses;
        this.games = games;
    }

    public int getRank() { return rank; }
    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public int getRating() { return rating; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getGames() { return games; }
}
