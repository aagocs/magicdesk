package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DialogContentInsetsTest {
    @Test public void captionPaddingTracksActualDialogOverlap() {
        assertEquals(60, DialogContentInsets.leading(153, 60, 153, 564));
        assertEquals(0, DialogContentInsets.leading(153, 60, 300, 200));
        assertEquals(13, DialogContentInsets.leading(153, 60, 200, 400));
        assertEquals(20, DialogContentInsets.leading(153, 60, 153, 20));
    }
    @Test public void absentSourcesDoNotCreateMarginsAndTrailingEdgesAreSymmetric() {
        assertEquals(0, DialogContentInsets.leading(153, 0, 100, 500));
        assertEquals(0, DialogContentInsets.trailing(717, 0, 300, 500));
        assertEquals(40, DialogContentInsets.trailing(717, 40, 153, 564));
        assertEquals(0, DialogContentInsets.trailing(717, 40, 300, 200));
    }

    @Test public void modalLifecycleFitsBeforeDrawingAndReleasesOwnerObservation() throws Exception {
        RuntimeSourceFixture.verify("""
                static class Insets {
                    final int left, top, right, bottom;
                    static final Insets NONE = new Insets(0,0,0,0);
                    Insets(int l,int t,int r,int b) { left=l;top=t;right=r;bottom=b; }
                    static Insets of(int l,int t,int r,int b) { return new Insets(l,t,r,b); }
                    static Insets max(Insets a,Insets b) {
                        return of(Math.max(a.left,b.left),Math.max(a.top,b.top),Math.max(a.right,b.right),Math.max(a.bottom,b.bottom));
                    }
                }
                static class WindowInsets {
                    Insets value;
                    WindowInsets(Insets value) { this.value=value; }
                    Insets getInsets(int types) { return value; }
                    static class Type {
                        static int systemBars() { return 1; }
                        static int displayCutout() { return 2; }
                        static int ime() { return 4; }
                        static int captionBar() { return 8; }
                    }
                    static class Builder {
                        Insets value;
                        Builder(WindowInsets original) { value=original.value; }
                        Builder setInsets(int types,Insets value) { this.value=value;return this; }
                        WindowInsets build() { return new WindowInsets(value); }
                    }
                }
                static class ViewTreeObserver {
                    interface OnPreDrawListener { boolean onPreDraw(); }
                    interface OnGlobalLayoutListener { void onGlobalLayout(); }
                    Set<OnPreDrawListener> draws=new HashSet<>();
                    Set<OnGlobalLayoutListener> layouts=new HashSet<>();
                    boolean isAlive() { return true; }
                    void addOnPreDrawListener(OnPreDrawListener listener) { draws.add(listener); }
                    void removeOnPreDrawListener(OnPreDrawListener listener) { draws.remove(listener); }
                    void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) { layouts.add(listener); }
                    void removeOnGlobalLayoutListener(OnGlobalLayoutListener listener) { layouts.remove(listener); }
                }
                static class View {
                    interface OnAttachStateChangeListener {
                        void onViewAttachedToWindow(View view); void onViewDetachedFromWindow(View view);
                    }
                    interface InsetsListener { WindowInsets apply(View view,WindowInsets insets); }
                    int left=8,top=6,right=8,bottom=6, x=100,y=200,width=640,height=500;
                    int layouts,insetRequests,paddingWrites;
                    boolean attached;
                    ViewTreeObserver observer=new ViewTreeObserver();
                    OnAttachStateChangeListener attachment;
                    InsetsListener listener;
                    WindowInsets published;
                    void addOnAttachStateChangeListener(OnAttachStateChangeListener l) { attachment=l; }
                    void setOnApplyWindowInsetsListener(InsetsListener l) { listener=l; }
                    ViewTreeObserver getViewTreeObserver() { return observer; }
                    boolean isAttachedToWindow() { return attached; }
                    WindowInsets getRootWindowInsets() { return published; }
                    void getLocationOnScreen(int[] out) { out[0]=x;out[1]=y; }
                    int getWidth() { return width; } int getHeight() { return height; }
                    int getPaddingLeft() { return left; } int getPaddingTop() { return top; }
                    int getPaddingRight() { return right; } int getPaddingBottom() { return bottom; }
                    void setPadding(int l,int t,int r,int b) { left=l;top=t;right=r;bottom=b;paddingWrites++; }
                    void requestApplyInsets() { insetRequests++; }
                    void requestLayout() { layouts++; }
                }
                static class Activity {
                    View decor;
                    Activity(View view) { decor=view; }
                    Activity getWindow() { return this; }
                    View getDecorView() { return decor; }
                }
                """ + "static " + RuntimeSourceFixture.nestedClass("DialogContentInsets", "DialogContentInsets") + """
                public static void verify() {
                    View owner=new View(),content=new View(); owner.attached=true;
                    owner.published=new WindowInsets(Insets.of(0,40,0,0));
                    DialogContentInsets.bind(content,new Activity(owner));
                    check(owner.listener==null && owner.top==6, "native decor was modified");
                    check(owner.observer.layouts.isEmpty(), "detached dialog retained owner listener");
                    content.attached=true; content.attachment.onViewAttachedToWindow(content);
                    check(content.insetRequests==1 && owner.observer.layouts.size()==1, "missing attachment observation");
                    var fit=content.observer.draws.iterator().next();
                    check(!fit.onPreDraw() && content.top==46, "first frame overlaps caption");
                    check(fit.onPreDraw() && content.paddingWrites==1, "padding accumulated or layout did not converge");
                    content.y=240; content.height=460;
                    check(!fit.onPreDraw() && content.top==6, "native fitted caption was counted twice");
                    check(fit.onPreDraw(), "fitted dialog cannot draw");
                    content.y=216;content.height=484;
                    check(!fit.onPreDraw() && content.top==30, "partial caption overlap was lost");
                    content.y=300;content.height=200;
                    check(!fit.onPreDraw() && content.top==6, "centered modal received global caption padding");
                    var consumed=content.listener.apply(content,new WindowInsets(Insets.of(24,0,0,280)));
                    check(consumed.value==Insets.NONE, "children can double count handled sources");
                    check(!fit.onPreDraw() && content.left==32 && content.bottom==286, "IME or cutout missing");
                    check(fit.onPreDraw(), "unchanged IME padding did not converge");
                    content.listener.apply(content,new WindowInsets(Insets.NONE));
                    check(!fit.onPreDraw() && content.bottom==6 && content.left==8, "hidden IME retained padding");
                    content.y=200;content.height=500;
                    owner.published=new WindowInsets(Insets.of(12,60,14,16));
                    int requests=content.layouts;
                    owner.observer.layouts.iterator().next().onGlobalLayout();
                    check(content.layouts>requests, "owner caption change did not request layout");
                    check(!fit.onPreDraw() && content.left==20 && content.top==66 && content.right==22 && content.bottom==22,
                            "owner resize did not update all caption edges");
                    content.attached=false;content.attachment.onViewDetachedFromWindow(content);
                    check(owner.observer.layouts.isEmpty() && content.observer.draws.isEmpty(), "dismissal retained observation");
                    owner.published=new WindowInsets(Insets.NONE);
                    content.attached=true;content.attachment.onViewAttachedToWindow(content);
                    fit=content.observer.draws.iterator().next();
                    check(!fit.onPreDraw() && content.top==6 && content.bottom==6, "reopening captured already-adjusted padding");
                    check(fit.onPreDraw(), "reopened dialog did not converge");
                }
                """);
    }

    @Test public void allAppModalsUseTheSharedCreationBoundary() throws Exception {
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of(RuntimeSourceFixture.MAIN))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = java.nio.file.Files.readString(file);
                assertFalse(file.toString(), java.util.regex.Pattern.compile(
                        "new\\s+(android\\.app\\.)?AlertDialog(\\.Builder)?\\s*\\(").matcher(source).find());
            }
        }
        String create = RuntimeSourceFixture.methods("UiDialogs", "create");
        assertTrue(create.indexOf("dialog.create()") < create.indexOf("DialogContentInsets.bind"));
        assertFalse(create.contains("setOnShowListener"));
        assertFalse(create.contains("setOnDismissListener"));
        assertFalse(RuntimeSourceFixture.methods("UiAppearance", "dialog").contains("DialogContentInsets"));
    }
}
