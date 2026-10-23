package dz.electrogas;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Private documents, atomic metadata commits and portable, additive backups. */
public final class InvoiceStore {
    public static final int FREE_LIMIT=5, MAX_FILES=10, MAX_PAGES=30;
    private static final long MEDIA_LIMIT=20L*1024*1024, BACKUP_LIMIT=512L*1024*1024;
    final File root, cache;
    private static final Object LOCK=new Object();
    public InvoiceStore(Context c) { root=new File(c.getFilesDir(),"invoices");cache=new File(c.getCacheDir(),"invoice-work");root.mkdirs();cache.mkdirs(); }
    public static String id() {return UUID.randomUUID().toString();}
    static boolean safeId(String s) {return s!=null&&s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    static boolean safeName(String s) {return s!=null&&s.matches("[0-9a-f-]{36}\\.(jpg|pdf)")&&safeId(s.substring(0,36));}
    public File staging(String token) throws IOException {if(!safeId(token))throw new IOException("Brouillon invalide.");File f=new File(cache,token);f.mkdirs();return f;}
    public static JSONObject create() throws JSONException {
        return new JSONObject().put("id",id()).put("title","Facture Sonelgaz").put("date",new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date()))
            .put("period","").put("home","Mon logement").put("amount","").put("paid",false).put("files",new JSONArray());
    }
    private AtomicFile index(){return new AtomicFile(new File(root,"index.json"));}
    public List<JSONObject> list() throws IOException {
        synchronized(LOCK) {
            List<JSONObject> result=new ArrayList<>();
            if(!new File(root,"index.json").exists()&&!new File(root,"index.json.bak").exists())return result;
            try(InputStream in=index().openRead()) {
                JSONObject data=new JSONObject(new String(read(in,4*1024*1024),"UTF-8"));
                if(data.getInt("version")!=1)throw new IOException("Version de l’historique inconnue.");
                JSONArray rows=data.getJSONArray("invoices");
                for(int i=0;i<rows.length();i++){JSONObject b=rows.getJSONObject(i);validate(b);result.add(b);}
            }catch(JSONException e){throw new IOException("Historique illisible. Restaurez une sauvegarde sans effacer vos données.",e);}
            Collections.sort(result,(a,b)->b.optString("date").compareTo(a.optString("date")));return result;
        }
    }
    public JSONObject get(String id) throws IOException {for(JSONObject b:list())if(b.optString("id").equals(id))return b;throw new IOException("Facture introuvable.");}
    private void write(List<JSONObject> bills) throws IOException {
        FileOutputStream out=null;AtomicFile atomic=index();
        try {JSONArray rows=new JSONArray();for(JSONObject b:bills)rows.put(b);byte[] bytes=new JSONObject().put("version",1).put("invoices",rows).toString().getBytes("UTF-8");
            out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);
        }catch(Exception e){if(out!=null)atomic.failWrite(out);throw new IOException("Impossible d’enregistrer l’historique.",e);}
    }
    public static void validate(JSONObject b) throws IOException {
        try {
            if(!safeId(b.getString("id")))throw new IOException("Identifiant de facture invalide.");
            for(String k:new String[]{"title","date","period","home","amount"})if(b.getString(k).length()>160)throw new IOException("Texte trop long.");
            if(b.getString("title").trim().isEmpty())throw new IOException("Indiquez un titre.");
            SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);String date=b.getString("date");
            if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!f.format(f.parse(date)).equals(date))throw new IOException("Date invalide.");
            String amount=b.getString("amount");
            if(!amount.isEmpty()&&(!amount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")||new BigDecimal(amount).signum()<0))throw new IOException("Montant invalide : deux décimales maximum.");
            b.getBoolean("paid");JSONArray files=b.getJSONArray("files");
            if(files.length()<1||files.length()>MAX_FILES)throw new IOException("Ajoutez entre 1 et 10 photos ou PDF.");
            Set<String> names=new HashSet<>();
            for(int i=0;i<files.length();i++) {
                JSONObject m=files.getJSONObject(i);String name=m.getString("name"),mime=m.getString("mime");
                if(!safeName(name)||!names.add(name)||!mime.equals(name.endsWith(".pdf")?"application/pdf":"image/jpeg"))throw new IOException("Document invalide.");
            }
        }catch(JSONException|java.text.ParseException|NumberFormatException e){throw new IOException("Informations de facture invalides.",e);}
    }
    public File media(JSONObject bill,JSONObject media,String stage) throws IOException {
        String id=bill.optString("id"),name=media.optString("name");if(!safeId(id)||!safeName(name))throw new IOException("Document invalide.");
        if(stage!=null){File pending=new File(staging(stage),name);if(pending.isFile())return pending;}
        File f=new File(new File(root,id),name);if(!f.isFile())throw new IOException("Copie manquante. Restaurez votre sauvegarde.");return f;
    }
    /** No consumer-side override: purchase entitlement must come from verified Play billing later. */
    public boolean isPremium(){return false;}
    public void save(JSONObject bill,String stage) throws IOException {
        synchronized(LOCK) {
            validate(bill);List<JSONObject> bills=list();boolean existing=false;
            for(JSONObject b:bills)if(b.optString("id").equals(bill.optString("id")))existing=true;
            if(!existing&&!isPremium()&&bills.size()>=FREE_LIMIT)throw new IOException("La version gratuite conserve 5 factures. L’achat Premium sera disponible dans une prochaine version.");
            JSONArray files=bill.optJSONArray("files");int pages=0;long size=0;List<File> copied=new ArrayList<>();
            File dir=new File(root,bill.optString("id"));dir.mkdirs();
            try {
                for(int i=0;i<files.length();i++) {
                    JSONObject m=files.optJSONObject(i);File src=media(bill,m,stage);pages+=pageCount(src);size+=src.length();
                    if(pages>MAX_PAGES||size>50L*1024*1024)throw new IOException("Maximum 30 pages et 50 Mo par facture.");
                    File dest=new File(dir,m.optString("name"));if(!src.equals(dest)){copy(src,dest,MEDIA_LIMIT);copied.add(dest);}
                }
                for(Iterator<JSONObject> it=bills.iterator();it.hasNext();)if(it.next().optString("id").equals(bill.optString("id")))it.remove();
                bills.add(bill);write(bills);
            }catch(IOException e){for(File f:copied)f.delete();throw e;}
            Set<String> keep=new HashSet<>();for(int i=0;i<files.length();i++)keep.add(files.optJSONObject(i).optString("name"));
            File[] old=dir.listFiles();if(old!=null)for(File f:old)if(!keep.contains(f.getName()))f.delete();
            if(stage!=null)remove(staging(stage));
        }
    }
    public void delete(String id) throws IOException {synchronized(LOCK){if(!safeId(id))throw new IOException("Identifiant invalide.");List<JSONObject> bills=list();for(Iterator<JSONObject> it=bills.iterator();it.hasNext();)if(it.next().optString("id").equals(id))it.remove();write(bills);remove(new File(root,id));}}
    public JSONObject importMedia(InputStream input,String token) throws IOException {
        File dir=staging(token),raw=new File(dir,id()+".tmp"),out=null;
        try {
            try(OutputStream dest=new FileOutputStream(raw)){transfer(input,dest,MEDIA_LIMIT);}
            byte[] header=new byte[5];try(InputStream in=new FileInputStream(raw)){if(in.read(header)<5)throw new IOException("Document vide ou invalide.");}
            boolean pdf=new String(header,"US-ASCII").equals("%PDF-");
            out=new File(dir,id()+(pdf?".pdf":".jpg"));
            if(pdf){if(!raw.renameTo(out))throw new IOException("Impossible de copier le PDF.");pageCount(out);}
            else normalize(raw,out);
            return new JSONObject().put("name",out.getName()).put("mime",pdf?"application/pdf":"image/jpeg");
        }catch(Exception e){if(out!=null)out.delete();throw new IOException(e instanceof IOException?e.getMessage():"Ce document n’est pas une image ou un PDF lisible.",e);}
        finally{raw.delete();}
    }
    private static void normalize(File raw,File out) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(raw.getPath(),options);
        if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>120000000)throw new IOException("Image non prise en charge ou trop grande.");
        options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>3072)options.inSampleSize*=2;
        Bitmap image=BitmapFactory.decodeFile(raw.getPath(),options);if(image==null)throw new IOException("Image illisible.");
        try {
            int orientation=1;try{orientation=new ExifInterface(raw.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;default:break;}
            if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);if(rotated!=image){image.recycle();image=rotated;}}
            if(image.hasAlpha()){Bitmap opaque=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(opaque);canvas.drawColor(Color.WHITE);canvas.drawBitmap(image,0,0,null);image.recycle();image=opaque;}
            try(OutputStream stream=new FileOutputStream(out)){if(!image.compress(Bitmap.CompressFormat.JPEG,90,stream))throw new IOException("Copie de l’image impossible.");}
        }finally{image.recycle();}
    }
    static int pageCount(File f) throws IOException {
        if(!f.isFile()||f.length()==0||f.length()>MEDIA_LIMIT)throw new IOException("Document vide ou supérieur à 20 Mo.");
        if(f.getName().endsWith(".pdf")) {
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)) {
                int n=pdf.getPageCount();if(n<1||n>MAX_PAGES)throw new IOException("Le PDF doit contenir de 1 à 30 pages.");return n;
            }catch(SecurityException|IllegalArgumentException e){throw new IOException("PDF protégé ou illisible.",e);}
        }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        if(o.outWidth<=0||o.outHeight<=0||(long)o.outWidth*o.outHeight>120000000)throw new IOException("Image illisible.");return 1;
    }
    public static Bitmap preview(File f,int page) throws IOException {
        if(f.getName().endsWith(".pdf")) {
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd);PdfRenderer.Page p=pdf.openPage(page)) {
                float scale=1400f/Math.max(p.getWidth(),p.getHeight());Bitmap b=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return b;
            }
        }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1800)o.inSampleSize*=2;o.inJustDecodeBounds=false;
        Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);if(b==null)throw new IOException("Image illisible.");return b;
    }
    public File exportPdf(JSONObject bill) throws IOException {
        File dir=new File(cache,"exports");dir.mkdirs();File dest=new File(dir,id()+".pdf");
        PdfDocument pdf=new PdfDocument();try {
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(9,45,50));paint.setTextSize(20);
            PdfDocument.Page cover=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());Canvas c=cover.getCanvas();c.drawColor(Color.WHITE);c.drawText("ElectroGas — Copie de facture",36,60,paint);paint.setTextSize(14);
            String[] lines={bill.optString("title"),"Date : "+bill.optString("date"),"Période : "+bill.optString("period"),"Logement : "+bill.optString("home"),"Montant : "+bill.optString("amount")+" DA",bill.optBoolean("paid")?"Payée":"À payer","Documents joints aux pages suivantes.","Copie numérique — ne certifie pas le paiement."};
            int y=105;for(String line:lines){String shown=line.length()>65?line.substring(0,62)+"…":line;c.drawText(shown,36,y,paint);y+=30;}pdf.finishPage(cover);
            JSONArray files=bill.optJSONArray("files");int index=2;
            for(int i=0;i<files.length();i++){File file=media(bill,files.optJSONObject(i),null);int count=pageCount(file);for(int p=0;p<count;p++){
                Bitmap bitmap=preview(file,p);try {PdfDocument.Page sheet=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,index++).create());Canvas canvas=sheet.getCanvas();canvas.drawColor(Color.WHITE);
                    float scale=Math.min(559f/bitmap.getWidth(),806f/bitmap.getHeight()),w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;canvas.drawBitmap(bitmap,null,new RectF((595-w)/2,(842-h)/2,(595+w)/2,(842+h)/2),null);pdf.finishPage(sheet);
                }finally{bitmap.recycle();}
            }}
            try(OutputStream out=new FileOutputStream(dest)){pdf.writeTo(out);}return dest;
        }catch(Exception e){dest.delete();throw new IOException("Export PDF impossible : "+e.getMessage(),e);}finally{try{pdf.close();}catch(IllegalStateException ignored){}}
    }
    public void backup(OutputStream output) throws IOException {
        synchronized(LOCK){List<JSONObject> bills=list();try(ZipOutputStream zip=new ZipOutputStream(output)) {
            JSONArray rows=new JSONArray();for(JSONObject b:bills)rows.put(b);
            try{zip.putNextEntry(new ZipEntry("index.json"));zip.write(new JSONObject().put("version",1).put("invoices",rows).toString().getBytes("UTF-8"));zip.closeEntry();}catch(JSONException e){throw new IOException(e);}
            long size=0;for(JSONObject b:bills){JSONArray files=b.optJSONArray("files");for(int i=0;i<files.length();i++){JSONObject m=files.optJSONObject(i);File f=media(b,m,null);size+=f.length();if(size>BACKUP_LIMIT)throw new IOException("Sauvegarde limitée à 512 Mo par fichier.");zip.putNextEntry(new ZipEntry(b.optString("id")+"/"+m.optString("name")));try(InputStream in=new FileInputStream(f)){transfer(in,zip,MEDIA_LIMIT);}zip.closeEntry();}}
        }}
    }
    /** Validates everything in temporary storage before an atomic merge; existing IDs are never overwritten. */
    public int restore(InputStream input) throws IOException {
        synchronized(LOCK) {
            File stage=staging(id()),archive=new File(stage,"backup.zip");List<File> installed=new ArrayList<>();
            try {
                try(OutputStream out=new FileOutputStream(archive)){transfer(input,out,BACKUP_LIMIT);}
                List<JSONObject> current=list();Set<String> existing=new HashSet<>();for(JSONObject b:current)existing.add(b.optString("id"));
                try(ZipFile zip=new ZipFile(archive)) {
                    Set<String> paths=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();long declared=0,actual=0;int entryCount=0;
                    while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();String n=e.getName();if(++entryCount>5501||e.isDirectory()||!paths.add(n)||!(n.equals("index.json")||n.matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|pdf)")))throw new IOException("Chemin ou doublon invalide dans la sauvegarde.");if(e.getSize()<0||e.getSize()>(n.equals("index.json")?4*1024*1024:MEDIA_LIMIT))throw new IOException("Document de sauvegarde trop grand.");declared+=e.getSize();if(declared>BACKUP_LIMIT)throw new IOException("Sauvegarde supérieure à 512 Mo.");}
                    ZipEntry meta=zip.getEntry("index.json");if(meta==null)throw new IOException("Sauvegarde ElectroGas attendue.");JSONObject data;try(InputStream in=zip.getInputStream(meta)){data=new JSONObject(new String(read(in,4*1024*1024),"UTF-8"));}
                    if(data.getInt("version")!=1)throw new IOException("Version de sauvegarde inconnue.");JSONArray rows=data.getJSONArray("invoices");if(rows.length()>500)throw new IOException("Maximum 500 factures par sauvegarde.");
                    Set<String> ids=new HashSet<>(),expected=new HashSet<>();expected.add("index.json");List<JSONObject> additions=new ArrayList<>();
                    for(int i=0;i<rows.length();i++){JSONObject b=rows.getJSONObject(i);validate(b);String id=b.getString("id");if(!ids.add(id))throw new IOException("Facture dupliquée dans la sauvegarde.");JSONArray files=b.getJSONArray("files");File dir=new File(stage,id);dir.mkdirs();int pages=0;long size=0;
                        for(int j=0;j<files.length();j++){JSONObject m=files.getJSONObject(j);String path=id+"/"+m.getString("name");expected.add(path);ZipEntry e=zip.getEntry(path);if(e==null)throw new IOException("Document manquant dans la sauvegarde.");File f=new File(dir,m.getString("name"));try(InputStream in=zip.getInputStream(e);OutputStream out=new FileOutputStream(f)){transfer(in,out,MEDIA_LIMIT);}actual+=f.length();if(actual>BACKUP_LIMIT)throw new IOException("Sauvegarde supérieure à 512 Mo après extraction.");pages+=pageCount(f);size+=f.length();if(pages>MAX_PAGES||size>50L*1024*1024)throw new IOException("Facture trop volumineuse.");}
                        if(!existing.contains(id))additions.add(b);
                    }
                    if(!expected.equals(paths))throw new IOException("Fichiers inattendus dans la sauvegarde.");
                    for(JSONObject b:additions){File src=new File(stage,b.getString("id")),dest=new File(root,b.getString("id"));if(dest.exists())throw new IOException("Conflit de restauration. Aucune facture existante n’a été remplacée.");if(!src.renameTo(dest))throw new IOException("Restauration impossible.");installed.add(dest);current.add(b);}
                    write(current);return additions.size();
                }
            }catch(Exception e){for(File f:installed)remove(f);throw new IOException(e instanceof IOException?e.getMessage():"Sauvegarde invalide.",e);}
            finally{remove(stage);}
        }
    }
    static byte[] read(InputStream in,long limit) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();transfer(in,out,limit);return out.toByteArray();}
    static void copy(File src,File dest,long limit) throws IOException {try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(dest)){transfer(in,out,limit);}catch(IOException e){dest.delete();throw e;}}
    static void transfer(InputStream in,OutputStream out,long limit) throws IOException {if(in==null)throw new IOException("Document inaccessible.");byte[] b=new byte[32768];long size=0;int n;while((n=in.read(b))!=-1){size+=n;if(size>limit)throw new IOException("Fichier trop volumineux.");out.write(b,0,n);}}
    static void remove(File f){File[] files=f.listFiles();if(files!=null)for(File child:files)remove(child);f.delete();}
}
