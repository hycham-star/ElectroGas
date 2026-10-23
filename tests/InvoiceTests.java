package dz.electrogas.tests;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import dz.electrogas.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Runs inside Android; no external UI hierarchy polling or third-party test framework. */
public final class InvoiceTests extends Instrumentation {
    int checks;File sandbox;Activity activity;String uiId;InvoiceStore live;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    interface Action{void run() throws Exception;}
    void fails(Action action,String label) throws Exception {boolean failed=false;try{action.run();}catch(IOException e){failed=true;}check(failed,label);}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;try{storeTests();uiTests();result.putString("result","PASS: "+checks+" invoice checks (private copies, quota, edits, PDF, backup/restore, malformed archives, UI import and persistence).");}catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("result","FAIL: "+e);android.util.Log.e("InvoiceTests","Failed",e);}finally{if(activity!=null)runOnMainSync(()->activity.finish());if(live!=null&&uiId!=null)try{live.delete(uiId);}catch(Exception e){code=Activity.RESULT_CANCELED;result.putString("result","FAIL: test cleanup: "+e);}if(sandbox!=null)remove(sandbox);}finish(code,result);}
    static void remove(File f){File[] files=f.listFiles();if(files!=null)for(File child:files)remove(child);f.delete();}
    Context isolated(String name){File base=new File(sandbox,name);return new ContextWrapper(getTargetContext()){
        @Override public File getFilesDir(){File f=new File(base,"files");f.mkdirs();return f;}
        @Override public File getCacheDir(){File f=new File(base,"cache");f.mkdirs();return f;}
    };}
    byte[] jpeg(){Bitmap b=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.BLACK);p.setTextSize(32);c.drawText("FACTURE DE TEST",40,90,p);c.drawText("3157,01 DA",40,160,p);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,90,out);b.recycle();return out.toByteArray();}
    JSONObject bill(InvoiceStore store,String token) throws Exception {JSONObject b=InvoiceStore.create();b.put("title","Facture de test").put("amount","3157.01").put("period","T3 2026");b.getJSONArray("files").put(store.importMedia(new ByteArrayInputStream(jpeg()),token));return b;}
    byte[] zip(String[] paths,byte[][] contents) throws Exception {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){for(int i=0;i<paths.length;i++){z.putNextEntry(new ZipEntry(paths[i]));z.write(contents[i]);z.closeEntry();}}return bytes.toByteArray();}
    void storeTests() throws Exception {
        sandbox=new File(getTargetContext().getCacheDir(),"invoice-tests-"+UUID.randomUUID());sandbox.mkdirs();Context c=isolated("source");InvoiceStore store=new InvoiceStore(c);
        check(store.list().isEmpty(),"initial empty history");String stage=InvoiceStore.id();JSONObject first=bill(store,stage);String firstId=first.getString("id");store.save(first,stage);
        check(store.list().size()==1,"save invoice");check(new InvoiceStore(c).get(firstId).getString("amount").equals("3157.01"),"metadata survives reopening");
        File photo=store.media(first,first.getJSONArray("files").getJSONObject(0),null);check(photo.isFile(),"independent private media copy");check(!new File(store.staging(stage),photo.getName()).exists(),"staging removed after save");
        first.put("paid",true);store.save(first,InvoiceStore.id());check(store.get(firstId).getBoolean("paid"),"paid status edit");
        File pdf=store.exportPdf(first);check(pdf.length()>0,"PDF export created");String pdfStage=InvoiceStore.id();JSONObject pdfMedia;try(InputStream in=new FileInputStream(pdf)){pdfMedia=store.importMedia(in,pdfStage);}check(pdfMedia.getString("mime").equals("application/pdf"),"PDF reimport");
        Bitmap preview=InvoiceStore.preview(photo,0);check(preview.getWidth()>0,"image preview");preview.recycle();preview=InvoiceStore.preview(pdf,1);check(preview.getHeight()>0,"PDF preview");preview.recycle();
        for(int i=1;i<InvoiceStore.FREE_LIMIT;i++){String token=InvoiceStore.id();store.save(bill(store,token),token);}check(store.list().size()==5,"five free invoices");
        String sixthStage=InvoiceStore.id();JSONObject sixth=bill(store,sixthStage);fails(()->store.save(sixth,sixthStage),"sixth invoice blocked");check(store.list().size()==5,"quota failure preserves history");
        first.put("title","Facture modifiée");store.save(first,InvoiceStore.id());check(store.get(firstId).getString("title").equals("Facture modifiée"),"editing allowed at free limit");
        ByteArrayOutputStream out=new ByteArrayOutputStream();store.backup(out);byte[] backup=out.toByteArray();check(backup.length>0,"portable backup created");
        InvoiceStore restored=new InvoiceStore(isolated("restored"));check(restored.restore(new ByteArrayInputStream(backup))==5,"restore all invoices");check(restored.list().size()==5,"restored count");
        JSONObject changed=restored.get(firstId);changed.put("title","Modification locale");restored.save(changed,InvoiceStore.id());check(restored.restore(new ByteArrayInputStream(backup))==0,"idempotent restore");check(restored.get(firstId).getString("title").equals("Modification locale"),"restore does not overwrite edits");
        String extraStage=InvoiceStore.id();JSONObject extra=bill(restored,extraStage);JSONObject meta=new JSONObject().put("version",1).put("invoices",new JSONArray().put(extra));JSONObject media=extra.getJSONArray("files").getJSONObject(0);String path=extra.getString("id")+"/"+media.getString("name");byte[] mediaBytes;try(InputStream in=new FileInputStream(restored.media(extra,media,extraStage))){mediaBytes=bytes(in);}
        byte[] extraBackup=zip(new String[]{"index.json",path},new byte[][]{meta.toString().getBytes("UTF-8"),mediaBytes});check(restored.restore(new ByteArrayInputStream(extraBackup))==1,"restoration allowed over free limit");check(restored.list().size()==6,"six restored documents remain accessible");
        byte[] traversal=zip(new String[]{"../outside","index.json"},new byte[][]{"bad".getBytes("UTF-8"),new JSONObject().put("version",1).put("invoices",new JSONArray()).toString().getBytes("UTF-8")});fails(()->restored.restore(new ByteArrayInputStream(traversal)),"reject ZIP traversal");
        byte[] missing=zip(new String[]{"index.json"},new byte[][]{meta.toString().getBytes("UTF-8")});fails(()->restored.restore(new ByteArrayInputStream(missing)),"reject missing attachment");check(restored.list().size()==6,"bad restore leaves history intact");
        JSONObject invalid=new JSONObject(first.toString()).put("amount","NaN");fails(()->InvoiceStore.validate(invalid),"invalid amount rejected");invalid.put("amount","1.00").put("date","2026-02-30");fails(()->InvoiceStore.validate(invalid),"invalid date rejected");
        fails(()->store.importMedia(new ByteArrayInputStream("not an image".getBytes("UTF-8")),InvoiceStore.id()),"invalid media rejected");
        restored.delete(firstId);check(restored.list().size()==5,"delete metadata");check(!new File(new File(isolated("restored").getFilesDir(),"invoices"),firstId).exists(),"delete private media");
    }
    byte[] bytes(InputStream in) throws IOException {ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}
    View find(View root,String tag){if(tag.equals(root.getTag()))return root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i),tag);if(f!=null)return f;}}return null;}
    void click(String tag){runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),tag);if(v==null)throw new AssertionError("Missing "+tag);v.performClick();});waitForIdleSync();}
    void waitForSaved(String id) throws Exception {long deadline=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<deadline){try{live.get(id);return;}catch(IOException e){Thread.sleep(100);}}throw new AssertionError("UI save timeout");}
    // Metadata is committed before the save callback closes the editor. Do not reuse a task being removed.
    void waitForClosed(Activity editor) throws Exception {long deadline=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<deadline){final boolean[] destroyed={false};runOnMainSync(()->destroyed[0]=editor.isDestroyed());if(destroyed[0]){waitForIdleSync();return;}Thread.sleep(100);}throw new AssertionError("Saved editor did not finish within 15 seconds");}
    void uiTests() throws Exception {
        live=new InvoiceStore(getTargetContext());if(live.list().size()>=5)throw new AssertionError("Use a test device with a free invoice slot; existing documents are never erased");
        final String uiTitle="Facture test UI "+UUID.randomUUID().toString().substring(0,8);
        // Camera monitor writes a full-sized synthetic photo to the actual provider output URI.
        ActivityMonitor camera=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(!android.provider.MediaStore.ACTION_IMAGE_CAPTURE.equals(intent.getAction()))return null;try{android.net.Uri uri=intent.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT);try(OutputStream output=getTargetContext().getContentResolver().openOutputStream(uri)){output.write(jpeg());}return new ActivityResult(Activity.RESULT_OK,null);}catch(Exception e){throw new RuntimeException(e);}}};addMonitor(camera);
        Intent edit=new Intent(getTargetContext(),InvoiceActivity.class).putExtra("mode","edit").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);activity=startActivitySync(edit);waitForIdleSync();
        runOnMainSync(()->((EditText)find(activity.getWindow().getDecorView(),"invoice.title")).setText(uiTitle));click("invoice.camera");
        long deadline=System.currentTimeMillis()+25000;boolean photoReady=false;while(System.currentTimeMillis()<deadline){final boolean[] found={false};runOnMainSync(()->found[0]=find(activity.getWindow().getDecorView(),"invoice.preview.0")!=null);if(found[0]){photoReady=true;break;}Thread.sleep(100);}check(photoReady,"camera output copied into editor");removeMonitor(camera);
        runOnMainSync(()->((EditText)find(activity.getWindow().getDecorView(),"invoice.amount")).setText("4321,50"));click("invoice.save");
        deadline=System.currentTimeMillis()+25000;JSONObject saved=null;while(System.currentTimeMillis()<deadline){for(JSONObject row:live.list())if(row.optString("title").equals(uiTitle))saved=row;if(saved!=null)break;Thread.sleep(100);}check(saved!=null,"UI save completed");uiId=saved.getString("id");check(saved.getString("amount").equals("4321.50"),"French decimal input normalized");
        waitForClosed(activity);
        File importedPdf=live.exportPdf(saved);final android.net.Uri importedUri=InvoiceProvider.exported(importedPdf.getName());
        ActivityMonitor documents=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){return Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())?new ActivityResult(Activity.RESULT_OK,new Intent().setData(importedUri)):null;}};addMonitor(documents);
        activity=startActivitySync(new Intent(getTargetContext(),InvoiceActivity.class).putExtra("mode","edit").putExtra("id",uiId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();click("invoice.import");
        deadline=System.currentTimeMillis()+25000;boolean imported=false;while(System.currentTimeMillis()<deadline){final boolean[] found={false};runOnMainSync(()->found[0]=find(activity.getWindow().getDecorView(),"invoice.preview.1")!=null);if(found[0]){imported=true;break;}Thread.sleep(100);}removeMonitor(documents);check(imported,"document picker PDF copied into editor");click("invoice.save");
        deadline=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<deadline){saved=live.get(uiId);if(saved.getJSONArray("files").length()==2)break;Thread.sleep(100);}check(saved.getJSONArray("files").length()==2,"multi-document edit saved");check(saved.getJSONArray("files").getJSONObject(1).getString("mime").equals("application/pdf"),"PDF type preserved after picker import");
        waitForClosed(activity);
        Intent detail=new Intent(getTargetContext(),InvoiceActivity.class).putExtra("mode","view").putExtra("id",uiId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);activity=startActivitySync(detail);waitForIdleSync();final boolean[] exists={false};runOnMainSync(()->exists[0]=find(activity.getWindow().getDecorView(),"invoice.document.0")!=null);check(exists[0],"saved document visible after reopening");
        ActivityMonitor viewer=addMonitor(InvoiceViewerActivity.class.getName(),null,false);click("invoice.document.0");Activity opened=waitForMonitorWithTimeout(viewer,15000);check(opened!=null,"viewer opened");removeMonitor(viewer);
        deadline=System.currentTimeMillis()+25000;boolean rendered=false;while(System.currentTimeMillis()<deadline){final boolean[] ready={false};runOnMainSync(()->{View v=find(opened.getWindow().getDecorView(),"invoice.viewer.caption");ready[0]=v instanceof TextView&&((TextView)v).getText().toString().contains("Page 1");});if(ready[0]){rendered=true;break;}Thread.sleep(100);}check(rendered,"saved photo rendered in zoom viewer");runOnMainSync(()->opened.finish());
        check(new InvoiceStore(getTargetContext()).get(uiId).getString("amount").equals("4321.50"),"persisted invoice after view launch");
        android.net.Uri capture=InvoiceProvider.capture(getTargetContext(),InvoiceStore.id()+".jpg");byte[] original=jpeg();try(OutputStream output=getTargetContext().getContentResolver().openOutputStream(capture,"w")){output.write(original);}
        try(ParcelFileDescriptor ignored=getTargetContext().getContentResolver().openFileDescriptor(capture,"rw")){}try(InputStream input=getTargetContext().getContentResolver().openInputStream(capture)){check(bytes(input).length==original.length,"camera rw reopen preserves photo data");}
        // Confirm read-only export provider cannot be used to overwrite invoices.
        File exported=live.exportPdf(saved);fails(()->{try(OutputStream ignored=getTargetContext().getContentResolver().openOutputStream(InvoiceProvider.exported(exported.getName()))){}} ,"exports URI is read-only");
        fails(()->{try(android.os.ParcelFileDescriptor ignored=getTargetContext().getContentResolver().openFileDescriptor(android.net.Uri.parse("content://dz.electrogas.documents/capture/..%2Findex.json"),"w")){}} ,"provider rejects traversal");
    }
}
