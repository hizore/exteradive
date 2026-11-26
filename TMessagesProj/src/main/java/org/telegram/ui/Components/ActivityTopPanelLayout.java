package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

/** The mini-player draws itself; each contiguous group of other rows has its own rounded glass. */
public abstract class ActivityTopPanelLayout extends AnimatedLinearLayout implements Theme.Colorable {
    protected final Path clipPath = new Path();
    protected FragmentContextView callFragmentContextView;
    private final RectF content = new RectF(), playerGap = new RectF();
    private BlurredBackgroundDrawableViewFactory glassFactory;
    private BlurredBackgroundColorProvider glassColors;
    private BlurredBackgroundDrawable glass, lowerGlass;
    private int radiusDp;

    protected ActivityTopPanelLayout(Context context, int radiusDp) {
        super(context);
        this.radiusDp = radiusDp;
        setOrientation(VERTICAL);
    }

    public void setBlurredBackground(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colors) {
        glassFactory = factory;
        glassColors = colors;
        glass = createGlass();
        lowerGlass = null;
        updatePanelBounds();
    }

    private BlurredBackgroundDrawable createGlass() {
        return glassFactory.create(this, glassColors).setPadding(dp(7));
    }

    public void setDefaultRadiusDp(int radiusDp) {
        this.radiusDp = radiusDp;
        updatePanelBounds();
    }

    public void setCallFragmentContextView(FragmentContextView view) {
        callFragmentContextView = view;
        view.getCapsuleBlobDrawable().setCallback(this);
        updatePanelBounds();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || callFragmentContextView != null && callFragmentContextView.getCapsuleBlobDrawable() == who;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updatePanelBounds();
    }

    @Override
    protected void onItemsChanged() {
        updatePanelBounds();
    }

    private void updatePanelBounds() {
        content.set(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getPaddingTop() + getMetadata().getTotalHeight());
        findPlayerGap(callFragmentContextView, content, playerGap);
        clipPath.rewind();
        boolean split = !playerGap.isEmpty();
        boolean both = split && playerGap.top > content.top && playerGap.bottom < content.bottom;
        if (both && glass != null && lowerGlass == null) lowerGlass = createGlass();
        if (!split) {
            setSegment(glass, content.top, content.bottom);
        } else {
            clipPath.addRect(playerGap, Path.Direction.CW);
            if (playerGap.top > content.top) {
                setSegment(glass, content.top, playerGap.top);
                if (both) setSegment(lowerGlass, playerGap.bottom, content.bottom);
            } else {
                setSegment(glass, playerGap.bottom, content.bottom);
            }
        }
        if (!both && lowerGlass != null) lowerGlass.setBounds(0, 0, 0, 0);
        invalidate();
    }

    private void setSegment(BlurredBackgroundDrawable drawable, float top, float bottom) {
        if (bottom <= top || content.width() <= 0) {
            if (drawable != null) drawable.setBounds(0, 0, 0, 0);
            return;
        }
        float r = Math.min(dp(radiusDp), Math.min(content.width(), bottom - top) / 2f);
        clipPath.addRoundRect(content.left, top, content.right, bottom, r, r, Path.Direction.CW);
        if (drawable != null) {
            int p = dp(7);
            drawable.setBounds((int) content.left - p, Math.round(top) - p, (int) content.right + p, Math.round(bottom) + p);
            drawable.setRadius(r);
            drawable.setAlpha((int) (255 * getMetadata().getTotalVisibility()));
        }
    }

    protected void drawPanelGlass(Canvas canvas) {
        if (glass != null && !glass.getPaddedBounds().isEmpty()) glass.draw(canvas);
        if (lowerGlass != null && !lowerGlass.getPaddedBounds().isEmpty()) lowerGlass.draw(canvas);
    }

    protected boolean inPlayerGap(float y) {
        return !playerGap.isEmpty() && y >= playerGap.top && y <= playerGap.bottom;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int x = (int) ev.getX(), y = (int) ev.getY();
        return super.dispatchTouchEvent(ev) || ev.getActionMasked() == MotionEvent.ACTION_DOWN &&
            (glass != null && glass.getPaddedBounds().contains(x, y) || lowerGlass != null && lowerGlass.getPaddedBounds().contains(x, y));
    }

    @Override
    public void updateColors() {
        if (glass != null) glass.updateColors();
        if (lowerGlass != null) lowerGlass.updateColors();
        invalidate();
    }
}
