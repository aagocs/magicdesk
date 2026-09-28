package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class HostedScrollOutputTest {
    @Test public void x11RetainsFractionsAndAxisDirections() throws Exception { verify("X11SurfaceOutput", 1); }
    @Test public void waylandRetainsFractionsAndAxisDirections() throws Exception { verify("WaylandSurfaceOutput", 15); }

    private static void verify(String source, int unit) throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk",
                RuntimeSourceFixture.methods(source, "scroll") + "static final int UNIT = " + unit + ";" + """
            static class Output {
                float x,y,h,v; int calls;
                void pointer(float x,float y){this.x=x;this.y=y;}
                void scroll(float h,float v){this.h=h;this.v=v;calls++;}
                void scroll(float x,float y,float h,float v){pointer(x,y);scroll(h,v);}
            }
            final Output output=new Output();
            public static void verify() {
                var fixture=new Fixture();
                fixture.scroll(.3f,.6f,.25f,-.5f);
                check(fixture.output.h==.25f*UNIT && fixture.output.v==.5f*UNIT,"right/down without rounding");
                check(fixture.output.x==.3f && fixture.output.y==.6f,"scroll follows its pointer anchor");
                fixture.scroll(.7f,.2f,-.125f,.75f);
                check(fixture.output.h==-.125f*UNIT && fixture.output.v==-.75f*UNIT,"left/up without rounding");
                check(fixture.output.calls==2,"one delivery per axis pair");
            }
            """);
    }
}
