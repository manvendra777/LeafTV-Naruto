import com.leaftv.launcher.HomeImmersion;
public class HomeImmersionTest {
    public static void main(String[] args) {
        HomeImmersion state = new HomeImmersion();
        if (state.isWallpaperOnly()) throw new AssertionError("starts on Home");
        if (!state.toggle()) throw new AssertionError("first Back shows wallpaper");
        if (state.isWallpaperOnly() != true) throw new AssertionError("stays immersive");
        if (state.toggle()) throw new AssertionError("second Back restores Home");
        state.toggle();
        state.reset();
        if (state.isWallpaperOnly()) throw new AssertionError("Home key restores chrome");
        System.out.println("PASS: immersive wallpaper Back toggle and HOME reset");
    }
}
