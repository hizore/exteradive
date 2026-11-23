package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.GapView;

/** Lays out the message menu and reaction picker in chat coordinates. */
public final class RaisedMessagePanels {
    private final ChatScrimPopupContainerLayout container;
    private final ReactionsContainerLayout reactions;
    private final ActionBarPopupWindowLayout popup;
    private final View menu;
    private final View footer;
    private final boolean preferCompact;
    private final RectF bubble = new RectF();
    private final Matrix inverseMatrix = new Matrix();
    private final float[] touchPoint = new float[2];

    private float senderHeight;
    private boolean outgoing;
    private boolean above;
    private boolean sideChosen;
    private boolean modeChosen;
    private boolean layingOut;
    private float initialAnchorHeight;
    private int viewportTop;
    private int viewportBottom = Integer.MAX_VALUE;
    private float initialShiftY;

    RaisedMessagePanels(ChatScrimPopupContainerLayout container, ReactionsContainerLayout reactions,
                        ActionBarPopupWindowLayout popup, View footer, boolean compact) {
        this.container = container;
        this.reactions = reactions;
        this.popup = popup;
        this.menu = popup != null ? popup : container.getChildAt(0);
        this.footer = footer;
        this.preferCompact = compact;
        if (!visible(menu) && footer != null) {
            footer.setVisibility(View.GONE);
        }
        if (visible(reactions)) {
            // The popup can measure taller than its visible page to fit swipe-back submenus.
            reactions.bringToFront();
        }
    }

    /** Call after adding the children. Mount MATCH_PARENT. The flag can force compact mode. */
    public static RaisedMessagePanels attach(ChatScrimPopupContainerLayout container, boolean preferCompact) {
        return container.setRaised(preferCompact);
    }

    /** Set the stable vertical viewport in overlay-local pixels before the first measure. */
    public void setViewport(int top, int bottom) {
        if (viewportTop != top || viewportBottom != bottom) {
            viewportTop = top;
            viewportBottom = bottom;
            container.requestLayout();
        }
    }

    /** The bubble and sender height are expressed in overlay-local pixels. */
    public void updatePlacement(RectF bubble, float senderHeight, boolean outgoing) {
        float sender = Math.max(0, senderHeight);
        if (this.bubble.equals(bubble) && this.senderHeight == sender && this.outgoing == outgoing) {
            return;
        }
        boolean firstAnchor = !sideChosen && this.bubble.isEmpty() && !bubble.isEmpty();
        this.bubble.set(bubble);
        this.senderHeight = sender;
        this.outgoing = outgoing;
        if (firstAnchor) {
            container.requestLayout();
        }
        position();
    }

    /** Read after the first measure with the unshifted bubble, then apply once to the bubble and sender. */
    public float getInitialShiftY() {
        return initialShiftY;
    }

