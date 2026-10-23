package dz.electrogas;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public final class InvoiceActivity extends DocumentActivity {
    static final int CAMERA=41,IMPORT=42,BACKUP=43,RESTORE=44,PDF=45;
    InvoiceStore store;JSONObject bill;String stage,capture,mode,filter="";File pendingPdf;
    final Map<String,EditText> inputs=new LinkedHashMap<>();CheckBox paid;LinearLayout pages,listContainer;boolean saved,dirty;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new InvoiceStore(this);mode=getIntent().getStringExtra("mode");if(mode==null)mode="list";
        try {
            if(mode.equals("edit")){
                stage=state==null?InvoiceStore.id():state.getString("stage");capture=state==null?null:state.getString("capture");
                bill=state!=null?new JSONObject(state.getString("bill")):getIntent().hasExtra("id")?store.get(getIntent().getStringExtra("id")):InvoiceStore.create();
                dirty=state!=null&&state.getBoolean("dirty");editor();
            }else if(mode.equals("view")){bill=store.get(getIntent().getStringExtra("id"));detail();}else history();
            if(state!=null&&state.getString("pdf")!=null)pendingPdf=new File(new File(store.cache,"exports"),state.getString("pdf"));
        }catch(Exception e){error(e.getMessage());}
    }
    @Override protected void onResume(){super.onResume();if(store!=null&&mode!=null){if(mode.equals("list")&&listContainer!=null)refresh();else if(mode.equals("view")&&bill!=null)try{bill=store.get(bill.optString("id"));detail();}catch(IOException e){finish();}}}
    @Override protected void onSaveInstanceState(Bundle out){if(mode.equals("edit")&&bill!=null){harvest();out.putString("bill",bill.toString());out.putString("stage",stage);out.putString("capture",capture);out.putBoolean("dirty",dirty);}if(pendingPdf!=null)out.putString("pdf",pendingPdf.getName());super.onSaveInstanceState(out);}
    void open(String mode,String id){Intent i=new Intent(this,InvoiceActivity.class).putExtra("mode",mode);if(id!=null)i.putExtra("id",id);startActivity(i);}
    void history(){
        screen("Mes factures","Gardez une copie, retrouvez votre historique.");
        LinearLayout c=card();c.addView(text("Carnet de factures",20,INK,true));c.addView(text("5 factures gratuites. Photos et PDF, plusieurs pages par facture. Les copies restent sur cet appareil.",14,MUTED,false));
        button(c,"＋ Ajouter une facture","archive.add",true,()->{try{if(store.list().size()>=InvoiceStore.FREE_LIMIT&&!store.isPremium())premium();else open("edit",null);}catch(IOException e){error(e.getMessage());}});
        button(c,"Découvrir Premium","archive.premium",false,()->premium());
        EditText search=new EditText(this);search.setHint("Rechercher : année, logement, période…");search.setSingleLine(true);search.setTextSize(15);search.setContentDescription("Rechercher une facture");search.setTag("archive.search");body.addView(search);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int d){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int a,int b,int d){filter=s.toString();refresh();}});
        listContainer=column();body.addView(listContainer);
        c=card();c.addView(text("Protégez votre historique",20,INK,true));c.addView(text("La perte du téléphone ou la désinstallation peut effacer les copies locales. Exportez régulièrement une sauvegarde ZIP vers un emplacement sûr, sur votre PC ou votre espace cloud personnel.",14,MUTED,false));
        button(c,"Exporter une sauvegarde ZIP","archive.backup",false,()->createDocument("application/zip","ElectroGas-factures-"+new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date())+".zip",BACKUP));
        button(c,"Restaurer une sauvegarde ZIP","archive.restore",false,()->new AlertDialog.Builder(this).setTitle("Restaurer un historique").setMessage("Les nouvelles factures seront ajoutées. Les factures ayant le même identifiant seront conservées sans être remplacées. La restauration reste disponible au-delà de la limite gratuite.").setNegativeButton("Annuler",null).setPositiveButton("Choisir le ZIP",(d,w)->pick("application/zip",RESTORE)).show());refresh();
    }
    void refresh(){
        listContainer.removeAllViews();try {
            List<JSONObject> bills=store.list();TextView count=text(bills.size()+" facture"+(bills.size()>1?"s":"")+" enregistrée"+(bills.size()>1?"s":""),14,MUTED,true);count.setTag("archive.count");listContainer.addView(count);
            int shown=0;String q=filter.toLowerCase(Locale.FRANCE);
            for(JSONObject b:bills){String searchable=b.optString("title")+" "+b.optString("date")+" "+b.optString("home")+" "+b.optString("period")+" "+(b.optBoolean("paid")?"payée":"à payer");if(!searchable.toLowerCase(Locale.FRANCE).contains(q))continue;shown++;
                LinearLayout item=column();item.setPadding(dp(16),dp(12),dp(16),dp(16));item.setBackground(background(android.graphics.Color.WHITE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));listContainer.addView(item,p);
                item.addView(text(b.optString("title"),18,INK,true));item.addView(text(b.optString("date")+" · "+b.optString("home"),13,MUTED,false));
                item.addView(text((b.optString("amount").isEmpty()?"Montant non renseigné":b.optString("amount").replace('.',',')+" DA")+" · "+(b.optBoolean("paid")?"Payée":"À payer"),16,TEAL,true));
                button(item,"Ouvrir la facture","invoice.open."+b.optString("id"),false,()->open("view",b.optString("id")));
            }
            if(shown==0)listContainer.addView(text(bills.isEmpty()?"Votre première facture vous attend. Photographiez-la ou importez une copie pour commencer.":"Aucune facture ne correspond à cette recherche.",16,MUTED,false));
        }catch(IOException e){listContainer.addView(text(e.getMessage(),15,INK,false));}
    }
    void premium(){new AlertDialog.Builder(this).setTitle("ElectroGas Premium").setMessage("Prévu : historique illimité de factures sur cet appareil.\n\nLa version actuelle permet de conserver 5 factures gratuitement, avec export et restauration des sauvegardes.\n\nL’achat Premium n’est pas encore disponible : aucun paiement ne sera demandé dans cette version.\n\nVos factures existantes et vos sauvegardes restent accessibles, même si la limite gratuite est dépassée après restauration.").setPositiveButton("Compris",null).show();}
    void editor(){
        screen(getIntent().hasExtra("id")?"Modifier la facture":"Nouvelle facture","Prenez une photo lisible, puis vérifiez les informations.");
        LinearLayout c=card();c.addView(text("Informations",20,INK,true));
        field(c,"Titre","title",false);field(c,"Date de facture · AAAA-MM-JJ","date",false);
        inputs.get("date").setFocusable(false);inputs.get("date").setOnClickListener(v->{Calendar cal=Calendar.getInstance();try{cal.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(inputs.get("date").getText().toString()));}catch(java.text.ParseException ignored){}new DatePickerDialog(this,(view,y,m,d)->inputs.get("date").setText(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d)),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});
        field(c,"Période · exemple : juillet–septembre 2026","period",false);field(c,"Logement / compteur","home",false);field(c,"Montant réel de la facture · DA (facultatif)","amount",true);
        paid=new CheckBox(this);paid.setText("Facture payée");paid.setTag("invoice.paid");paid.setChecked(bill.optBoolean("paid"));paid.setOnCheckedChangeListener((v,b)->dirty=true);c.addView(paid);
        c=card();c.addView(text("Documents joints",20,INK,true));c.addView(text("Jusqu’à 10 photos ou PDF, 30 pages au total. Les informations sont saisies manuellement ; aucune lecture automatique n’est activée.",13,MUTED,false));
        button(c,"Photographier une page","invoice.camera",true,()->camera());button(c,"Importer des photos ou un PDF","invoice.import",false,()->pickMedia());pages=column();c.addView(pages);renderPages();
        button(body,"Enregistrer la facture","invoice.save",true,()->{harvest();try{InvoiceStore.validate(bill);}catch(IOException e){error(e.getMessage());return;}work("Enregistrement de la copie…",()->{store.save(bill,stage);return true;},v->{saved=true;dirty=false;Toast.makeText(this,"Facture enregistrée",Toast.LENGTH_SHORT).show();finish();});});
        button(body,"Annuler","invoice.cancel",false,()->onBackPressed());
    }
    void field(LinearLayout c,String label,String key,boolean number){c.addView(text(label,14,INK,true));EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(17);e.setTag("invoice."+key);e.setContentDescription(label);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(background(0xffeef5f2));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setText(bill.optString(key));c.addView(e);inputs.put(key,e);e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int d){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int a,int b,int d){dirty=true;}});}
    void harvest(){if(bill==null)return;try{for(Map.Entry<String,EditText> e:inputs.entrySet()){String value=e.getValue().getText().toString().trim();bill.put(e.getKey(),e.getKey().equals("amount")?value.replace(',','.'):value);}if(paid!=null)bill.put("paid",paid.isChecked());}catch(JSONException ignored){}}
    void renderPages(){pages.removeAllViews();JSONArray files=bill.optJSONArray("files");for(int i=0;i<files.length();i++){final int n=i;JSONObject media=files.optJSONObject(i);LinearLayout row=column();pages.addView(row);button(row,(media.optString("mime").equals("application/pdf")?"PDF":"Photo")+" "+(i+1)+" · Voir","invoice.preview."+i,false,()->preview(n));button(row,"Retirer ce document","invoice.remove."+i,false,()->new AlertDialog.Builder(this).setMessage("Retirer ce document du brouillon ?").setNegativeButton("Conserver",null).setPositiveButton("Retirer",(d,w)->{files.remove(n);dirty=true;renderPages();}).show());}}
    void preview(int index){harvest();startActivity(new Intent(this,InvoiceViewerActivity.class).putExtra("bill",bill.toString()).putExtra("stage",stage).putExtra("index",index));}
    void camera(){if(bill.optJSONArray("files").length()>=InvoiceStore.MAX_FILES){error("Maximum 10 documents par facture.");return;}capture=InvoiceStore.id()+".jpg";Uri uri=InvoiceProvider.capture(this,capture);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri);i.setClipData(ClipData.newRawUri("Photo de facture",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{startActivityForResult(i,CAMERA);}catch(ActivityNotFoundException e){capture=null;error("Aucune application photo disponible. Importez une photo depuis votre PC ou votre galerie.");}}
    void pickMedia(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"}).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,IMPORT);}catch(ActivityNotFoundException e){error("Aucun sélecteur de documents disponible.");}}
    void pick(String mime,int request){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE),request);}catch(ActivityNotFoundException e){error("Aucun sélecteur de documents disponible.");}}
    void createDocument(String type,String name,int request){try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),request);}catch(ActivityNotFoundException e){error("Aucun emplacement d’export disponible.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==CAMERA){final String name=capture;capture=null;if(name==null)return;final File source=new File(new File(getCacheDir(),"invoice-capture"),name);Uri uri=InvoiceProvider.capture(this,name);revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(result!=RESULT_OK){source.delete();return;}work("Préparation de la photo…",()->{try(InputStream in=new FileInputStream(source)){return store.importMedia(in,stage);}finally{source.delete();}},m->{bill.getJSONArray("files").put(m);dirty=true;renderPages();});return;
        }
        if(result!=RESULT_OK||data==null)return;
        if(request==IMPORT){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;
            if(bill.optJSONArray("files").length()+uris.size()>InvoiceStore.MAX_FILES){error("Maximum 10 documents par facture. Sélectionnez moins de fichiers.");return;}
            work("Copie des documents…",()->{List<JSONObject> added=new ArrayList<>();try{for(Uri u:uris)try(InputStream in=getContentResolver().openInputStream(u)){added.add(store.importMedia(in,stage));}return added;}catch(Exception e){for(JSONObject m:added)new File(store.staging(stage),m.optString("name")).delete();throw e;}},added->{for(JSONObject m:added)bill.getJSONArray("files").put(m);dirty=true;renderPages();});
        }else if(data.getData()!=null){final Uri uri=data.getData();if(request==BACKUP)work("Export de la sauvegarde…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Emplacement inaccessible.");store.backup(out);}return true;},v->new AlertDialog.Builder(this).setTitle("Sauvegarde exportée").setMessage("Vérifiez que le ZIP se trouve hors du téléphone si vous souhaitez vous protéger contre sa perte. Cette sauvegarde contient vos factures ; conservez-la dans un emplacement privé.").setPositiveButton("Compris",null).show());
            else if(request==RESTORE)work("Vérification et restauration…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){return store.restore(in);}},n->{refresh();Toast.makeText(this,n+" nouvelle(s) facture(s) restaurée(s)",Toast.LENGTH_LONG).show();});
            else if(request==PDF&&pendingPdf!=null){final File pdf=pendingPdf;pendingPdf=null;work("Enregistrement du PDF…",()->{try(InputStream in=new FileInputStream(pdf);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Emplacement inaccessible.");InvoiceStore.transfer(in,out,100L*1024*1024);}return true;},v->Toast.makeText(this,"PDF exporté",Toast.LENGTH_LONG).show());}
        }
    }
    void detail(){
        screen("Ma facture",bill.optString("title"));LinearLayout c=card();c.addView(text(bill.optString("title"),21,INK,true));
        for(String k:new String[]{"date","period","home"})c.addView(text(bill.optString(k),16,MUTED,false));c.addView(text(bill.optString("amount").isEmpty()?"Montant non renseigné":bill.optString("amount").replace('.',',')+" DA",26,TEAL,true));c.addView(text(bill.optBoolean("paid")?"Payée":"À payer",16,TEAL,true));
        JSONArray files=bill.optJSONArray("files");for(int i=0;i<files.length();i++){final int index=i;button(c,"Voir le document "+(i+1)+(files.optJSONObject(i).optString("name").endsWith(".pdf")?" · PDF":" · Photo"),"invoice.document."+i,true,()->preview(index));}
        button(c,"Modifier les informations / documents","invoice.edit",false,()->open("edit",bill.optString("id")));
        button(c,"Partager la facture en PDF","invoice.share",false,()->work("Création du PDF…",()->store.exportPdf(bill),pdf->{Uri uri=InvoiceProvider.exported(pdf.getName());Intent share=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("Facture",uri));startActivity(Intent.createChooser(share,"Partager la copie de facture"));}));
        button(c,"Enregistrer une copie PDF","invoice.exportPdf",false,()->work("Création du PDF…",()->store.exportPdf(bill),pdf->{pendingPdf=pdf;createDocument("application/pdf","Facture-ElectroGas-"+bill.optString("date")+".pdf",PDF);}));
        c=card();c.addView(text("Copie numérique, pas justificatif de paiement. Exportez une sauvegarde avant toute désinstallation.",13,MUTED,false));button(c,"Supprimer cette facture","invoice.delete",false,()->new AlertDialog.Builder(this).setTitle("Supprimer définitivement ?").setMessage("La facture et ses copies locales seront supprimées. Exportez d’abord une sauvegarde si vous souhaitez les conserver.").setNegativeButton("Conserver",null).setPositiveButton("Supprimer",(d,w)->work("Suppression…",()->{store.delete(bill.optString("id"));return true;},v->finish())).show());
    }
    @Override public void onBackPressed(){if(busy)return;if(mode!=null&&mode.equals("edit")&&dirty&&!saved)new AlertDialog.Builder(this).setTitle("Quitter sans enregistrer ?").setMessage("Les nouveaux documents de ce brouillon ne seront pas conservés.").setNegativeButton("Continuer la saisie",null).setPositiveButton("Abandonner",(d,w)->{try{InvoiceStore.remove(store.staging(stage));}catch(IOException ignored){}dirty=false;finish();}).show();else super.onBackPressed();}
}
