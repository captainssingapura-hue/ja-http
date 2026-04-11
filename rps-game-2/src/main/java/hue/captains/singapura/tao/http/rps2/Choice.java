package hue.captains.singapura.tao.http.rps2;

public enum Choice {
    ROCK, PAPER, SCISSORS;

    public boolean beats(Choice other) {
        return switch (this) {
            case ROCK     -> other == SCISSORS;
            case PAPER    -> other == ROCK;
            case SCISSORS -> other == PAPER;
        };
    }
}
