/* Zair Auto 0.2.0, 2026-10-14. GPL-3.0. */
package me.aap.fermata.ui.view;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

/** Perspective is applied only to visible views; no artwork copies or bitmap transforms. */
public final class ZairCoverFlow {
    private final RecyclerView list;
    private final LinearSnapHelper snap = new LinearSnapHelper();
    private boolean enabled;
    public ZairCoverFlow(RecyclerView list) {
        this.list = list;
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView v, int dx, int dy) { transform(); }
        });
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View v) { list.post(ZairCoverFlow.this::transform); }
            @Override public void onChildViewDetachedFromWindow(@NonNull View v) { reset(v); }
        });
        list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> transform());
    }
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        snap.attachToRecyclerView(value ? list : null);
        if (!value) for (int i=0; i<list.getChildCount(); i++) reset(list.getChildAt(i));
        else list.post(this::transform);
    }
    private static void reset(View v) {
        v.setScaleX(1); v.setScaleY(1); v.setRotationY(0); v.setAlpha(1); v.setTranslationY(0);
    }
    private void transform() {
        if (!enabled || list.getWidth() == 0) return;
        float center = list.getWidth() / 2f;
        for (int i=0; i<list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            float distance = (child.getLeft()+child.getWidth()/2f-center) / Math.max(1, child.getWidth());
            float fraction = Math.min(1f, Math.abs(distance));
            float scale = 1f - .12f * fraction;
            child.setCameraDistance(8000 * list.getResources().getDisplayMetrics().density);
            child.setScaleX(scale); child.setScaleY(scale);
            child.setRotationY(-Math.max(-1, Math.min(1, distance))*10);
            child.setAlpha(1f-.16f*fraction);
            child.setTranslationY((list.getHeight()-child.getHeight())/2f-child.getTop());
        }
    }
}
