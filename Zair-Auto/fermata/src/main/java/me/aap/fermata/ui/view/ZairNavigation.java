/* Zair Auto 0.2.0, 2026-10-14. GPL-3.0. */
package me.aap.fermata.ui.view;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.fermata.media.lib.IntentPlayable;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.ToolBarMediator;
import me.aap.fermata.util.ZairPlaybackGuard;
import me.aap.utils.ui.activity.ActivityListener;

/** One navigation drawer, shared by Home, the library, favorites and settings. */
public final class ZairNavigation {
    private ZairNavigation() {}
    public static int dp(Context c, float value) {
        return Math.round(value*c.getResources().getDisplayMetrics().density);
    }
    public static int color(Context c, int attr) {
        TypedValue value=new TypedValue();c.getTheme().resolveAttribute(attr,value,true);
        return value.resourceId==0 ? value.data : c.getColor(value.resourceId);
    }
    public static boolean light(Context c) {
        TypedValue value=new TypedValue();c.getTheme().resolveAttribute(android.R.attr.isLightTheme,value,true);
        return value.data!=0;
    }
    public static GradientDrawable background(Context c) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, light(c) ?
                new int[]{0xfff5f8fc,0xffdce9f5} : new int[]{0xff080d12,0xff0c273c});
    }
    private static void own(MainActivityDelegate a, Dialog dialog) {
        ActivityListener listener=(delegate,event)->dialog.dismiss();
        a.addBroadcastListener(listener,ActivityListener.ACTIVITY_DESTROY);
        dialog.setOnDismissListener(d->a.removeBroadcastListener(listener));
    }
    public static void show(MainActivityDelegate a) {
        if(a.isCarActivity()) {showCarMenu(a);return;}
        Context c=a.getContext();
        Dialog dialog=new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(c);
        LinearLayout panel=new LinearLayout(c);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(c,16),dp(c,26),dp(c,16),dp(c,24));
        panel.setBackgroundColor(light(c)?0xfff4f8fd:0xff101c29);
        scroll.addView(panel);
        TextView brand=new TextView(c);brand.setText("Zair Auto");brand.setTextSize(27);
        brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        brand.setTextColor(color(c,android.R.attr.textColorPrimary));brand.setPadding(dp(c,14),0,0,dp(c,22));
        panel.addView(brand);
        if(!a.isRootPage()) row(panel,dialog,me.aap.utils.R.drawable.back,R.string.zair_back,a::onBackPressed);
        row(panel,dialog,R.drawable.zair_home,R.string.zair_home,()->a.showFragment(R.id.zair_home_fragment));
        row(panel,dialog,me.aap.utils.R.drawable.folder,R.string.zair_library,()->a.showFragment(R.id.folders_fragment));
        row(panel,dialog,R.drawable.favorite_filled,R.string.favorites,()->a.showFragment(R.id.favorites_fragment));
        row(panel,dialog,R.drawable.playlist,R.string.playlists,()->a.showFragment(R.id.playlists_fragment));
        divider(panel);
        for(FermataAddon addon:AddonManager.get().getAddons()) {
            if(!(addon instanceof FermataFragmentAddon))continue;
            var info=addon.getInfo();
            row(panel,dialog,info.icon,info.addonName,()->{
                FermataAddon current=AddonManager.get().getAddon(info.className);
                if(current instanceof FermataFragmentAddon section) a.showFragment(section.getFragmentId());
                else a.showFragment(R.id.settings_fragment);
            });
        }
        divider(panel);
        row(panel,dialog,R.drawable.add_folder,R.string.add_folder,()->addFolder(a));
        row(panel,dialog,R.drawable.web,R.string.zair_open_link,()->openLink(a));
        if(a.getActiveMediaLibFragment()!=null) {
            row(panel,dialog,R.drawable.view_grid,R.string.zair_change_view,()->a.getPrefs().setGridViewPref(a,!a.isGridView()));
            row(panel,dialog,R.drawable.sort,R.string.zair_sort,()->ToolBarMediator.onSortButtonClick(new View(c)));
        }
        row(panel,dialog,R.drawable.equalizer,R.string.zair_sound,()->a.showFragment(R.id.audio_effects_fragment));
        row(panel,dialog,R.drawable.settings,R.string.settings,()->a.showFragment(R.id.settings_fragment));
        row(panel,dialog,R.drawable.more,R.string.zair_share_report,()->me.aap.fermata.util.ZairDiagnostics.share(a));
        row(panel,dialog,R.drawable.more,R.string.zair_compatibility,()->new AlertDialog.Builder(c).setTitle(R.string.zair_compatibility).setMessage(R.string.zair_auto_modes).setPositiveButton(android.R.string.ok,null).show());
        row(panel,dialog,R.drawable.more,R.string.zair_more,()->a.getNavBarMediator().showMenu(a));
        dialog.setContentView(scroll);own(a,dialog);dialog.show();
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs=window.getAttributes();attrs.gravity=Gravity.START|Gravity.TOP;attrs.dimAmount=.62f;
            window.setAttributes(attrs);
            window.setLayout(Math.min(dp(c,320),Math.round(c.getResources().getDisplayMetrics().widthPixels*.86f)),WindowManager.LayoutParams.MATCH_PARENT);
        }
        scroll.setTranslationX(-dp(c,320));scroll.animate().translationX(0).setDuration(180).start();
    }
    private static void aPost(Context c, Runnable action) {
        var a=MainActivityDelegate.get(c);
        a.post(()->{if(!a.getSupportFragmentManager().isDestroyed() && !a.getSupportFragmentManager().isStateSaved())action.run();});
    }
    private static void showCarMenu(MainActivityDelegate a) {
        a.getToolBarMenu().show(b->{
            if(!a.isRootPage())b.addItem(me.aap.utils.R.id.tool_bar_back_button,me.aap.utils.R.drawable.back,R.string.zair_back).setHandler(i->{a.post(a::onBackPressed);return true;});
            b.addItem(R.id.zair_home_fragment,R.drawable.zair_home,R.string.zair_home).setHandler(i->{a.post(()->a.showFragment(R.id.zair_home_fragment));return true;});
            b.addItem(R.id.folders_fragment,me.aap.utils.R.drawable.folder,R.string.zair_library).setHandler(i->{a.post(()->a.showFragment(R.id.folders_fragment));return true;});
            b.addItem(R.id.favorites_fragment,R.drawable.favorite_filled,R.string.favorites).setHandler(i->{a.post(()->a.showFragment(R.id.favorites_fragment));return true;});
            b.addItem(R.id.playlists_fragment,R.drawable.playlist,R.string.playlists).setHandler(i->{a.post(()->a.showFragment(R.id.playlists_fragment));return true;});
            for(FermataAddon addon:AddonManager.get().getAddons()) {
                if(addon instanceof FermataFragmentAddon section) {
                    var info=addon.getInfo();
                    b.addItem(section.getFragmentId(),info.icon,info.addonName).setHandler(i->{a.post(()->a.showFragment(section.getFragmentId()));return true;});
                }
            }
            b.addItem(R.id.settings_fragment,R.drawable.settings,R.string.settings).setHandler(i->{a.post(()->a.showFragment(R.id.settings_fragment));return true;});
            b.addItem(R.id.menu,R.drawable.more,R.string.zair_more).setHandler(i->{a.post(()->a.getNavBarMediator().showMenu(a));return true;});
        });
    }
    private static void divider(LinearLayout panel) {
        Context c=panel.getContext();View line=new View(c);line.setBackgroundColor(light(c)?0x18102438:0x20ffffff);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(c,1));lp.setMargins(dp(c,12),dp(c,12),dp(c,12),dp(c,12));panel.addView(line,lp);
    }
    private static void row(LinearLayout panel,Dialog dialog,int icon,int title,Runnable action) {
        Context c=panel.getContext();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c,14),dp(c,14),dp(c,14),dp(c,14));row.setMinimumHeight(dp(c,52));
        row.setFocusable(true);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x306bade1),new ColorDrawable(0),new ColorDrawable(-1)));
        ImageView image=new ImageView(c);image.setImageResource(icon);image.setImageTintList(ColorStateList.valueOf(light(c)?0xff216296:0xffa6d0f1));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(image,new LinearLayout.LayoutParams(dp(c,23),dp(c,23)));
        TextView text=new TextView(c);text.setText(title);text.setTextSize(16);text.setPadding(dp(c,16),0,0,0);
        text.setTextColor(color(c,android.R.attr.textColorPrimary));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->{dialog.dismiss();aPost(c,action);});panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    public static void addFolder(MainActivityDelegate a) {
        if(a.showFragment(R.id.folders_fragment) instanceof FoldersFragment fragment) fragment.addFolder();
    }
    public static void openLink(MainActivityDelegate a) {
        Context c=a.getContext();EditText input=new EditText(c);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://…/video.mp4");input.setMaxLines(3);input.setPadding(dp(c,20),dp(c,16),dp(c,20),dp(c,16));
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(R.string.zair_open_link).setMessage(R.string.zair_link_message)
                .setView(input).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.zair_play,null).create();
        own(a,dialog);
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            // Links copied from a browser or file manager often contain plain spaces.
            String value=input.getText().toString().trim().replace(" ","%20");
            if(!ZairPlaybackGuard.isMediaUrl(value)){input.setError(c.getString(R.string.zair_invalid_link));return;}
            try {
                if(a.showFragment(R.id.folders_fragment)==null)return;
                a.getMediaServiceBinder().playItem(new IntentPlayable(a,Uri.parse(value)));dialog.dismiss();
            } catch(RuntimeException ex){input.setError(c.getString(R.string.zair_play_error));}
        }));
        dialog.show();
    }
}