    void measure(int width, int height) {
        int margin = Math.min(dp(20), width / 4);
        int maxWidth = width - 2 * margin;
        int gap = dp(8);
        int top = Math.max(0, viewportTop);
        int bottom = Math.min(height, viewportBottom);
        int viewport = Math.max(0, bottom - top - 2 * gap);
        if (visible(reactions)) {
            // Hint measurement may update the layout-param height on the first pass.
            if (!modeChosen && maxWidth > dp(16) && reactions.getTopOffset() > 0) {
                reactions.measure(exact(maxWidth), exact(reactions.getLayoutParams().height));
                reactions.measureHint();
            }
            int reactionWidth = Math.max(reactions.getTotalWidth(), reactions.getHintTextWidth() + dp(24));
            reactions.measure(exact(Math.min(maxWidth, Math.max(reactionWidth, dp(48)))), exact(reactions.getLayoutParams().height));
        }
        if (!modeChosen && visible(menu)) {
            menu.measure(atMost(maxWidth), atMost(viewport));
        }
        if (visible(footer)) {
            footer.measure(exact(Math.min(maxWidth, mainMenuWidth())), atMost(viewport));
        }
        int reactionHeight = visible(reactions) ? reactions.getMeasuredHeight() + gap : 0;
        float stackHeight = senderHeight + reactionHeight;
        int footerHeight = visible(footer) ? footer.getMeasuredHeight() : 0;
        if (!modeChosen && !bubble.isEmpty()) {
            initialAnchorHeight = bubble.height() + stackHeight;
        }
        int menuLimit = Math.max(0, (int) (viewport - initialAnchorHeight - gap));
        if (!modeChosen && !bubble.isEmpty() && viewport > 0) {
            if (visible(popup) && (preferCompact || mainMenuHeight() + footerHeight > menuLimit)) {
                makeIconStrip(popup);
                if (footer != null) {
                    footer.setVisibility(View.GONE);
                }
                footerHeight = 0;
            }
            modeChosen = true;
        }
        if (visible(menu)) {
            // exteradive: страницы swipe-back («Назад»: список реакций /
            // просмотревшие) раньше измерялись на весь вьюпорт — панель
            // раздувалась на полэкрана. Потолок — 55% вьюпорта, список
            // скроллится внутри.
            int limit;
            if (popup != null && popup.getSwipeBack() != null) {
                limit = Math.min((int) (viewport * 0.55f), Math.max(dp(240), menuLimit - footerHeight));
            } else {
                limit = Math.min(viewport, Math.max(dp(64), menuLimit - footerHeight));
            }
            menu.measure(atMost(maxWidth), atMost(limit));
        }
        if (!sideChosen && !bubble.isEmpty() && viewport > 0) {
            int menuHeight = visible(menu) ? mainMenuHeight() + footerHeight + gap : 0;
            float minShift = top + gap - (bubble.top - stackHeight);
            float maxShift = bottom - gap - bubble.bottom;
            float shiftBelow = closestShift(minShift, maxShift - menuHeight);
            float shiftAbove = closestShift(minShift + menuHeight, maxShift);
            // When the entire stack cannot fit, show the controls above the visible message start.
            above = menuHeight > 0 && (minShift > maxShift - menuHeight || Math.abs(shiftAbove) < Math.abs(shiftBelow));
            initialShiftY = above ? shiftAbove : shiftBelow;
            sideChosen = true;
        }
    }

    private static float closestShift(float lower, float upper) {
        // With an over-height stack, favor visibility of the reaction picker and the start of the message.
        return Math.max(lower, Math.min(0f, upper));
    }

    void layout() {
        layingOut = true;
        layoutChild(reactions);
        layoutChild(menu);
        layoutChild(footer);
        layingOut = false;
        position();
    }

    private static void layoutChild(View view) {
        if (visible(view)) {
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }
    }

    void position() {
        if (layingOut || container.getWidth() == 0 || bubble.isEmpty()) {
            return;
        }
        int width = container.getWidth();
        // exteradive: панели не должны «липнуть» к краю экрана — минимум dp(24)
        int margin = Math.min(dp(24), width / 4);
        int gap = dp(8);
        float upperEdge = bubble.top - senderHeight;
        if (visible(reactions)) {
            reactions.setTranslationX(alignedX(width, margin, reactions.getMeasuredWidth()));
            upperEdge -= gap + reactions.getMeasuredHeight();
            reactions.setTranslationY(upperEdge);
        }
        if (visible(menu)) {
            // exteradive: позиция считается по ФАКТИЧЕСКОЙ ширине попапа.
            // visibleMenuWidth() учитывает скейл перехода — с ним панель
            // уезжала за правый край экрана («липнет к краю»).
            int menuWidth = Math.max(visibleMenuWidth(), menu.getMeasuredWidth());
            int menuHeight = visibleMenuHeight();
            int footerHeight = visible(footer) ? footer.getMeasuredHeight() : 0;
            boolean placeAbove = above;
            PopupSwipeBackLayout swipeBack = popup == null ? null : popup.getSwipeBack();
            if (swipeBack != null && swipeBack.isForegroundOpen()) {
                float spaceAbove = upperEdge - gap - Math.max(0, viewportTop);
                float spaceBelow = Math.min(container.getHeight(), viewportBottom) - bubble.bottom - gap;
                if (menuHeight + footerHeight > (above ? spaceAbove : spaceBelow)) {
                    placeAbove = spaceAbove > spaceBelow;
                }
            }
            float x = alignedX(width, margin, menuWidth);
            float y = placeAbove ? upperEdge - gap - menuHeight - footerHeight : bubble.bottom + gap;
            menu.setTranslationX(x);
            menu.setTranslationY(y);
            if (visible(footer)) {
                footer.setTranslationX(alignedX(width, margin, footer.getMeasuredWidth()));
                footer.setTranslationY(y + menuHeight);
            }
        }
    }

