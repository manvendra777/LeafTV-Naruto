import com.leaftv.launcher.AppLayout;
import java.util.*;

public class AppLayoutTest {
    static void check(String label, List<String> expected, List<String> got) {
        if (!expected.equals(got)) throw new AssertionError(label + " expected " + expected + "; got " + got);
    }
    public static void main(String[] args) {
        List<String> home = Arrays.asList("youtube", "netflix", "plex");
        check("move right", Arrays.asList("netflix","youtube","plex"), AppLayout.move(home,"youtube",1));
        check("move left", Arrays.asList("youtube","plex","netflix"), AppLayout.move(home,"netflix",1));
        check("remove pinned", Arrays.asList("youtube","plex"), AppLayout.toggle(home,"netflix"));
        check("add pinned", Arrays.asList("youtube","netflix","plex","twitch"), AppLayout.toggle(home,"twitch"));
        check("edge", home, AppLayout.move(home,"youtube",-1));
        check("filter uninstalled", Arrays.asList("youtube","plex"), AppLayout.filter(home,Arrays.asList("plex","youtube","twitch")));
        check("merge custom", Arrays.asList("plex","youtube","netflix","twitch"), AppLayout.merge(Arrays.asList("plex","youtube"), Arrays.asList("netflix","plex","twitch","youtube")));
        check("empty home", Collections.emptyList(), AppLayout.filter(Collections.emptyList(), Arrays.asList("youtube","netflix")));
        check("dedupe", Arrays.asList("youtube","netflix"), AppLayout.seed(Arrays.asList("youtube","youtube","netflix")));
        System.out.println("PASS: 9 AppLayout regression tests");
    }
}
