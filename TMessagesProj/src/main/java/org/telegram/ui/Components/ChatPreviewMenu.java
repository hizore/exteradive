package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import org.telegram.ui.ActionBar.Theme;

/** Bottom-anchored actions for a dialog preview; only the current page occupies space. */
public final class ChatPreviewMenu extends ActionBarPopupWindowLayout {
    // Bounds of the resting card in the reference portrait layout.
    private static final float CARD_TOP = .09f, CARD_BOTTOM = .87f;
    private LinearLayout actions;

    public ChatPreviewMenu(Context context, Theme.ResourcesProvider resourcesProvider, boolean hasFolders) {
        super(context, R.drawable.popup_fixed_alert4, resourcesProvider,
                FLAG_SHOWN_FROM_BOTTOM | (hasFolders ? FLAG_USE_SWIPEBACK : 0));
        if (hasFolders) {
            getSwipeBack().addOnSwipeBackProgressListener((layout, target, progress) -> {
                if (getParent() != null) getParent().requestLayout();
            });
        }
    }

    /** Keep the original buttons accessible to drag-to-select inside the horizontal strip. */
    public void compactActions() {
        if (actions == null) actions = RaisedMessagePanels.makeIconStrip(this);
    }

    public int getActionCount() {
        return actions == null ? 0 : actions.getChildCount();
    }

    public View getActionAt(int index) {
        return actions.getChildAt(index);
    }

    /** Lay out the resting strip and card together, above navigation and below any cutout. */
    public void measureWithCard(View card, int width, int height, int topInset, int actionBarHeight) {
        ViewGroup container = (ViewGroup) getParent();
        width = Math.max(0, width - container.getPaddingLeft() - container.getPaddingRight());
        int usableBottom = Math.max(0, height - container.getPaddingBottom());
        FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
        FrameLayout.LayoutParams menuParams = (FrameLayout.LayoutParams) getLayoutParams();
        int cardTop = Math.max(actionBarHeight, Math.max(topInset, Math.round(height * CARD_TOP)));
        cardParams.topMargin = cardTop - actionBarHeight;
        int side = Math.min(dp(24), width / 4);
        int gap = dp(8);
        int available = Math.max(0, usableBottom - cardTop - 2 * gap);
        int maxHeight = Math.min(available, Math.min(dp(368), Math.max(dp(64), available / 2)));
        measure(MeasureSpec.makeMeasureSpec(width - 2 * side, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));

        // Anchor to the main strip even while the folder page is open; it grows upward.
        int stripHeight = getSwipeBack() == null ? getMeasuredHeight()
                : getSwipeBack().getChildAt(0).getMeasuredHeight() + getPaddingTop() + getPaddingBottom();
        int bottomSpace = Math.max(gap, usableBottom - Math.round(height * CARD_BOTTOM) - stripHeight - gap);
        int visibleWidth = Math.round(getMeasuredWidth() * getBackScaleX());
        menuParams.leftMargin = LocaleController.isRTL ? width - side - visibleWidth : side;
        menuParams.topMargin = usableBottom - actionBarHeight - bottomSpace - getMeasuredHeight();
        cardParams.bottomMargin = bottomSpace + getVisibleHeight() + gap;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // A hidden folder page can be taller/wider than the strip. Taps in that
        // transparent part belong to the chat card (or the backdrop).
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getSwipeBack() != null) {
            float width = getWidth() * getBackScaleX();
            if (event.getY() < getHeight() - getVisibleHeight() || event.getX() >= width) {
                return false;
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