    private float alignedX(int width, int margin, int panelWidth) {
        // exteradive: панель никогда не выходит за поля: ширина ограничена
        // рабочей областью, нижняя граница clamp не может стать больше верхней.
        int usable = Math.max(1, width - 2 * margin);
        int w = Math.min(panelWidth, usable);
        float x = outgoing ? bubble.right - w : bubble.left;
        return Math.max((float) margin, Math.min(x, (float) (width - margin - w)));
    }

    private View mainMenu() {
        PopupSwipeBackLayout swipeBack = popup == null ? null : popup.getSwipeBack();
        return swipeBack != null ? swipeBack.getChildAt(0) : null;
    }

    private int mainMenuWidth() {
        View main = mainMenu();
        return main == null ? visibleMenuWidth() : main.getMeasuredWidth() + popup.getPaddingLeft() + popup.getPaddingRight();
    }

    private int mainMenuHeight() {
        View main = mainMenu();
        return main == null ? visibleMenuHeight() : main.getMeasuredHeight() + popup.getPaddingTop() + popup.getPaddingBottom();
    }

    private int visibleMenuWidth() {
        if (!visible(menu)) return 0;
        return popup == null ? menu.getMeasuredWidth() : Math.round(menu.getMeasuredWidth() * Math.min(1f, popup.getBackScaleX()));
    }

    private int visibleMenuHeight() {
        if (!visible(menu)) return 0;
        return popup == null ? menu.getMeasuredHeight() : Math.min(menu.getMeasuredHeight(), popup.getVisibleHeight());
    }

    void clipToViewport(Canvas canvas) {
        canvas.clipRect(0, Math.max(0, viewportTop), container.getWidth(), Math.min(container.getHeight(), viewportBottom));
    }

    public boolean contains(float x, float y) {
        if (bubble.isEmpty() || x < 0f || y < Math.max(0, viewportTop)
                || x >= container.getWidth() || y >= Math.min(container.getHeight(), viewportBottom)) {
            return false;
        }
        return hit(menu, x, y, visibleMenuWidth(), visibleMenuHeight())
                || hit(reactions, x, y, widthOf(reactions), heightOf(reactions))
                || hit(footer, x, y, widthOf(footer), heightOf(footer))
                || containsSender(x, y);
    }

    /**
     * exteradive: плашка автора (аватар + ник) живёт не в контейнере, а прямо на
     * экране чата, но координаты совпадают — контейнер на весь contentView.
     * Без этого касания до неё не доходили и бабл был некликабельным.
     */
    private View senderView;

    public void setSenderView(View view) {
        senderView = view;
    }

    public boolean containsSender(float x, float y) {
        if (!visible(senderView)) {
            return false;
        }
        return x >= senderView.getX() && x < senderView.getX() + senderView.getWidth()
            && y >= senderView.getY() && y < senderView.getY() + senderView.getHeight();
    }

