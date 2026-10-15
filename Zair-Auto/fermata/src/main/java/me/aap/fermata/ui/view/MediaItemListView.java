// Zair Auto modification, 2026-10-14: horizontal cover shelves. GPL-3.0.
package me.aap.fermata.ui.view;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.ui.UiUtils.isVisible;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.app.App;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemListView extends RecyclerView implements PreferenceStore.Listener {
	private boolean isSelectionActive;
	private boolean grid;
	private ZairCoverFlow coverFlow;
	private int focusReq;

	public MediaItemListView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		coverFlow = new ZairCoverFlow(this);
		configure(ctx.getResources().getConfiguration());
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setWillNotDraw(false); // The empty-state message is drawn in onDraw().
	}

	/** RecyclerView never calls performClick(), so taps on the empty library are detected here. */
	@Override
	public boolean onTouchEvent(android.view.MotionEvent e) {
		boolean handled = super.onTouchEvent(e);
		if ((e.getActionMasked() == android.view.MotionEvent.ACTION_UP) && isEmptyLibraryRoot()
				&& ((e.getEventTime() - e.getDownTime()) < android.view.ViewConfiguration.getLongPressTimeout())
				&& (getActivity().getActiveFragment() instanceof me.aap.fermata.ui.fragment.FoldersFragment f)) {
			f.addFolder();
			return true;
		}
		return handled;
	}

	private boolean isEmptyLibraryRoot() {
		var adapter = (MediaItemListViewAdapter) super.getAdapter();
		if ((adapter == null) || (adapter.getItemCount() != 0) || adapter.isLoading() || adapter.isFiltered())
			return false;
		var f = getActivity().getActiveFragment();
		return (f instanceof me.aap.fermata.ui.fragment.FoldersFragment ff) && (ff.getView() == this) && ff.isRootPage();
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		configure(newConfig);
	}

	private void configure(Configuration cfg) {
		Context ctx = getContext();
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			MainActivityPrefs prefs = a.getPrefs();
			grid = a.isGridView();

			if (grid) {
                setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
                coverFlow.setEnabled(true);
                setClipToPadding(false); setOverScrollMode(OVER_SCROLL_NEVER);
                setHorizontalScrollBarEnabled(false); setVerticalScrollBarEnabled(false);
                setBackground(ZairNavigation.background(ctx));
                int edge = Math.max(ZairNavigation.dp(ctx,12), (getWidth()-ZairNavigation.dp(ctx,coverGeometry().itemWidth))/2);
                setPadding(edge,0,edge,0);
            } else {
                coverFlow.setEnabled(false); setPadding(0,0,0,0);
                setBackground(null); setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
                setVerticalScrollBarEnabled(true);
				setLayoutManager(new LinearLayoutManager(ctx));
			}
		});
	}

    public me.aap.fermata.util.ZairCoverGeometry coverGeometry() {
        float density=getResources().getDisplayMetrics().density;
        var cfg=getResources().getConfiguration();
        return me.aap.fermata.util.ZairCoverGeometry.fit(
            getWidth()>0?Math.round(getWidth()/density):cfg.screenWidthDp,
            getHeight()>0?Math.round(getHeight()/density):cfg.screenHeightDp-110,cfg.fontScale);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        if (!grid || !(getLayoutManager() instanceof LinearLayoutManager lm) || lm.getOrientation()!=LinearLayoutManager.HORIZONTAL) return;
        int edge=Math.max(ZairNavigation.dp(getContext(),12),(w-ZairNavigation.dp(getContext(),coverGeometry().itemWidth))/2);
        setPadding(edge,0,edge,0);
        post(()->{
            var a=getActivity();
            for(int i=0;i<getChildCount();i++) {
                if(getChildAt(i) instanceof MediaItemView item) item.setSize(getContext(),true,a.getPrefs().getTextIconSizePref(a));
            }
        });
    }

    private android.graphics.Paint emptyTitlePaint, emptyHintPaint;

    @Override public void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        var adapter=(MediaItemListViewAdapter)super.getAdapter();
        // Nothing to say while the folder is still loading: the progress bar is visible.
        if(adapter==null||adapter.getItemCount()!=0||adapter.isLoading()||adapter.getParent()==null)return;
        Context ctx=getContext();
        if(emptyTitlePaint==null){
            emptyTitlePaint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            emptyTitlePaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            emptyTitlePaint.setTextSize(ZairNavigation.dp(ctx,18));
            emptyHintPaint=new android.graphics.Paint(emptyTitlePaint);
            emptyHintPaint.setTextSize(ZairNavigation.dp(ctx,13));
        }
        int color=ZairNavigation.color(ctx,android.R.attr.textColorSecondary);
        emptyTitlePaint.setColor(color);emptyHintPaint.setColor(color);
        float cx=getScrollX()+getWidth()/2f, cy=getScrollY()+getHeight()/2f;
        String title=getResources().getString(adapter.isFiltered()?me.aap.fermata.R.string.zair_no_results:me.aap.fermata.R.string.zair_no_items);
        canvas.drawText(title,cx,cy,emptyTitlePaint);
        if(isEmptyLibraryRoot())
            canvas.drawText(getResources().getString(me.aap.fermata.R.string.zair_empty_library_hint),cx,cy+ZairNavigation.dp(ctx,28),emptyHintPaint);
    }

	@NonNull
	@Override
	public MediaItemListViewAdapter getAdapter() {
		return (MediaItemListViewAdapter) requireNonNull(super.getAdapter());
	}

	public boolean isSelectionActive() {
		return isSelectionActive;
	}

	public void select(boolean select) {
		if (!select && !isSelectionActive) return;

		boolean selectAll = isSelectionActive;
		isSelectionActive = select;

		for (MediaItemWrapper w : getAdapter().getList()) {
			if (selectAll) w.setSelected(select, true);
			else w.refreshViewCheckbox();
		}
	}

	public void discardSelection() {
		select(false);
	}

	public void refresh() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refresh();
		}
	}

	public void refreshState() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refresh();
		}
	}

	@Override
	public void smoothScrollToPosition(int position) {
		scrollToPosition(position);
	}

	@Override
	public void scrollToPosition(int position) {
		scrollToPosition(position, true);
	}

	public void scrollToPosition(int position, boolean focus) {
		List<MediaItemWrapper> list = getAdapter().getList();
		if ((position < 0) || (position >= list.size())) return;

		focusReq = -1;
		super.scrollToPosition(position);
		if (!focus || !isVisible(this)) return;
		MainActivityDelegate a = getActivity();
		if (a.getBody().isVideoMode()) return;
		MediaItemWrapper w = list.get(position);
		MediaItemViewHolder h = w.getViewHolder();
		if ((h != null) && h.isAttached() && (h.getItemWrapper() == w)) h.getItemView().requestFocus();
		else focusReq = position;
	}

	public int getScrollPosition() {
		List<MediaItemWrapper> list = getAdapter().getList();
		if (list.isEmpty()) return 0;

		for (int i = 0, n = getChildCount(); i < n; i++) {
			MediaItemView v = (MediaItemView) getChildAt(i);
			MediaItemWrapper w = v.getItemWrapper();
			if (w == null) continue;
			MediaItemViewHolder h = w.getViewHolder();
			if ((h != null) && h.isAttached() && (h.getItemWrapper() == w)) {
				for (int j = 0, s = list.size(); j < s; j++) {
					if (list.get(j) == w) return j;
				}
			}
		}
		return 0;
	}

	void holderAttached(MediaItemViewHolder h) {
		if ((focusReq != -1) && (h.getAdapterPosition() == focusReq)) {
			focusReq = -1;
			h.getItemView().requestFocus();
		}
	}

	public void pageUp() {
		int cnt = getChildCount();
		if (cnt == 0) return;
		MediaItemWrapper w = ((MediaItemView) getChildAt(0)).getItemWrapper();
		if (w == null) return;
		int idx = getAdapter().getList().indexOf(w);
		if (idx > 0) scrollToPosition(Math.max(idx - cnt, 0), false);
	}

	public void pageDown() {
		int cnt = getChildCount();
		if (cnt == 0) return;
		MediaItemWrapper w = ((MediaItemView) getChildAt(cnt - 1)).getItemWrapper();
		if (w == null) return;
		int idx = getAdapter().getList().indexOf(w);
		if (idx < 0) return;
		int size = getAdapter().getList().size();
		scrollToPosition(Math.min(idx + cnt, size - 1), false);
	}

	@Nullable
	public static View focusSearchFirst(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		if (f instanceof MediaLibFragment) {
			MediaItemListView v = ((MediaLibFragment) f).getListView();
			List<MediaItemWrapper> list = v.getAdapter().getList();
			return ((list != null) && !list.isEmpty()) ? v.focusTo(focused, list, 0) : v.focusEmpty();
		}
		return null;
	}

	@Nullable
	public static View focusSearchFirstVisible(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		if (f instanceof MediaLibFragment) {
			MediaItemListView lv = ((MediaLibFragment) f).getListView();
			LinearLayoutManager lm = (LinearLayoutManager) lv.getLayoutManager();
			if (lm != null) {
				int pos = lm.findFirstVisibleItemPosition();
				if (pos != RecyclerView.NO_POSITION) {
					return lv.getAdapter().getList().get(pos).getView();
				}
			}
			View first = lv.getChildAt(0);
			return (first != null) ? first : lv.focusEmpty();
		}
		return focused;
	}

	@Nullable
	public static View focusSearchLast(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		if (f instanceof MediaLibFragment) {
			MediaItemListView v = ((MediaLibFragment) f).getListView();
			List<MediaItemWrapper> list = v.getAdapter().getList();
			return ((list != null) && !list.isEmpty())
					? v.focusTo(focused, list, list.size() - 1)
					: v.focusEmpty();
		}
		return null;
	}

	public static void focusActive(Context ctx, @Nullable View focused) {
		View v = focusSearchActive(ctx, focused);
		if (v != null) App.get().getHandler().post(v::requestFocus);
	}

	public static View focusSearchActive(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		return (f instanceof MediaLibFragment)
				? (((MediaLibFragment) f).getListView()).findActive(focused) : null;
	}

	private View findActive(View focused) {
		List<MediaItemWrapper> list = getAdapter().getList();
		MainActivityDelegate a = getActivity();
		PlayableItem p = a.getCurrentPlayable();

		if (p != null) {
			for (int i = 0, n = list.size(); i < n; i++) {
				MediaItemWrapper w = list.get(i);
				if (w.getItem() == p) return focusTo(focused, w, i);
			}
		}

		int n = list.size();
		if (n == 0) return focusEmpty();

		for (int i = 0; i < n; i++) {
			MediaItemWrapper w = list.get(i);
			Item item = w.getItem();
			if ((item instanceof PlayableItem) && ((PlayableItem) item).isLastPlayed()) {
				return focusTo(focused, w, i);
			}
		}

		return focusTo(focused, list.get(0), 0);
	}

	private View focusEmpty() {
		View v = getActivity().getFloatingButton();
		return isVisible(v) ? v : this;
	}

	@Override
	public View focusSearch(@Nullable View focused, int direction) {
		if (!(focused instanceof MediaItemView) || (focused.getParent() != this)) return focused;
		if (MainActivityDelegate.get(focused.getContext()).getActiveMenu() instanceof View v)
			return v.findFocus();

		if (grid) {
            if (getLayoutManager() instanceof LinearLayoutManager lm && lm.getOrientation()==LinearLayoutManager.HORIZONTAL) {
                if(direction==FOCUS_UP)return focusUp(focused);
                if(direction==FOCUS_DOWN)return focusDown(focused);
            }
			if (direction == FOCUS_LEFT) {
				List<MediaItemWrapper> list = getAdapter().getList();
				if (list != null) return focusTo(focused, list, indexOf(list, focused) - 1);
			} else if (direction == FOCUS_RIGHT) {
				List<MediaItemWrapper> list = getAdapter().getList();
				int idx = indexOf(list, focused);
				if ((idx != -1) && (idx < list.size() - 1)) return focusTo(focused, list, idx + 1);
				else return focusRight(focused);
			}

			return super.focusSearch(focused, direction);
		}

		if (direction == FOCUS_LEFT) return focusLeft(focused);
		if (direction == FOCUS_RIGHT) return focusRight(focused);

		List<MediaItemWrapper> list = getAdapter().getList();

		if ((list == null) || list.isEmpty()) {
			return (direction == FOCUS_UP) ? focusUp(focused) : focusDown(focused);
		}

		ViewHolder vh = getChildViewHolder(focused);
		if (!(vh instanceof MediaItemViewHolder)) return focused;

		int pos = vh.getAdapterPosition();
		if ((pos < 0) || (pos >= list.size())) return focused;
		return focusTo(focused, list, (direction == FOCUS_UP) ? (pos - 1) : (pos + 1));
	}

	private View focusLeft(@Nullable View focused) {
		MainActivityDelegate a = getActivity();
		NavBarView n = a.getNavBar();
		if (isVisible(n) && n.isLeft()) return n.focusSearch();
		List<MediaItemWrapper> list = getAdapter().getList();
		return ((list != null) && !list.isEmpty()) ? focusTo(focused, list, 0) : focused;
	}

	private View focusRight(@Nullable View focused) {
		MainActivityDelegate a = getActivity();
		BodyLayout b = a.getBody();
		if (b.isBothMode() || b.isVideoMode()) return b.getVideoView();
		View v = a.getFloatingButton();
		if (isVisible(v)) return v;
		NavBarView n = a.getNavBar();
		return (isVisible(n) && n.isRight()) ? n.focusSearch() : focused;
	}

	private View focusUp(@Nullable View focused) {
		ToolBarView tb = getActivity().getToolBar();
		if (isVisible(tb)) return tb.focusSearch();

		List<MediaItemWrapper> list = getAdapter().getList();
		return ((list != null) && !list.isEmpty()) ? focusTo(focused, list, list.size() - 1) : focused;
	}

	private View focusDown(@Nullable View focused) {
		MainActivityDelegate a = getActivity();
		ControlPanelView p = a.getControlPanel();
		if (isVisible(p)) return p.focusSearch();

		NavBarView n = a.getNavBar();
		if (isVisible(n) && n.isBottom()) return n.focusSearch();

		List<MediaItemWrapper> list = getAdapter().getList();
		return ((list != null) && !list.isEmpty()) ? focusTo(focused, list, 0) : focused;
	}

	@Nullable
	public MediaItemView focusTo(Item i) {
		List<MediaItemWrapper> list = getAdapter().getList();
		int pos = 0;
		for (MediaItemWrapper w : list) {
			if (i == w.getItem()) {
				View focus = focusTo(null, w, pos);
				if (focus != null) focus.requestFocus();
				return (MediaItemView) focus;
			}
			pos++;
		}
		return null;
	}

	public View focusTo(@Nullable View focused, List<MediaItemWrapper> list, int pos) {
		if (pos < 0) return focusUp(focused);
		if (pos >= list.size()) return focusDown(focused);
		return focusTo(focused, list.get(pos), pos);
	}

	private View focusTo(@Nullable View focused, MediaItemWrapper w, int pos) {
		MediaItemViewHolder h = w.getViewHolder();

		if ((h != null) && h.isAttached() && (h.getItemWrapper() == w)) {
			super.scrollToPosition(pos);
			return h.getItemView();
		} else {
			focusReq = pos;
			super.scrollToPosition(pos);
			return focused;
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		MainActivityDelegate a = getActivity();

		if (MainActivityPrefs.hasGridViewPref(a, prefs)) {
			configure(getContext().getResources().getConfiguration());
			MediaLibFragment f = a.getActiveMediaLibFragment();

			if ((f != null) && (f.getView() == this)) {
				MediaLib.BrowsableItem i = getAdapter().getParent();

				if (i != null) {
					i.getLastPlayedItem().onSuccess(pi -> {
						if (pi != null) f.revealItem(pi);
					});
				}
			}
		}
	}

	@NonNull
	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private int indexOf(List<MediaItemWrapper> list, View v) {
		for (int i = 0, n = list.size(); i < n; i++) {
			MediaItemViewHolder h = list.get(i).getViewHolder();
			if ((h != null) && (h.getItemView() == v) && h.isAttached()) return i;
		}
		return -1;
	}
}
