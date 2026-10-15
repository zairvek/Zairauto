/* Zair Auto 0.2.0, 2026-10-14. GPL-3.0; based on Fermata by Andrey Pavlenko. */
package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.view.ZairCoverFlow;
import me.aap.fermata.ui.view.ZairNavigation;
import me.aap.fermata.ui.view.ZairPosterDrawable;
import me.aap.fermata.util.ZairCoverGeometry;
import me.aap.utils.async.FutureSupplier;

/** A single shelf of covers; media sections live in the navigation drawer. */
public final class ZairHomeFragment extends MainActivityFragment implements MediaSessionCallback.Listener {
    private Shelf shelf;
    private CoverAdapter adapter;
    private TextView status;
    private MediaSessionCallback callback;
    private String displayedId;
    private int generation;
    @Override public int getFragmentId(){return R.id.zair_home_fragment;}
    @Override public CharSequence getTitle(){return "Zair Auto";}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state) {
        Context c=requireContext();LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ZairNavigation.background(c));
        shelf=new Shelf(c);adapter=new CoverAdapter();shelf.setAdapter(adapter);
        root.addView(shelf,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(c);status.setGravity(Gravity.CENTER);status.setTextSize(12);
        status.setTextColor(ZairNavigation.color(c,android.R.attr.textColorSecondary));
        status.setPadding(dp(18),dp(8),dp(18),dp(18));status.setMaxLines(2);
        status.setText(R.string.zair_shelf_hint);root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        refresh();return root;
    }
    @Override public void onStart(){
        super.onStart();callback=getActivityDelegate().getMediaSessionCallback();callback.addBroadcastListener(this);refresh();
    }
    @Override public void onStop(){
        if(callback!=null)callback.removeBroadcastListener(this);callback=null;super.onStop();
    }
    @Override public void onHiddenChanged(boolean hidden){super.onHiddenChanged(hidden);if(!hidden&&shelf!=null)refresh();}
    @Override public void onPlaybackStateChanged(MediaSessionCallback cb,PlaybackStateCompat state){
        if(shelf==null||isHidden())return;
        shelf.post(()->{
            if(shelf==null||!isAdded()||isHidden())return;
            var current=getActivityDelegate().getMediaServiceBinder().getCurrentItem();
            String id=current==null?null:current.getId();
            if(!java.util.Objects.equals(id,displayedId))refresh();
            else updateStatus();
        });
    }
    private void updateStatus(){
        if(status==null)return;
        var binder=getActivityDelegate().getMediaServiceBinder();
        if(binder.getCurrentItem()==null)status.setText(R.string.zair_shelf_hint);
        else status.setText(binder.isPlaying()?R.string.zair_playing:R.string.zair_shelf_hint);
    }
    private void refresh(){
        if(shelf==null||adapter==null)return;
        final int request=++generation;
        var current=getActivityDelegate().getMediaServiceBinder().getCurrentItem();
        displayedId=current==null?null:current.getId();
        List<Entry> entries=sections();
        if(current!=null)entries.add(0,recent(current));
        adapter.replace(entries);updateStatus();
        if(current==null)getActivityDelegate().getLib().getLastPlayedItem().main().onSuccess(item->{
            if(item==null||generation!=request||shelf==null||adapter==null)return;
            List<Entry> updated=sections();updated.add(0,recent(item));adapter.replace(updated);
        });
    }
    private List<Entry> sections(){
        List<Entry> result=new ArrayList<>();
        result.add(new Entry(getString(R.string.zair_library),me.aap.utils.R.drawable.folder,0xff214e78,()->open(R.id.folders_fragment),null));
        result.add(new Entry(getString(R.string.favorites),R.drawable.favorite_filled,0xff5f346d,()->open(R.id.favorites_fragment),null));
        result.add(new Entry(getString(R.string.playlists),R.drawable.playlist,0xff69512c,()->open(R.id.playlists_fragment),null));
        for(FermataAddon addon:AddonManager.get().getAddons()){
            if(!(addon instanceof FermataFragmentAddon))continue;
            var info=addon.getInfo();
            // The other installed tools remain accessible in the drawer.
            if(!info.className.contains("web")&&!info.className.contains("tv"))continue;
            int color=info.className.contains("Youtube")?0xff843c46:0xff275c62;
            result.add(new Entry(getString(info.addonName),info.icon,color,()->{
                var active=AddonManager.get().getAddon(info.className);
                if(active instanceof FermataFragmentAddon section)open(section.getFragmentId());
                else open(R.id.settings_fragment);
            },null));
        }
        result.add(new Entry(getString(R.string.zair_open_link),R.drawable.web,0xff344a6d,()->ZairNavigation.openLink(getActivityDelegate()),null));
        return result;
    }
    private Entry recent(PlayableItem item){
        return new Entry(item.getName(),item.getIcon(),0xff2e445b,()->{
            var a=getActivityDelegate();
            if(a.getMediaServiceBinder().isPlaying()&&item.equals(a.getMediaServiceBinder().getCurrentItem()))a.goToCurrent();
            else if(a.showFragment(R.id.folders_fragment)!=null)a.getMediaServiceBinder().playItem(item);
        },item);
    }
    private void open(int id){
        var a=getActivityDelegate();
        a.post(()->{if(isAdded()&&!isHidden())a.showFragment(id);});
    }
    @Override public void onDestroyView(){
        generation++;
        if(shelf!=null)shelf.setAdapter(null);
        shelf=null;adapter=null;status=null;displayedId=null;super.onDestroyView();
    }
    private int dp(float value){return ZairNavigation.dp(requireContext(),value);}
    private static final class Entry {
        final String title;final int icon,color;final Runnable action;final PlayableItem item;
        Entry(String t,int i,int c,Runnable a,PlayableItem item){title=t;icon=i;color=c;action=a;this.item=item;}
    }
    private final class Shelf extends RecyclerView {
        ZairCoverGeometry geometry;
        final LinearLayoutManager manager;
        Shelf(Context c){
            super(c);setClipToPadding(false);setClipChildren(false);setOverScrollMode(OVER_SCROLL_NEVER);
            setHorizontalScrollBarEnabled(false);setItemAnimator(null);
            manager=new LinearLayoutManager(c,LinearLayoutManager.HORIZONTAL,false);setLayoutManager(manager);
            var cfg=getResources().getConfiguration();geometry=ZairCoverGeometry.fit(cfg.screenWidthDp,cfg.screenHeightDp-110,cfg.fontScale);
            new ZairCoverFlow(this).setEnabled(true);
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            super.onSizeChanged(w,h,oldw,oldh);if(w==0||h==0)return;
            float density=getResources().getDisplayMetrics().density;
            geometry=ZairCoverGeometry.fit(Math.round(w/density),Math.round(h/density),getResources().getConfiguration().fontScale);
            int edge=Math.max(dp(12),(w-dp(geometry.itemWidth))/2);setPadding(edge,0,edge,0);
            post(()->{if(getAdapter()!=null)getAdapter().notifyDataSetChanged();});
        }
    }
    private final class CoverAdapter extends RecyclerView.Adapter<CoverHolder> {
        List<Entry> entries=new ArrayList<>();
        void replace(List<Entry> value){entries=value;notifyDataSetChanged();}
        @Override public int getItemCount(){return entries.size();}
        @NonNull @Override public CoverHolder onCreateViewHolder(@NonNull ViewGroup p,int type){
            return new CoverHolder(p.getContext());
        }
        @Override public void onBindViewHolder(@NonNull CoverHolder holder,int position){
            holder.clear();Entry entry=entries.get(position);holder.bound=entry;
            var geometry=shelf.geometry;
            holder.root.setLayoutParams(new RecyclerView.LayoutParams(dp(geometry.itemWidth),ViewGroup.LayoutParams.MATCH_PARENT));
            holder.cover.setLayoutParams(new LinearLayout.LayoutParams(dp(geometry.posterWidth),dp(geometry.posterHeight)));
            holder.cover.setImageDrawable(new ZairPosterDrawable(holder.root.getContext(),entry.icon,entry.color,entry.title));
            holder.title.setText(entry.title);holder.root.setContentDescription(entry.title);
            holder.root.setOnClickListener(v->entry.action.run());
            holder.root.setOnLongClickListener(v->{ZairNavigation.show(getActivityDelegate());return true;});
            if(entry.item==null)return;
            holder.metadata=entry.item.getMediaDescription().main().onSuccess(description->{
                if(holder.bound!=entry||description==null||shelf==null)return;
                if(description.getTitle()!=null)holder.title.setText(description.getTitle());
                Bitmap bitmap=description.getIconBitmap();
                if(bitmap!=null){holder.cover.setImageBitmap(bitmap);return;}
                if(description.getIconUri()==null)return;
                holder.artwork=entry.item.getLib().getBitmap(description.getIconUri().toString(),true,true).main().onSuccess(image->{
                    if(holder.bound==entry&&image!=null&&shelf!=null)holder.cover.setImageBitmap(image);
                });
            });
        }
        @Override public void onViewRecycled(@NonNull CoverHolder holder){holder.clear();}
    }
    private final class CoverHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;final ImageView cover;final TextView title;
        Entry bound;FutureSupplier<?> metadata,artwork;
        CoverHolder(Context c){super(new LinearLayout(c));root=(LinearLayout)itemView;
            root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);
            root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setFocusable(true);root.setClipChildren(false);
            cover=new ImageView(c);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable frame=new GradientDrawable();frame.setColor(0xff0b1420);frame.setCornerRadius(dp(4));frame.setStroke(dp(1),0xff4b657c);
            cover.setBackground(frame);cover.setPadding(dp(2),dp(2),dp(2),dp(2));cover.setClipToOutline(true);
            cover.setElevation(dp(4));cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(cover);
            title=new TextView(c);title.setTextSize(16);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setMinLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(ZairNavigation.color(c,android.R.attr.textColorPrimary));title.setPadding(0,dp(16),0,0);
            root.addView(title,new LinearLayout.LayoutParams(-1,-2));
            root.setOnFocusChangeListener((v,focused)->{if(focused&&getBindingAdapterPosition()!=RecyclerView.NO_POSITION&&shelf!=null)shelf.smoothScrollToPosition(getBindingAdapterPosition());});
        }
        void clear(){bound=null;if(metadata!=null)metadata.cancel();if(artwork!=null)artwork.cancel();metadata=null;artwork=null;cover.setImageDrawable(null);}
    }
}
