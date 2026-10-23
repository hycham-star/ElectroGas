package dz.electrogas;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Shared native UI and background IO for invoice screens. */
public class DocumentActivity extends Activity {
    static final int INK=0xff123a3e,TEAL=0xff087e72,MUTED=0xff596d70,BG=0xfff3f6f5;
    LinearLayout root,body;final ExecutorService io=Executors.newSingleThreadExecutor();boolean busy;AlertDialog progress;
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(0,dp(5),0,dp(5));return t;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void screen(String title,String subtitle){
        root=column();root.setBackgroundColor(BG);setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{applyInsets(insets);return insets;});root.requestApplyInsets();
        LinearLayout header=column();header.setPadding(dp(20),dp(12),dp(20),dp(16));header.setBackgroundColor(0xff092d32);root.addView(header);
        TextView back=text("‹  Retour",14,0xff80dfc7,true);back.setMinHeight(dp(44));back.setOnClickListener(v->onBackPressed());header.addView(back);
        header.addView(text(title,26,Color.WHITE,true));header.addView(text(subtitle,13,0xffbdcfd0,false));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=column();body.setPadding(dp(16),dp(16),dp(16),dp(24));scroll.addView(body);
    }
    @SuppressWarnings("deprecation") private void applyInsets(WindowInsets i){if(Build.VERSION.SDK_INT>=30){Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else root.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());}
    LinearLayout card(){LinearLayout c=column();c.setBackground(background(Color.WHITE));c.setPadding(dp(16),dp(12),dp(16),dp(16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);body.addView(c,p);return c;}
    Button button(LinearLayout parent,String label,String tag,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setTag(tag);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:TEAL);b.setBackground(background(primary?TEAL:0xffe3efeb));b.setMinHeight(dp(48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);parent.addView(b,p);b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    void error(String message){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Impossible de terminer").setMessage(message==null?"Veuillez réessayer.":message).setPositiveButton("Compris",null).show();}
    interface Job<T>{T run() throws Exception;}
    interface Done<T>{void accept(T value) throws Exception;}
    <T> void work(String message,Job<T> job,Done<T> done){
        if(busy)return;busy=true;int orientation=getRequestedOrientation();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        ProgressBar spinner=new ProgressBar(this);LinearLayout box=column();box.setPadding(dp(24),dp(16),dp(24),dp(16));box.addView(spinner);box.addView(text(message,15,INK,false));progress=new AlertDialog.Builder(this).setView(box).setCancelable(false).create();progress.show();
        io.execute(()->{T result=null;Exception failure=null;try{result=job.run();}catch(Exception e){failure=e;}final T value=result;final Exception e=failure;
            runOnUiThread(()->{busy=false;if(!isDestroyed()){progress.dismiss();setRequestedOrientation(orientation);if(!isFinishing()){if(e!=null)error(e.getMessage());else try{done.accept(value);}catch(Exception problem){error(problem.getMessage());}}}});
        });
    }
    @Override protected void onDestroy(){if(progress!=null&&progress.isShowing())progress.dismiss();super.onDestroy();io.shutdown();}
    @SuppressWarnings("deprecation") @Override public void onBackPressed(){if(!busy)super.onBackPressed();}
}