    private boolean hit(View view, float x, float y, int width, int height) {
        if (!visible(view) || view.getAlpha() <= 0f || !view.getMatrix().invert(inverseMatrix)) {
            return false;
        }
        touchPoint[0] = x - view.getLeft();
        touchPoint[1] = y - view.getTop();
        inverseMatrix.mapPoints(touchPoint);
        return touchPoint[0] >= 0 && touchPoint[0] < width && touchPoint[1] >= 0 && touchPoint[1] < height;
    }

    private static int widthOf(View view) {
        return view == null ? 0 : view.getWidth();
    }

    private static int heightOf(View view) {
        return view == null ? 0 : view.getHeight();
    }

    private static boolean visible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(Math.max(0, size), View.MeasureSpec.EXACTLY);
    }

    private static int atMost(int size) {
        return View.MeasureSpec.makeMeasureSpec(Math.max(0, size), View.MeasureSpec.AT_MOST);
    }

    private static void makeIconStrip(ActionBarPopupWindowLayout popup) {
        if (popup.getItemsCount() == 0) {
            return;
        }
        ViewGroup original = (ViewGroup) popup.getItemAt(0).getParent();
        HorizontalScrollView scroll = new HorizontalScrollView(popup.getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout row = new LinearLayout(popup.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // exteradive: в компактную полосу попадают ТОЛЬКО пункты-иконки и плашка
        // прочитавших. Текстовые подсказки («перевести из … на …») и разделители
        // в строку не берутся — они и создавали светлый блок с текстом. Плашка
        // ставится первой, чтобы галочка была слева, аватары справа от неё, а
        // остальные кнопки сдвигались правее.
        java.util.ArrayList<View> iconItems = new java.util.ArrayList<>();
        java.util.ArrayList<View> textItems = new java.util.ArrayList<>();
        java.util.ArrayList<View> keepItems = new java.util.ArrayList<>();
        java.util.ArrayList<View> seenViews = new java.util.ArrayList<>();
        java.util.ArrayList<View> drop = new java.util.ArrayList<>();
        while (original.getChildCount() > 0) {
            View child = original.getChildAt(0);
            original.removeViewAt(0);
            if (child instanceof GapView || child.getVisibility() != View.VISIBLE) {
                drop.add(child);
                continue;
            }
            if (hasIconSubItem(child)) {
                iconItems.add(child);
            } else if (containsSeenView(child)) {
                seenViews.add(child);
            } else if (child instanceof ActionBarMenuSubItem) {
                // exteradive: пункты без иконки раньше ВЫБРАСЫВАровались из
                // компактной плашки — у пользователя «пропала половина кнопок».
                // Теперь они остаются текстовыми таблетками в той же строке.
                textItems.add(child);
            } else if (child.isClickable() || child.isLongClickable()) {
                // exteradive: кликабельные кастомные строки (сводка «N реакций»
                // с аватарами, «Кто просмотрел») — вход в swipe-back подменю,
                // выбрасывать нельзя. Некликабельные подсказки — по-прежнему DROP.
                keepItems.add(child);
            } else {
                drop.add(child);
            }
        }
        for (View seen : seenViews) {
            // exteradive: MessageSeenView переопределяет свою ширину по ширине
            // родителя, поэтому в горизонтальной строке он растягивался на весь
            // ряд и аватары встав поверх кнопок. Оборачиваем его в контейнер
            // фиксированной ширины (галочка + три аватара): плашка стоит первой,
            // аватары — справа от галочки, а кнопки сдвигаются правее.
            android.widget.FrameLayout seenBox = new android.widget.FrameLayout(popup.getContext());
            seenBox.addView(seen, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(96), dp(44));
            params.weight = 0;
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            row.addView(seenBox, params);
        }
        for (View item : iconItems) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.weight = 0;
            params.width = compactItem((ActionBarMenuSubItem) item);
            item.setMinimumWidth(0);
            row.addView(item, params);
        }
        for (View item : textItems) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                compactTextItem((ActionBarMenuSubItem) item), dp(44));
            params.weight = 0;
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            row.addView(item, params);
        }
        for (View item : keepItems) {
            // сводка реакций и т.п.: натуральная высота, ширина до dp(260)
            item.measure(atMost(dp(260)), atMost(dp(48)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(dp(48), item.getMeasuredWidth()), dp(44));
            params.weight = 0;
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            row.addView(item, params);
        }
        popup.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popup.setFitItems(false);
        popup.setMinimumWidth(0);
    }

    /**
     * exteradive: пункт меню без иконки в компактной строке — текстовой
     * таблеткой: одна строка, конец обрезается многоточием, подпункт скрыт.
     */
    private static int compactTextItem(ActionBarMenuSubItem item) {
        item.getTextView().setAlpha(1f);
        item.getTextView().setMaxLines(1);
        item.getTextView().setEllipsize(TextUtils.TruncateAt.END);
        if (item.subtextView != null) {
            item.subtextView.setVisibility(View.GONE);
        }
        ImageView icon = item.getImageView();
        if (icon != null) {
            icon.setVisibility(View.GONE);
        }
        item.setFocusable(item.isClickable() || item.isLongClickable());
        item.setPadding(dp(14), 0, dp(14), 0);
        item.setMinimumWidth(0);
        item.measure(atMost(dp(220)), View.MeasureSpec.makeMeasureSpec(dp(44), View.MeasureSpec.EXACTLY));
        int w = Math.max(dp(44), item.getMeasuredWidth());
        return Math.min(w, dp(220));
    }

    /** Пункт меню с видимой иконкой — только такие попадают в компактную полосу. */
    private static boolean hasIconSubItem(View view) {
        if (!(view instanceof ActionBarMenuSubItem)) {
            return false;
        }
        ActionBarMenuSubItem item = (ActionBarMenuSubItem) view;
        android.widget.ImageView icon = item.getImageView();
        return icon != null && icon.getVisibility() == View.VISIBLE
            && (icon.getDrawable() != null || item.getIconResId() != 0);
    }

    /** Строка «кто прочитал»: галочка + аватары. */
    private static boolean containsSeenView(View view) {
        if (view instanceof org.telegram.ui.MessageSeenView || view instanceof org.telegram.ui.Components.MessagePrivateSeenView) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsSeenView(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int compactItem(ActionBarMenuSubItem item) {
        ImageView icon = item.getImageView();
        if (icon.getVisibility() != View.VISIBLE || icon.getDrawable() == null && item.getIconResId() == 0) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        CharSequence label = item.getContentDescription();
        if (TextUtils.isEmpty(label)) {
            label = item.getTextView().getText();
        }
        item.setContentDescription(label);
        item.setFocusable(item.isClickable() || item.isLongClickable());
        item.getTextView().setAlpha(0f);
        item.getTextView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (item.subtextView != null) {
            item.subtextView.setVisibility(View.GONE);
        }
        item.setPadding(0, 0, 0, 0);
        item.setMinimumWidth(0);
        ImageView arrow = item.getRightIcon();
        boolean separateAction = arrow != null && arrow.getVisibility() == View.VISIBLE && arrow.isClickable();
        FrameLayout.LayoutParams iconParams = (FrameLayout.LayoutParams) icon.getLayoutParams();
        iconParams.width = dp(48);
        iconParams.gravity = Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (arrow != null && arrow.getVisibility() == View.VISIBLE) {
            if (separateAction) {
                arrow.setMinimumWidth(dp(48));
                arrow.getLayoutParams().width = dp(48);
                arrow.setContentDescription(label + ", " + LocaleController.getString(R.string.AccDescrMoreOptions));
                arrow.setFocusable(true);
            } else {
                arrow.setVisibility(View.GONE);
            }
        }
        return dp(separateAction ? 96 : 48);
    }
}
