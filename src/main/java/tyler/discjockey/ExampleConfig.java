package tyler.discjockey;

public class ExampleConfig {

    public final DiscJockeyConfig discJockey = new DiscJockeyConfig();
    public static class DiscJockeyConfig {
        public boolean enabled = true;
        public float playbackSpeed = 1.0f;
        public boolean loopSong = false;
        public boolean shuffle = false;
        public boolean rotateToBlock = true;
    }
}
