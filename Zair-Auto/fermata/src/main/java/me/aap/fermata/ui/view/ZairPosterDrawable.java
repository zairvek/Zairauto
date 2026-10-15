/* Zair Auto 0.2.0, 2026-10-14. GPL-3.0. Original vector artwork for section covers. */
package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

public final class ZairPosterDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    private final Drawable icon;
    private final String label;
    public ZairPosterDrawable(Context c, int resource, int color, String label) {
        this.color = color; this.label = label;
        Drawable d = AppCompatResources.getDrawable(c, resource);
        icon = d == null ? null : d.mutate();
        if (icon != null) icon.setTint(0xffe7f4ff);
    }
    @Override public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        canvas.save(); canvas.translate(b.left, b.top);
        float w=b.width(), h=b.height();
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{color,0xff08111d},null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0,0,w,h,5,5,paint); paint.setShader(null);
        paint.setColor(0x1fffffff);
        canvas.drawCircle(w*.95f,h*.36f,w*.7f,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,w*.005f));
        paint.setColor(0x32ffffff);
        for (int i=0;i<5;i++) canvas.drawCircle(w*.95f,h*.36f,w*(.34f+i*.14f),paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x70000000);canvas.drawRect(0,0,w*.045f,h,paint);
        paint.setColor(0x30ffffff);canvas.drawRect(w*.045f,0,w*.052f,h,paint);
        paint.setColor(0xcce4f2ff);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(w*.044f);canvas.drawText("ZAIR AUTO",w*.12f,h*.1f,paint);
        if(icon!=null) {
            int size=(int)(w*.42f), left=(int)((w-size)/2);
            icon.setBounds(left,(int)(h*.34f),left+size,(int)(h*.34f)+size);icon.draw(canvas);
        }
        paint.setTextSize(w*.078f);paint.setColor(0xfff5f9ff);
        String title=label;
        while (title.length()>2 && paint.measureText(title)>w*.76f) title=title.substring(0,title.length()-1);
        if(!title.equals(label)) title += "…";
        canvas.drawText(title,w*.12f,h*.87f,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(0x6680a8c7);
        canvas.drawRoundRect(1,1,w-1,h-1,5,5,paint);paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
    @Override public void setAlpha(int value) { paint.setAlpha(value); }
    @Override public void setColorFilter(@Nullable ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
