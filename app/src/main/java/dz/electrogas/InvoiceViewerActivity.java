package dz.electrogas;

import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;

public final class InvoiceViewerActivity extends DocumentActivity {
    JSONObject bill;String stage;int index,page;InvoiceStore store;ZoomPage image;TextView caption;Bitmap current;
    @Override public void onCreate(Bundle state){super.onCreate(state);try{store=new InvoiceStore(this);bill=new JSONObject(getIntent().getStringExtra("bill"));stage=getIntent().getStringExtra("stage");index=state==null?getIntent().getIntExtra("index",0):state.getInt("index");page=state==null?0:state.getInt("page");
        screen("Copie de facture","Pincez pour zoomer, glissez pour déplacer.");root.removeView((android.view.View)body.getParent());
        image=new ZoomPage();root.addView(image,new LinearLayout.LayoutParams(-1,0,3));caption=text("",14,INK,true);caption.setTag("invoice.viewer.caption");caption.setGravity(Gravity.CENTER);root.addView(caption);
        LinearLayout controls=new LinearLayout(this);root.addView(controls);Button prev=new Button(this),next=new Button(this);prev.setText("‹ Précédente");next.setText("Suivante ›");controls.addView(prev,new LinearLayout.LayoutParams(0,-2,1));controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));prev.setOnClickListener(v->move(-1));next.setOnClickListener(v->move(1));load();
    }catch(Exception e){error(e.getMessage());}}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("index",index);out.putInt("page",page);super.onSaveInstanceState(out);}
    void load(){work("Lecture du document…",()->InvoiceStore.preview(store.media(bill,bill.getJSONArray("files").getJSONObject(index),stage),page),bitmap->{if(current!=null)current.recycle();current=bitmap;image.set(bitmap);int count=InvoiceStore.pageCount(store.media(bill,bill.getJSONArray("files").getJSONObject(index),stage));caption.setText("Document "+(index+1)+" / "+bill.getJSONArray("files").length()+" · Page "+(page+1)+" / "+count);});}
    void move(int direction){if(busy)return;try{JSONArray files=bill.getJSONArray("files");int count=InvoiceStore.pageCount(store.media(bill,files.getJSONObject(index),stage));if(direction>0){if(page+1<count)page++;else if(index+1<files.length()){index++;page=0;}else return;}else{if(page>0)page--;else if(index>0){index--;page=InvoiceStore.pageCount(store.media(bill,files.getJSONObject(index),stage))-1;}else return;}load();}catch(Exception e){error(e.getMessage());}}
    @Override protected void onDestroy(){super.onDestroy();if(current!=null)current.recycle();}
    final class ZoomPage extends android.view.View {
        Bitmap bitmap;float zoom=1,offsetX,offsetY,lastX,lastY;final ScaleGestureDetector pinch;
        ZoomPage(){super(InvoiceViewerActivity.this);setContentDescription("Copie de facture, zoom par pincement");pinch=new ScaleGestureDetector(InvoiceViewerActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(1,Math.min(6,zoom*d.getScaleFactor()));clamp();invalidate();return true;}});}
        void set(Bitmap b){bitmap=b;zoom=1;offsetX=offsetY=0;invalidate();}
        float fit(){return bitmap==null?1:Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());}
        void clamp(){if(bitmap==null)return;float s=fit()*zoom,maxX=Math.max(0,(bitmap.getWidth()*s-getWidth())/2),maxY=Math.max(0,(bitmap.getHeight()*s-getHeight())/2);offsetX=Math.max(-maxX,Math.min(maxX,offsetX));offsetY=Math.max(-maxY,Math.min(maxY,offsetY));}
        @Override protected void onDraw(Canvas canvas){canvas.drawColor(0xffdce5e4);if(bitmap!=null&&!bitmap.isRecycled()){clamp();float s=fit()*zoom,w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);canvas.drawBitmap(bitmap,null,new RectF((getWidth()-w)/2+offsetX,(getHeight()-h)/2+offsetY,(getWidth()+w)/2+offsetX,(getHeight()+h)/2+offsetY),p);}}
        @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!pinch.isInProgress()){offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;clamp();invalidate();}lastX=e.getX();lastY=e.getY();return true;}
    }
}
