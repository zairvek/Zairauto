import me.aap.fermata.util.ZairCoverGeometry;

/** Regression matrix for real portrait, landscape, split-screen and large-font viewports. */
public final class ZairCoverGeometryTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        int[][] viewports={{360,650},{800,280},{412,700},{960,380},{320,240},{600,820},{1536,700}};
        float[] scales={1,1.3f,1.8f,2};
        for(int[] viewport:viewports) for(float scale:scales){
            var geometry=ZairCoverGeometry.fit(viewport[0],viewport[1],scale);
            check(geometry.itemWidth<=viewport[0],"cover must fit viewport width");
            check(geometry.posterHeight+Math.round(64*scale)+30<=viewport[1],"art and accessible title must fit viewport height");
            check(Math.abs(geometry.posterWidth*1.5f-geometry.posterHeight)<2,"portrait cover aspect ratio");
            check(geometry.itemWidth>=74,"cover remains a usable touch target");
        }
        var normal=ZairCoverGeometry.fit(800,300,1);
        var large=ZairCoverGeometry.fit(800,300,2);
        check(large.posterHeight<normal.posterHeight,"large fonts must get more label space");
        System.out.println(checks+" responsive cover assertions passed.");
    }
}
