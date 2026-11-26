"""Exercise the production card/strip placement with measured pages, without a device."""
from pathlib import Path
import subprocess


def test_chat_card_reserves_only_the_visible_page(tmp_path):
    source = Path('TMessagesProj/src/main/java/org/telegram/ui/Components/ChatPreviewMenu.java').read_text()
    constants = next(line for line in source.splitlines() if 'float CARD_TOP' in line)
    method = source[source.index('    public void measureWithCard('):]
    method = method[:method.index('\n    }') + 6]
    java = '''
class ChatPreviewLayoutTest {
    CONSTANTS
    static final int density = 3;
    static int dp(int d) { return d * density; }
    static class LocaleController { static boolean isRTL; }
    static class MeasureSpec {
        static final int AT_MOST = 2;
        static int makeMeasureSpec(int size, int mode) {
            if (size < 0 || mode != AT_MOST) throw new AssertionError("invalid constraint");
            return size;
        }
    }
    static class FrameLayout { static class LayoutParams { int leftMargin, topMargin, bottomMargin; } }
    static class View {
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams();
        Object getLayoutParams() { return lp; }
        int getMeasuredHeight() { return dp(48); }
    }
    static class ViewGroup extends View {
        int bottom;
        int getPaddingLeft() { return 0; }
        int getPaddingRight() { return 0; }
        int getPaddingBottom() { return bottom; }
        View getChildAt(int i) { return new View(); }
    }
    final ViewGroup parent = new ViewGroup();
    final View menu = new View(), card = new View();
    boolean hasFolders;
    float progress;
    int width, height;
    Object getParent() { return parent; }
    Object getLayoutParams() { return menu.lp; }
    ViewGroup getSwipeBack() { return hasFolders ? parent : null; }
    int getPaddingTop() { return dp(8); }
    int getPaddingBottom() { return dp(8); }
    void measure(int maxW, int maxH) {
        width = Math.min(maxW, dp(hasFolders ? 320 : 256));
        height = Math.min(maxH, dp(hasFolders ? 650 : 64));
    }
    int getMeasuredWidth() { return width; }
    int getMeasuredHeight() { return height; }
    float getBackScaleX() {
        int stripW = Math.min(width, dp(256));
        return (stripW + (width - stripW) * progress) / width;
    }
    int getVisibleHeight() {
        int stripH = Math.min(height, dp(64));
        return stripH + Math.round((height - stripH) * progress);
    }
    METHOD
    static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    void exercise(int w, int h, int status, int nav, int bar, boolean rtl) {
        LocaleController.isRTL = rtl;
        parent.bottom = nav;
        hasFolders = false; progress = 0;
        measureWithCard(card, w, h, status, bar);
        int resting = h - nav - card.lp.bottomMargin;
        int ceiling = card.lp.topMargin + bar;
        check(ceiling >= status && ceiling >= bar, "card behind top inset");
        if (h == 2400 && nav == dp(24) && status == dp(28)) {
            check(ceiling == 216 && resting == 2088, "reference portrait bounds");
        }
        hasFolders = true;
        int anchor = -1, previous = 0;
        for (float page : new float[] {0, .5f, 1, 0}) {
            progress = page;
            measureWithCard(card, w, h, status, bar);
            int cardBottom = h - nav - card.lp.bottomMargin;
            int menuBottom = bar + menu.lp.topMargin + height;
            int visibleTop = menuBottom - getVisibleHeight();
            int visibleRight = menu.lp.leftMargin + Math.round(width * getBackScaleX());
            check(card.lp.topMargin + bar == ceiling && cardBottom > ceiling, "card disappears");
            check(visibleTop - cardBottom == dp(8) && menuBottom <= h - nav, "menu overlaps card or system bar");
            check(menu.lp.leftMargin >= 0 && visibleRight <= w, "menu outside screen");
            check(rtl ? w - visibleRight == dp(24) : menu.lp.leftMargin == dp(24), "wrong start edge");
            if (anchor < 0) anchor = menuBottom;
            check(menuBottom == anchor, "folder page moved bottom anchor");
            check(page == 0 ? cardBottom == resting : cardBottom < previous, "hidden folder rows consumed card space");
            previous = cardBottom;
        }
    }
    public static void main(String[] args) {
        for (boolean rtl : new boolean[] {false, true}) {
            new ChatPreviewLayoutTest().exercise(1080, 2400, dp(28), dp(24), 0, rtl);
            new ChatPreviewLayoutTest().exercise(900, 1200, dp(48), dp(48), dp(56), rtl);
        }
    }
}
'''.replace('CONSTANTS', constants).replace('METHOD', method)
    runner = tmp_path / 'ChatPreviewLayoutTest.java'
    runner.write_text(java)
    subprocess.run(['java', str(runner)], check=True)
