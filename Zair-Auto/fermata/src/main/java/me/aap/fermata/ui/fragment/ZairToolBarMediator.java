/* Zair Auto 0.2.0, 2026-10-14. GPL-3.0. */
package me.aap.fermata.ui.fragment;

import android.view.View;
import android.widget.EditText;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.ZairNavigation;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/** Minimal toolbar; search retains the existing native filtering implementation. */
public final class ZairToolBarMediator implements ToolBarView.Mediator.BackTitleFilter {
    public static final ZairToolBarMediator instance=new ZairToolBarMediator();
    @Override public void enable(ToolBarView tb,ActivityFragment fragment) {
        ToolBarView.Mediator.BackTitleFilter.super.enable(tb,fragment);
        tb.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        update(tb,fragment);
    }
    @Override public int getBackButtonIcon(){return me.aap.utils.R.drawable.menu;}
    @Override public int getBackButtonVisibility(ActivityFragment fragment){return View.VISIBLE;}
    @Override public boolean backOnTitleClick(){return false;}
    @Override public void onClick(View v) {
        MainActivityDelegate a=MainActivityDelegate.get(v.getContext());
        if(v.getId()==getBackButtonId()) {
            if(!onBackPressed(a.getToolBar()))ZairNavigation.show(a);
        } else ToolBarView.Mediator.BackTitleFilter.super.onClick(v);
    }
    @Override public void onActivityEvent(ToolBarView tb,ActivityDelegate a,long event) {
        ToolBarView.Mediator.BackTitleFilter.super.onActivityEvent(tb,a,event);
        if(a.getActiveFragment()!=null)update(tb,a.getActiveFragment());
    }
    @Override public boolean onBackPressed(ToolBarView tb) {
        boolean closed=ToolBarView.Mediator.BackTitleFilter.super.onBackPressed(tb);
        if(closed){
            var keyboard=(android.view.inputmethod.InputMethodManager)tb.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(tb.getWindowToken(),0);
        }
        return closed;
    }
    public void installDrawerButton(ToolBarView tb) {
        if(tb==null || tb.getMediator()==this||tb.findViewById(R.id.zair_drawer_button)!=null)return;
        View button=addButton(tb,me.aap.utils.R.drawable.menu,v->ZairNavigation.show(MainActivityDelegate.get(v.getContext())),R.id.zair_drawer_button,androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT);
        button.setContentDescription(tb.getContext().getString(R.string.menu));
    }
    private void update(ToolBarView tb,ActivityFragment fragment) {
        View search=tb.findViewById(getFilterButtonId());
        EditText input=tb.findViewById(getFilterId());
        if(search==null || input==null) return;
        search.setVisibility(fragment instanceof MediaLibFragment && input.getVisibility()!=View.VISIBLE?View.VISIBLE:View.GONE);
        search.setContentDescription(tb.getContext().getString(R.string.zair_search));
        tb.findViewById(getBackButtonId()).setContentDescription(tb.getContext().getString(R.string.menu));
    }
}
